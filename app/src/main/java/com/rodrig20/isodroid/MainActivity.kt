package com.rodrig20.isodroid

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.rodrig20.isodroid.data.DiskItemRepository
import com.rodrig20.isodroid.data.SettingsRepository
import com.rodrig20.isodroid.manager.RootManager
import com.rodrig20.isodroid.models.DiskItem
import com.rodrig20.isodroid.ui.theme.ISOdroidTheme
import com.rodrig20.isodroid.utils.getDisplayName
import com.rodrig20.isodroid.utils.getRealPathFromTreeUri
import com.rodrig20.isodroid.utils.getRealPathFromURI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import java.io.File

/**
 * Copies a content URI into app-private storage so the USB gadget (which
 * needs a real file path, not a content:// URI) can use it. Returns the
 * absolute path of the copy, or null when the content can't be read.
 */
private suspend fun copyUriToAppStorage(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            val rawName = getDisplayName(context, uri)?.takeIf { it.isNotBlank() }
                ?: "image_${System.currentTimeMillis()}.iso"
            val safeName = File(rawName).name
            val dir = File(context.filesDir, "images").apply { mkdirs() }
            val out = File(dir, safeName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            out.absolutePath
        } catch (_: Exception) {
            null
        }
    }


// Screen navigation sealed class to handle different screens in the app
sealed class Screen {
    object Home : Screen()
    object Settings : Screen()
}

/**
 * Main activity for the ISOdroid application
 * Handles the initialization of the app and sets the content
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make content appear under the status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }

        setContent {
            ISOdroidTheme {
                App()
            }
        }
    }
}

/**
 * Main App composable that handles the application lifecycle and navigation
 * Manages app state, root checks, and screen navigation
 */
@OptIn(InternalSerializationApi::class)
@Composable
fun App() {
    // State management for app navigation and state
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val diskItemRepository = remember { DiskItemRepository(context) }
    // Observe maximum devices setting from data store
    val maxDevices by settingsRepository.maxDevicesFlow.collectAsState(initial = 15)
    val rootManager = remember { RootManager(context) }
    // Observe app enabled state from root manager
    val isAppEnabled by rootManager.isAppEnabledFlow.collectAsState()
    var isRooted by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    // System UI controller to manage status bar and navigation bar colors
    val systemUiController = rememberSystemUiController()
    val isDarkTheme = isSystemInDarkTheme()

    // Update status bar and navigation bar colors when theme changes
    LaunchedEffect(isDarkTheme) {
        // Get the current theme background color
        val backgroundColor = if (isDarkTheme) {
            com.rodrig20.isodroid.ui.theme.md_theme_dark_background
        } else {
            com.rodrig20.isodroid.ui.theme.md_theme_light_background
        }

        // Apply status bar color to match background
        systemUiController.setStatusBarColor(
            color = backgroundColor,
            darkIcons = !isDarkTheme // Use dark icons for light theme, light icons for dark theme
        )

        // Apply navigation bar color to match surface
        val surfaceColor = if (isDarkTheme) {
            com.rodrig20.isodroid.ui.theme.md_theme_dark_surface
        } else {
            com.rodrig20.isodroid.ui.theme.md_theme_light_surface
        }

        systemUiController.setNavigationBarColor(
            color = surfaceColor,
            darkIcons = !isDarkTheme // Use dark icons for light theme, light icons for dark theme
        )
    }

    // Initialize app when maxDevices changes
    LaunchedEffect(maxDevices) {
        rootManager.setMaxDevicesProvider { maxDevices }
        rootManager.checkRoot()
        isRooted = rootManager.isRooted
        rootManager.initializeAppState()
        rootManager.getChargingState() // Initialize charging state
        isLoading = false
    }

    // Handle app loading and root status
    if (isLoading) {
        // Show loading indicator while checking root status
        Box(modifier = Modifier.fillMaxSize())
    } else if (!isRooted) {
        // Show not rooted screen if device is not rooted
        NotRootedScreen()
    } else {
        // Render appropriate screen based on navigation state
        // Shared snackbar state so enable/disable errors surface in the UI instead of failing silently.
        val snackbarHostState = remember { SnackbarHostState() }
        // While a turn on/off script runs, lock the toggle with a
        // progress bar so taps feel answered immediately.
        var isToggling by remember { mutableStateOf(false) }
        when (currentScreen) {
            is Screen.Home -> HomeScreen(
                isAppEnabled = isAppEnabled,
                snackbarHostState = snackbarHostState,
                isToggling = isToggling,
                onAppEnabledChange = { enabled ->
                    // Handle app enable/disable actions
                    if (isToggling) return@HomeScreen
                    isToggling = true
                    coroutineScope.launch {
                        try {
                            if (enabled) {
                                val result = rootManager.turnOnApp()
                                if (result.startsWith("Error")) {
                                    snackbarHostState.showSnackbar(result)
                                } else if (result.contains("waiting-host")) {
                                    snackbarHostState.showSnackbar(
                                        "Gadget on, but the PC has not enumerated it yet (plug the cable or rescan)"
                                    )
                                }
                            } else {
                                val result = rootManager.turnOffApp()
                                if (result.startsWith("Error")) {
                                    snackbarHostState.showSnackbar(result)
                                }

                                // Eject all active disk items when disabling the app
                                val diskItems = diskItemRepository.diskItems.first()
                                val itemsToEject = mutableListOf<String>()

                                for (item in diskItems) {
                                    if (item.isActive && item.lunId != null) {
                                        diskItemRepository.updateDiskItem(
                                            item.copy(isActive = false, lunId = null)
                                        )
                                        itemsToEject.add(item.lunId)
                                    }
                                }

                                for (lunId in itemsToEject) {
                                    rootManager.ejectItem(lunId)
                                }
                            }
                        } finally {
                            isToggling = false
                        }
                    }
                },
                rootManager = rootManager,
                onNavigateToSettings = { currentScreen = Screen.Settings }
            )
            is Screen.Settings -> SettingsScreen(
                onNavigateBack = { currentScreen = Screen.Home },
                rootManager = rootManager,
                isAppEnabled = isAppEnabled
            )
        }
    }
}

/**
 * Screen displayed when the device is not rooted
 * Shows a message indicating that root access is required
 */
@Composable
fun NotRootedScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
                    Text(
                        text = "Root access is required to use this application.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface, // Explicitly set color
                        modifier = Modifier.padding(16.dp)
                    )
        Button(onClick = { (context as? Activity)?.finish() }) {
            Text("Exit")
        }
    }
}

/**
 * Home screen of the application
 * Displays the app enabler card and list of disk items
 */
@OptIn(ExperimentalMaterial3Api::class, InternalSerializationApi::class)
@Composable
fun HomeScreen(
    isAppEnabled: Boolean, // Whether the USB gadget is currently enabled
    onAppEnabledChange: (Boolean) -> Unit, // Callback for changing the USB gadget state
    rootManager: RootManager, // Manager for root operations
    onNavigateToSettings: () -> Unit, // Callback for navigating to settings
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    isToggling: Boolean = false // A turn on/off script is running
) {
    val context = LocalContext.current
    val diskItemRepository = remember { DiskItemRepository(context) }
    // Observe disk items from the repository
    val itemList by diskItemRepository.diskItems.collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }
    // Item awaiting remove confirmation (null = no dialog).
    var itemPendingRemove by remember { mutableStateOf<DiskItem?>(null) }
    // Checked inside the dialog: also delete the file from storage.
    var deleteFileToo by remember(itemPendingRemove) { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ISOdroid") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            item { SectionHeader("USB gadget") }
            item {
                PreferenceRow(
                    title = "Enable USB gadget",
                    summary = if (isToggling) "Applying USB change..."
                    else if (isAppEnabled) "Phone presents its LUNs to the PC"
                    else "Turn the phone into a USB drive",
                    enabled = rootManager.isRooted && !isToggling,
                    trailing = {
                        Switch(
                            checked = isAppEnabled,
                            onCheckedChange = onAppEnabledChange,
                            enabled = rootManager.isRooted && !isToggling
                        )
                    }
                )
            }
            item { SectionHeader("Disk images") }
            if (itemList.isEmpty()) {
                item {
                    PreferenceRow(
                        title = "No images yet",
                        summary = "Tap + to add an ISO or disk image",
                        enabled = false
                    )
                }
            }
            // Display all disk items in the list
            items(items = itemList, key = { it.id }) { item ->
                // Handle mounting/ejecting of the disk item
                val onToggle = { newItemState: Boolean ->
                    coroutineScope.launch {
                        var updatedItem: DiskItem
                        if (newItemState) {
                            // Mount the item if the new state is active
                            val result = rootManager.mountItem(item.path ?: "", item.name, item.mode)
                            if (result.startsWith("Success:")) {
                                val lunId = result.substring("Success:".length)
                                updatedItem = item.copy(isActive = true, lunId = lunId)
                            } else {
                                snackbarHostState.showSnackbar(
                                    result.ifBlank { "Error: Could not mount item" }
                                )
                                return@launch
                            }
                        } else {
                            // Eject the item if the new state is inactive
                            updatedItem = if (item.lunId != null) {
                                val lunId = item.lunId
                                val result = rootManager.ejectItem(lunId)
                                if (result.startsWith("Success:")) {
                                    item.copy(isActive = false, lunId = null)
                                } else {
                                    // Host holds the LUN (PREVENT-ALLOW MEDIUM
                                    // REMOVAL): offer a per-LUN force eject that
                                    // leaves the other LUNs serving.
                                    val action = snackbarHostState.showSnackbar(
                                        message = result.ifBlank { "Error: Could not eject item" },
                                        actionLabel = "Force"
                                    )
                                    if (action == SnackbarResult.ActionPerformed) {
                                        val forceResult = rootManager.forceEjectItem(lunId)
                                        if (forceResult.startsWith("Success:")) {
                                            item.copy(isActive = false, lunId = null)
                                        } else {
                                            snackbarHostState.showSnackbar(
                                                forceResult.ifBlank { "Error: Force eject failed" }
                                            )
                                            return@launch
                                        }
                                    } else {
                                        return@launch
                                    }
                                }
                            } else {
                                item.copy(isActive = false, lunId = null)
                            }
                        }
                        diskItemRepository.updateDiskItem(updatedItem)
                    }
                }
                PreferenceRow(
                    title = item.name.ifBlank { item.mode },
                    summary = buildString {
                        append(item.mode)
                        item.path?.let { fullPath ->
                            val fileName = Uri.parse(fullPath).lastPathSegment?.split("/")?.last() ?: fullPath
                            append(" · $fileName")
                        }
                        item.lunId?.let { append(" · LUN $it") }
                    },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Remove opens a confirmation: list only, or list + file
                            // Needs no gadget (list edit, root rm); only blocked
                            IconButton(
                                onClick = { itemPendingRemove = item },
                                enabled = !item.isActive
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove")
                            }
                            Switch(
                                checked = item.isActive,
                                onCheckedChange = { onToggle(it) },
                                enabled = isAppEnabled
                            )
                        }
                    }
                )
            }
            // Bottom spacer so the + button never covers the last row.
            item {
                Spacer(modifier = Modifier.height(88.dp))
            }
        }

        // Remove confirmation: list only, list + file, or cancel.
        itemPendingRemove?.let { pending ->
            // Disk items store the folder; the image itself is folder/name.img.
            val targetFile = if (pending.mode.equals("Disk", ignoreCase = true) && pending.path != null) {
                "${pending.path}/${pending.name}.img"
            } else {
                pending.path
            }
            AlertDialog(
                onDismissRequest = { itemPendingRemove = null },
                title = { Text(pending.name.ifBlank { pending.mode }) },
                text = {
                    Column {
                        Text("Remove this item from the list?")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Checkbox(
                                checked = deleteFileToo,
                                onCheckedChange = { deleteFileToo = it }
                            )
                            Text(
                                text = "Delete from storage",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        itemPendingRemove = null
                        coroutineScope.launch {
                            if (deleteFileToo && !targetFile.isNullOrBlank()) {
                                val deleted = rootManager.deleteFile(targetFile)
                                if (!deleted.startsWith("Success")) {
                                    snackbarHostState.showSnackbar(deleted)
                                    return@launch
                                }
                            }
                            diskItemRepository.removeDiskItem(pending)
                        }
                    }) { Text("Remove") }
                },
                dismissButton = {
                    TextButton(onClick = { itemPendingRemove = null }) { Text("Cancel") }
                }
            )
        }

        // Show the add item dialog if needed
        if (showDialog) {
            AddItemDialog(
                rootManager = rootManager,
                onDismiss = { showDialog = false},
                onItemAction = { newItem ->
                    // Add the new item to the repository
                    coroutineScope.launch {
                        if (newItem.mode.equals("Disk", ignoreCase = true) &&
                            newItem.path != null &&
                            newItem.diskSizeGB > 0 &&
                            newItem.name.isNotEmpty()) {
                            // Create disk image if mode is Disk
                            val diskImagePathResult = rootManager.createDiskImage(newItem.path, newItem.name, newItem.diskSizeGB)
                            if (diskImagePathResult.startsWith("Success:")) {
                                val imagePath = diskImagePathResult.substring("Success:".length).trim()
                                val diskItemWithImagePath = newItem.copy(path = imagePath)
                                diskItemRepository.addDiskItem(diskItemWithImagePath)
                            } else {
                                diskItemRepository.addDiskItem(newItem)
                            }
                        } else {
                            diskItemRepository.addDiskItem(newItem)
                        }
                    }
                    showDialog = false
                }
            )
        }
    }
}

/**
 * Dialog for adding a new disk item
 * Allows users to specify whether it's an ISO or Disk image and provide required information
 */
@OptIn(InternalSerializationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddItemDialog(
    rootManager: RootManager, // Manager for root operations
    onDismiss: () -> Unit, // Callback for dismissing the dialog
    onItemAction: (DiskItem) -> Unit // Callback for when an item is added
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedMode by remember { mutableStateOf("ISO") } // Selected mode: ISO or Disk
    var path by remember { mutableStateOf<String?>(null) } // File or folder path
    var name by remember { mutableStateOf("") } // Display name for the item
    var diskSizeGB by remember { mutableStateOf(0.0) } // Size of the disk in GB
    var isPathValid by remember { mutableStateOf(false) } // Whether the path is valid
    var isPathValidationLoading by remember { mutableStateOf(false) } // Whether path validation is in progress
    var isResolving by remember { mutableStateOf(false) } // Reading/copying a picked file
    var resolveNote by remember { mutableStateOf<String?>(null) } // Picker outcome hint

    // System file picker: the USB gadget needs a real file path, so picked content is resolved to a path when possible, else copied into app storage; the display name is pre-filled from the file.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val picked = uri ?: return@rememberLauncherForActivityResult
        isResolving = true
        resolveNote = null
        scope.launch(Dispatchers.IO) {
            val resolved = getRealPathFromURI(context, picked)
            if (resolved != null) {
                withContext(Dispatchers.Main) {
                    path = resolved
                    if (name.isBlank()) {
                        getDisplayName(context, picked)?.let { name = it }
                    }
                    isResolving = false
                }
            } else {
                val copied = copyUriToAppStorage(context, picked)
                withContext(Dispatchers.Main) {
                    if (copied != null) {
                        path = copied
                        if (name.isBlank()) {
                            getDisplayName(context, picked)?.let { name = it }
                        }
                        resolveNote = "Copied into app storage"
                    } else {
                        resolveNote = "Could not read this file"
                    }
                    isResolving = false
                }
            }
        }
    }

    // System folder picker for Disk mode: the folder holds the created image; tree URIs only resolve on the primary volume, otherwise manual input is needed.
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val picked = uri ?: return@rememberLauncherForActivityResult
        val resolved = getRealPathFromTreeUri(context, picked)
        if (resolved != null) {
            path = resolved
            resolveNote = null
        } else {
            resolveNote = "Could not resolve folder, type it manually"
        }
    }

    // Validate the path whenever it changes using RootManager
    LaunchedEffect(path, selectedMode) {
        if (!path.isNullOrBlank()) {
            isPathValidationLoading = true
            isPathValid = try {
                rootManager.validatePath(path, selectedMode)
            } catch (_: Exception) {
                false
            }
            isPathValidationLoading = false
        } else {
            isPathValid = false
        }
    }

    // Enable the add button based on validation criteria
    val isAddButtonEnabled = when (selectedMode) {
        "ISO" -> !path.isNullOrBlank() && name.isNotBlank() && isPathValid && !isResolving
        "Disk" -> !path.isNullOrBlank() && diskSizeGB > 0 && name.isNotBlank() && isPathValid
        else -> name.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add item") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Mode selector chips instead of radio buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedMode == "ISO",
                        onClick = { selectedMode = "ISO" },
                        label = { Text("ISO") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedMode == "Disk",
                        onClick = { selectedMode = "Disk" },
                        label = { Text("Disk") },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Display name input field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name *") },
                    placeholder = { Text("Enter display name") },
                    isError = name.isBlank(),
                    supportingText = {
                        if (name.isBlank()) {
                            Text(
                                text = "Display name is required",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Show different inputs based on selected mode
                if (selectedMode == "ISO") {
                    Column {
                        // Path input for ISO files
                        OutlinedTextField(
                            value = path ?: "",
                            onValueChange = { path = it },
                            label = { Text("ISO File Path") },
                            placeholder = { Text("Enter full path to ISO file") },
                            isError = path.isNullOrBlank() || (!path.isNullOrBlank() && !isPathValid),
                            supportingText = {
                                if (path.isNullOrBlank()) {
                                    Text(
                                        text = "ISO file path is required",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else if (isPathValidationLoading) {
                                    Text(
                                        text = "Checking path...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (!isPathValid) {
                                    Text(
                                        text = "File does not exist",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    Text(
                                        text = "File exists",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { pickFile.launch(arrayOf("*/*")) },
                            enabled = !isResolving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isResolving) "Reading file..." else "Browse files")
                        }
                    }
                } else if (selectedMode == "Disk") {
                    Column {
                        // Path input for disk folders
                        OutlinedTextField(
                            value = path ?: "",
                            onValueChange = { path = it },
                            label = { Text("Folder Path") },
                            placeholder = { Text("Enter full path to folder for creating disk image") },
                            isError = path.isNullOrBlank() || (!path.isNullOrBlank() && !isPathValid),
                            supportingText = {
                                if (path.isNullOrBlank()) {
                                    Text(
                                        text = "Folder path is required",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else if (isPathValidationLoading) {
                                    Text(
                                        text = "Checking path...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (!isPathValid) {
                                    Text(
                                        text = "Folder does not exist",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    Text(
                                        text = "Folder exists",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { pickFolder.launch(null) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Browse folders")
                        }
                        // Disk size input for disk mode
                        OutlinedTextField(
                            value = if (diskSizeGB > 0) diskSizeGB.toString() else "",
                            onValueChange = {
                                val value = it.toDoubleOrNull()
                                diskSizeGB = value ?: 0.0
                            },
                            label = { Text("Disk Size (GB)") },
                            placeholder = { Text("Enter disk size in GB") },
                            isError = diskSizeGB <= 0,
                            supportingText = {
                                if (diskSizeGB <= 0) {
                                    Text(
                                        text = "Disk size must be greater than 0",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Picker outcome hint (copied into app storage, errors, ...).
                resolveNote?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    // Create and add the new item
                    val newItem = DiskItem(
                        mode = selectedMode,
                        path = path,
                        name = name,
                        diskSizeGB = diskSizeGB
                    )
                    onItemAction(newItem)
                },
                enabled = isAddButtonEnabled
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Preview function for Compose UI
 * Allows previewing the HomeScreen in Android Studio
 */
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    val context = LocalContext.current
    ISOdroidTheme {
        HomeScreen(
            isAppEnabled = true,
            onAppEnabledChange = {},
            rootManager = RootManager(context),
            onNavigateToSettings = {}
        )
    }
}