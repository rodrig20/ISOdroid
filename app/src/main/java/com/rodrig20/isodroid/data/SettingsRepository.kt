package com.rodrig20.isodroid.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Extension property to create a DataStore instance for app settings
 * Stores settings in a preferences file called "settings"
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * USB gadget identity strings shown to the host PC. Empty means keep the
 * Android default for that field.
 */
data class UsbIdentity(
    val manufacturer: String = "",
    val product: String = "",
    val serial: String = ""
)

/**
 * Repository class for managing app settings
 * Provides methods to access and update app settings
 */
class SettingsRepository(private val context: Context) {

    companion object {
        // Key for storing the maximum number of devices in preferences
        private val MAX_DEVICES_KEY = intPreferencesKey("max_devices")
        // Keys for the USB identity strings (empty = Android default)
        private val USB_MANUFACTURER_KEY = stringPreferencesKey("usb_manufacturer")
        private val USB_PRODUCT_KEY = stringPreferencesKey("usb_product")
        private val USB_SERIAL_KEY = stringPreferencesKey("usb_serial")
        // USB string descriptors cap (keep well under the 126-char limit).
        const val USB_STRING_MAX_LEN = 64

        /**
         * Sanitizes a USB string descriptor value: no line breaks or control
         * characters, no single quotes (shell-arg safe), capped to
         * USB_STRING_MAX_LEN.
         */
        fun sanitizeUsbString(value: String): String =
            value.trim().filter { it >= ' ' && it != '\'' }.take(USB_STRING_MAX_LEN)
    }

    // Flow of the maximum number of devices setting that can be observed for changes
    val maxDevicesFlow: Flow<Int> = context.settingsDataStore.data
        .map { preferences ->
            // Return the stored value or 1 as default if not set
            preferences[MAX_DEVICES_KEY] ?: 1
        }

    /**
     * Sets the maximum number of devices setting
     * @param maxDevices The new maximum number of devices value (will be clamped to minimum of 1)
     */
    suspend fun setMaxDevices(maxDevices: Int) {
        // Clamp the value to a minimum of 1
        val clampedValue = if (maxDevices < 1) 1 else maxDevices
        context.settingsDataStore.edit { settings ->
            settings[MAX_DEVICES_KEY] = clampedValue
        }
    }

    // Flow of the USB identity strings (empty fields keep Android defaults).
    val usbIdentityFlow: Flow<UsbIdentity> = combine(
        context.settingsDataStore.data.map { it[USB_MANUFACTURER_KEY] ?: "" },
        context.settingsDataStore.data.map { it[USB_PRODUCT_KEY] ?: "" },
        context.settingsDataStore.data.map { it[USB_SERIAL_KEY] ?: "" }
    ) { manufacturer, product, serial ->
        UsbIdentity(manufacturer, product, serial)
    }

    /**
     * Sets the USB identity strings. Each value is trimmed, stripped of
     * control characters and capped; blank stays blank (Android default).
     */
    suspend fun setUsbIdentity(manufacturer: String, product: String, serial: String) {
        context.settingsDataStore.edit { settings ->
            settings[USB_MANUFACTURER_KEY] = sanitizeUsbString(manufacturer)
            settings[USB_PRODUCT_KEY] = sanitizeUsbString(product)
            settings[USB_SERIAL_KEY] = sanitizeUsbString(serial)
        }
    }
}
