package com.example.aio_musica



import android.net.Uri
import com.example.aio_musica.library.Library
import com.example.aio_musica.library.Playlist
import com.example.aio_musica.library.Song
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LibraryPlaylistIntegrationTest {

    private lateinit var library: Library

    private fun makeSong(id: String) = Song(
        id = id,
        title = "Title $id",
        uri = mockk<Uri>(),
        mimeType = "audio/mpeg"
    )

    @Before
    fun setUp() {
        library = Library()
    }

    // Test 1: Removing a library song also removes it from every playlist.
    @Test
    fun removeSong_removesSongFromLibraryAndAllPlaylists() {
        val song = makeSong("1")
        val playlist1 = Playlist("Driving", library)
        val playlist2 = Playlist("Workout", library)

        library.addSong(song)
        library.addPlaylist(playlist1)
        library.addPlaylist(playlist2)

        playlist1.addSong(song)
        playlist2.addSong(song)

        library.removeSong(song)

        assertFalse(library.containsSong("1"))
        assertTrue(playlist1.getSongIDs().isEmpty())
        assertTrue(playlist2.getSongIDs().isEmpty())
    }

    // Test 2: Renaming a playlist updates both the playlist and library map.
    @Test
    fun renamePlaylist_updatesPlaylistAndLibrary() {
        val playlist = Playlist("Driving", library)

        library.addPlaylist(playlist)
        library.renamePlaylist("Workout", playlist)

        assertEquals("Workout", playlist.getName())
        assertTrue(library.getPlaylists().containsKey("Workout"))
        assertFalse(library.getPlaylists().containsKey("Driving"))
    }

    // Test 3: Removing a playlist does not remove its songs from the library.
    @Test
    fun removePlaylist_keepsSongsInLibrary() {
        val song = makeSong("1")
        val playlist = Playlist("Driving", library)

        library.addSong(song)
        library.addPlaylist(playlist)
        playlist.addSong(song)

        library.removePlaylist(playlist)

        assertTrue(library.getPlaylists().isEmpty())
        assertTrue(library.containsSong("1"))
        assertEquals(1, library.getSongs().size)
    }

    // Test 4: Resetting a playlist clears its songs but not the library.
    @Test
    fun resetPlaylist_keepsSongsInLibrary() {
        val song1 = makeSong("1")
        val song2 = makeSong("2")
        val playlist = Playlist("Workout", library)

        library.addSong(song1)
        library.addSong(song2)
        library.addPlaylist(playlist)

        playlist.addSong(song1)
        playlist.addSong(song2)

        playlist.reset()

        assertTrue(playlist.getSongIDs().isEmpty())
        assertEquals(2, library.getSongs().size)
        assertTrue(library.containsSong("1"))
        assertTrue(library.containsSong("2"))
    }

    // Test 5: Clearing playlists leaves songs intact; clearing songs empties the library.
    @Test
    fun clearLibraryCollections_clearsExpectedData() {
        val song = makeSong("1")
        val playlist = Playlist("Relaxing", library)

        library.addSong(song)
        library.addPlaylist(playlist)

        assertNotNull(playlist.getDateCreated())

        library.clearPlaylists()

        assertTrue(library.getPlaylists().isEmpty())
        assertEquals(1, library.getSongs().size)

        library.clearSongs()

        assertTrue(library.getSongs().isEmpty())
    }
}
