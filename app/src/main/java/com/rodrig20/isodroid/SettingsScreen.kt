package com.rodrig20.isodroid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rodrig20.isodroid.data.SettingsRepository
import kotlinx.coroutines.launch

/**
 * Settings screen that allows users to configure app settings
 * Supports setting the maximum number of devices and charging suspension
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    rootManager: com.rodrig20.isodroid.manager.RootManager,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }

    // Observe the maximum number of devices from the settings repository
    val maxDevices by settingsRepository.maxDevicesFlow.collectAsState(initial = 1)
    // Observe the charging suspension state from the root manager
    val isChargingSuspended by rootManager.isChargingSuspendedFlow.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // State variable to hold the text field value
    var textValue by remember(maxDevices) { mutableStateOf(maxDevices.toString()) }

    // Initialize the charging state when the screen is created
    LaunchedEffect(Unit) {
        rootManager.getChargingState()
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // App Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Maximum number of devices setting
                    Column {
                        Text(
                            text = "USB  LUNs Limit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Set the maximum number of simulated devices (LUNs) that can be connected simultaneously",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { newValue ->
                                textValue = newValue
                                // Convert to integer and clamp to minimum value of 1
                                newValue.toIntOrNull()?.let { value ->
                                    val clampedValue = if (value < 1) 1 else value
                                    if (clampedValue.toString() != newValue) {
                                        textValue = clampedValue.toString()
                                    }
                                    // Save the new maximum number of devices to the repository
                                    coroutineScope.launch {
                                        settingsRepository.setMaxDevices(clampedValue)
                                    }
                                }
                            },
                            label = { Text("Device Count") },
                            supportingText = {
                                Text("Current limit: $textValue device${if (textValue != "1") "s" else ""}")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            // Device Settings Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Charging suspension switch
                    Column {
                        Text(
                            text = "Allow Charging",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (!isChargingSuspended) "Charging is currently enabled" else "Charging is currently suspended",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (!isChargingSuspended) "Enabled" else "Suspended",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (!isChargingSuspended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Switch(
                                checked = !isChargingSuspended, // Switch is ON when charging is NOT suspended
                                onCheckedChange = { allowCharging ->
                                    coroutineScope.launch {
                                        val result = rootManager.setChargingState(!allowCharging)
                                        if (result.contains("Error")) {
                                            // Handle error if needed
                                            println("Error setting charging state: $result")
                                        }
                                    }
                                },
                                enabled = rootManager.isRooted,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
            }

            // Root Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 2.dp
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (rootManager.isRooted) "Root access granted" else "Root access required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (rootManager.isRooted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = if (rootManager.isRooted) "Device is rooted and ready to use" else "This app requires root access",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (rootManager.isRooted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        if (rootManager.isRooted) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Rooted",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Not Rooted",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
