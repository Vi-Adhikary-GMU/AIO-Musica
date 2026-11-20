package com.example.aio_musica.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.aio_musica.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sits between the playlist screens and [Library] / [LibraryStorage].
 * Every change is saved to disk right away, then a fresh [LibraryState] is published for the UI.
 *
 * The functions that change something return an error message to show the user,
 * or null when everything worked.
 *
 * Shared through `activityViewModels()` so the playlist list and a single playlist's
 * screen look at the same data.
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val storage = LibraryStorage(application.applicationContext)
    private var library: Library = storage.load()

    private val _state = MutableStateFlow(snapshot())

    /** What the UI draws. Collect this from the fragments. */
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    /**
     * Re-reads the library from disk. Call this when a screen becomes visible, because
     * other screens (like the File tab) save their own changes to the same file.
     */
    fun reload() {
        library = storage.load()
        publish()
    }

    /**
     * Creates an empty playlist.
     * @param rawName the name the user typed (extra spaces around it are ignored)
     * @return an error message, or null on success
     */
    fun createPlaylist(rawName: String): String? {
        val name = rawName.trim()
        if (name.isEmpty()) return string(R.string.playlist_error_name_empty)

        return try {
            library.addPlaylist(Playlist(name, library))
            persist()
            null
        } catch (e: IllegalArgumentException) {
            string(R.string.playlist_error_name_taken, name)
        }
    }

    /**
     * Deletes a playlist. The songs stay in the library.
     * @return an error message, or null on success
     */
    fun deletePlaylist(name: String): String? {
        val playlist = library.getPlaylists()[name] ?: return string(R.string.playlist_error_not_found)
        library.removePlaylist(playlist)
        persist()
        return null
    }

    /**
     * Adds songs to a playlist. Songs that are already in it are skipped.
     * @param playlistName which playlist
     * @param songIds ids of the songs to add
     * @return an error message, or null on success
     */
    fun addSongs(playlistName: String, songIds: List<String>): String? {
        val playlist = library.getPlaylists()[playlistName]
            ?: return string(R.string.playlist_error_not_found)
        val songs = library.getSongs()

        for (id in songIds) {
            val song = songs[id] ?: continue
            try {
                playlist.addSong(song)
            } catch (e: IllegalArgumentException) {
                // Already in the playlist, nothing to do.
            }
        }
        persist()
        return null
    }

    /**
     * Removes a song from a playlist (the song stays in the library).
     * @return an error message, or null on success
     */
    fun removeSong(playlistName: String, songId: String): String? {
        val playlist = library.getPlaylists()[playlistName]
            ?: return string(R.string.playlist_error_not_found)
        val song = library.getSongs()[songId] ?: return null

        try {
            playlist.removeSong(song)
        } catch (e: NoSuchElementException) {
            // Already gone.
        }
        persist()
        return null
    }

    private fun persist() {
        storage.save(library)
        publish()
    }

    private fun publish() {
        _state.value = snapshot()
    }

    /** Copies the current library into plain immutable objects for the UI. */
    private fun snapshot(): LibraryState {
        val playlists = library.getPlaylists().values
            .sortedBy { it.getDateCreated().time }
            .map { PlaylistInfo(name = it.getName(), songIds = it.getSongIDs()) }

        return LibraryState(
            songs = library.getSongs().values.sortedBy { it.title.lowercase() },
            playlists = playlists
        )
    }

    private fun string(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)
}

/** Everything the playlist screens need to draw themselves. */
data class LibraryState(
    val songs: List<Song> = emptyList(),
    val playlists: List<PlaylistInfo> = emptyList()
)

/** An immutable view of one playlist: its name and the ids of its songs, in order. */
data class PlaylistInfo(
    val name: String,
    val songIds: List<String>
)
