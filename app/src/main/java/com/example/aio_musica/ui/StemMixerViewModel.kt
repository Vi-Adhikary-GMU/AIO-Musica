package com.example.aio_musica.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.lifecycle.viewModelScope
import com.example.aio_musica.mvsep.MvsepRepository
import com.example.aio_musica.mvsep.MvsepDownloader
import com.example.aio_musica.library.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.ContentResolver
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Job


data class StemState(
    val id: String,
    val name: String,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val audioFile: File? = null
)

class StemMixerViewModel : ViewModel() {

    /**
     * Repository for MVSEP API operations.
     */
    private var mvsepRepository: MvsepRepository? = null

    /**
     * Downloader for saving separated audio files.
     */
    private val downloader = MvsepDownloader()
    /**
     * Active separation coroutine.
     */
    private var separationJob: Job? = null
    /**
     * Current mixer tracks.
     */
    private val _stems = MutableStateFlow<List<StemState>>(emptyList())
    val stems: StateFlow<List<StemState>> = _stems.asStateFlow()

    /**
     * The song currently selected in the Original tab (set by MusicScreen).
     */
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    /**
     * The stem the user asked to export while the "Save as" screen is open.
     */
    var pendingExportId: String? = null

    /**
     * Display order of stems: Vocals, Instrumental, the rest, Other last.
     */
    private val stemOrder = listOf("vocal", "instrum", "guitar", "piano", "bass", "drum", "other")

    /**
     * Indicates whether separation or downloading is in progress.
     */
    private val _isProcessing = MutableStateFlow(false)

    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    /**
     * Current processing status message.
     */
    private val _status = MutableStateFlow("")

    val status: StateFlow<String> = _status.asStateFlow()

    /**
     * Current error message, if any.
     */
    private val _error = MutableStateFlow<String?>(null)

    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Remembers which song the Mixer can separate.
     */
    fun setCurrentSong(song: Song?) {
        _currentSong.value = song
    }

    /**
     * Configure MVSEP repository.
     */
    fun configure(repository: MvsepRepository) {
        mvsepRepository = repository
        _error.value = null
    }

    /**
     * Separates an audio file using MVSEP and downloads
     * the resulting audio stems.
     * After downloading, each stem is converted into a
     * [StemState] for use by the mixer interface.
     *
     * @param file Original audio file to separate.
     * @param destinationDir Directory where separated stems
     * will be stored.
     */
    fun separateSong(
        file: File,
        destinationDir: File
    ) {
        if (separationJob?.isActive == true) {
            return
        }
        val repository = mvsepRepository
        if (repository == null) {
            _error.value = "MVSEP repository is not configured."
            return
        }
        if (!file.exists() || !file.isFile) {
            _error.value = "Selected audio file does not exist."
            return
        }
        if (destinationDir.exists() && !destinationDir.isDirectory) {
            _error.value = "Destination path is not a directory."
            return
        }
        separationJob = viewModelScope.launch {
            _isProcessing.value = true
            _error.value = null
            _status.value = "Uploading song..."
            _stems.value = emptyList()
            try {
                // Submit audio separation request.
                val result = repository.separateSong(file)
                _status.value = "Downloading separated tracks..."
                // Download the generated audio stems.
                val downloaded = downloader.downloadStems(
                    stems = result,
                    destinationDir = destinationDir
                )
                // Convert downloaded files into mixer tracks.
                _stems.value = downloaded.map { stem ->
                    StemState(
                        id = UUID.randomUUID().toString(),
                        name = stem.name,
                        // Instrumental already contains the other instruments, so it starts muted.
                        muted = isInstrumental(stem.name),
                        audioFile = stem.file
                    )
                }.sortedBy { stem ->
                    val n = stem.name.lowercase()
                    stemOrder.indexOfFirst { it in n }.takeIf { it >= 0 } ?: stemOrder.size
                }
                _status.value = "Separation complete!"
            } catch (e: CancellationException) {
                _status.value = "Separation cancelled"
                throw e
            } catch (e: Exception) {
                _error.value = e.message
                    ?: "An error occurred during audio separation."
                _status.value = "Separation failed"
            } finally {
                _isProcessing.value = false
                separationJob = null
            }
        }
    }

    /**
     * Updates the playback volume of a specific stem.
     * Volume is restricted to the range 0.0 to 1.0.
     *
     * @param id Unique identifier of the stem.
     * @param volume Requested volume level.
     */
    fun setVolume(id: String, volume: Float) {
        updateStem(id) {
            it.copy(
                volume = volume.coerceIn(0f, 1f)
            )
        }
    }

    /**
     * Toggles the mute state of a specific stem.
     *
     * @param id Unique identifier of the stem.
     */
    fun toggleMute(id: String) {
        _stems.update { list ->
            val target = list.firstOrNull { it.id == id } ?: return@update list
            val unmuting = target.muted
            list.map { stem ->
                when {
                    stem.id == id -> stem.copy(muted = !stem.muted)
                    // Unmuting Instrumental mutes bass, drums, piano, guitar and other.
                    unmuting && isInstrumental(target.name) && isBackingStem(stem.name) ->
                        stem.copy(muted = true)
                    // Muting Instrumental brings the individual stems back.
                    !unmuting && isInstrumental(target.name) && isBackingStem(stem.name) ->
                        stem.copy(muted = false)
                    // Unmuting one of those mutes Instrumental, so nothing plays twice.
                    unmuting && isBackingStem(target.name) && isInstrumental(stem.name) ->
                        stem.copy(muted = true)
                    else -> stem
                }
            }
        }
    }

    private fun isInstrumental(name: String) = "instrum" in name.lowercase()

    /** The stems that are already inside Instrumental (everything except vocals). */
    private fun isBackingStem(name: String): Boolean {
        val n = name.lowercase()
        return listOf("bass", "drum", "piano", "guitar", "other").any { it in n }
    }

    /**
     * Retrieves the local audio file associated with a stem.
     *
     * @param id Unique identifier of the stem.
     * @return Audio file if found, otherwise null.
     */
    fun getStemFile(id: String): File? {
        return _stems.value
            .firstOrNull { it.id == id }
            ?.audioFile
    }

    /**
     * Copies a stem's audio file to a location the user picked in the
     * system "Save as" screen.
     *
     * @param id Unique identifier of the stem.
     * @param destination Document Uri chosen by the user.
     * @param resolver Used to open the destination for writing.
     */
    fun exportStem(id: String, destination: Uri, resolver: ContentResolver) {
        val file = getStemFile(id)
        if (file == null || !file.exists()) {
            _error.value = "Stem audio file not found."
            return
        }
        viewModelScope.launch {
            _status.value = "Exporting ${file.name}..."
            try {
                withContext(Dispatchers.IO) {
                    val output = resolver.openOutputStream(destination)
                        ?: throw IOException("Could not open the chosen location.")
                    output.use { out ->
                        file.inputStream().use { input -> input.copyTo(out) }
                    }
                }
                _status.value = "Saved ${file.name}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Couldn't export: ${e.message}"
                _status.value = "Export failed"
            }
        }
    }

    /**
     * Clears the current error message.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Updates a stem using its unique identifier.
     *
     * @param id Unique identifier of the stem.
     * @param change Function producing the updated stem state.
     */
    private fun updateStem(id: String, change: (StemState) -> StemState) {
        _stems.update { list -> list.map { stem -> if (stem.id == id) {change(stem)} else {stem}}}
    }
}