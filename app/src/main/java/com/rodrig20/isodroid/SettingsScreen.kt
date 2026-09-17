package com.rodrig20.isodroid

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rodrig20.isodroid.data.SettingsRepository
import kotlinx.coroutines.launch

/**
 * Settings screen in the conventional Preferences style: section headers
 * with tappable rows. Editing happens in dialogs; rows only display state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    rootManager: com.rodrig20.isodroid.manager.RootManager,
    onNavigateBack: () -> Unit,
    isAppEnabled: Boolean = false // LUN limit is locked while the gadget is on
) {
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }

    // Observe the maximum number of devices from the settings repository
    val maxDevices by settingsRepository.maxDevicesFlow.collectAsState(initial = 1)
    // Observe the charging suspension state from the root manager
    val isChargingSuspended by rootManager.isChargingSuspendedFlow.collectAsState()
    // False on kernels without a known charging-control node.
    val isChargingSupported by rootManager.isChargingSupportedFlow.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    // Effect verification can take seconds; lock the switch meanwhile.
    var isChargingBusy by remember { mutableStateOf(false) }

    // LUN limit dialog state (text is committed with Set, not per keystroke).
    var showLunDialog by remember { mutableStateOf(false) }
    var dialogText by remember { mutableStateOf("") }
    // Kernel LUN probe state.
    var isProbing by remember { mutableStateOf(false) }
    var probedMax by remember { mutableStateOf<Int?>(null) }

    // Initialize the charging state when the screen is created
    LaunchedEffect(Unit) {
        rootManager.getChargingState()
    }

    // System back button/gesture returns to the home screen.
    BackHandler {
        onNavigateBack()
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader("USB")
            PreferenceRow(
                icon = Icons.Default.Storage,
                title = "LUN limit",
                summary = if (isAppEnabled) "Disable the USB gadget to change ($maxDevices)"
                else {
                    val beyondHosts = maxDevices > 8
                    "$maxDevices device${if (maxDevices != 1) "s" else ""}" +
                        if (beyondHosts) " · some hosts show max 8" else ""
                },
                enabled = !isAppEnabled,
                onClick = {
                    dialogText = maxDevices.toString()
                    showLunDialog = true
                }
            )
            Divider()
            PreferenceRow(
                icon = Icons.Default.BatteryChargingFull,
                title = "Allow charging",
                summary = if (isChargingBusy) "Applying change..."
                else if (!isChargingSupported) "Not supported by this kernel"
                else if (!isChargingSuspended) "Charging is enabled"
                else "Charging is suspended",
                enabled = rootManager.isRooted && isChargingSupported && !isChargingBusy,
                trailing = {
                    Switch(
                        checked = !isChargingSuspended, // Switch is ON when charging is NOT suspended
                        onCheckedChange = { allowCharging ->
                            if (isChargingBusy) return@Switch
                            isChargingBusy = true
                            coroutineScope.launch {
                                try {
                                    val result = rootManager.setChargingState(!allowCharging)
                                    if (result.contains("Error")) {
                                        snackbarHostState.showSnackbar(result)
                                    }
                                } finally {
                                    isChargingBusy = false
                                }
                            }
                        },
                        enabled = rootManager.isRooted && isChargingSupported && !isChargingBusy,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            )
        }

        // LUN limit editor dialog.
        if (showLunDialog) {
            val parsed = dialogText.toIntOrNull()
            AlertDialog(
                onDismissRequest = { showLunDialog = false },
                title = { Text("LUN limit") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dialogText,
                            onValueChange = { dialogText = it },
                            label = { Text("Device Count") },
                            supportingText = {
                                val limit = dialogText.toIntOrNull()
                                Text(
                                    if (limit != null && limit > 8) "Some hosts show max 8 per USB device"
                                    else "Simultaneous virtual USB devices"
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedButton(
                            onClick = {
                                if (isProbing) return@OutlinedButton
                                isProbing = true
                                coroutineScope.launch {
                                    try {
                                        val result = rootManager.probeMaxLuns()
                                        if (result.startsWith("Success:")) {
                                            val probed = result.substring("Success:".length).trim().toIntOrNull()
                                            if (probed != null) {
                                                probedMax = probed
                                            } else {
                                                snackbarHostState.showSnackbar(result)
                                            }
                                        } else {
                                            snackbarHostState.showSnackbar(
                                                result.ifBlank { "Error: Could not probe LUN limit" }
                                            )
                                        }
                                    } finally {
                                        isProbing = false
                                    }
                                }
                            },
                            enabled = rootManager.isRooted && !isProbing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isProbing) "Probing..." else "Detect kernel max")
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val clamped = if (parsed != null && parsed >= 1) parsed else 1
                            showLunDialog = false
                            coroutineScope.launch {
                                settingsRepository.setMaxDevices(clamped)
                            }
                        },
                        enabled = parsed != null
                    ) { Text("Set") }
                },
                dismissButton = {
                    TextButton(onClick = { showLunDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Kernel probe result dialog.
        probedMax?.let { max ->
            AlertDialog(
                onDismissRequest = { probedMax = null },
                title = { Text("Kernel LUN limit") },
                text = {
                    Text(
                        if (max > 8) "This kernel supports up to $max LUNs, but most hosts only show the first 8 without a manual rescan. Apply as the limit?"
                        else "This kernel supports up to $max LUNs. Apply as the limit?"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        probedMax = null
                        showLunDialog = false
                        coroutineScope.launch {
                            settingsRepository.setMaxDevices(max)
                        }
                    }) { Text("Apply") }
                },
                dismissButton = {
                    TextButton(onClick = { probedMax = null }) { Text("Cancel") }
                }
            )
        }
    }
}

/**
 * Small caps section header, as in system Settings.
 */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

/**
 * One conventional preference row: optional icon, title + summary, and an
 * optional trailing control. Tapping fires onClick when enabled.
 */
@Composable
private fun PreferenceRow(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                modifier = Modifier.padding(end = 16.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        trailing?.invoke()
    }
}
