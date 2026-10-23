package com.example.aio_musica.file_upload

import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Handles "uploading" a file into the app: the file the user picked is copied into the app's
 * own storage so the library can play it later without depending on the original location.
 * @param fileReader Reads metadata and streams from the picked file.
 * @param storageDir The folder inside app storage where imported files are saved
 * (e.g. `File(context.filesDir, "songs")`).
 * @param ioDispatcher The dispatcher used for the file copy.
 */
class FileRepository(
    private val fileReader: FileReader,
    private val storageDir: File,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Copies a file into app storage.
     * Emits [ProgressUpdate]s as bytes are copied. The final emission has
     * [ProgressUpdate.result] set to the saved file. If the copy fails or is cancelled,
     * the partially-written file is deleted.
     * @param contentUri The URI of the file the user picked.
     * @return A flow of [ProgressUpdate] objects representing the copy progress.
     */
    fun uploadFile(contentUri: Uri): Flow<ProgressUpdate> = channelFlow {
        val info = fileReader.readFileInfo(contentUri)

        storageDir.mkdirs()
        val destination = File(storageDir, UUID.randomUUID().toString() + info.safeExtension())

        try {
            val copied = withContext(ioDispatcher) {
                fileReader.openInputStream(contentUri).use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var totalCopied = 0L
                        var lastReported = 0L

                        while (true) {
                            ensureActive() // Lets cancelUpload() stop the copy between chunks.
                            val read = input.read(buffer)
                            if (read == -1) break

                            output.write(buffer, 0, read)
                            totalCopied += read

                            // Only report every so often so we don't flood the UI with updates.
                            if (info.sizeBytes > 0 && totalCopied - lastReported >= REPORT_INTERVAL_BYTES) {
                                lastReported = totalCopied
                                send(ProgressUpdate(totalCopied, info.sizeBytes))
                            }
                        }
                        totalCopied
                    }
                }
            }

            send(
                ProgressUpdate(
                    bytesSent = copied,
                    totalBytes = copied,
                    result = ImportedFile(
                        file = destination,
                        displayName = info.name,
                        mimeType = info.mimeType
                    )
                )
            )
        } catch (e: Throwable) {
            destination.delete() // Don't leave half-copied files behind (also runs on cancellation).
            throw e
        }
    }

    private fun FileInfo.safeExtension(): String {
        val extension = name.substringAfterLast('.', "").filter { it.isLetterOrDigit() }
        return if (extension.isEmpty() || extension.length > 8) "" else ".$extension"
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val REPORT_INTERVAL_BYTES = 256 * 1024L
    }
}

/**
 * Contains information about the current copy progress.
 * @param bytesSent The number of bytes copied so far.
 * @param totalBytes The total number of bytes to copy.
 * @param result Only set on the final update: the file that was saved into app storage.
 */
data class ProgressUpdate(
    val bytesSent: Long,
    val totalBytes: Long,
    val result: ImportedFile? = null
)

/**
 * A file that has been copied into app storage.
 * @param file The saved copy inside app storage.
 * @param displayName The original file name the user picked (useful as a default song title).
 * @param mimeType The file type, such as "audio/mpeg". Empty if unknown.
 */
data class ImportedFile(
    val file: File,
    val displayName: String,
    val mimeType: String
)
