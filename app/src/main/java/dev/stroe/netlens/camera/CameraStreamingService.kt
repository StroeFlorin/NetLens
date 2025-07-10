package dev.stroe.netlens.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import dev.stroe.netlens.server.MjpegHttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class Resolution(val width: Int, val height: Int, val name: String) {
    override fun toString(): String = "$name (${width}x${height})"
}

class CameraStreamingService(private val context: Context) {
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null
    private var mjpegServer: MjpegHttpServer? = null
    private var currentResolution: Resolution = Resolution(1280, 720, "HD")

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "CameraStreamingService"
    }

    fun getAvailableResolutions(): List<Resolution> {
        return try {
            val cameraId = cameraManager.cameraIdList[0]
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val outputSizes = map?.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()

            val commonResolutions = listOf(
                Resolution(1920, 1080, "Full HD"),
                Resolution(1280, 720, "HD"),
                Resolution(854, 480, "WVGA"),
                Resolution(640, 480, "VGA"),
                Resolution(352, 288, "CIF"),
                Resolution(320, 240, "QVGA")
            )

            commonResolutions.filter { resolution ->
                outputSizes.any { it.width == resolution.width && it.height == resolution.height }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available resolutions", e)
            listOf(Resolution(640, 480, "VGA"))
        }
    }

    fun setResolution(resolution: Resolution) {
        currentResolution = resolution
        Log.d(TAG, "Resolution changed to: ${resolution.name}")

        // If streaming, restart with new resolution
        if (mjpegServer != null) {
            serviceScope.launch {
                Log.d(TAG, "Restarting streaming with new resolution")
                closeCamera()
                openCamera()
            }
        }
    }

    fun getCurrentResolution(): Resolution = currentResolution

    fun startStreaming() {
        serviceScope.launch {
            Log.d(TAG, "Starting camera streaming")
            startBackgroundThread()
            openCamera()
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

    private fun openCamera() {
        try {
            val cameraId = cameraManager.cameraIdList[0]
            Log.d(TAG, "Opening camera: $cameraId")

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val outputSizes = map?.getOutputSizes(ImageFormat.JPEG)

            // Use the selected resolution
            val size = outputSizes?.find {
                it.width == currentResolution.width && it.height == currentResolution.height
            } ?: outputSizes?.get(0) ?: Size(640, 480)

            Log.d(TAG, "Selected resolution: ${size.width}x${size.height}")

            imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
            if (mjpegServer == null) {
                mjpegServer = MjpegHttpServer(8082)
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
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    camera.close()
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
            Log.d(TAG, "Creating capture session")
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

    private fun startRepeatingRequest() {
        val surface = imageReader?.surface
        if (surface != null) {
            Log.d(TAG, "Starting repeating capture request")
            val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureRequestBuilder?.addTarget(surface)
            captureRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            captureRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

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
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
        Log.d(TAG, "Camera closed")
    }
}