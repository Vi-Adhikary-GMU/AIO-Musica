package com.example.aio_musica

import android.content.Context
import android.net.Uri
import com.example.aio_musica.library.Library
import com.example.aio_musica.library.LibraryStorage
import com.example.aio_musica.library.Playlist
import com.example.aio_musica.library.Song
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.sql.Timestamp

// All the tests for saving and loading the library with LibraryStorage

// Uri is an Android class, so we mock it for plain JVM unit tests.
// LibraryStorage saves song.uri.toString(), so the mock returns the text we give it.
private fun makeUri(text: String): Uri {
    val uri = mockk<Uri>()
    every { uri.toString() } returns text
    return uri
}

private fun makeSong(
    id: String,
    title: String = "Title $id",
    artist: String = "Artist $id",
    album: String = "Album $id",
    mimeType: String = "audio/mpeg"
) = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    uri = makeUri("file:///songs/$id.mp3"),
    mimeType = mimeType
)

// LibraryStorage.load() calls Uri.parse()
// Uri.parse is replaced with a mock Uri that remembers the text it was given
private fun mockUriParse() {
    mockkStatic(Uri::class)
    every { Uri.parse(any()) } answers { makeUri(firstArg()) }
}

// A fake Context whose filesDir is the given temporary folder
private fun makeContext(dir: File): Context {
    val context = mockk<Context>()
    every { context.filesDir } returns dir
    return context
}

class SongStorage {
    private lateinit var dir: File
    private lateinit var storage: LibraryStorage
    private lateinit var library: Library

    // Runs before every test so each test gets its own empty temporary folder
    @Before
    fun setUp() {
        mockUriParse()
        dir = Files.createTempDirectory("library-test").toFile()
        storage = LibraryStorage(makeContext(dir))
        library = Library()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
        unmockkAll()
    }

    // test1: save a library with one song and load it back, the song should exist
    @Test
    fun saveLoad_oneSong_songExists() {
        library.addSong(makeSong("1"))

        storage.save(library)
        val loaded = storage.load()

        assertEquals(1, loaded.getSongs().size)
        assertTrue(loaded.containsSong("1"))
    }

    // test2: save three songs, all three should be loaded
    @Test
    fun saveLoad_multipleSongs_allSongsLoaded() {
        library.addSong(makeSong("1"))
        library.addSong(makeSong("2"))
        library.addSong(makeSong("3"))

        storage.save(library)
        val loaded = storage.load()

        assertEquals(3, loaded.getSongs().size)
    }

    // test3: song ids should be the same after loading
    @Test
    fun saveLoad_songIds_arePreserved() {
        library.addSong(makeSong("abc"))
        library.addSong(makeSong("xyz"))

        storage.save(library)
        val loaded = storage.load()

        assertEquals(setOf("abc", "xyz"), loaded.getSongs().keys)
    }

    // test4: song title should be the same after loading
    @Test
    fun saveLoad_songTitle_isPreserved() {
        library.addSong(makeSong("1", title = "Bohemian Rhapsody"))

        storage.save(library)
        val loaded = storage.load()

        assertEquals("Bohemian Rhapsody", loaded.getSongs()["1"]?.title)
    }

    // test5: song artist and album should be the same after loading
    @Test
    fun saveLoad_artistAndAlbum_arePreserved() {
        library.addSong(makeSong("1", artist = "Queen", album = "A Night at the Opera"))

        storage.save(library)
        val loaded = storage.load()

        assertEquals("Queen", loaded.getSongs()["1"]?.artist)
        assertEquals("A Night at the Opera", loaded.getSongs()["1"]?.album)
    }

    // test6: song mime type should be the same after loading
    @Test
    fun saveLoad_mimeType_isPreserved() {
        library.addSong(makeSong("1", mimeType = "audio/flac"))

        storage.save(library)
        val loaded = storage.load()

        assertEquals("audio/flac", loaded.getSongs()["1"]?.mimeType)
    }

    // test7: song uri should be the same after loading (compared as text)
    @Test
    fun saveLoad_songUri_isPreserved() {
        library.addSong(makeSong("1"))

        storage.save(library)
        val loaded = storage.load()

        assertEquals("file:///songs/1.mp3", loaded.getSongs()["1"]?.uri.toString())
    }
}

class PlaylistStorage {
    private lateinit var dir: File
    private lateinit var storage: LibraryStorage
    private lateinit var library: Library

    @Before
    fun setUp() {
        mockUriParse()
        dir = Files.createTempDirectory("library-test").toFile()
        storage = LibraryStorage(makeContext(dir))
        library = Library()
        library.addSong(makeSong("1"))
        library.addSong(makeSong("2"))
        library.addSong(makeSong("3"))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
        unmockkAll()
    }

    // test8: save a library with one playlist and load it back, the playlist should exist
    @Test
    fun saveLoad_onePlaylist_playlistExists() {
        library.addPlaylist(Playlist("Road Trip", library))

        storage.save(library)
        val loaded = storage.load()

        assertEquals(1, loaded.getPlaylists().size)
    }

    // test9: playlist name should be the same after loading
    @Test
    fun saveLoad_playlistName_isPreserved() {
        library.addPlaylist(Playlist("Road Trip", library))

        storage.save(library)
        val loaded = storage.load()

        assertTrue(loaded.getPlaylists().containsKey("Road Trip"))
        assertEquals("Road Trip", loaded.getPlaylists()["Road Trip"]?.getName())
    }

    // test10: ids of the songs in the playlist should be the same after loading
    @Test
    fun saveLoad_playlistSongIds_arePreserved() {
        val playlist = Playlist("Road Trip", library)
        library.addPlaylist(playlist)
        playlist.addSong(library.getSongs().getValue("1"))
        playlist.addSong(library.getSongs().getValue("2"))

        storage.save(library)
        val loaded = storage.load()

        assertEquals(listOf("1", "2"), loaded.getPlaylists().getValue("Road Trip").getSongIDs())
    }

    // test11: the time the playlist was created should be the same after loading
    @Test
    fun saveLoad_playlistDateCreated_isPreserved() {
        library.addPlaylist(Playlist("Road Trip", library, Timestamp(1_700_000_000_000L)))

        storage.save(library)
        val loaded = storage.load()

        assertEquals(1_700_000_000_000L, loaded.getPlaylists().getValue("Road Trip").getDateCreated().time)
    }

    // test12: save three playlists, all three should be loaded
    @Test
    fun saveLoad_multiplePlaylists_allPlaylistsLoaded() {
        library.addPlaylist(Playlist("Rock", library))
        library.addPlaylist(Playlist("Jazz", library))
        library.addPlaylist(Playlist("Study", library))

        storage.save(library)
        val loaded = storage.load()

        assertEquals(setOf("Rock", "Jazz", "Study"), loaded.getPlaylists().keys)
    }

    // test13: a playlist with several songs keeps the order the songs were added in
    @Test
    fun saveLoad_playlistWithSeveralSongs_keepsSongOrder() {
        val playlist = Playlist("Road Trip", library)
        library.addPlaylist(playlist)
        playlist.addSong(library.getSongs().getValue("3"))
        playlist.addSong(library.getSongs().getValue("1"))
        playlist.addSong(library.getSongs().getValue("2"))

        storage.save(library)
        val loaded = storage.load()

        assertEquals(listOf("3", "1", "2"), loaded.getPlaylists().getValue("Road Trip").getSongIDs())
    }
}

class EmptyLibraryStorage {
    private lateinit var dir: File
    private lateinit var storage: LibraryStorage

    @Before
    fun setUp() {
        mockUriParse()
        dir = Files.createTempDirectory("library-test").toFile()
        storage = LibraryStorage(makeContext(dir))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
        unmockkAll()
    }

    // test14: loading when library.json does not exist returns an empty library
    @Test
    fun load_noFile_returnsEmptyLibrary() {
        val loaded = storage.load()

        assertEquals(0, loaded.getSongs().size)
        assertEquals(0, loaded.getPlaylists().size)
    }

    // test15: save an empty library and load it back, no songs and no playlists
    @Test
    fun saveLoad_emptyLibrary_stillEmpty() {
        storage.save(Library())
        val loaded = storage.load()

        assertEquals(0, loaded.getSongs().size)
        assertEquals(0, loaded.getPlaylists().size)
    }
}

class StorageFile {
    private lateinit var dir: File
    private lateinit var storage: LibraryStorage
    private lateinit var library: Library

    @Before
    fun setUp() {
        mockUriParse()
        dir = Files.createTempDirectory("library-test").toFile()
        storage = LibraryStorage(makeContext(dir))
        library = Library()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
        unmockkAll()
    }

    // test16: saving creates library.json in the files directory
    @Test
    fun save_createsLibraryJsonFile() {
        assertFalse(File(dir, "library.json").exists())

        storage.save(library)

        assertTrue(File(dir, "library.json").exists())
    }

    // test17: save, change the library, save again, load should give the changed library
    @Test
    fun save_afterModifyingLibrary_loadReturnsUpdatedLibrary() {
        val song1 = makeSong("1")
        library.addSong(song1)
        storage.save(library)

        library.addSong(makeSong("2"))
        library.removeSong(song1)
        library.addPlaylist(Playlist("New Playlist", library))
        storage.save(library)
        val loaded = storage.load()

        assertEquals(setOf("2"), loaded.getSongs().keys)
        assertTrue(loaded.getPlaylists().containsKey("New Playlist"))
    }
}
