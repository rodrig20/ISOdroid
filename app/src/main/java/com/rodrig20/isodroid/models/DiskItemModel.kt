package com.rodrig20.isodroid.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

/**
 * Data class representing a disk item (ISO file or disk image)
 * Contains information about the item and its current state
 */
@InternalSerializationApi
@Serializable
data class DiskItem(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(), // Unique identifier for the disk item
    val mode: String,                 // Mode of the item (e.g. "ISO" or "Disk")
    val path: String? = null,        // Path to the file or folder
    val isActive: Boolean = false,   // Whether the item is currently active/mounted
    val lunId: String? = null,       // LUN ID if the item is mounted
    val name: String = "",           // Display name for the item
    val diskSizeGB: Double = 0.0     // Size of the disk in GB (for disk mode)
)

/**
 * Custom serializer for UUID to ensure proper serialization/deserialization
 * Converts UUIDs to/from strings
 */
@OptIn(ExperimentalSerializationApi::class)
object UUIDSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    /**
     * Serializes a UUID to a string
     * @param encoder Encoder to use for serialization
     * @param value UUID to serialize
     */
    override fun serialize(encoder: Encoder, value: UUID) {
        encoder.encodeString(value.toString())
    }

    /**
     * Deserializes a UUID from a string
     * @param decoder Decoder to use for deserialization
     * @return Deserialized UUID
     */
    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }
}