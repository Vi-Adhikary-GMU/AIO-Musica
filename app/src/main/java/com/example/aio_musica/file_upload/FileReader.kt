package com.example.aio_musica.file_upload

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.UUID

class FileReader(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher
) {

    /**
     * Reads a file from the given URI and returns its content as a [FileInfo] object.
     * @param contentUri The URI of the file to read.
     * @param ioDispatcher The coroutine dispatcher to use for I/O operations.
     */
    suspend fun uriToFileInfo(contentUri: Uri): FileInfo {
        return withContext(ioDispatcher) {
            val bytes = context.contentResolver.openInputStream(contentUri)?.use { inputStream -> inputStream.readBytes() } ?: byteArrayOf() // Handles null case // TODO: This method of byte reading will probably not work when dealing with longer file sizes. Look into a better byte-by-byte implementation.

            val fileName = UUID.randomUUID().toString() // TODO: This generates a random file name. It's expected that the backend will resolve this and give the file it's own name when it is uploaded, but we can change this if needed.
            val mimeType = context.contentResolver.getType(contentUri) ?: ""

            FileInfo(
                name = fileName,
                mimeType = mimeType,
                bytes = bytes
            )
        }
    }
}

/**
 * Contains general information about our passed in file.
 * @param name The name of the file.
 * @param mimeType The file type.
 * @param bytes The file bytes.
 */
class FileInfo(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray
)