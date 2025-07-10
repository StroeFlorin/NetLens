package dev.stroe.netlens.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.stroe.netlens.camera.CameraInfo
import dev.stroe.netlens.camera.Resolution

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    availableCameras: List<CameraInfo>,
    selectedCamera: CameraInfo?,
    availableResolutions: List<Resolution>,
    selectedResolution: Resolution?,
    selectedPort: String,
    onCameraChanged: (CameraInfo) -> Unit,
    onResolutionChanged: (Resolution) -> Unit,
    onPortChanged: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var showCameraDropdown by remember { mutableStateOf(false) }
    var showResolutionDropdown by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Handle system back button
    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Camera Selection
            if (availableCameras.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Camera",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        ExposedDropdownMenuBox(
                            expanded = showCameraDropdown,
                            onExpandedChange = { showCameraDropdown = !showCameraDropdown }
                        ) {
                            OutlinedTextField(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                readOnly = true,
                                value = selectedCamera?.toString() ?: "Select Camera",
                                onValueChange = {},
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCameraDropdown) }
                            )

                            ExposedDropdownMenu(
                                expanded = showCameraDropdown,
                                onDismissRequest = { showCameraDropdown = false }
                            ) {
                                availableCameras.forEach { camera ->
                                    DropdownMenuItem(
                                        text = { Text(camera.toString()) },
                                        onClick = {
                                            onCameraChanged(camera)
                                            showCameraDropdown = false
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                    )
                                }
                            }
                        }
                    }
                }
            }

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
                        placeholder = { Text("8080") },
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
        }
    }
}
