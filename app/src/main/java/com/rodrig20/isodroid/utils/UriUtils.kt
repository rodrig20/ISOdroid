package com.rodrig20.isodroid.utils

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
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
            val contentUri = ContentUris.withAppendedId(
                "content://downloads/public_downloads".toUri(), id.toLong()
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
