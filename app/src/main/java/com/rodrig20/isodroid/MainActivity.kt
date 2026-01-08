package com.rodrig20.isodroid

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.rodrig20.isodroid.data.DiskItemRepository
import com.rodrig20.isodroid.data.SettingsRepository
import com.rodrig20.isodroid.manager.RootManager
import com.rodrig20.isodroid.models.DiskItem
import com.rodrig20.isodroid.ui.theme.ISOdroidTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.InternalSerializationApi


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
        when (currentScreen) {
            is Screen.Home -> HomeScreen(
                isAppEnabled = isAppEnabled,
                onAppEnabledChange = { enabled ->
                    // Handle app enable/disable actions
                    coroutineScope.launch {
                        if (enabled) {
                            rootManager.turnOnApp()
                        } else {
                            rootManager.turnOffApp()

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
                    }
                },
                rootManager = rootManager,
                onNavigateToSettings = { currentScreen = Screen.Settings }
            )
            is Screen.Settings -> SettingsScreen(
                onNavigateBack = { currentScreen = Screen.Home },
                rootManager = rootManager
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
 * Card component for enabling/disabling the USB gadget functionality
 * Provides a switch with descriptive text
 */
@Composable
fun AppEnablerCard(
    isAppEnabled: Boolean, // Current state of the USB gadget
    onCheckedChange: (Boolean) -> Unit, // Callback for when the switch is toggled
    isRooted: Boolean // Whether the device has root access
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Enable USB Gadget",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Allow the app to control the USB gadget",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Switch(
                checked = isAppEnabled,
                onCheckedChange = onCheckedChange,
                enabled = isRooted
            )
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
    onNavigateToSettings: () -> Unit // Callback for navigating to settings
) {
    val context = LocalContext.current
    val diskItemRepository = remember { DiskItemRepository(context) }
    // Observe disk items from the repository
    val itemList by diskItemRepository.diskItems.collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }
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
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (isAppEnabled) {
                    showDialog = true
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Item")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // First item in the list is the app enabler card
            item {
                AppEnablerCard(
                    isAppEnabled = isAppEnabled,
                    onCheckedChange = onAppEnabledChange,
                    isRooted = rootManager.isRooted
                )
            }
            // Display all disk items in the list
            items(items = itemList, key = { it.id }) { item ->
                ItemCard(
                    item = item,
                    isRooted = true,
                    isAppEnabled = isAppEnabled,
                    onRemove = {
                        // Remove the disk item from the repository
                        coroutineScope.launch { diskItemRepository.removeDiskItem(item) }
                    },
                    onToggle = { newItemState ->
                        // Handle mounting/ejecting of the disk item
                        coroutineScope.launch {
                            var updatedItem: DiskItem
                            if (newItemState) {
                                // Mount the item if the new state is active
                                val result = rootManager.mountItem(item.path ?: "", item.name, item.mode)
                                if (result.startsWith("Success:")) {
                                    val lunId = result.substring("Success:".length)
                                    updatedItem = item.copy(isActive = true, lunId = lunId)
                                } else {
                                    return@launch
                                }
                            } else {
                                // Eject the item if the new state is inactive
                                updatedItem = if (item.lunId != null) {
                                    val result = rootManager.ejectItem(item.lunId)
                                    if (result.startsWith("Success:")) {
                                        item.copy(isActive = false, lunId = null)
                                    } else {
                                        return@launch
                                    }
                                } else {
                                    item.copy(isActive = false, lunId = null)
                                }
                            }
                            diskItemRepository.updateDiskItem(updatedItem)
                        }
                    }
                )
            }
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
 * Card component to display a single disk item
 * Shows information about the disk item and provides toggle/remove actions
 */
@OptIn(InternalSerializationApi::class)
@Composable
fun ItemCard(
    item: DiskItem, // The disk item to display
    isRooted: Boolean, // Whether the device has root access
    isAppEnabled: Boolean, // Whether the app is enabled
    onRemove: (DiskItem) -> Unit, // Callback for removing the item
    onToggle: (Boolean) -> Unit // Callback for toggling the item state
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Display the item name or mode if name is blank
                Text(
                    text = item.name.ifBlank { "Mode: ${item.mode}" },
                    style = MaterialTheme.typography.titleMedium
                )
                // Switch to enable/disable the item
                Switch(
                    checked = item.isActive,
                    onCheckedChange = { onToggle(it) },
                    enabled = isRooted && isAppEnabled
                )
            }
            // Display the file path if available
            item.path?.let { fullPath ->
                val fileName = Uri.parse(fullPath).lastPathSegment?.split("/")?.last() ?: fullPath
                Text(
                    text = "Path: $fileName",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // Display the LUN ID if available
            item.lunId?.let { lunId ->
                Text(
                    text = "LUN: $lunId",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                    color = Color.Gray
                )
            }
            // Action buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                // Remove button is only enabled when not active and app is enabled
                IconButton(onClick = { onRemove(item) }, enabled = isRooted && isAppEnabled && !item.isActive) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }
        }
    }
}


/**
 * Dialog for adding a new disk item
 * Allows users to specify whether it's an ISO or Disk image and provide required information
 */
@OptIn(InternalSerializationApi::class)
@Composable
fun AddItemDialog(
    rootManager: RootManager, // Manager for root operations
    onDismiss: () -> Unit, // Callback for dismissing the dialog
    onItemAction: (DiskItem) -> Unit // Callback for when an item is added
) {
    LocalContext.current
    var selectedMode by remember { mutableStateOf("ISO") } // Selected mode: ISO or Disk
    var path by remember { mutableStateOf<String?>(null) } // File or folder path
    var name by remember { mutableStateOf("") } // Display name for the item
    var diskSizeGB by remember { mutableStateOf(0.0) } // Size of the disk in GB
    var isPathValid by remember { mutableStateOf(false) } // Whether the path is valid
    var isPathValidationLoading by remember { mutableStateOf(false) } // Whether path validation is in progress

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

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Add New Item",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Radio buttons to select ISO or Disk mode
                Row {
                    RadioButton(
                        selected = selectedMode == "ISO",
                        onClick = { selectedMode = "ISO" }
                    )
                    Text("ISO", modifier = Modifier.align(Alignment.CenterVertically))
                    Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(
                        selected = selectedMode == "Disk",
                        onClick = { selectedMode = "Disk" }
                    )
                    Text("Disk", modifier = Modifier.align(Alignment.CenterVertically))
                }
                Spacer(modifier = Modifier.height(16.dp))

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
                Spacer(modifier = Modifier.height(8.dp))

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

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    // Enable the add button based on validation criteria
                    val isAddButtonEnabled = when (selectedMode) {
                        "ISO" -> !path.isNullOrBlank() && name.isNotBlank() && isPathValid
                        "Disk" -> !path.isNullOrBlank() && diskSizeGB > 0 && name.isNotBlank() && isPathValid
                        else -> name.isNotBlank()
                    }
                    Button(
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
                }
            }
        }
    }
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