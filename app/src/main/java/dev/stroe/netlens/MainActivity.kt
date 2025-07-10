package dev.stroe.netlens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.stroe.netlens.camera.CameraStreamingService
import dev.stroe.netlens.camera.Resolution
import dev.stroe.netlens.preferences.AppPreferences
import dev.stroe.netlens.ui.theme.NetLensTheme
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {
    private lateinit var cameraService: CameraStreamingService
    private lateinit var appPreferences: AppPreferences
    private var isStreaming by mutableStateOf(false)
    private var streamUrl by mutableStateOf("")
    private var availableResolutions by mutableStateOf<List<Resolution>>(emptyList())
    private var selectedResolution by mutableStateOf<Resolution?>(null)
    private var selectedPort by mutableStateOf("8082")

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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CameraStreamingUI(
                        modifier = Modifier.padding(innerPadding),
                        isStreaming = isStreaming,
                        streamUrl = streamUrl,
                        availableResolutions = availableResolutions,
                        selectedResolution = selectedResolution,
                        selectedPort = selectedPort,
                        onStartStreaming = { startStreaming() },
                        onStopStreaming = { stopStreaming() },
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
                        }
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
    }

    private fun initializeCameraService() {
        cameraService = CameraStreamingService(this)
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
            val port = selectedPort.toIntOrNull() ?: 8082
            cameraService.startStreaming(port)
            isStreaming = true
            streamUrl = "http://${getLocalIpAddress()}:${port}/stream"
        }
    }

    private fun stopStreaming() {
        if (::cameraService.isInitialized) {
            cameraService.stopStreaming()
            isStreaming = false
            streamUrl = ""
        }
    }

    private fun getLocalIpAddress(): String {
        return NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .find { !it.isLoopbackAddress && it.address.size == 4 }
            ?.hostAddress ?: "localhost"
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraService.isInitialized) {
            cameraService.stopStreaming()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraStreamingUI(
    modifier: Modifier = Modifier,
    isStreaming: Boolean,
    streamUrl: String,
    availableResolutions: List<Resolution>,
    selectedResolution: Resolution?,
    selectedPort: String,
    onStartStreaming: () -> Unit,
    onStopStreaming: () -> Unit,
    onResolutionChanged: (Resolution) -> Unit,
    onPortChanged: (String) -> Unit
) {
    var showResolutionDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "NetLens Camera Streaming",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Resolution Selection
        if (availableResolutions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Resolution",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = showResolutionDropdown,
                        onExpandedChange = { showResolutionDropdown = !showResolutionDropdown }
                    ) {
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            readOnly = true,
                            value = selectedResolution?.toString() ?: "Select Resolution",
                            onValueChange = {},
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showResolutionDropdown) }
                        )

                        ExposedDropdownMenu(
                            expanded = showResolutionDropdown,
                            onDismissRequest = { showResolutionDropdown = false }
                        ) {
                            availableResolutions.forEach { resolution ->
                                DropdownMenuItem(
                                    text = { Text(resolution.toString()) },
                                    onClick = {
                                        onResolutionChanged(resolution)
                                        showResolutionDropdown = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Port Configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Server Port",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = selectedPort,
                    onValueChange = { newPort ->
                        // Only allow numbers and limit to reasonable port range
                        if (newPort.all { it.isDigit() } && newPort.length <= 5) {
                            onPortChanged(newPort)
                        }
                    },
                    label = { Text("Port Number") },
                    placeholder = { Text("8082") },
                    enabled = !isStreaming,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = selectedPort.toIntOrNull()?.let { it < 1024 || it > 65535 } ?: true,
                    supportingText = {
                        if (selectedPort.toIntOrNull()?.let { it < 1024 || it > 65535 } == true) {
                            Text("Port must be between 1024 and 65535")
                        } else if (selectedPort.isEmpty()) {
                            Text("Port is required")
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isStreaming) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Streaming Active",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Stream URL:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = streamUrl,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Resolution: ${selectedResolution?.toString() ?: "Unknown"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Port: ${selectedPort}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = if (isStreaming) onStopStreaming else onStartStreaming,
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedResolution != null && selectedPort.toIntOrNull()?.let { it in 1024..65535 } == true
        ) {
            Text(if (isStreaming) "Stop Streaming" else "Start Streaming")
        }
    }
}