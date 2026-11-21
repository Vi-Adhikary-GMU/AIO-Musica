package com.example.aio_musica

import android.net.Uri
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import io.mockk.mockk
import com.example.aio_musica.library.LibraryState
import com.example.aio_musica.library.Song
import com.example.aio_musica.library.PlaylistInfo

private fun makeSong(id: String) = Song(
    id = id,
    title = "Title $id",
    uri = mockk<Uri>(),
    mimeType = "audio/mpeg"
)

class LibraryStateTest {

    @Test
    fun check_DefaultState_isEmpty() {
        val state = LibraryState()

        assertTrue(state.songs.isEmpty())
        assertTrue(state.playlists.isEmpty())
    }

    @Test
    fun check_StateStorage() {
        val songs = listOf(
            makeSong("TestSong")
        )

        val playlists = listOf(
            PlaylistInfo(
                name = "TestPlaylist",
                songIds = listOf("Song1", "Song2")
            )
        )

        val state = LibraryState(
            songs = songs,
            playlists = playlists
        )

        assertEquals(songs, state.songs)
        assertEquals(playlists, state.playlists)
    }

    @Test
    fun check_StateCopy() {
        val original = LibraryState()
        val playlists = listOf(
            PlaylistInfo("TestPlaylist", listOf("Song1"))
        )
        val updated = original.copy(playlists = playlists)

        assertTrue(original.playlists.isEmpty())
        assertEquals(playlists, updated.playlists)
    }
}