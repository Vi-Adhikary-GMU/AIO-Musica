package com.example.aio_musica.file_upload

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import okio.FileNotFoundException

/**
 * ViewModel for uploading a file.
 * Links to the actual progress bar. Watches the progress from FileRepository.kt
 * @param repository The FileRepository class that handles the upload.
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
     * Uploads a file to the server.
     * @param content The URI of the file to upload.
     * @throws CancellationException if the upload is canceled.
     * @throws OutOfMemoryError if the file is too large.
     * @throws FileNotFoundException if the file is not found.
     * @throws UnresolvedAddressException if there is no internet connection.
     * @throws Exception if something goes wrong.
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
                    errorMessage = null
                )
            }
            /**
             * Updates the progress of the upload.
             * Casts progress update to float and divides by total bytes to get a percentage.
             */
            .onEach { progressUpdate ->
                state = state.copy(
                    progress = progressUpdate.bytesSent.toFloat() / progressUpdate.totalBytes.toFloat()
                )
            }
            /**
             * Ends the upload.
             * If the upload is successful, the state is updated to reflect that.
             * If the upload is canceled, the state is updated to reflect that.
             * If the upload fails, the state is updated to reflect that.
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
                    is OutOfMemoryError -> "File too large"
                    is FileNotFoundException -> "File not found"
                    is UnresolvedAddressException -> "No internet"
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
    val errorMessage: String? = null
)