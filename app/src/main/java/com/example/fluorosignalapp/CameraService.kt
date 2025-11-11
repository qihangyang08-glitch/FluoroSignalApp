package com.example.fluorosignalapp

import android.annotation.SuppressLint
import android.content.Context
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

    // 1. StateFlow for camera readiness
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
        // 2. Robustness check
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

    /**
     * 拍摄照片并保存到待分析目录
     * @return 保存后的图片文件对象
     */
    suspend fun takePicture(): File {
        if (!isCameraReady.value) throw IllegalStateException("Camera is not ready")
        val session = captureSession ?: throw IllegalStateException("Capture session is null")
        val reader = imageReader ?: throw IllegalStateException("ImageReader is null")

        val captureBuilder = session.device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(reader.surface)
            // Copy parameters from preview
            set(CaptureRequest.CONTROL_AE_MODE, previewRequestBuilder.get(CaptureRequest.CONTROL_AE_MODE))
            set(CaptureRequest.SENSOR_SENSITIVITY, previewRequestBuilder.get(CaptureRequest.SENSOR_SENSITIVITY))
            set(CaptureRequest.SENSOR_EXPOSURE_TIME, previewRequestBuilder.get(CaptureRequest.SENSOR_EXPOSURE_TIME))
            set(CaptureRequest.CONTROL_AF_MODE, previewRequestBuilder.get(CaptureRequest.CONTROL_AF_MODE))
            // You may need to handle orientation dynamically
            set(CaptureRequest.JPEG_ORIENTATION, 90)
        }

        val image = suspendCancellableCoroutine<Image> { continuation ->
            reader.setOnImageAvailableListener({ ir ->
                val latestImage = ir.acquireLatestImage()
                if (latestImage != null) {
                    if (continuation.isActive) continuation.resume(latestImage)
                }
            }, null)
            session.capture(captureBuilder.build(), null, null)
        }

        // 将图片转换为字节数组
        val imageBytes = imageToByteArray(image)
        image.close()

        // 使用FileManager保存图片到待分析目录
        return FileManager.saveImageToPending(imageBytes)
    }

    /**
     * 将Image对象转换为字节数组
     */
    private fun imageToByteArray(image: Image): ByteArray {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    fun getPreviewSize(): Size? = previewSize

    fun closeCamera() {
        _isCameraReady.value = false // Signal that camera is closing
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
            val characteristics = cameraManager.getCameraCharacteristics(id)
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
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
                    device.close()
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
                    session.close()
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
            }, null)
        }

    private fun startPreview(session: CameraCaptureSession, device: CameraDevice, surface: Surface) {
        // CRITICAL FIX: Assign to the class member, not a local variable
        this.previewRequestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }

        session.setRepeatingRequest(previewRequestBuilder.build(), null, null)
        _isCameraReady.value = true // Signal that camera is now ready
    }
}