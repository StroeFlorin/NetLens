package dev.stroe.netlens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.Surface
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
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.stroe.netlens.camera.CameraStreamingService
import dev.stroe.netlens.camera.Resolution
import dev.stroe.netlens.camera.CameraInfo
import dev.stroe.netlens.preferences.AppPreferences
import dev.stroe.netlens.ui.theme.NetLensTheme
import dev.stroe.netlens.ui.SettingsScreen
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {
    private lateinit var cameraService: CameraStreamingService
    private lateinit var appPreferences: AppPreferences
    private var isStreaming by mutableStateOf(false)
    private var streamUrl by mutableStateOf("")
    private var availableResolutions by mutableStateOf<List<Resolution>>(emptyList())
    private var selectedResolution by mutableStateOf<Resolution?>(null)
    private var selectedPort by mutableStateOf("8082")
    private var availableCameras by mutableStateOf<List<CameraInfo>>(emptyList())
    private var selectedCamera by mutableStateOf<CameraInfo?>(null)
    private var showSettings by mutableStateOf(false)

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
    }

    private fun startStreaming() {
        if (::cameraService.isInitialized) {
            try {
                val port = selectedPort.toIntOrNull() ?: 8082
                val orientation = getDeviceOrientation()
                cameraService.setOrientation(orientation)
                cameraService.startStreaming(port)
                isStreaming = true
                val localIp = getLocalIpAddress()
                streamUrl = "http://${localIp}:${port}/stream"
                Log.d("MainActivity", "Started streaming on port $port with orientation $orientation")
            } catch (e: Exception) {
                Log.e("MainActivity", "Error starting streaming", e)
                isStreaming = false
                streamUrl = ""
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
            } catch (e: Exception) {
                Log.e("MainActivity", "Error stopping streaming", e)
                isStreaming = false
                streamUrl = ""
            }
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

            // Camera Stream Display - Only show when streaming
            if (isStreaming && streamUrl.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(
                            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                16f / 9f  // Landscape aspect ratio
                            } else {
                                9f / 16f  // Portrait aspect ratio
                            }
                        ),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    // Display the actual camera stream in a WebView
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = WebViewClient()
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    builtInZoomControls = false
                                    displayZoomControls = false
                                    allowContentAccess = true
                                    allowFileAccess = true
                                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    // Enable auto-fit content to viewport
                                    layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                                    // Disable cache to ensure fresh content on orientation change
                                    cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                                }
                            }
                        },
                        update = { webView ->
                            try {
                                // Use localhost for WebView, but display actual IP in stream URL text
                                val port = selectedPort.toIntOrNull() ?: 8082
                                val localhostUrl = "http://localhost:${port}/stream"
                                if (streamUrl.isNotEmpty()) {
                                    webView.loadUrl(localhostUrl)
                                }
                            } catch (e: Exception) {
                                Log.e("WebView", "Error loading stream URL", e)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }