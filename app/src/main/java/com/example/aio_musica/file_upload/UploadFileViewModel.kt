package com.example.aio_musica.file_upload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import java.io.FileNotFoundException
import java.io.IOException
import com.example.aio_musica.library.Library
import com.example.aio_musica.library.LibraryStorage
import com.example.aio_musica.library.Metadata

/**
 * ViewModel for importing a file into the app.
 * Links to the actual progress bar. Watches the progress from FileRepository.kt
 * @param repository The FileRepository class that handles copying the file.
 * @param library The library containing songs and playlists
 * @param libraryStorage Handles saving the library to storage
 */
class UploadFileViewModel (
    private val repository: FileRepository,
    private val library: Library,
    private val libraryStorage: LibraryStorage
) : ViewModel() {

    private val _state = MutableStateFlow(UploadSate())

    /**
     * State of the upload. Collect this from the UI.
     * (This is a StateFlow rather than Compose state because MainActivity uses XML Views.)
     */
    val state: StateFlow<UploadSate> = _state.asStateFlow()

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
        if (_state.value.isUploading) return // Ignore picks while an upload is already running.

        uploadJob = repository
            .uploadFile(content)
            /**
             * Starts the upload.
             */
            .onStart {
                _state.update {
                    it.copy(
                        isUploading = true,
                        isUploadComplete = false,
                        progress = 0f,
                        errorMessage = null,
                        importedFile = null
                    )
                }
            }
            /**
             * Updates the progress of the upload.
             * Casts progress update to float and divides by total bytes to get a percentage.
             * The final update also carries the saved file.
             */
            .onEach { progressUpdate ->
                _state.update {
                    it.copy(
                        progress = if (progressUpdate.totalBytes > 0) {
                            progressUpdate.bytesSent.toFloat() / progressUpdate.totalBytes.toFloat()
                        } else {
                            it.progress
                        },
                        importedFile = progressUpdate.result ?: it.importedFile
                    )
                }
            }
            /**
             * Ends the upload.
             * If the upload is successful, the state is updated to reflect that,
             * and it adds the imported audio file to the library as a song.
             * If the upload is canceled, the state is updated to reflect that.
             * If the upload fails, the catch block below updates the state.
             */
            .onCompletion { cause ->
                if(cause == null) {
                    val importedFile =  _state.value.importedFile
                    if (importedFile != null) {
                        val metadata = Metadata(
                        uri = Uri.fromFile(importedFile.file),
                        fallbackTitle = importedFile.displayName.substringBeforeLast('.'),
                        mimeType = importedFile.mimeType.ifBlank { "audio/mpeg" }
                        )
                        val song = metadata.generateSong()
                        library.addSong(song)
                        libraryStorage.save(library)
                    }
                    _state.update {
                        it.copy(
                            isUploading = false,
                            isUploadComplete = true,
                        )
                    }
                } else if(cause is CancellationException) {
                    _state.update {
                        it.copy(
                            isUploading = false,
                            errorMessage = "Upload cancelled",
                            isUploadComplete = false,
                            progress = 0f
                        )
                    }
                }
            }
            .catch { cause ->
                val message = when(cause) {
                    is FileNotFoundException -> "File not found"
                    is IOException -> "Couldn't copy file. Is your storage full?"
                    else -> "Something went wrong"
                }
                _state.update {
                    it.copy(
                        isUploading = false,
                        errorMessage = message
                    )
                }
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
