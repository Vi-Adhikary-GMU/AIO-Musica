package com.example.aio_musica.file_upload

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.InputStream

class FileReader(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher
) {

    /**
     * Reads the metadata (name, type, size) of the file at the given URI.
     * This does NOT load the file contents into memory; use [openInputStream] and read it in chunks.
     * @param contentUri The URI of the file the user picked.
     * @return A [FileInfo] describing the file.
     */
    suspend fun readFileInfo(contentUri: Uri): FileInfo {
        return withContext(ioDispatcher) {
            val resolver = context.contentResolver

            var displayName: String? = null
            var sizeBytes = UNKNOWN_SIZE

            resolver.query(
                contentUri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        displayName = cursor.getString(nameIndex)
                    }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }

            FileInfo(
                name = displayName ?: contentUri.lastPathSegment ?: "Unknown",
                mimeType = resolver.getType(contentUri) ?: "",
                sizeBytes = sizeBytes
            )
        }
    }

    /**
     * Opens a stream to read the file at the given URI.
     * The caller is responsible for closing it (use `.use { }`).
     * @throws FileNotFoundException if the file can't be opened.
     */
    fun openInputStream(contentUri: Uri): InputStream {
        return context.contentResolver.openInputStream(contentUri)
            ?: throw FileNotFoundException("Unable to open $contentUri")
    }

    companion object {
        const val UNKNOWN_SIZE = -1L
    }
}

/**
 * Contains general information about a file the user picked.
 * @param name The file's display name (e.g. "my_song.mp3").
 * @param mimeType The file type (e.g. "audio/mpeg"). Empty if unknown.
 * @param sizeBytes The size in bytes, or [FileReader.UNKNOWN_SIZE] if the provider didn't report one.
 */
class FileInfo(
    val name: String,
    val mimeType: String,
    val sizeBytes: Long
)
