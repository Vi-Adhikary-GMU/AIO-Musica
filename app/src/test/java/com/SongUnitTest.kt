package com.example.aio_musica

import com.example.aio_musica.library.Song
import android.net.Uri
import io.mockk.mockk
import org.junit.Test
import org.junit.Assert.*

// Tests for the Song class

// Uri is an Android class, mock it for plain JVM unit tests
private fun makeSong(id: String) = Song(
    id = id,
    title = "Title $id",
    uri = mockk<Uri>(),
    mimeType = "audio/mpeg"
)

class SongUnitTest {

    // test1: create a song
    @Test
    fun createSong_storesCorrectInformation() {
        val uri = mockk<Uri>()

        val song = Song(
            id = "1",
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            uri = uri,
            mimeType = "audio/mpeg"
        )

        assertEquals("1", song.id)
        assertEquals("Test Song", song.title)
        assertEquals("Test Artist", song.artist)
        assertEquals("Test Album", song.album)
        assertSame(uri, song.uri)
        assertEquals("audio/mpeg", song.mimeType)
    }

    // test2: create a song without artist or album
    @Test
    fun createSong_defaultArtistAndAlbum() {
        val song = makeSong("2")

        assertEquals("Unknown Artist", song.artist)
        assertEquals("Unknown Album", song.album)
    }

    // test3: update a song's title, the title should change
    @Test
    fun updateTitle_titleIsUpdated() {
        val song = makeSong("3")

        song.title = "Updated Title"

        assertEquals("Updated Title", song.title)
    }

    // test4: create two songs with different IDs, the songs should not be equal
    @Test
    fun createSong_differentIdsNotEqual() {
        val uri = mockk<Uri>()

        val song1 = Song(
            id = "1",
            title = "Test Song",
            uri = uri,
            mimeType = "audio/mpeg"
        )

        val song2 = Song(
            id = "2",
            title = "Test Song",
            uri = uri,
            mimeType = "audio/mpeg"
        )

        assertNotEquals(song1, song2)
    }
}
