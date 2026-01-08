package com.rodrig20.isodroid.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Extension property to create a DataStore instance for app settings
 * Stores settings in a preferences file called "settings"
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Repository class for managing app settings
 * Provides methods to access and update app settings
 * Currently only supports the maximum number of devices setting
 */
class SettingsRepository(private val context: Context) {

    companion object {
        // Key for storing the maximum number of devices in preferences
        private val MAX_DEVICES_KEY = intPreferencesKey("max_devices")
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
}
