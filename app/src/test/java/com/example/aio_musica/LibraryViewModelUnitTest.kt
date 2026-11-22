package com.example.aio_musica

import android.app.Application
import android.net.Uri
import com.example.aio_musica.library.Library
import com.example.aio_musica.library.LibraryStorage
import com.example.aio_musica.library.LibraryViewModel
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

// All the tests for LibraryViewModel (playlist create / delete / add songs / remove song)

// Uri is an Android class, so we mock it for plain JVM unit tests
private fun makeSong(id: String, title: String = "Title $id"): Song {
    val uri = mockk<Uri>()
    every { uri.toString() } returns "file:///songs/$id.mp3"
    return Song(
        id = id,
        title = title,
        uri = uri,
        mimeType = "audio/mpeg"
    )
}

// LibraryStorage.load() calls Uri.parse(), which does not work in a JVM test,
// so Uri.parse is replaced with a mock Uri that remembers the text it was given
private fun mockUriParse() {
    mockkStatic(Uri::class)
    every { Uri.parse(any()) } answers {
        val uri = mockk<Uri>()
        every { uri.toString() } returns firstArg<String>()
        uri
    }
}

// A fake Application whose filesDir is the given temporary folder.
// The ViewModel asks it for error message text, so getString returns a simple message.
private fun makeApplication(dir: File): Application {
    val app = mockk<Application>()
    every { app.applicationContext } returns app
    every { app.filesDir } returns dir
    every { app.getString(any<Int>(), *anyVararg()) } returns "error message"
    return app
}

// Saves a library with the given song ids (and no playlists) before the ViewModel is created
private fun saveSongs(app: Application, vararg ids: String) {
    val library = Library()
    ids.forEach { library.addSong(makeSong(it)) }
    LibraryStorage(app).save(library)
}

// Saves a library with the given songs and one empty playlist named "Rock"
private fun saveSongsAndPlaylist(app: Application, vararg ids: String) {
    val library = Library()
    ids.forEach { library.addSong(makeSong(it)) }
    library.addPlaylist(Playlist("Rock", library))
    LibraryStorage(app).save(library)
}

class ViewModelInitialState {
    private lateinit var dir: File
    private lateinit var app: Application

    // Runs before every test so each test gets its own empty temporary folder
    @Before
    fun setUp() {
        mockUriParse()
        dir = Files.createTempDirectory("viewmodel-test").toFile()
        app = makeApplication(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
        unmockkAll()
    }

    // test1: a new ViewModel with nothing saved has no songs and no playlists
    @Test
    fun init_noSavedLibrary_stateIsEmpty() {
        val viewModel = LibraryViewModel(app)

        assertEquals(0, viewModel.state.value.songs.size)
        assertEquals(0, viewModel.state.value.playlists.size)
    }

    // test2: the songs in the saved library show up in state.songs
    @Test
    fun state_savedSongs_areExposed() {
        saveSongs(app, "1", "2")

        val viewModel = LibraryViewModel(app)

        assertEquals(setOf("1", "2"), viewModel.state.value.songs.map { it.id }.toSet())
    }

    // test3: the playlists in the saved library show up in state.playlists
    @Test
    fun state_savedPlaylists_areExposed() {
        saveSongsAndPlaylist(app, "1")

        val viewModel = LibraryViewModel(app)

        assertEquals(1, viewModel.state.value.playlists.size)
        assertEquals("Rock", viewModel.state.value.playlists[0].name)
    }

    // test4: songs in the state are sorted alphabetically by title (ignoring upper/lower case)
    @Test
    fun state_songs_areSortedByTitle() {
        val library = Library()
        library.addSong(makeSong("1", title = "banana"))
        library.addSong(makeSong("2", title = "Cherry"))
        library.addSong(makeSong("3", title = "Apple"))
        LibraryStorage(app).save(library)

        val viewModel = LibraryViewModel(app)

        assertEquals(listOf("Apple", "banana", "Cherry"), viewModel.state.value.songs.map { it.title })
    }

    // test5: reload() picks up what was saved to the file after the ViewModel was created
    @Test
    fun reload_afterSavingToStorage_stateHasNewData() {
        val viewModel = LibraryViewModel(app)
        assertEquals(0, viewModel.state.value.songs.size)

        saveSongsAndPlaylist(app, "1", "2")
        viewModel.reload()

        assertEquals(2, viewModel.state.value.songs.size)
        assertEquals(1, viewModel.state.value.playlists.size)
    }
}

class CreatePlaylistViewModel {
    private lateinit var dir: File
    private lateinit var app: Application
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        mockUriParse()
        dir = Files.createTempDirectory("viewmodel-test").toFile()
        app = makeApplication(dir)
        viewModel = LibraryViewModel(app)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
        unmockkAll()
    }

    // test6: creating a playlist with a normal name returns null and the playlist is in the state
    @Test
    fun createPlaylist_validName_appearsInState() {
        val error = viewModel.createPlaylist("Rock")

        assertNull(error)
        assertEquals(listOf("Rock"), viewModel.state.value.playlists.map { it.name })
    }

    // test7: spaces around the name are removed
    @Test
    fun createPlaylist_nameWithSpaces_isTrimmed() {
        viewModel.createPlaylist("   Rock   ")

        assertEquals("Rock", viewModel.state.value.playlists[0].name)
    }

    // test8: an empty name returns an error message and creates nothing
    @Test
    fun createPlaylist_emptyName_returnsError() {
        val error = viewModel.createPlaylist("")

        assertNotNull(error)
        assertEquals(0, viewModel.state.value.playlists.size)
    }

    // test9: a name with only spaces returns an error message and creates nothing
    @Test
    fun createPlaylist_onlySpaces_returnsError() {
        val error = viewModel.createPlaylist("     ")

        assertNotNull(error)
        assertEquals(0, viewModel.state.value.playlists.size)
    }

    // test10: a name that already exists returns an error message and does not add a second playlist
    @Test
    fun createPlaylist_duplicateName_returnsError() {
        viewModel.createPlaylist("Rock")

        val error = viewModel.createPlaylist("Rock")

        assertNotNull(error)
        assertEquals(1, viewModel.state.value.playlists.size)
    }

    // test11: a created playlist is saved to library.json
    @Test
    fun createPlaylist_validName_isSavedToStorage() {
        viewModel.createPlaylist("Rock")

        val saved = LibraryStorage(app).load()

        assertTrue(saved.getPlaylists().containsKey("Rock"))
    }
}

class DeletePlaylistViewModel {
    private lateinit var dir: File
    private lateinit var app: Application
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        mockUriParse()
        dir = Files.createTempDirectory("viewmodel-test").toFile()
        app = makeApplication(dir)
        saveSongsAndPlaylist(app, "1", "2")
        viewModel = LibraryViewModel(app)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
        unmockkAll()
    }

    // test12: deleting an existing playlist returns null and it is gone from the state
    @Test
    fun deletePlaylist_existingPlaylist_disappearsFromState() {
        val error = viewModel.deletePlaylist("Rock")

        assertNull(error)
        assertEquals(0, viewModel.state.value.playlists.size)
    }

    // test13: deleting a playlist that does not exist returns an error message
    @Test
    fun deletePlaylist_unknownPlaylist_returnsError() {
        val error = viewModel.deletePlaylist("Jazz")

        assertNotNull(error)
        assertEquals(1, viewModel.state.value.playlists.size)
    }

    // test14: deleting a playlist does not delete its songs from the library
    @Test
    fun deletePlaylist_songsStayInLibrary() {
        viewModel.addSongs("Rock", listOf("1", "2"))

        viewModel.deletePlaylist("Rock")

        assertEquals(2, viewModel.state.value.songs.size)
    }

    // test15: a deleted playlist is also gone from library.json
    @Test
    fun deletePlaylist_isRemovedFromStorage() {
        viewModel.deletePlaylist("Rock")

        val saved = LibraryStorage(app).load()

        assertFalse(saved.getPlaylists().containsKey("Rock"))
        assertEquals(2, saved.getSongs().size)
    }
}

class AddSongsViewModel {
    private lateinit var dir: File
    private lateinit var app: Application
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        mockUriParse()
        dir = Files.createTempDirectory("viewmodel-test").toFile()
        app = makeApplication(dir)
        saveSongsAndPlaylist(app, "1", "2", "3")
        viewModel = LibraryViewModel(app)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
        unmockkAll()
    }

    // helper: the song ids of the playlist named "Rock" in the current state
    private fun rockSongIds(): List<String> =
        viewModel.state.value.playlists.first { it.name == "Rock" }.songIds

    // test16: add one existing song, its id is in the playlist
    @Test
    fun addSongs_oneSong_idIsInPlaylist() {
        val error = viewModel.addSongs("Rock", listOf("1"))

        assertNull(error)
        assertEquals(listOf("1"), rockSongIds())
    }

    // test17: add several existing songs, all ids are in the playlist
    @Test
    fun addSongs_multipleSongs_allIdsAreInPlaylist() {
        viewModel.addSongs("Rock", listOf("1", "2", "3"))

        assertEquals(listOf("1", "2", "3"), rockSongIds())
    }

    // test18: adding the same song again does not duplicate it
    @Test
    fun addSongs_sameSongTwice_isNotDuplicated() {
        viewModel.addSongs("Rock", listOf("1"))

        val error = viewModel.addSongs("Rock", listOf("1", "1"))

        assertNull(error)
        assertEquals(listOf("1"), rockSongIds())
    }

    // test19: a song id that is not in the library is skipped, the others are still added
    @Test
    fun addSongs_unknownSongId_isSkipped() {
        val error = viewModel.addSongs("Rock", listOf("nope", "2"))

        assertNull(error)
        assertEquals(listOf("2"), rockSongIds())
    }

    // test20: adding songs to a playlist that does not exist returns an error message
    @Test
    fun addSongs_unknownPlaylist_returnsError() {
        val error = viewModel.addSongs("Jazz", listOf("1"))

        assertNotNull(error)
    }

    // test21: after adding songs, the published state has them
    @Test
    fun addSongs_updatesPublishedState() {
        assertEquals(0, rockSongIds().size)

        viewModel.addSongs("Rock", listOf("1", "2"))

        assertEquals(2, rockSongIds().size)
    }

    // test22: added songs are saved to library.json
    @Test
    fun addSongs_isSavedToStorage() {
        viewModel.addSongs("Rock", listOf("1", "2"))

        val saved = LibraryStorage(app).load()

        assertEquals(listOf("1", "2"), saved.getPlaylists().getValue("Rock").getSongIDs())
    }
}

class RemoveSongViewModel {
    private lateinit var dir: File
    private lateinit var app: Application
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        mockUriParse()
        dir = Files.createTempDirectory("viewmodel-test").toFile()
        app = makeApplication(dir)
        saveSongsAndPlaylist(app, "1", "2", "3")
        viewModel = LibraryViewModel(app)
        viewModel.addSongs("Rock", listOf("1", "2"))
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
        unmockkAll()
    }

    private fun rockSongIds(): List<String> =
        viewModel.state.value.playlists.first { it.name == "Rock" }.songIds

    // test23: removing a song takes its id out of the playlist
    @Test
    fun removeSong_songInPlaylist_idIsGone() {
        val error = viewModel.removeSong("Rock", "1")

        assertNull(error)
        assertEquals(listOf("2"), rockSongIds())
    }

    // test24: removing a song from a playlist keeps it in the library
    @Test
    fun removeSong_songStaysInLibrary() {
        viewModel.removeSong("Rock", "1")

        assertEquals(3, viewModel.state.value.songs.size)
        assertTrue(viewModel.state.value.songs.any { it.id == "1" })
    }

    // test25: removing from a playlist that does not exist returns an error message
    @Test
    fun removeSong_unknownPlaylist_returnsError() {
        val error = viewModel.removeSong("Jazz", "1")

        assertNotNull(error)
    }

    // test26: a song id that is not in the library returns null and changes nothing
    @Test
    fun removeSong_unknownSongId_doesNotCrash() {
        val error = viewModel.removeSong("Rock", "nope")

        assertNull(error)
        assertEquals(listOf("1", "2"), rockSongIds())
    }

    // test27: a song that is in the library but not in the playlist returns null and changes nothing
    @Test
    fun removeSong_songNotInPlaylist_doesNotCrash() {
        val error = viewModel.removeSong("Rock", "3")

        assertNull(error)
        assertEquals(listOf("1", "2"), rockSongIds())
    }

    // test28: after removing a song, the published state is updated
    @Test
    fun removeSong_updatesPublishedState() {
        assertEquals(2, rockSongIds().size)

        viewModel.removeSong("Rock", "2")

        assertEquals(1, rockSongIds().size)
    }
}

class ViewModelPersistence {
    private lateinit var dir: File
    private lateinit var app: Application

    @Before
    fun setUp() {
        mockUriParse()
        dir = Files.createTempDirectory("viewmodel-test").toFile()
        app = makeApplication(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
        unmockkAll()
    }

    // test29: a playlist created by one ViewModel is still there in a new ViewModel (like reopening the app)
    @Test
    fun newViewModel_afterCreatingPlaylist_playlistIsStillThere() {
        LibraryViewModel(app).createPlaylist("Rock")

        val reopened = LibraryViewModel(app)

        assertEquals(listOf("Rock"), reopened.state.value.playlists.map { it.name })
    }

    // test30: a song added to a playlist is still in it after creating a new ViewModel
    @Test
    fun newViewModel_afterAddingSong_songIsStillInPlaylist() {
        saveSongsAndPlaylist(app, "1", "2")
        LibraryViewModel(app).addSongs("Rock", listOf("2"))

        val reopened = LibraryViewModel(app)

        assertEquals(listOf("2"), reopened.state.value.playlists[0].songIds)
    }

    // test31: a deleted playlist does not come back in a new ViewModel
    @Test
    fun newViewModel_afterDeletingPlaylist_playlistStaysDeleted() {
        saveSongsAndPlaylist(app, "1")
        LibraryViewModel(app).deletePlaylist("Rock")

        val reopened = LibraryViewModel(app)

        assertEquals(0, reopened.state.value.playlists.size)
    }
}
