package com.example.aio_musica

import com.example.aio_musica.library.Playlist
import com.example.aio_musica.library.Library
import com.example.aio_musica.library.Song
import org.junit.Test
import org.junit.Assert.*
import android.net.Uri
import io.mockk.mockk
import org.junit.Before

// Shared helper: Uri is an Android class, so we mock it for plain JVM unit tests
private fun makeSong(id: String) = Song(
    id = id,
    title = "Title $id",
    uri = mockk<Uri>(),
    mimeType = "audio/mpeg"
)


class AddSongLibrary{
    private lateinit var library: Library

    @Before
    fun setUp() {
        library = Library()
    }

    // test1: add song to library, map length should be 1
    @Test
    fun addSong_oneSong_libraryHasOneSong() {
        library.addSong(makeSong("1"))

        assertEquals(1, library.getSongs().size)
    }

    // test2: add two songs, map length should be 2
    @Test
    fun addSong_twoSongs_libraryHasTwoSongs() {
        library.addSong(makeSong("1"))
        library.addSong(makeSong("2"))

        assertEquals(2, library.getSongs().size)
    }

    // test3: adding a song that is already in the library throws IllegalArgumentException
    @Test
    fun addSong_duplicateSong_throwsIllegalArgumentException() {
        val song = makeSong("1")
        library.addSong(song)

        assertThrows(IllegalArgumentException::class.java) {
            library.addSong(song)
        }
    }
}

class removeSongLibrary{
    private lateinit var library: Library

    @Before
    fun setUp() {
        library = Library()
    }

    // test1: add song then remove it, length should be 0
    @Test
    fun removeSong_oneSong_libraryIsEmpty() {
        val song = makeSong("1")
        library.addSong(song)

        library.removeSong(song)

        assertEquals(0, library.getSongs().size)
    }

    // test2: add two songs, remove one, length should be 1
    @Test
    fun removeSong_twoSongsRemoveOne_libraryHasOneSong() {
        val song1 = makeSong("1")
        val song2 = makeSong("2")
        library.addSong(song1)
        library.addSong(song2)

        library.removeSong(song1)

        assertEquals(1, library.getSongs().size)
    }

    // test3: removing a song that is not in the library throws NoSuchElementException
    @Test
    fun removeSong_songNotInLibrary_throwsNoSuchElementException() {
        val song = makeSong("1")

        assertThrows(NoSuchElementException::class.java) {
            library.removeSong(song)
        }
    }

    // test4: song is in two playlists, removing it from the library empties both playlists
    @Test
    fun removeSong_songInTwoPlaylists_removedFromBothPlaylists() {
        val song = makeSong("1")
        library.addSong(song)
        val playlist1 = Playlist("Eating Playlist", library)
        val playlist2 = Playlist("Workout", library)
        library.addPlaylist(playlist1)
        library.addPlaylist(playlist2)
        playlist1.addSong(song)
        playlist2.addSong(song)

        library.removeSong(song)

        assertEquals(0, playlist1.getSongIDs().size)
        assertEquals(0, playlist2.getSongIDs().size)
    }
    // test5: song is in one playlist but not the other
    // removing it should only affect the playlist containing it
    @Test
    fun removeSong_songNotInEveryPlaylist_onlyRemovesFromMatchingPlaylist() {
        val song = makeSong("1")
        library.addSong(song)

        val playlist1 = Playlist("Eating Playlist", library)
        val playlist2 = Playlist("Workout", library)

        library.addPlaylist(playlist1)
        library.addPlaylist(playlist2)

        playlist1.addSong(song)

        library.removeSong(song)

        assertEquals(0, playlist1.getSongIDs().size)
        assertEquals(0, playlist2.getSongIDs().size)
    }
}

class addPlaylistLibrary{
    private lateinit var library: Library

    @Before
    fun setUp() {
        library = Library()
    }

    // test1: add a playlist, length should be 1
    @Test
    fun addPlaylist_onePlaylist_libraryHasOnePlaylist() {
        library.addPlaylist(Playlist("relax", library))

        assertEquals(1, library.getPlaylists().size)
    }

    // test2: add two playlists, length should be 2
    @Test
    fun addPlaylist_twoPlaylists_libraryHasTwoPlaylists() {
        library.addPlaylist(Playlist("Relaxing", library))
        library.addPlaylist(Playlist("Workout", library))

        assertEquals(2, library.getPlaylists().size)
    }

    // test3: adding the same playlist twice throws IllegalArgumentException
    @Test
    fun addPlaylist_duplicatePlaylist_throwsIllegalArgumentException() {
        val playlist = Playlist("sleep music", library)
        library.addPlaylist(playlist)

        assertThrows(IllegalArgumentException::class.java) {
            library.addPlaylist(playlist)
        }
    }
}

class removePlaylistLibrary{
    private lateinit var library: Library

    @Before
    fun setUp() {
        library = Library()
    }

    // test1: add a playlist then remove it, length should be 0
    @Test
    fun removePlaylist_onePlaylist_libraryIsEmpty() {
        val playlist = Playlist("sleep", library)
        library.addPlaylist(playlist)

        library.removePlaylist(playlist)

        assertEquals(0, library.getPlaylists().size)
    }

    // test2: add two playlists, remove one, length should be 1
    @Test
    fun removePlaylist_twoPlaylistsRemoveOne_libraryHasOnePlaylist() {
        val playlist1 = Playlist("driving", library)
        val playlist2 = Playlist("Workout", library)
        library.addPlaylist(playlist1)
        library.addPlaylist(playlist2)

        library.removePlaylist(playlist1)

        assertEquals(1, library.getPlaylists().size)
    }

    // test3: add a playlist, then try to remove a different one, throws NoSuchElementException
    @Test
    fun removePlaylist_differentPlaylist_throwsNoSuchElementException() {
        val playlist = Playlist("driving", library)
        val other = Playlist("Workout", library)
        library.addPlaylist(playlist)

        assertThrows(NoSuchElementException::class.java) {
            library.removePlaylist(other)
        }
    }
}

class renamePlaylistLibrary{
    private lateinit var library: Library

    @Before
    fun setUp() {
        library = Library()
    }

    // test1: rename a playlist, it should have the new name and be stored under the new name
    @Test
    fun renamePlaylist_validName_playlistHasNewName() {
        val playlist = Playlist("sleeping", library)
        library.addPlaylist(playlist)

        library.renamePlaylist("Workout", playlist)

        assertEquals("Workout", playlist.getName())
        assertTrue(library.getPlaylists().containsKey("Workout"))
        assertFalse(library.getPlaylists().containsKey("sleeping"))
    }

    // test2: renaming to a name that another playlist already has throws IllegalArgumentException
    @Test
    fun renamePlaylist_nameAlreadyTaken_throwsIllegalArgumentException() {
        val playlist1 = Playlist("sleeping", library)
        val playlist2 = Playlist("Workout", library)
        library.addPlaylist(playlist1)
        library.addPlaylist(playlist2)

        assertThrows(IllegalArgumentException::class.java) {
            library.renamePlaylist("Workout", playlist1)
        }
    }

    // test3: renaming a playlist when the map is empty throws NoSuchElementException
    @Test
    fun renamePlaylist_emptyLibrary_throwsNoSuchElementException() {
        val playlist = Playlist("walking outside", library)

        assertThrows(NoSuchElementException::class.java) {
            library.renamePlaylist("Workout", playlist)
        }
    }
}
class clearMapOfSongsLibrary{
    // add three songs, clear, size should be 0
    @Test
    fun clearSongs_threeSongs_libraryIsEmpty() {
        val library = Library()
        library.addSong(makeSong("1"))
        library.addSong(makeSong("2"))
        library.addSong(makeSong("3"))

        library.clearSongs()

        assertEquals(0, library.getSongs().size)
    }
}
class clearPlaylistLibrary{
    // add three playlists, clear, size should be 0
    @Test
    fun clearPlaylists_threePlaylists_libraryIsEmpty() {
        val library = Library()
        library.addPlaylist(Playlist("One", library))
        library.addPlaylist(Playlist("Two", library))
        library.addPlaylist(Playlist("Three", library))

        library.clearPlaylists()

        assertEquals(0, library.getPlaylists().size)
    }
}
class getSongsLibrary{
    // add three songs, all three ids should be in the map we get back
    @Test
    fun getSongs_threeSongs_containsAllIds() {
        val library = Library()
        library.addSong(makeSong("1"))
        library.addSong(makeSong("2"))
        library.addSong(makeSong("3"))

        val songs = library.getSongs()

        assertTrue(songs.containsKey("1"))
        assertTrue(songs.containsKey("2"))
        assertTrue(songs.containsKey("3"))
    }
}
class getPlayListLibrary{
    // add three playlists, all three names should be in the map we get back
    @Test
    fun getPlaylists_threePlaylists_containsAllNames() {
        val library = Library()
        library.addPlaylist(Playlist("One", library))
        library.addPlaylist(Playlist("Two", library))
        library.addPlaylist(Playlist("Three", library))

        val playlists = library.getPlaylists()

        assertTrue(playlists.containsKey("One"))
        assertTrue(playlists.containsKey("Two"))
        assertTrue(playlists.containsKey("Three"))
    }
}
class containsSongLibrary{
    // add a song, containsSong should be true
    @Test
    fun containsSong_oneSongAdded_returnsTrue() {
        val library = Library()
        library.addSong(makeSong("1"))

        assertTrue(library.containsSong("1"))
    }

    // same check for two songs
    @Test
    fun containsSong_twoSongsAdded_returnsTrueForBoth() {
        val library = Library()
        library.addSong(makeSong("1"))
        library.addSong(makeSong("2"))

        assertTrue(library.containsSong("1"))
        assertTrue(library.containsSong("2"))
    }

    // song not added, containsSong should be false
    @Test
    fun containsSong_songNotAdded_returnsFalse() {
        val library = Library()

        assertFalse(library.containsSong("1"))
    }
}