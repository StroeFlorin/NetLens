package dev.stroe.netlens.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import dev.stroe.netlens.server.MjpegHttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class Resolution(val width: Int, val height: Int, val name: String) {
    override fun toString(): String = "$name (${width}x${height})"
}

data class CameraInfo(val id: String, val name: String, val facing: Int) {
    override fun toString(): String = name
}

data class FPSSetting(val fps: Int, val delayMs: Long, val name: String) {
    override fun toString(): String = name
}

data class QualitySetting(val quality: Int, val name: String) {
    override fun toString(): String = name
}

data class OrientationSetting(val mode: String, val name: String) {
    override fun toString(): String = name
}

enum class OrientationMode {
    AUTO,
    LANDSCAPE,
    PORTRAIT
}

class CameraStreamingService(private val context: Context) {
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var mjpegServer: MjpegHttpServer? = null
    private var currentPort: Int = 8082
    private var currentResolution: Resolution = Resolution(1280, 720, "HD")
    private var currentFPS: FPSSetting = AVAILABLE_FPS_SETTINGS[2] // Default to 30 FPS
    private var currentQuality: QualitySetting = AVAILABLE_QUALITY_SETTINGS[2] // Default to High Quality (85%)
    private var currentOrientation: OrientationSetting = AVAILABLE_ORIENTATION_SETTINGS[0] // Default to Auto
    private var currentCameraId: String = ""
    private var deviceOrientation: Int = 0

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "CameraStreamingService"
        
        val AVAILABLE_FPS_SETTINGS = listOf(
            FPSSetting(15, 66, "15 FPS"),
            FPSSetting(24, 41, "24 FPS"),
            FPSSetting(30, 33, "30 FPS"),
            FPSSetting(60, 16, "60 FPS")
        )
        
        val AVAILABLE_QUALITY_SETTINGS = listOf(
            QualitySetting(50, "Low Quality (50%)"),
            QualitySetting(70, "Medium Quality (70%)"),
            QualitySetting(85, "High Quality (85%)"),
            QualitySetting(95, "Maximum Quality (95%)")
        )
        
        val AVAILABLE_ORIENTATION_SETTINGS = listOf(
            OrientationSetting(OrientationMode.AUTO.name, "Auto (Follow Device)"),
            OrientationSetting(OrientationMode.LANDSCAPE.name, "Force Landscape"),
            OrientationSetting(OrientationMode.PORTRAIT.name, "Force Portrait")
        )
    }

    fun getAvailableCameras(): List<CameraInfo> {
        return try {
            val frontCameraCount = mutableMapOf<Int, Int>()
            val backCameraCount = mutableMapOf<Int, Int>()
            
            cameraManager.cameraIdList.map { cameraId ->
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK
                
                val name = when (facing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> {
                        val count = frontCameraCount.getOrDefault(facing, 0) + 1
                        frontCameraCount[facing] = count
                        if (count == 1) "Front Camera" else "Front Camera $count"
                    }
                    CameraCharacteristics.LENS_FACING_BACK -> {
                        val count = backCameraCount.getOrDefault(facing, 0) + 1
                        backCameraCount[facing] = count
                        if (count == 1) "Back Camera" else "Back Camera $count"
                    }
                    else -> "Camera $cameraId"
                }
                CameraInfo(cameraId, name, facing)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available cameras", e)
            emptyList()
        }
    }

    fun setCamera(cameraInfo: CameraInfo) {
        currentCameraId = cameraInfo.id
        Log.d(TAG, "Camera changed to: ${cameraInfo.name}")

        // If streaming, restart with new camera
        if (mjpegServer != null) {
            serviceScope.launch {
                Log.d(TAG, "Restarting streaming with new camera")
                closeCamera()
                openCamera(currentPort)
            }
        }
    }

    fun getAvailableResolutions(): List<Resolution> {
        return try {
            val cameraId = getCurrentCameraId()
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val outputSizes = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()

            val commonResolutions = listOf(
                Resolution(1920, 1080, "Full HD"),
                Resolution(1280, 720, "HD")
            )

            commonResolutions.filter { resolution ->
                outputSizes.any { it.width == resolution.width && it.height == resolution.height }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available resolutions", e)
            listOf(Resolution(1280, 720, "HD"))
        }
    }

    private fun getCurrentCameraId(): String {
        if (currentCameraId.isEmpty()) {
            currentCameraId = cameraManager.cameraIdList.firstOrNull() ?: "0"
        }
        return currentCameraId
    }

    fun setResolution(resolution: Resolution) {
        currentResolution = resolution
        Log.d(TAG, "Resolution changed to: ${resolution.name}")

        // If streaming, restart with new resolution
        if (mjpegServer != null) {
            serviceScope.launch {
                Log.d(TAG, "Restarting streaming with new resolution")
                closeCamera()
                openCamera(currentPort)
            }
        }
    }

    fun getCurrentResolution(): Resolution = currentResolution

    fun setOrientation(orientation: Int) {
        deviceOrientation = orientation
        Log.d(TAG, "Device orientation changed to: $orientation")
        
        // Only restart if not currently streaming to avoid crashes
        // If streaming, the orientation will be applied on next stream start
        if (mjpegServer != null && cameraDevice != null) {
            Log.d(TAG, "Updating orientation during active streaming")
            // Update the capture request with new orientation without restarting camera
            updateCaptureRequestOrientation()
        }
    }

    fun setFPS(fpsSetting: FPSSetting) {
        currentFPS = fpsSetting
        Log.d(TAG, "FPS changed to: ${fpsSetting.name}")
        
        // Update server FPS if streaming
        mjpegServer?.updateFPS(fpsSetting.delayMs)
    }

    fun getCurrentFPS(): FPSSetting = currentFPS

    fun getAvailableFPS(): List<FPSSetting> = AVAILABLE_FPS_SETTINGS

    fun setQuality(qualitySetting: QualitySetting) {
        currentQuality = qualitySetting
        Log.d(TAG, "Quality changed to: ${qualitySetting.name}")
        
        // If streaming, update the capture request with new quality
        if (mjpegServer != null && cameraDevice != null && captureSession != null) {
            updateCaptureRequestQuality()
        }
    }

    fun getCurrentQuality(): QualitySetting = currentQuality

    fun getAvailableQuality(): List<QualitySetting> = AVAILABLE_QUALITY_SETTINGS

    fun setOrientationSetting(orientationSetting: OrientationSetting) {
        currentOrientation = orientationSetting
        Log.d(TAG, "Orientation setting changed to: ${orientationSetting.name}")
        
        // If streaming, update the capture request with new orientation
        if (mjpegServer != null && cameraDevice != null && captureSession != null) {
            updateCaptureRequestOrientation()
        }
    }

    fun getCurrentOrientationSetting(): OrientationSetting = currentOrientation

    fun getAvailableOrientationSettings(): List<OrientationSetting> = AVAILABLE_ORIENTATION_SETTINGS

    private fun updateCaptureRequestOrientation() {
        val surface = imageReader?.surface
        
        if (surface != null && cameraDevice != null && captureSession != null) {
            try {
                Log.d(TAG, "Updating capture request with new orientation")
                val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                
                captureRequestBuilder?.addTarget(surface)
                
                captureRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                captureRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                
                // Set JPEG orientation based on device orientation and camera characteristics
                val jpegOrientation = calculateJpegOrientation()
                captureRequestBuilder?.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)

                captureSession?.setRepeatingRequest(
                    captureRequestBuilder?.build()!!,
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            // Log.d(TAG, "Capture completed with new orientation")
                        }
                    },
                    backgroundHandler
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error updating capture request orientation", e)
            }
        }
    }

    private fun updateCaptureRequestQuality() {
        val surface = imageReader?.surface
        
        if (surface != null && cameraDevice != null && captureSession != null) {
            try {
                Log.d(TAG, "Updating capture request with new quality: ${currentQuality.quality}")
                val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                
                captureRequestBuilder?.addTarget(surface)
                
                captureRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                captureRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                
                // Set JPEG quality
                captureRequestBuilder?.set(CaptureRequest.JPEG_QUALITY, currentQuality.quality.toByte())
                
                // Set JPEG orientation based on device orientation and camera characteristics
                val jpegOrientation = calculateJpegOrientation()
                captureRequestBuilder?.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)

                captureSession?.setRepeatingRequest(
                    captureRequestBuilder?.build()!!,
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            // Log.d(TAG, "Capture completed with new quality")
                        }
                    },
                    backgroundHandler
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error updating capture request quality", e)
            }
        }
    }

    private fun calculateJpegOrientation(): Int {
        val cameraId = getCurrentCameraId()
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK
        
        // Determine the rotation based on orientation setting
        val rotation = when (OrientationMode.valueOf(currentOrientation.mode)) {
            OrientationMode.AUTO -> {
                // Use device orientation
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.display.rotation
                } else {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay.rotation
                }
            }
            OrientationMode.LANDSCAPE -> Surface.ROTATION_90
            OrientationMode.PORTRAIT -> Surface.ROTATION_0
        }
        
        val degrees = when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        
        val jpegOrientation = if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            // Front camera: mirror the orientation calculation
            (sensorOrientation + degrees) % 360
        } else {
            // Back camera: normal orientation calculation
            (sensorOrientation - degrees + 360) % 360
        }
        
        Log.d(TAG, "Calculated JPEG orientation: $jpegOrientation (sensor: $sensorOrientation, device: $degrees, facing: ${if (facing == CameraCharacteristics.LENS_FACING_FRONT) "front" else "back"}, mode: ${currentOrientation.mode})")
        return jpegOrientation
    }

    fun startStreaming(port: Int = 8082) {
        currentPort = port
        serviceScope.launch {
            Log.d(TAG, "Starting camera streaming on port $port")
            startBackgroundThread()
            openCamera(port)
            startHttpServer()
        }
    }

    fun stopStreaming() {
        serviceScope.launch {
            Log.d(TAG, "Stopping camera streaming")
            closeCamera()
            stopHttpServer()
            stopBackgroundThread()
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread?.looper!!)
        Log.d(TAG, "Background thread started")
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
            Log.d(TAG, "Background thread stopped")
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    private fun openCamera(port: Int = 8082) {
        try {
            val cameraId = getCurrentCameraId()
            Log.d(TAG, "Opening camera: $cameraId")

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val outputSizes = map?.getOutputSizes(ImageFormat.JPEG)
            
            // Get camera orientation
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            Log.d(TAG, "Camera sensor orientation: $sensorOrientation")

            // Use the selected resolution
            val size = outputSizes?.find {
                it.width == currentResolution.width && it.height == currentResolution.height
            } ?: outputSizes?.get(0) ?: Size(640, 480)

            Log.d(TAG, "Selected resolution: ${size.width}x${size.height}")

            imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
            if (mjpegServer == null) {
                mjpegServer = MjpegHttpServer(port, currentFPS.delayMs)
            }

            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                image?.let {
                    try {
                        val buffer = it.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        Log.d(TAG, "Captured frame: ${bytes.size} bytes")
                        mjpegServer?.updateFrame(bytes)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing image", e)
                    } finally {
                        it.close()
                    }
                }
            }, backgroundHandler)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.d(TAG, "Camera opened successfully")
                    cameraDevice = camera
                    createCaptureSession()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.d(TAG, "Camera disconnected")
                    try {
                        camera.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error closing disconnected camera", e)
                    }
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    try {
                        camera.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error closing camera with error", e)
                    }
                    cameraDevice = null
                }
            }, backgroundHandler)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception opening camera", e)
        }
    }

    private fun createCaptureSession() {
        val surface = imageReader?.surface
        
        if (surface != null) {
            Log.d(TAG, "Creating capture session for streaming only")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // Use the modern API for Android 9 (API 28) and above
                val outputConfiguration = OutputConfiguration(surface)
                val sessionConfiguration = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    listOf(outputConfiguration),
                    context.mainExecutor,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            Log.d(TAG, "Capture session configured")
                            captureSession = session
                            startRepeatingRequest()
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e(TAG, "Capture session configuration failed")
                        }
                    }
                )
                cameraDevice?.createCaptureSession(sessionConfiguration)
            } else {
                // Use the legacy API for older versions
                @Suppress("DEPRECATION")
                cameraDevice?.createCaptureSession(
                    listOf(surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            Log.d(TAG, "Capture session configured")
                            captureSession = session
                            startRepeatingRequest()
                        }

                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            Log.e(TAG, "Capture session configuration failed")
                        }
                    },
                    backgroundHandler
                )
            }
        }
    }

    private fun startRepeatingRequest() {
        val surface = imageReader?.surface
        
        if (surface != null) {
            Log.d(TAG, "Starting repeating capture request for streaming")
            val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            
            captureRequestBuilder?.addTarget(surface)
            
            captureRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            captureRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            
            // Set JPEG quality
            captureRequestBuilder?.set(CaptureRequest.JPEG_QUALITY, currentQuality.quality.toByte())
            
            // Set JPEG orientation based on device orientation and camera characteristics
            val jpegOrientation = calculateJpegOrientation()
            captureRequestBuilder?.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation)

            captureSession?.setRepeatingRequest(
                captureRequestBuilder?.build()!!,
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        // Log.d(TAG, "Capture completed")
                    }
                },
                backgroundHandler
            )
        }
    }

    private fun startHttpServer() {
        Log.d(TAG, "Starting HTTP server")
        mjpegServer?.start()
    }

    private fun stopHttpServer() {
        Log.d(TAG, "Stopping HTTP server")
        mjpegServer?.stop()
        mjpegServer = null
    }

    private fun closeCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            imageReader?.close()
            imageReader = null
            Log.d(TAG, "Camera closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
            captureSession = null
            cameraDevice = null
            imageReader = null
        }
    }
}