package com.example.aio_musica.file_upload

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import java.io.FileNotFoundException
import java.io.IOException

/**
 * ViewModel for importing a file into the app.
 * Links to the actual progress bar. Watches the progress from FileRepository.kt
 * @param repository The FileRepository class that handles copying the file.
 */
class UploadFileViewModel (
    private val repository: FileRepository
) : ViewModel() {

    /**
     * State of the upload.
     */
    var state by mutableStateOf(UploadSate())
        private set

    /**
     * Job for the upload.
     */
    private var uploadJob: Job? = null

    /**
     * Copies the chosen file into app storage.
     * Failures are reported through [UploadSate.errorMessage] rather than thrown:
     * - [CancellationException]: the upload was cancelled.
     * - [FileNotFoundException]: the file could not be found/opened.
     * - [IOException]: reading or writing failed (e.g. device storage is full).
     * @param content The URI of the file to import.
     */
    fun uploadFile(content: Uri) {
        uploadJob = repository
            .uploadFile(content)
            /**
             * Starts the upload.
             */
            .onStart {
                state = state.copy(
                    isUploading = true,
                    isUploadComplete = false,
                    progress = 0f,
                    errorMessage = null,
                    importedFile = null
                )
            }
            /**
             * Updates the progress of the upload.
             * Casts progress update to float and divides by total bytes to get a percentage.
             * The final update also carries the saved file.
             */
            .onEach { progressUpdate ->
                state = state.copy(
                    progress = if (progressUpdate.totalBytes > 0) {
                        progressUpdate.bytesSent.toFloat() / progressUpdate.totalBytes.toFloat()
                    } else {
                        state.progress
                    },
                    importedFile = progressUpdate.result ?: state.importedFile
                )
            }
            /**
             * Ends the upload.
             * If the upload is successful, the state is updated to reflect that.
             * If the upload is canceled, the state is updated to reflect that.
             * If the upload fails, the catch block below updates the state.
             */
            .onCompletion { cause ->
                if(cause == null) {
                    state = state.copy(
                        isUploading = false,
                        isUploadComplete = true,
                    )
                } else if(cause is CancellationException) {
                    state = state.copy(
                        isUploading = false,
                        errorMessage = "Upload cancelled",
                        isUploadComplete = false,
                        progress = 0f
                    )
                }
            }
            .catch { cause ->
                val message = when(cause) {
                    is FileNotFoundException -> "File not found"
                    is IOException -> "Couldn't copy file. Is your storage full?"
                    else -> "Something went wrong"
                }
                state = state.copy(
                    isUploading = false,
                    errorMessage = message
                )
            }
            .launchIn(viewModelScope)
    }

    /**
     * Cancels the upload
     */
    fun cancelUpload() {
        uploadJob?.cancel()
    }
}

data class UploadSate(
    val isUploading: Boolean = false,
    val isUploadComplete: Boolean = false,
    val progress: Float = 0f,
    val errorMessage: String? = null,
    /** The saved file once the upload completes. Use this to create a Song in the library. */
    val importedFile: ImportedFile? = null
)
