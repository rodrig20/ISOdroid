package com.rodrig20.isodroid.utils

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri

/**
 * Converts a content URI to a real file path
 * Handles different types of content URIs including document URIs, media URIs, etc.
 * @param context The application context
 * @param uri The URI to convert to a file path
 * @return The real file path or null if conversion fails
 */
fun getRealPathFromURI(context: Context, uri: Uri): String? {
    if (DocumentsContract.isDocumentUri(context, uri)) {
        // Handle Document URIs
        if (isExternalStorageDocument(uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val type = split[0]

            if ("primary".equals(type, ignoreCase = true)) {
                return "${Environment.getExternalStorageDirectory()}/${split[1]}"
            }
        }

        else if (isDownloadsDocument(uri)) {
            // Handle Downloads document URIs
            val id = DocumentsContract.getDocumentId(uri)
            // Android 11+: raw file path embedded in the id.
            if (id.startsWith("raw:")) {
                return id.removePrefix("raw:")
            }
            // MediaStore-backed download (msf:<id>, Android 10+): resolve via Downloads collection; null under scoped storage when _data is unavailable.
            if (id.startsWith("msf:") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val msfId = id.removePrefix("msf:")
                return getDataColumn(
                    context,
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Downloads._ID}=?",
                    arrayOf(msfId)
                )
            }
            val numericId = id.toLongOrNull() ?: return null
            val contentUri = ContentUris.withAppendedId(
                "content://downloads/public_downloads".toUri(), numericId
            )
            return getDataColumn(context, contentUri, null, null)
        }

        else if (isMediaDocument(uri)) {
            // Handle Media document URIs
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val type = split[0]

            var contentUri: Uri? = null
            when (type) {
                "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val selection = "_id=?"
            val selectionArgs = arrayOf(split[1])

            return getDataColumn(context, contentUri, selection, selectionArgs)
        }
    }

    else if ("content".equals(uri.scheme, ignoreCase = true)) {
        // Handle content URIs
        return getDataColumn(context, uri, null, null)
    }

    else if ("file".equals(uri.scheme, ignoreCase = true)) {
        // Handle file URIs
        return uri.path
    }

    return null
}

/**
 * Gets the data column value from a content URI
 * Used to extract file paths from content URIs
 * @param context The application context
 * @param uri The content URI to query
 * @param selection Selection criteria for the query
 * @param selectionArgs Arguments for the selection criteria
 * @return The value in the data column or null if not found
 */
fun getDataColumn(context: Context, uri: Uri?, selection: String?, selectionArgs: Array<String>?): String? {
    var cursor: Cursor? = null
    val column = "_data" // The column that contains the file path
    val projection = arrayOf(column)

    try {
        cursor = context.contentResolver.query(uri!!, projection, selection, selectionArgs, null)
        if (cursor != null && cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndexOrThrow(column)
            return cursor.getString(columnIndex)
        }
    } finally {
        cursor?.close()
    }
    return null
}

/**
 * Checks if the URI is from external storage document provider
 * @param uri The URI to check
 * @return True if the URI is from external storage document provider
 */
fun isExternalStorageDocument(uri: Uri): Boolean {
    return "com.android.externalstorage.documents" == uri.authority
}

/**
 * Checks if the URI is from downloads document provider
 * @param uri The URI to check
 * @return True if the URI is from downloads document provider
 */
fun isDownloadsDocument(uri: Uri): Boolean {
    return "com.android.providers.downloads.documents" == uri.authority
}

/**
 * Checks if the URI is from media document provider
 * @param uri The URI to check
 * @return True if the URI is from media document provider
 */
fun isMediaDocument(uri: Uri): Boolean {
    return "com.android.providers.media.documents" == uri.authority
}

/**
 * Resolves a tree URI from a folder picker to a real directory path.
 * Parses the authority directly instead of relying on isDocumentUri
 * (which rejects tree URIs on several Android versions).
 * Primary volume maps to external storage; removable volumes (SD cards,
 * USB-OTG) map to /storage/<uuid>/. Returns null when unresolvable
 * (caller keeps manual input).
 */
fun getRealPathFromTreeUri(context: Context, treeUri: Uri): String? {
    if (!"com.android.externalstorage.documents".equals(treeUri.authority, ignoreCase = true)) {
        return null
    }
    val docId = try {
        DocumentsContract.getTreeDocumentId(treeUri)
    } catch (_: Exception) {
        return null
    }
    val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    if (split.isEmpty()) return null
    if ("primary".equals(split[0], ignoreCase = true)) {
        val base = Environment.getExternalStorageDirectory().toString()
        return if (split.size > 1) "$base/${split[1]}" else base
    }
    // Removable volume (SD card, USB-OTG): mounted at /storage/<uuid>.
    val base = "/storage/${split[0]}"
    if (split.size > 1) {
        val candidate = "$base/${split[1]}"
        if (java.io.File(candidate).isDirectory) return candidate
        // Fall back to the volume root when the subpath is stale.
        if (java.io.File(base).isDirectory) return base
        return null
    }
    return base
}

/**
 * Best-effort display name for any content/file URI (for pre-filling).
 */
fun getDisplayName(context: Context, uri: Uri): String? {
    if ("file".equals(uri.scheme, ignoreCase = true)) {
        return uri.lastPathSegment
    }
    if (!"content".equals(uri.scheme, ignoreCase = true)) return null
    var cursor: Cursor? = null
    try {
        cursor = context.contentResolver.query(
            uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null
        )
        if (cursor != null && cursor.moveToFirst()) {
            return cursor.getString(0)
        }
    } catch (_: Exception) {
        // Provider without DISPLAY_NAME support.
    } finally {
        cursor?.close()
    }
    return uri.lastPathSegment
}
