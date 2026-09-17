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
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.rodrig20.isodroid.data.UsbIdentity
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
    // USB identity strings shown to the host (empty = Android default).
    val usbIdentity by settingsRepository.usbIdentityFlow.collectAsState(initial = UsbIdentity())
    // Disk image format for newly created images.
    val diskFormat by settingsRepository.diskFormatFlow.collectAsState(initial = SettingsRepository.DISK_FORMAT_DEFAULT)
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
    // USB identity dialog state (empty field = keep Android default).
    var showIdentityDialog by remember { mutableStateOf(false) }
    var dialogManufacturer by remember { mutableStateOf("") }
    var dialogProduct by remember { mutableStateOf("") }
    var dialogSerial by remember { mutableStateOf("") }
    // Disk format picker dialog state.
    var showFormatDialog by remember { mutableStateOf(false) }
    var dialogFormat by remember { mutableStateOf(SettingsRepository.DISK_FORMAT_DEFAULT) }
    // Probe result: format -> "" supported, else reason. Null = not probed yet.
    var fsSupport by remember { mutableStateOf<Map<String, String>?>(null) }

    // Probe tool support whenever the format dialog opens (fast, read-only).
    LaunchedEffect(showFormatDialog) {
        if (!showFormatDialog) return@LaunchedEffect
        val result = rootManager.probeFsTools()
        if (!result.startsWith("Success:")) return@LaunchedEffect
        fsSupport = result.removePrefix("Success:").trim()
            .split(Regex("\\s+"))
            .mapNotNull { token ->
                val kv = token.split("=", limit = 2)
                if (kv.size != 2) null
                else {
                    val value = kv[1].split(":", limit = 2)
                    if (value.getOrNull(0) == "1") kv[0] to ""
                    else kv[0] to (value.getOrNull(1) ?: "unavailable")
                }
            }.toMap()
    }
    // Kernel LUN probe state.
    var isProbing by remember { mutableStateOf(false) }
    var probedMax by remember { mutableStateOf<Int?>(null) }

    // Initialize the charging state when the screen is created
    LaunchedEffect(Unit) {
        rootManager.getChargingState()
    }

    // One-line summary of the USB identity (blank fields show defaults).
    val usbIdentitySummary = listOf(
        usbIdentity.manufacturer,
        usbIdentity.product,
        usbIdentity.serial
    ).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Android defaults" }

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
                icon = Icons.Default.Usb,
                title = "USB identity",
                summary = if (isAppEnabled) "Disable the USB gadget to change ($usbIdentitySummary)"
                else usbIdentitySummary,
                enabled = !isAppEnabled,
                onClick = {
                    dialogManufacturer = usbIdentity.manufacturer
                    dialogProduct = usbIdentity.product
                    dialogSerial = usbIdentity.serial
                    showIdentityDialog = true
                }
            )
            Divider()
            PreferenceRow(
                title = "Disk image format",
                summary = SettingsRepository.diskFormatLabel(diskFormat),
                onClick = {
                    dialogFormat = diskFormat
                    showFormatDialog = true
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

        // USB identity editor dialog: blank fields keep Android defaults; changes apply on next enable and a new serial appears as a new device to the PC.
        if (showIdentityDialog) {
            AlertDialog(
                onDismissRequest = { showIdentityDialog = false },
                title = { Text("USB identity") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dialogManufacturer,
                            onValueChange = { dialogManufacturer = it },
                            label = { Text("Manufacturer") },
                            placeholder = { Text("Android default") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = dialogProduct,
                            onValueChange = { dialogProduct = it },
                            label = { Text("Product") },
                            placeholder = { Text("Android default") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = dialogSerial,
                            onValueChange = { dialogSerial = it },
                            label = { Text("Serial number") },
                            placeholder = { Text("Android default") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Text(
                            text = "Applies on next enable. A new serial shows up as a new device on the PC.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showIdentityDialog = false
                        coroutineScope.launch {
                            settingsRepository.setUsbIdentity(
                                dialogManufacturer,
                                dialogProduct,
                                dialogSerial
                            )
                        }
                    }) { Text("Set") }
                },
                dismissButton = {
                    TextButton(onClick = { showIdentityDialog = false }) { Text("Cancel") }
                }
            )
        }

        // Disk image format picker dialog.
        if (showFormatDialog) {
            AlertDialog(
                onDismissRequest = { showFormatDialog = false },
                title = { Text("Disk image format") },
                text = {
                    Column {
                        SettingsRepository.DISK_FORMATS.forEach { format ->
                            // Null map = probe pending: rows stay enabled.
                            val reason = fsSupport?.get(format)
                            val supported = reason == null || reason.isEmpty()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (supported) Modifier.clickable { dialogFormat = format }
                                        else Modifier
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = dialogFormat == format,
                                    onClick = { dialogFormat = format },
                                    enabled = supported
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(
                                        text = SettingsRepository.diskFormatLabel(format),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (supported) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (!reason.isNullOrEmpty()) {
                                        Text(
                                            text = SettingsRepository.fsProbeReason(reason),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = "Used when creating new disk images.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (fsSupport?.get(dialogFormat)?.isNotEmpty() == true) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    "${SettingsRepository.diskFormatLabel(dialogFormat)} is not supported by this kernel"
                                )
                            }
                            return@TextButton
                        }
                        showFormatDialog = false
                        coroutineScope.launch {
                            settingsRepository.setDiskFormat(dialogFormat)
                        }
                    }) { Text("Set") }
                },
                dismissButton = {
                    TextButton(onClick = { showFormatDialog = false }) { Text("Cancel") }
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
fun SectionHeader(text: String) {
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
fun PreferenceRow(
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
