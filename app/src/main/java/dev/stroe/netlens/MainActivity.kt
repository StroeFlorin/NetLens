package dev.stroe.netlens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.view.SurfaceView
import android.view.SurfaceHolder
import dev.stroe.netlens.camera.CameraStreamingService
import dev.stroe.netlens.camera.Resolution
import dev.stroe.netlens.camera.CameraInfo
import dev.stroe.netlens.camera.FPSSetting
import dev.stroe.netlens.camera.QualitySetting
import dev.stroe.netlens.camera.OrientationSetting
import dev.stroe.netlens.camera.OrientationMode
import android.content.pm.ActivityInfo
import dev.stroe.netlens.preferences.AppPreferences
import dev.stroe.netlens.ui.theme.NetLensTheme
import dev.stroe.netlens.ui.SettingsScreen
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {
    private lateinit var cameraService: CameraStreamingService
    private lateinit var appPreferences: AppPreferences
    private var wakeLock: PowerManager.WakeLock? = null
    private var isStreaming by mutableStateOf(false)
    private var streamUrl by mutableStateOf("")
    private var availableResolutions by mutableStateOf<List<Resolution>>(emptyList())
    private var selectedResolution by mutableStateOf<Resolution?>(null)
    private var selectedPort by mutableStateOf("8080")
    private var availableCameras by mutableStateOf<List<CameraInfo>>(emptyList())
    private var selectedCamera by mutableStateOf<CameraInfo?>(null)
    private var availableFPS by mutableStateOf<List<FPSSetting>>(emptyList())
    private var selectedFPS by mutableStateOf<FPSSetting?>(null)
    private var availableQuality by mutableStateOf<List<QualitySetting>>(emptyList())
    private var selectedQuality by mutableStateOf<QualitySetting?>(null)
    private var availableOrientation by mutableStateOf<List<OrientationSetting>>(emptyList())
    private var selectedOrientation by mutableStateOf<OrientationSetting?>(null)
    private var showSettings by mutableStateOf(false)
    private var keepScreenOn by mutableStateOf(true)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initializeCameraService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Hide system bars for immersive experience
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // Initialize preferences
        appPreferences = AppPreferences(this)
        
        // Load saved settings
        loadSettings()
        // Lock orientation based on saved setting
        applyOrientationLock(selectedOrientation)

        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                initializeCameraService()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        setContent {
            NetLensTheme {
                if (showSettings) {
                    SettingsScreen(
                        availableCameras = availableCameras,
                        selectedCamera = selectedCamera,
                        availableResolutions = availableResolutions,
                        selectedResolution = selectedResolution,
                        availableFPS = availableFPS,
                        selectedFPS = selectedFPS,
                        availableQuality = availableQuality,
                        selectedQuality = selectedQuality,
                        availableOrientation = availableOrientation,
                        selectedOrientation = selectedOrientation,
                        selectedPort = selectedPort,
                        onCameraChanged = { camera ->
                            selectedCamera = camera
                            // Stop current preview/streaming
                            if (isStreaming) {
                                cameraService.stopStreaming()
                            } else {
                                cameraService.stopPreviewOnly()
                            }
                            // Apply new camera settings
                            cameraService.setCamera(camera)
                            // Save the new camera
                            appPreferences.saveCamera(camera)
                            // Update available resolutions for the new camera
                            availableResolutions = cameraService.getAvailableResolutions()
                            // Reset to default resolution for the new camera
                            selectedResolution = cameraService.getCurrentResolution()
                            // Restart preview with new camera
                            if (!isStreaming) {
                                cameraService.startPreviewOnly()
                            }
                        },
                        onResolutionChanged = { resolution ->
                            selectedResolution = resolution
                            cameraService.setResolution(resolution)
                            // Save the new resolution
                            appPreferences.saveResolution(resolution)
                        },
                        onFPSChanged = { fps ->
                            selectedFPS = fps
                            cameraService.setFPS(fps)
                            // Save the new FPS
                            appPreferences.saveFPS(fps)
                        },
                        onQualityChanged = { quality ->
                            selectedQuality = quality
                            cameraService.setQuality(quality)
                            // Save the new quality
                            appPreferences.saveQuality(quality)
                        },
                        onOrientationChanged = { orientation ->
                            selectedOrientation = orientation
                            cameraService.setOrientationSetting(orientation)
                            // Save the new orientation
                            appPreferences.saveOrientationSetting(orientation)
                            // Lock orientation immediately
                            this@MainActivity.applyOrientationLock(orientation)
                        },
                        onPortChanged = { port ->
                            selectedPort = port
                            // Save the new port
                            appPreferences.savePort(port)
                        },
                        onNavigateBack = { showSettings = false }
                    )                } else {
                    // Full-screen camera view with overlay controls
                    FullScreenCameraUI(
                        isStreaming = isStreaming,
                        streamUrl = streamUrl,
                        selectedResolution = selectedResolution,
                        selectedPort = selectedPort,
                        selectedCamera = selectedCamera,
                        selectedFPS = selectedFPS,
                        selectedOrientation = selectedOrientation,
                        cameraService = if (::cameraService.isInitialized) cameraService else null,
                        onStartStreaming = { startStreaming() },
                        onStopStreaming = { stopStreaming() },
                        onShowSettings = { showSettings = true }
                    )
                }
            }
        }
    }

    private fun loadSettings() {
        // Load saved port
        selectedPort = appPreferences.getPort()
        
        // Load saved resolution (will be applied after camera service is initialized)
        if (appPreferences.hasResolution()) {
            selectedResolution = appPreferences.getResolution()
        }
        
        // Load saved camera (will be applied after camera service is initialized)
        if (appPreferences.hasCamera()) {
            selectedCamera = appPreferences.getCamera()
        }
        
        // Load saved FPS (will be applied after camera service is initialized)
        if (appPreferences.hasFPS()) {
            selectedFPS = appPreferences.getFPS()
        }
        
        // Load saved quality (will be applied after camera service is initialized)
        if (appPreferences.hasQuality()) {
            selectedQuality = appPreferences.getQuality()
        }
        
        // Load saved orientation (will be applied after camera service is initialized)
        if (appPreferences.hasOrientationSetting()) {
            selectedOrientation = appPreferences.getOrientationSetting()
        }
        
        // Load keep screen on setting
        keepScreenOn = appPreferences.getKeepScreenOn()
    }
    // Apply activity orientation lock based on orientation setting
    private fun applyOrientationLock(orientationSetting: OrientationSetting?) {
        requestedOrientation = when (orientationSetting?.mode) {
            OrientationMode.LANDSCAPE.name -> {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }

            OrientationMode.PORTRAIT.name -> {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            else -> {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    private fun initializeCameraService() {
        cameraService = CameraStreamingService(this)
        availableCameras = cameraService.getAvailableCameras()
        
        // Set initial orientation
        val orientation = getDeviceOrientation()
        cameraService.setOrientation(orientation)
        
        // Apply saved camera if available and valid
        val savedCamera = selectedCamera
        if (savedCamera != null) {
            // Check if the saved camera is still available
            val matchingCamera = availableCameras.find { 
                it.id == savedCamera.id 
            }
            if (matchingCamera != null) {
                selectedCamera = matchingCamera
                cameraService.setCamera(matchingCamera)
            } else {
                // Fallback to first available camera if saved camera is no longer available
                selectedCamera = availableCameras.firstOrNull()
                selectedCamera?.let { cameraService.setCamera(it) }
            }
        } else {
            // No saved camera, use first available
            selectedCamera = availableCameras.firstOrNull()
            selectedCamera?.let { cameraService.setCamera(it) }
        }
        
        availableResolutions = cameraService.getAvailableResolutions()
        
        // Apply saved resolution if available and valid
        val savedResolution = selectedResolution
        if (savedResolution != null) {
            // Check if the saved resolution is still available
            val matchingResolution = availableResolutions.find { 
                it.width == savedResolution.width && it.height == savedResolution.height 
            }
            if (matchingResolution != null) {
                selectedResolution = matchingResolution
                cameraService.setResolution(matchingResolution)
            } else {
                // Fallback to default if saved resolution is no longer available
                selectedResolution = cameraService.getCurrentResolution()
            }
        } else {
            selectedResolution = cameraService.getCurrentResolution()
        }
        
        // Initialize available FPS settings
        availableFPS = cameraService.getAvailableFPS()
        
        // Apply saved FPS if available and valid
        val savedFPS = selectedFPS
        if (savedFPS != null) {
            // Check if the saved FPS is still available
            val matchingFPS = availableFPS.find { 
                it.fps == savedFPS.fps && it.delayMs == savedFPS.delayMs 
            }
            if (matchingFPS != null) {
                selectedFPS = matchingFPS
                cameraService.setFPS(matchingFPS)
            } else {
                // Fallback to default if saved FPS is no longer available
                selectedFPS = cameraService.getCurrentFPS()
            }
        } else {
            selectedFPS = cameraService.getCurrentFPS()
        }
        
        // Initialize available quality settings
        availableQuality = cameraService.getAvailableQuality()
        
        // Apply saved quality if available and valid
        val savedQuality = selectedQuality
        if (savedQuality != null) {
            // Check if the saved quality is still available
            val matchingQuality = availableQuality.find { 
                it.quality == savedQuality.quality 
            }
            if (matchingQuality != null) {
                selectedQuality = matchingQuality
                cameraService.setQuality(matchingQuality)
            } else {
                // Fallback to default if saved quality is no longer available
                selectedQuality = cameraService.getCurrentQuality()
            }
        } else {
            selectedQuality = cameraService.getCurrentQuality()
        }
        
        // Initialize available orientation settings
        availableOrientation = cameraService.getAvailableOrientationSettings()
        
        // Apply saved orientation if available and valid
        val savedOrientation = selectedOrientation
        if (savedOrientation != null) {
            // Check if the saved orientation is still available
            val matchingOrientation = availableOrientation.find { 
                it.mode == savedOrientation.mode 
            }
            if (matchingOrientation != null) {
                selectedOrientation = matchingOrientation
                cameraService.setOrientationSetting(matchingOrientation)
            } else {
                // Fallback to default if saved orientation is no longer available
                selectedOrientation = cameraService.getCurrentOrientationSetting()
            }
        } else {
            selectedOrientation = cameraService.getCurrentOrientationSetting()
        }
        
        // Camera will be opened when preview surface is set or streaming starts
        Log.d("MainActivity", "Camera service initialized - ready for preview")
    }

    private fun startStreaming() {
        if (::cameraService.isInitialized) {
            try {
                val port = selectedPort.toIntOrNull() ?: 8082
                val orientation = getDeviceOrientation()
                cameraService.setOrientation(orientation)
                
                // Acquire wake lock to prevent device from sleeping
                acquireWakeLock()
                
                cameraService.startStreaming(port)
                isStreaming = true
                val localIp = getLocalIpAddress()
                streamUrl = "http://${localIp}:${port}/stream"
                Log.d("MainActivity", "Started streaming on port $port with orientation $orientation")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error starting streaming", e)
                isStreaming = false
                streamUrl = ""
                // Release wake lock if streaming failed to start
                releaseWakeLock()
            }
        }
    }

    private fun stopStreaming() {
        if (::cameraService.isInitialized) {
            try {
                cameraService.stopStreaming()
                isStreaming = false
                streamUrl = ""
                Log.d("MainActivity", "Stopped streaming")
                
                // Release wake lock when streaming stops
                releaseWakeLock()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error stopping streaming", e)
                isStreaming = false
                streamUrl = ""
                // Ensure wake lock is released even if stopping fails
                releaseWakeLock()
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "NetLens::StreamingWakeLock"
            )
            
            // Acquire wake lock with a timeout of 1 hour (3600000 ms)
            // This ensures the OS will clean up the wake lock if the app doesn't properly release it
            wakeLock?.acquire(3600000L) // 1 hour timeout
            
            // Keep screen on while streaming if the setting is enabled
            if (keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            
            Log.d("MainActivity", "Wake lock acquired with 1 hour timeout - device will stay awake during streaming")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error acquiring wake lock", e)
        }
    }
    
    private fun releaseWakeLock() {
        try {
            wakeLock?.let { lock ->
                if (lock.isHeld) {
                    lock.release()
                    Log.d("MainActivity", "Wake lock released")
                }
            }
            wakeLock = null
            
            // Remove keep screen on flag
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error releasing wake lock", e)
        }
    }

    internal fun getDeviceOrientation(): Int {
        val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        
        return when (display?.rotation) {
            Surface.ROTATION_0 -> 0    // Portrait
            Surface.ROTATION_90 -> 90  // Landscape (rotated 90 degrees counter-clockwise)
            Surface.ROTATION_180 -> 180 // Portrait (upside down)
            Surface.ROTATION_270 -> 270 // Landscape (rotated 90 degrees clockwise)
            else -> 0
        }
    }

    private fun getLocalIpAddress(): String {
        return NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .find { !it.isLoopbackAddress && it.address.size == 4 }
            ?.hostAddress ?: "localhost"
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        
        // Update orientation when configuration changes
        if (::cameraService.isInitialized) {
            try {
                val orientation = getDeviceOrientation()
                cameraService.setOrientation(orientation)
                Log.d("MainActivity", "Orientation changed to: $orientation")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error updating orientation", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraService.isInitialized) {
            cameraService.shutdown()
        }
        // Ensure wake lock is released when activity is destroyed
        releaseWakeLock()
    }

    override fun onPause() {
        super.onPause()
        // Keep streaming when app goes to background, but optionally release wake lock
        // depending on your app's requirements. For now, we'll keep it active.
        // Note: We don't stop the camera preview on pause to maintain streaming
    }

    override fun onResume() {
        super.onResume()
        // Re-acquire wake lock if streaming is active and wake lock was lost
        if (isStreaming && (wakeLock == null || !wakeLock!!.isHeld)) {
            acquireWakeLock()
        }
        
        // Camera will be managed by the CameraStreamingService
        Log.d("MainActivity", "Activity resumed")
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FullScreenCameraUI(
    isStreaming: Boolean,
    streamUrl: String,
    selectedResolution: Resolution?,
    selectedPort: String,
    selectedCamera: CameraInfo?,
    selectedFPS: FPSSetting?,
    selectedOrientation: OrientationSetting?,
    cameraService: CameraStreamingService?,
    onStartStreaming: () -> Unit,
    onStopStreaming: () -> Unit,
    onShowSettings: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    
    // Track orientation changes and update camera service
    LaunchedEffect(configuration.orientation) {
        try {
            if (context is MainActivity) {
                val orientation = context.getDeviceOrientation()
                cameraService?.setOrientation(orientation)
            }
        } catch (e: Exception) {
            Log.e("FullScreenCameraUI", "Error updating orientation in LaunchedEffect", e)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Full-screen camera preview
        CameraPreview(
            cameraService = cameraService,
            selectedOrientation = selectedOrientation,
            modifier = Modifier.fillMaxSize()
        )
        
        // Top overlay with status
        Card(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding() // Add status bar padding
                .padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                .wrapContentWidth(), // Wrap content width instead of fixed percentage
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                if (isStreaming) {
                    Text(
                        text = "STREAMING",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF4CAF50)
                    )
                    if (streamUrl.isNotEmpty()) {
                        Text(
                            text = streamUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                } else {
                    Text(
                        text = "READY TO STREAM",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedCamera?.toString() ?: "No camera",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                    Text(
                        text = selectedResolution?.toString() ?: "No resolution",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                    Text(
                        text = selectedFPS?.toString() ?: "No FPS",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                    Text(
                        text = "Port: $selectedPort",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1
                    )
                }
            }
        }
        
        // Settings button - always in top right
        FloatingActionButton(
            onClick = onShowSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding() // Add status bar padding
                .padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                .size(48.dp),
            containerColor = Color.Black.copy(alpha = 0.7f),
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(0.dp)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                modifier = Modifier.size(24.dp)
            )
        }
        
        // Bottom overlay with streaming controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding() // Add navigation bar padding
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Start/Stop streaming button
            FloatingActionButton(
                onClick = if (isStreaming) onStopStreaming else onStartStreaming,
                modifier = Modifier.size(64.dp),
                containerColor = if (isStreaming) Color.Red else Color(0xFF4CAF50),
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(
                    if (isStreaming) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = if (isStreaming) "Stop Streaming" else "Start Streaming",
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Made with love attribution
            Text(
                text = "Made with ❤️ by Florin Stroe",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
    }

@Composable
fun CameraPreview(
    cameraService: CameraStreamingService?,
    selectedOrientation: OrientationSetting?,
    modifier: Modifier = Modifier
) {

    // Ensure camera service is ready when service becomes available
    LaunchedEffect(cameraService) {
        cameraService?.let {
            try {
                it.startPreviewOnly()
                Log.d("CameraPreview", "Camera preview started")
            } catch (e: Exception) {
                Log.e("CameraPreview", "Error starting camera preview", e)
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                val surfaceView = SurfaceView(ctx)
                // Lock preview buffer to the camera resolution to preserve aspect ratio
                cameraService?.getCurrentResolution()?.let { res ->
                    surfaceView.holder.setFixedSize(res.width, res.height)
                }
                
                // Configure the surface view for proper scaling
                surfaceView.holder.setKeepScreenOn(true)
                
                surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        Log.d("CameraPreview", "Surface created")
                        // Set the preview surface in the camera service
                        cameraService?.setPreviewSurface(holder.surface)
                        // Note: Camera will start when streaming begins
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                        Log.d("CameraPreview", "Surface changed: ${width}x${height}")
                        // The surface size changed, which may happen during rotation
                        // The camera service will handle orientation through capture requests
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        Log.d("CameraPreview", "Surface destroyed")
                        // Remove the preview surface from the camera service
                        cameraService?.setPreviewSurface(null)
                        // Stop preview if not streaming
                        cameraService?.stopPreviewOnly()
                    }
                })
                
                surfaceView
            },
            update = {
                // No manual rotation needed - the Camera2 API handles orientation internally
                // through the capture request JPEG_ORIENTATION setting in the camera service
                Log.d("CameraPreview", "Preview updated for orientation: ${selectedOrientation?.mode}")
            },
            modifier = Modifier
                .fillMaxSize() // Fill the parent size to match aspect ratio above
        )
    }
}
