package com.rodrig20.isodroid.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.rodrig20.isodroid.models.DiskItem
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

/**
 * Extension property to create a DataStore instance for disk items
 * Stores disk items in a JSON file called "disk_items.json"
 */
@OptIn(InternalSerializationApi::class)
private val Context.diskItemDataStore: DataStore<List<DiskItem>> by dataStore(
    fileName = "disk_items.json",
    serializer = DiskItemSerializer
)

/**
 * Repository class for managing disk items
 * Provides methods to add, update, and remove disk items
 * Uses DataStore for persistent storage
 */
@OptIn(InternalSerializationApi::class)
class DiskItemRepository(private val context: Context) {

    // Flow of disk items that can be observed for changes
    val diskItems = context.diskItemDataStore.data

    /**
     * Adds a new disk item to the repository
     * @param diskItem The disk item to add
     */
    suspend fun addDiskItem(diskItem: DiskItem) {
        context.diskItemDataStore.updateData { currentItems ->
            currentItems + diskItem
        }
    }

    /**
     * Updates an existing disk item in the repository
     * @param diskItem The disk item with updated information
     */
    suspend fun updateDiskItem(diskItem: DiskItem) {
        context.diskItemDataStore.updateData { currentItems ->
            currentItems.map {
                if (it.id == diskItem.id) {
                    diskItem
                } else {
                    it
                }
            }
        }
    }

    /**
     * Removes a disk item from the repository
     * @param diskItem The disk item to remove
     */
    suspend fun removeDiskItem(diskItem: DiskItem) {
        context.diskItemDataStore.updateData { currentItems ->
            currentItems.filterNot { it.id == diskItem.id }
        }
    }
}

/**
 * Serializer for the list of disk items
 * Handles reading and writing disk items to/from the DataStore
 */
@OptIn(InternalSerializationApi::class)
object DiskItemSerializer : Serializer<List<DiskItem>> {
    override val defaultValue: List<DiskItem>
        get() = emptyList()

    /**
     * Reads the disk items from the input stream
     * @param input The input stream to read from
     * @return List of disk items
     * @throws CorruptionException if the data can't be read properly
     */
    override suspend fun readFrom(input: InputStream): List<DiskItem> {
        try {
            return Json.decodeFromString(
                input.readBytes().decodeToString()
            )
        } catch (exception: SerializationException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    /**
     * Writes the disk items to the output stream
     * @param t The list of disk items to write
     * @param output The output stream to write to
     */
    override suspend fun writeTo(t: List<DiskItem>, output: OutputStream) {
        output.write(
            Json.encodeToString(t).encodeToByteArray()
        )
    }
}
