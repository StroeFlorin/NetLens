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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
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

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                            cameraService.setCamera(camera)
                            // Save the new camera
                            appPreferences.saveCamera(camera)
                            // Update available resolutions for the new camera
                            availableResolutions = cameraService.getAvailableResolutions()
                            // Reset to default resolution for the new camera
                            selectedResolution = cameraService.getCurrentResolution()
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
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            TopAppBar(
                                title = { Text("NetLens") },
                                actions = {
                                    IconButton(
                                        onClick = { showSettings = true },
                                        enabled = !isStreaming
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                                    }
                                }
                            )
                        },
                        contentWindowInsets = WindowInsets(0.dp) // Remove default insets to reduce space
                    ) { innerPadding ->
                        CameraStreamingUI(
                            modifier = Modifier.padding(innerPadding),
                            isStreaming = isStreaming,
                            streamUrl = streamUrl,
                            selectedResolution = selectedResolution,
                            selectedPort = selectedPort,
                            selectedCamera = selectedCamera,
                            selectedFPS = selectedFPS,
                            selectedQuality = selectedQuality,
                            selectedOrientation = selectedOrientation,
                            cameraService = if (::cameraService.isInitialized) cameraService else null,
                            onStartStreaming = { startStreaming() },
                            onStopStreaming = { stopStreaming() }
                        )
                    }
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
        when (orientationSetting?.mode) {
            OrientationMode.LANDSCAPE.name -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            OrientationMode.PORTRAIT.name -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
            cameraService.stopStreaming()
        }
        // Ensure wake lock is released when activity is destroyed
        releaseWakeLock()
    }

    override fun onPause() {
        super.onPause()
        // Keep streaming when app goes to background, but optionally release wake lock
        // depending on your app's requirements. For now, we'll keep it active.
    }

    override fun onResume() {
        super.onResume()
        // Re-acquire wake lock if streaming is active and wake lock was lost
        if (isStreaming && (wakeLock == null || !wakeLock!!.isHeld)) {
            acquireWakeLock()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CameraStreamingUI(
    modifier: Modifier = Modifier,
    isStreaming: Boolean,
    streamUrl: String,
    selectedResolution: Resolution?,
    selectedPort: String,
    selectedCamera: CameraInfo?,
    selectedFPS: FPSSetting?,
    selectedQuality: QualitySetting?,
    selectedOrientation: OrientationSetting?,
    cameraService: CameraStreamingService?,
    onStartStreaming: () -> Unit,
    onStopStreaming: () -> Unit
) {
    val scrollState = rememberScrollState()
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
            Log.e("CameraStreamingUI", "Error updating orientation in LaunchedEffect", e)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            // Camera Stream Display - Only show when streaming
            if (isStreaming && streamUrl.isNotEmpty()) {
                // Calculate aspect ratio based ONLY on the camera resolution and orientation setting
                // This ensures the preview maintains the correct ratio regardless of device orientation
                val aspectRatio = selectedResolution?.let { resolution ->
                    val orientationMode = selectedOrientation?.mode?.let { mode ->
                        try { dev.stroe.netlens.camera.OrientationMode.valueOf(mode) }
                        catch (e: Exception) { dev.stroe.netlens.camera.OrientationMode.AUTO }
                    } ?: dev.stroe.netlens.camera.OrientationMode.AUTO

                    when (orientationMode) {
                        dev.stroe.netlens.camera.OrientationMode.LANDSCAPE -> resolution.width.toFloat() / resolution.height.toFloat()
                        dev.stroe.netlens.camera.OrientationMode.PORTRAIT -> resolution.height.toFloat() / resolution.width.toFloat()
                        dev.stroe.netlens.camera.OrientationMode.AUTO -> if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) resolution.width.toFloat() / resolution.height.toFloat() else resolution.height.toFloat() / resolution.width.toFloat()
                    }
                } ?: run { 4f / 3f }

                Card(
                    modifier = Modifier
                        .let { modifier ->
                            val orientationMode = selectedOrientation?.mode?.let { mode ->
                                try { dev.stroe.netlens.camera.OrientationMode.valueOf(mode) }
                                catch (e: Exception) { dev.stroe.netlens.camera.OrientationMode.AUTO }
                            } ?: dev.stroe.netlens.camera.OrientationMode.AUTO

                            val isDeviceLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                            val shouldLimitWidth = when (orientationMode) {
                                dev.stroe.netlens.camera.OrientationMode.PORTRAIT -> isDeviceLandscape
                                dev.stroe.netlens.camera.OrientationMode.LANDSCAPE -> !isDeviceLandscape
                                dev.stroe.netlens.camera.OrientationMode.AUTO -> false
                            }

                            if (shouldLimitWidth) modifier.fillMaxWidth(0.6f).aspectRatio(aspectRatio) else modifier.fillMaxWidth().aspectRatio(aspectRatio)
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    CameraPreview(
                        cameraService = cameraService,
                        selectedOrientation = selectedOrientation,
                        modifier = Modifier.padding(2.dp)
                    )
                }
            }
            // Settings and Status Display
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = if (isStreaming) BorderStroke(2.dp, Color(0xFF4CAF50)) else null
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = if (isStreaming) "Streaming Active" else "Current Settings",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isStreaming) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isStreaming && streamUrl.isNotEmpty()) {
                        Text(
                            text = "Stream URL:",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = streamUrl,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("Camera: ")
                            }
                            append(selectedCamera?.toString() ?: "Not selected")
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("Resolution: ")
                            }
                            append(selectedResolution?.toString() ?: "Not selected")
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("Port: ")
                            }
                            append(selectedPort)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("Frame Rate: ")
                            }
                            append(selectedFPS?.toString() ?: "Not selected")
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("Video Quality: ")
                            }
                            append(selectedQuality?.toString() ?: "Not selected")
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                append("Orientation: ")
                            }
                            append(selectedOrientation?.toString() ?: "Not selected")
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Button( 
                onClick = if (isStreaming) onStopStreaming else onStartStreaming,
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedResolution != null && selectedPort.toIntOrNull()?.let { it in 1024..65535 } == true,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isStreaming) Color.Red else Color(0xFF4CAF50),
                    contentColor = Color.White
                )
            ) {
                Text(if (isStreaming) "STOP STREAMING" else "START STREAMING")
            }

            // Made with love attribution
            Text(
                text = "Made with ❤️ by Florin Stroe",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }

@Composable
fun CameraPreview(
    cameraService: CameraStreamingService?,
    selectedOrientation: OrientationSetting?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
                    }
                })
                
                surfaceView
            },
            update = { surfaceView ->
                // No manual rotation needed - the Camera2 API handles orientation internally
                // through the capture request JPEG_ORIENTATION setting in the camera service
                Log.d("CameraPreview", "Preview updated for orientation: ${selectedOrientation?.mode}")
            },
            modifier = Modifier
                .fillMaxSize() // Fill the parent size to match aspect ratio above
        )
    }
}
