package com.example.fluorosignalapp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.util.Log
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.File
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

        val jpegSize = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            .getOutputSizes(ImageFormat.JPEG).maxByOrNull { it.width * it.height }!!

        imageReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2)

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

        val captureBuilder = session.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(reader.surface)
            set(CaptureRequest.CONTROL_AE_MODE, previewRequestBuilder.get(CaptureRequest.CONTROL_AE_MODE))
            set(CaptureRequest.SENSOR_SENSITIVITY, previewRequestBuilder.get(CaptureRequest.SENSOR_SENSITIVITY))
            set(CaptureRequest.SENSOR_EXPOSURE_TIME, previewRequestBuilder.get(CaptureRequest.SENSOR_EXPOSURE_TIME))
            set(CaptureRequest.CONTROL_AF_MODE, previewRequestBuilder.get(CaptureRequest.CONTROL_AF_MODE))
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

        // Corrected: Convert the captured image to a PNG byte array.
        val imageBytes = imageToPngByteArray(image)
        image.close()

        return FileManager.saveImageToPending(imageBytes)
    }

    /**
     * Converts an Image object (containing JPEG data) into a lossless PNG byte array.
     */
    private fun imageToPngByteArray(image: Image): ByteArray {
        // 1. Extract the raw JPEG data from the image plane.
        val buffer = image.planes[0].buffer
        val jpegBytes = ByteArray(buffer.remaining())
        buffer.get(jpegBytes)

        // 2. Decode the JPEG byte array into a Bitmap.
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

        // 3. Compress the Bitmap into the PNG format in memory.
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream) // 100 is ignored for PNG (lossless).

        // 4. Return the resulting PNG byte array.
        return outputStream.toByteArray()
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
