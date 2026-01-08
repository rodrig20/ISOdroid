package com.rodrig20.isodroid.manager

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Root manager class that handles all operations requiring root access
 * Manages USB gadget configuration, mounting/ejecting items, and checking root status
 */
class RootManager(context: Context) {
    private val appContext = context.applicationContext

    // Tracks whether the device has root access
    var isRooted: Boolean = false
        private set

    // State flow for tracking whether the app is enabled
    private val _isAppEnabled = MutableStateFlow(false)
    val isAppEnabledFlow: StateFlow<Boolean> = _isAppEnabled

    private val _isChargingSuspended = MutableStateFlow(false)
    val isChargingSuspendedFlow: StateFlow<Boolean> = _isChargingSuspended

    // Default value for maximum number of devices
    private var maxDevicesValue: Int = 1
    // Provider function to get the current maximum devices setting
    private var maxDevicesProvider: (() -> Int)? = null

    /**
     * Sets a provider function for getting the maximum devices value
     * @param provider Function that returns the current maximum devices setting
     */
    fun setMaxDevicesProvider(provider: () -> Int) {
        maxDevicesProvider = provider
    }

    /**
     * Gets the current maximum devices value
     * Uses the provider function if available, otherwise returns the default value
     * @return Current maximum devices value
     */
    private fun getMaxDevices(): Int {
        return maxDevicesProvider?.invoke() ?: maxDevicesValue
    }

    /**
     * Checks if the device has root access and updates the isRooted property
     */
    suspend fun checkRoot() {
        isRooted = isRooted()
    }

    /**
     * Initializes the app state by checking if the USB gadget is currently configured
     * Updates the isAppEnabled flow based on the check result
     */
    suspend fun initializeAppState() {
        val result = runScriptAsRoot("check_gadget_status.sh")
        // Update the app enabled state based on the command output
        _isAppEnabled.value = result.trim().equals("true", ignoreCase = true)
    }

    /**
     * Turns on the USB gadget app by configuring the USB gadget system
     * Creates the necessary configuration and enables the gadget
     */
    suspend fun turnOnApp() {
        if (isRooted) {
            val maxDevices = getMaxDevices()

            runScriptAsRoot("turn_on_gadget.sh", listOf(maxDevices.toString()))
            // Update the app enabled state to true
            _isAppEnabled.value = true
        }
    }

    /**
     * Turns off the USB gadget app by disabling the USB gadget system
     * Cleans up the configuration and resets to default USB mode
     */
    @OptIn(InternalSerializationApi::class)
    suspend fun turnOffApp() {
        if (isRooted) {
            runScriptAsRoot("turn_off_gadget.sh")
            // Update the app enabled state to false
            _isAppEnabled.value = false
        }
    }

    /**
     * Mounts a file (ISO or disk image) to a LUN for USB mass storage
     * @param filePath Path to the file to mount
     * @param displayName Display name for the mounted device (defaults to empty string)
     * @param mode Mode of the item - either "ISO" or "Disk" (defaults to "ISO")
     * @return Result string indicating success or error
     */
    suspend fun mountItem(filePath: String, displayName: String = "", mode: String = "ISO"): String {
        if (!isRooted) return "Error: Device is not rooted"

        // Encode the display name to handle special characters
        val encodedName = displayName.replace("'", "'\"'\"'")
        // Construct the actual file path based on mode
        val actualFilePath = if (mode.equals("Disk", ignoreCase = true)) "$filePath/$displayName.img" else filePath
        val maxDevices = getMaxDevices()

        return runScriptAsRoot("mount_item.sh", listOf(filePath, encodedName, mode, actualFilePath, (maxDevices - 1).toString()))
    }

    /**
     * Creates a disk image file in the specified folder with the given size
     * @param folderPath Path to the folder where the image will be created
     * @param diskName Name of the disk (without extension)
     * @param sizeGB Size of the disk in gigabytes
     * @return Result string indicating success or error
     */
    suspend fun createDiskImage(folderPath: String, diskName: String, sizeGB: Double): String {
        if (!isRooted) return "Error: Device is not rooted"
        if (folderPath.isEmpty()) return "Error: Folder path is empty"
        if (sizeGB <= 0) return "Error: Disk size must be greater than 0"

        // Convert size from GB to bytes
        val sizeInBytes = (sizeGB * 1000 * 1000 * 1000).toLong()

        return runScriptAsRoot("create_disk_image.sh", listOf(folderPath, diskName, sizeInBytes.toString()))
    }

    /**
     * Ejects a mounted item by clearing its file path from the LUN
     * @param lunId ID of the LUN to eject
     * @return Result string indicating success or error
     */
    suspend fun ejectItem(lunId: String): String {
        if (!isRooted) return "Error: Device is not rooted"
        val cleanLunId = lunId.trim()

        return runScriptAsRoot("eject_item.sh", listOf(cleanLunId))
    }

    /**
     * Checks if the device has root access by running a simple command with su
     * @return True if the device is rooted, false otherwise
     */
    private suspend fun isRooted(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Executes shell commands as root user for checking purposes
     * Used to validate file paths and other checks
     * @param commands List of commands to execute
     * @return The last line of output from the executed commands
     */
    suspend fun runAsRootForChecking(commands: List<String>): String = withContext(Dispatchers.IO) {
        var lastLine = ""
        try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            for (command in commands) {
                os.writeBytes("$command\n")
            }
            os.writeBytes("exit\n")
            os.flush()
            os.close()

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                lastLine = line!!
            }
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        lastLine
    }

    /**
     * Validates if a path exists and is of the correct type based on the selected mode
     * @param path The file or directory path to validate
     * @param selectedMode The mode - either "ISO" (expects a file) or "Disk" (expects a directory)
     * @return True if the path exists and is of the expected type, false otherwise
     */
    suspend fun validatePath(path: String?, selectedMode: String): Boolean = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) {
            return@withContext false
        }

        try {
            // Determine expected type based on selected mode
            val expectedType = if (selectedMode == "ISO") "file" else "dir"
            val result = runScriptAsRoot("validate_path.sh", listOf(path, expectedType))

            // Check if the path matches the expected type
            val isValid = if (selectedMode == "ISO") {
                result.trim() == "VALID_FILE"
            } else {
                result.trim() == "VALID_DIR"
            }
            isValid
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Gets the current charging suspension state
     * Updates the isChargingSuspendedFlow with the current value
     * @return True if charging is suspended (value is 1), false otherwise (value is 0)
     */
    suspend fun getChargingState(): Boolean {
        if (!isRooted) {
            _isChargingSuspended.value = false
            return false
        }

        val result = runScriptAsRoot("get_charging_state.sh")
        val isSuspended = result.trim().toIntOrNull() == 1
        _isChargingSuspended.value = isSuspended
        return isSuspended
    }

    /**
     * Sets the charging suspension state
     * @param suspend True to suspend charging (set to 1), false to allow charging (set to 0)
     * @return Success status
     */
    suspend fun setChargingState(suspend: Boolean): String {
        if (!isRooted) return "Error: Device is not rooted"

        val value = if (suspend) "1" else "0"
        val result = runScriptAsRoot("set_charging_state.sh", listOf(value))

        // Update the flow with the new state if the operation was successful
        if (!result.contains("Error")) {
            _isChargingSuspended.value = suspend
        }

        return result
    }

    /**
     * Loads and executes as root a script from the assets folder
     * @param scriptName The name of the script file in assets/scripts/
     * @param args List of arguments to pass to the script
     * @return The output from the script execution
     */
    private suspend fun runScriptAsRoot(scriptName: String, args: List<String> = emptyList()): String = withContext(Dispatchers.IO) {
        var output: String
        try {
            // Read the script content from assets
            val scriptContent = appContext.assets.open("scripts/$scriptName").bufferedReader().readText()

            // Create a temporary file for the script
            val tempScript = java.io.File.createTempFile("temp_script_", ".sh", appContext.cacheDir)
            tempScript.writeText(scriptContent)
            tempScript.setExecutable(true)

            // Prepare arguments for the script
            val argsString = if (args.isNotEmpty()) {
                args.joinToString(" ") { "'$it'" }
            } else {
                ""
            }

            // Construct command to execute the temporary script with arguments
            val fullCommand = """${tempScript.absolutePath} $argsString"""

            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", fullCommand))

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val outputBuilder = StringBuilder()

            // Read standard output using useLines to prevent infinite blocking
            reader.useLines { lines ->
                lines.forEach { line ->
                    outputBuilder.appendLine(line)
                }
            }

            // Read error output if any using useLines to prevent infinite blocking
            errorReader.useLines { lines ->
                lines.forEach { line ->
                    outputBuilder.appendLine(line)
                }
            }

            process.waitFor()

            // Clean up the temporary file
            tempScript.delete()

            output = outputBuilder.toString().trim()
        } catch (e: Exception) {
            e.printStackTrace()
            output = "Error: Could not execute script - ${e.message}"
        }

        output
    }
}