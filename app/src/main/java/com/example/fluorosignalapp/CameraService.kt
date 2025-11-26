package com.example.fluorosignalapp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.TonemapCurve
import android.media.Image
import android.media.ImageReader
import android.util.Log
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

class CameraService(private val context: Context) {

    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    private lateinit var cameraCharacteristics: CameraCharacteristics
    private var previewSize: Size? = null

    private val _isCameraReady = MutableStateFlow(false)
    val isCameraReady = _isCameraReady.asStateFlow()

    @SuppressLint("MissingPermission")
    suspend fun openCamera(surface: Surface, viewWidth: Int, viewHeight: Int) {
        val backCameraId = getBackCameraId() ?: throw IllegalStateException("No back camera found")
        cameraCharacteristics = cameraManager.getCameraCharacteristics(backCameraId)
        previewSize = getOptimalPreviewSize(backCameraId, viewWidth, viewHeight)

        val yuvSize = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            .getOutputSizes(ImageFormat.YUV_420_888).maxByOrNull { it.width * it.height }!!

        imageReader = ImageReader.newInstance(yuvSize.width, yuvSize.height, ImageFormat.YUV_420_888, 2)

        cameraDevice = openCameraDevice(backCameraId)
        val surfaces = listOf(surface, imageReader!!.surface)
        captureSession = createCaptureSession(cameraDevice!!, surfaces)

        startPreview(captureSession!!, cameraDevice!!, surface)
    }

    fun updateParameters(iso: Int, exposureTimeMs: Long) {
        if (!isCameraReady.value || !::previewRequestBuilder.isInitialized) return

        val exposureTimeNs = exposureTimeMs * 1_000_000L
        val isoRange = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val exposureRange = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)

        val clampedIso = iso.coerceIn(isoRange?.lower ?: 100, isoRange?.upper ?: 3200)
        val clampedExposureTime = exposureTimeNs.coerceIn(exposureRange?.lower ?: 1000, exposureRange?.upper ?: 1_000_000_000)

        previewRequestBuilder.apply {
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            set(CaptureRequest.SENSOR_SENSITIVITY, clampedIso)
            set(CaptureRequest.SENSOR_EXPOSURE_TIME, clampedExposureTime)
        }

        try {
            captureSession?.setRepeatingRequest(previewRequestBuilder.build(), null, null)
        } catch (e: Exception) {
            Log.e("CameraService", "Failed to set repeating request", e)
        }
    }

    suspend fun takePicture(): File {
        if (!isCameraReady.value) throw IllegalStateException("Camera is not ready")
        val session = captureSession ?: throw IllegalStateException("Capture session is null")
        val reader = imageReader ?: throw IllegalStateException("ImageReader is null")

        // ====================================================================
        // ATOMIC SNAPSHOT OF EXPOSURE PARAMETERS
        // Purpose: Prevent race conditions where UI thread modifies
        //          previewRequestBuilder during capture request construction.
        // ====================================================================
        if (!::previewRequestBuilder.isInitialized) {
            throw IllegalStateException("Preview request builder not initialized")
        }

        // Take atomic snapshot of current exposure parameters
        val currentIso = previewRequestBuilder.get(CaptureRequest.SENSOR_SENSITIVITY)
            ?: throw IllegalStateException("Current ISO value is null")
        val currentExposure = previewRequestBuilder.get(CaptureRequest.SENSOR_EXPOSURE_TIME)
            ?: throw IllegalStateException("Current exposure time value is null")
        val currentAfMode = previewRequestBuilder.get(CaptureRequest.CONTROL_AF_MODE)
            ?: CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE

        Log.i("CameraService", "Taking picture with ISO=$currentIso, ExposureTime=${currentExposure}ns")

        val captureBuilder = session.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(reader.surface)

            // 1. Force Manual Exposure Mode
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            // Use atomic snapshot values (not re-reading from previewRequestBuilder)
            set(CaptureRequest.SENSOR_SENSITIVITY, currentIso)
            set(CaptureRequest.SENSOR_EXPOSURE_TIME, currentExposure)

            // 2. DISABLE ALL ISP POST-PROCESSING (Scientific Mode)
            set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
            val curve = floatArrayOf(0f, 0f, 1f, 1f) // Linear curve
            set(CaptureRequest.TONEMAP_CURVE, TonemapCurve(curve, curve, curve))
            set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
            set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
            set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF)

            // 3. Other settings (use atomic snapshot, not re-reading from previewRequestBuilder)
            set(CaptureRequest.CONTROL_AF_MODE, currentAfMode)
            set(CaptureRequest.JPEG_ORIENTATION, 90)
        }

        val image = suspendCancellableCoroutine<Image> { continuation ->
            reader.setOnImageAvailableListener({ ir ->
                ir.acquireLatestImage()?.let { img ->
                    if (continuation.isActive) continuation.resume(img)
                }
            }, null)
            session.capture(captureBuilder.build(), null, null)
        }

        val imageBytes = yuvToRgbPngBytes(image)
        image.close()

        return FileManager.saveImageToPending(imageBytes)
    }

    /**
     * Converts an Image in YUV_420_888 format to a lossless RGB PNG byte array.
     * This uses OpenCV for the color space conversion, bypassing Android's Bitmap processing
     * to preserve data linearity before it's saved as a PNG.
     */
    private fun yuvToRgbPngBytes(image: Image): ByteArray {
        if (image.format != ImageFormat.YUV_420_888) {
            throw IllegalArgumentException("Expected YUV_420_888 image, but got ${image.format}")
        }

        val width = image.width
        val height = image.height

        // 1. Extract Y, U, and V planes into a single byte array.
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val yuvBytes = ByteArray(ySize + uSize + vSize)
        yBuffer.get(yuvBytes, 0, ySize)
        uBuffer.get(yuvBytes, ySize, uSize)
        vBuffer.get(yuvBytes, ySize + uSize, vSize)

        // 2. Create an OpenCV Mat from the YUV data.
        val yuvMat = Mat(height + height / 2, width, CvType.CV_8UC1)
        yuvMat.put(0, 0, yuvBytes)
        
        val rgbMat = Mat()

        // 3. Convert YUV to BGR (OpenCV's default color order for RGB).
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2BGR_I420)

        // 4. Encode the BGR Mat to a lossless PNG byte array.
        val buffer = MatOfByte()
        Imgcodecs.imencode(".png", rgbMat, buffer)
        val pngBytes = buffer.toArray()

        // 5. Release native OpenCV resources.
        yuvMat.release()
        rgbMat.release()
        buffer.release()

        return pngBytes
    }

    fun getPreviewSize(): Size? = previewSize

    fun closeCamera() {
        _isCameraReady.value = false
        try {
            captureSession?.close()
            cameraDevice?.close()
            imageReader?.close()
        } catch (e: Exception) {
            Log.e("CameraService", "Error closing camera resources", e)
        }
        captureSession = null
        cameraDevice = null
        imageReader = null
    }

    private fun getOptimalPreviewSize(cameraId: String, viewWidth: Int, viewHeight: Int): Size? {
        val map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val outputSizes = map?.getOutputSizes(android.graphics.SurfaceTexture::class.java)
        if (outputSizes.isNullOrEmpty()) return null
        val targetRatio = viewWidth.toDouble() / viewHeight
        return outputSizes.filter { it.width <= 1920 && it.height <= 1080 }
            .minByOrNull { abs(it.width.toDouble() / it.height - targetRatio) } ?: outputSizes.first()
    }

    private fun getBackCameraId(): String? {
        return cameraManager.cameraIdList.find { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun openCameraDevice(cameraId: String): CameraDevice =
        suspendCancellableCoroutine { continuation ->
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) = continuation.resume(device)
                override fun onDisconnected(device: CameraDevice) { device.close() }
                override fun onError(device: CameraDevice, error: Int) {
                    val exception = RuntimeException("Camera error: $error")
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
            }, null)
        }

    private suspend fun createCaptureSession(device: CameraDevice, surfaces: List<Surface>): CameraCaptureSession =
        suspendCancellableCoroutine { continuation ->
            device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) = continuation.resume(session)
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    val exception = RuntimeException("Capture session configuration failed")
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
            }, null)
        }

    private fun startPreview(session: CameraCaptureSession, device: CameraDevice, surface: Surface) {
        this.previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }
        session.setRepeatingRequest(previewRequestBuilder.build(), null, null)
        _isCameraReady.value = true
    }
}
