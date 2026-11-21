package com.example.aio_musica

import com.example.aio_musica.library.Playlist
import com.example.aio_musica.library.Library
import com.example.aio_musica.library.Song
import org.junit.Test
import org.junit.Assert.*
import android.net.Uri
import io.mockk.mockk
import org.junit.Before

// All the tests for add song method of playlist

// Uri is an Android class, so we mock it for plain JVM unit tests
private fun makeSong(id: String) = Song(
    id = id,
    title = "Title $id",
    uri = mockk<Uri>(),
    mimeType = "audio/mpeg"
)

class AddSongPlayList{
    private lateinit var library: Library
    private lateinit var playlist: Playlist



    // Runs before every test so each test starts with a fresh library and playlist
    @Before
    fun setUp() {
        library = Library()
        playlist = Playlist("Driving to GMU", library)
    }

    // first test: add song to library and then to playlist, songIDs list should have length 1
    @Test
    fun addSong_oneSong_playlistHasOneSong() {
        val song = makeSong("1")
        library.addSong(song)

        playlist.addSong(song)

        assertEquals(1, playlist.getSongIDs().size)
    }

    // second test: same as the first but with two songs, list should have length 2
    @Test
    fun addSong_twoSongs_playlistHasTwoSongs() {
        val song1 = makeSong("1")
        val song2 = makeSong("2")
        library.addSong(song1)
        library.addSong(song2)

        playlist.addSong(song1)
        playlist.addSong(song2)

        assertEquals(2, playlist.getSongIDs().size)
    }

    // third test: adding a song that was never added to the library throws NoSuchElementException
    @Test
    fun addSong_songNotInLibrary_throwsNoSuchElementException() {
        val song = makeSong("1")

        assertThrows(NoSuchElementException::class.java) {
            playlist.addSong(song)
        }
    }

    // fourth test: adding the same song to the playlist twice throws IllegalArgumentException
    @Test
    fun addSong_duplicateSong_throwsIllegalArgumentException() {
        val song = makeSong("1")
        library.addSong(song)
        playlist.addSong(song)

        assertThrows(IllegalArgumentException::class.java) {
            playlist.addSong(song)
        }
    }
}

class RemoveSongPlaylist{
    private lateinit var library: Library
    private lateinit var playlist: Playlist

    @Before
    fun setUp() {
        library = Library()
        playlist = Playlist("Driving to GMU", library)
    }

    // first test: add one song to library and playlist, remove it, size should be 0
    @Test
    fun removeSong_oneSong_playlistIsEmpty() {
        val song = makeSong("1")
        library.addSong(song)
        playlist.addSong(song)

        playlist.removeSong(song)

        assertEquals(0, playlist.getSongIDs().size)
    }

    // second test: add two songs, remove one, size should be 1
    @Test
    fun removeSong_twoSongsRemoveOne_playlistHasOneSong() {
        val song1 = makeSong("1")
        val song2 = makeSong("2")
        library.addSong(song1)
        library.addSong(song2)
        playlist.addSong(song1)
        playlist.addSong(song2)

        playlist.removeSong(song1)

        assertEquals(1, playlist.getSongIDs().size)
        assertTrue(playlist.getSongIDs().contains("2"))
    }

    // third test: removing a song that was never added anywhere throws NoSuchElementException
    @Test
    fun removeSong_songNeverAdded_throwsNoSuchElementException() {
        val song = makeSong("1")

        assertThrows(NoSuchElementException::class.java) {
            playlist.removeSong(song)
        }
    }

    // fourth test: song is in the library but not the playlist, removing it throws NoSuchElementException
    @Test
    fun removeSong_inLibraryButNotInPlaylist_throwsNoSuchElementException() {
        val song = makeSong("1")
        library.addSong(song)

        assertThrows(NoSuchElementException::class.java) {
            playlist.removeSong(song)
        }
    }
}

class ResetPlaylist{
    // add song to library and playlist, call reset, size should be 0
    @Test
    fun reset_playlistWithSong_playlistIsEmpty() {
        val library = Library()
        val playlist = Playlist("Driving to gmu", library)
        val song = makeSong("1")
        library.addSong(song)
        playlist.addSong(song)

        playlist.reset()

        assertEquals(0, playlist.getSongIDs().size)
    }
    @Test
    fun reset_playlistWithMultipleSongs_playlistIsEmpty() {
        val library = Library()
        val playlist = Playlist("Driving", library)
        val song1 = makeSong("1")
        val song2 = makeSong("2")

        library.addSong(song1)
        library.addSong(song2)
        playlist.addSong(song1)
        playlist.addSong(song2)

        playlist.reset()

        assertTrue(playlist.getSongIDs().isEmpty())
    }
}
class GetNamePlaylist{
    // creating a playlist and getting the name gives back what we passed in
    @Test
    fun getName_returnsNameGivenAtCreation() {
        val playlist = Playlist("Driving", Library())

        assertEquals("Driving", playlist.getName())
    }
}
class SetNamePlaylist{
    // change the name with setName, then getName should return the new name
    @Test
    fun setName_changesName() {
        val playlist = Playlist("Driving to gmu", Library())

        playlist.setName("Workout")

        assertEquals("Workout", playlist.getName())
    }
}

class GetTimePlaylist{
    // creating a playlist should set the creation time, somewhere between just before and just after creation
    @Test
    fun getDateCreated_isSetAtCreation() {
        val before = System.currentTimeMillis()
        val playlist = Playlist("Relaxing", Library())
        val after = System.currentTimeMillis()

        val created = playlist.getDateCreated()

        assertNotNull(created)
        assertTrue(created.time >= before)
        assertTrue(created.time <= after)
    }
}
class GetSongIDsPlaylist{
    private lateinit var library: Library
    private lateinit var playlist: Playlist

    @Before
    fun setUp() {
        library = Library()
        playlist = Playlist("Gym", library)
    }

    // first test: new playlist has no songs
    @Test
    fun getSongIDs_newPlaylist_isEmpty() {
        assertEquals(0, playlist.getSongIDs().size)
    }

    // second test: one song added to library and playlist, length should be 1
    @Test
    fun getSongIDs_oneSong_hasLengthOne() {
        val song = makeSong("1")
        library.addSong(song)
        playlist.addSong(song)

        assertEquals(1, playlist.getSongIDs().size)
    }

    // third test: two songs added, both ids should be in the list
    @Test
    fun getSongIDs_twoSongs_containsBothIds() {
        val song1 = makeSong("1")
        val song2 = makeSong("2")
        library.addSong(song1)
        library.addSong(song2)
        playlist.addSong(song1)
        playlist.addSong(song2)

        val ids = playlist.getSongIDs()

        assertTrue(ids.contains("1"))
        assertTrue(ids.contains("2"))
    }
    //fourth test
    @Test
    fun getSongIDs_modifyingReturnedList_doesNotChangePlaylist() {
        val song = makeSong("1")
        library.addSong(song)
        playlist.addSong(song)

        val ids = playlist.getSongIDs()

        // The returned list is a snapshot, not the original list.
        assertEquals(listOf("1"), ids)
        assertEquals(listOf("1"), playlist.getSongIDs())
    }
}