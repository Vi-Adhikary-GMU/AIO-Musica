package com.example.aio_musica

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.aio_musica.file_upload.FileReader
import com.example.aio_musica.file_upload.FileRepository
import com.example.aio_musica.file_upload.UploadFileViewModel
import com.example.aio_musica.library.Library
import com.example.aio_musica.library.LibraryStorage
import com.example.aio_musica.library.Playlist
import com.example.aio_musica.library.Song
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * Integration tests: file upload -> library -> library storage (library.json).
 *
 * Everything in the app's own code is REAL here: FileReader, FileRepository, UploadFileViewModel,
 * Library, Playlist and LibraryStorage. Real files are copied into a temporary "app storage"
 * folder and a real library.json is written and read back.
 *
 * Only the Android framework is faked, because it doesn't exist on the host JVM:
 *  - Context / ContentResolver / Cursor (the "file picker" side)
 *  - Uri (mock objects whose toString() is the path)
 *  - MediaMetadataRetriever (the song's embedded tags)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FileUploadLibraryIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var filesDir: File
    private lateinit var songsDir: File
    private lateinit var libraryJson: File
    private lateinit var resolver: ContentResolver
    private lateinit var context: Context
    private lateinit var storage: LibraryStorage
    private lateinit var library: Library
    private lateinit var viewModel: UploadFileViewModel

    // The tags the fake MediaMetadataRetriever reports (null = the file has no such tag).
    private var tagTitle: String? = null
    private var tagArtist: String? = null
    private var tagAlbum: String? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        filesDir = tempFolder.newFolder("files")
        songsDir = File(filesDir, "songs")
        libraryJson = File(filesDir, "library.json")

        resolver = mockk()
        context = mockk()
        every { context.contentResolver } returns resolver
        every { context.filesDir } returns filesDir

        // Uri.fromFile (used by the ViewModel) and Uri.parse (used by LibraryStorage.load)
        mockkStatic(Uri::class)
        every { Uri.fromFile(any()) } answers { fakeUri(firstArg<File>().absolutePath) }
        every { Uri.parse(any()) } answers { fakeUri(firstArg<String>().removePrefix("file://")) }

        // Song tags
        mockkConstructor(MediaMetadataRetriever::class)
        every { anyConstructed<MediaMetadataRetriever>().setDataSource(any<String>()) } just Runs
        every {
            anyConstructed<MediaMetadataRetriever>().extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        } answers { tagTitle }
        every {
            anyConstructed<MediaMetadataRetriever>().extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        } answers { tagArtist }
        every {
            anyConstructed<MediaMetadataRetriever>().extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        } answers { tagAlbum }
        every { anyConstructed<MediaMetadataRetriever>().release() } just Runs

        storage = LibraryStorage(context)
        library = Library()
        viewModel = newViewModel(library)
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    // --------------------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------------------

    /** A Uri stand-in that remembers its path and prints as file://<path>. */
    private fun fakeUri(filePath: String): Uri = mockk {
        every { path } returns filePath
        every { this@mockk.toString() } returns "file://$filePath"
    }

    private fun newViewModel(forLibrary: Library) = UploadFileViewModel(
        repository = FileRepository(
            fileReader = FileReader(context, testDispatcher),
            storageDir = songsDir,
            ioDispatcher = testDispatcher
        ),
        library = forLibrary,
        libraryStorage = storage,
        ioDispatcher = testDispatcher
    )

    private fun bytes(size: Int) = ByteArray(size) { (it % 251).toByte() }

    /**
     * Switches Main to a dispatcher that QUEUES work, like the real Main thread does, and builds a
     * fresh ViewModel on it. Needed for cancel tests: with the default unconfined dispatcher the
     * whole upload runs inside uploadFile(), before the ViewModel has stored its upload job, so a
     * cancel from inside the copy would have nothing to cancel.
     * Call scheduler.advanceUntilIdle() after uploadFile() to actually run the upload.
     */
    private fun useQueuedMainDispatcher(): TestCoroutineScheduler {
        val scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        viewModel = newViewModel(library)
        return scheduler
    }

    /** Registers a file "on the phone" that the file picker could return. */
    private fun pickFile(
        name: String,
        data: ByteArray = bytes(2048),
        mimeType: String? = "audio/mpeg"
    ): Uri {
        val uri: Uri = mockk { every { lastPathSegment } returns name }
        val cursor: Cursor = mockk(relaxed = true) {
            every { moveToFirst() } returns true
            every { getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
            every { getColumnIndex(OpenableColumns.SIZE) } returns 1
            every { this@mockk.isNull(any<Int>()) } returns false
            every { getString(0) } returns name
            every { getLong(1) } returns data.size.toLong()
        }
        every { resolver.query(uri, any(), null, null, null) } returns cursor
        every { resolver.getType(uri) } returns mimeType
        every { resolver.openInputStream(uri) } answers { ByteArrayInputStream(data) }
        return uri
    }

    /** Reads library.json back from disk into a brand-new Library, like an app restart. */
    private fun reloadFromDisk(): Library = LibraryStorage(context).load()

    private fun storedCopies(): List<File> = songsDir.listFiles()?.toList() ?: emptyList()

    // --------------------------------------------------------------------------------------
    // Upload -> app storage
    // --------------------------------------------------------------------------------------

    @Test
    fun upload_copiesThePickedFileIntoAppStorage() {
        val data = bytes(150_000)

        viewModel.uploadFile(pickFile("track.mp3", data))

        val copy = viewModel.state.value.importedFile!!.file
        assertEquals(songsDir, copy.parentFile)
        assertTrue(copy.name.endsWith(".mp3"))
        assertArrayEquals(data, copy.readBytes())
        assertEquals(1, storedCopies().size)
    }

    @Test
    fun upload_largeFile_finishesWithFullProgress() {
        viewModel.uploadFile(pickFile("big.mp3", bytes(700 * 1024)))

        val state = viewModel.state.value
        assertTrue(state.isUploadComplete)
        assertFalse(state.isUploading)
        assertEquals(1f, state.progress, 0.0001f)
        assertNull(state.errorMessage)
    }

    // --------------------------------------------------------------------------------------
    // Upload -> Library (in memory)
    // --------------------------------------------------------------------------------------

    @Test
    fun upload_addsASongToTheLibraryWithTheFileTags() {
        tagTitle = "Background Music"
        tagArtist = "JonasBlakewood"
        tagAlbum = "n/a"

        viewModel.uploadFile(pickFile("track.mp3"))

        val song = library.getSongs().values.single()
        assertEquals("Background Music", song.title)
        assertEquals("JonasBlakewood", song.artist)
        assertEquals("n/a", song.album)
        assertEquals("audio/mpeg", song.mimeType)
    }

    @Test
    fun upload_songUriPointsAtTheCopyInAppStorage() {
        viewModel.uploadFile(pickFile("track.mp3"))

        val copy = viewModel.state.value.importedFile!!.file
        val song = library.getSongs().values.single()
        assertEquals("file://${copy.absolutePath}", song.uri.toString())
    }

    @Test
    fun upload_withoutTags_usesTheFileNameAsTheTitleAndDefaultsForTheRest() {
        viewModel.uploadFile(pickFile("my.cool.song.mp3"))

        val song = library.getSongs().values.single()
        assertEquals("my.cool.song", song.title)
        assertEquals("Unknown Artist", song.artist)
        assertEquals("Unknown Album", song.album)
    }

    @Test
    fun upload_unknownMimeType_isStoredAsAudioMpeg() {
        viewModel.uploadFile(pickFile("track.mp3", mimeType = null))

        assertEquals("audio/mpeg", library.getSongs().values.single().mimeType)
    }

    @Test
    fun upload_otherMimeType_isKept() {
        viewModel.uploadFile(pickFile("track.wav", mimeType = "audio/wav"))

        assertEquals("audio/wav", library.getSongs().values.single().mimeType)
    }

    @Test
    fun upload_sameFileTwice_createsTwoSongsAndTwoCopies() {
        val uri = pickFile("track.mp3")

        viewModel.uploadFile(uri)
        viewModel.uploadFile(uri)

        val songs = library.getSongs().values.toList()
        assertEquals(2, songs.size)
        assertNotEquals(songs[0].id, songs[1].id)
        assertNotEquals(songs[0].uri.toString(), songs[1].uri.toString())
        assertEquals(2, storedCopies().size)
    }

    // --------------------------------------------------------------------------------------
    // Upload -> Library storage (library.json)
    // --------------------------------------------------------------------------------------

    @Test
    fun upload_writesLibraryJsonToDisk() {
        assertFalse(libraryJson.exists())

        viewModel.uploadFile(pickFile("track.mp3"))

        assertTrue(libraryJson.exists())
        val songs = JSONObject(libraryJson.readText()).getJSONArray("songs")
        assertEquals(1, songs.length())
    }

    @Test
    fun upload_leavesNoTemporaryFileBehind() {
        viewModel.uploadFile(pickFile("track.mp3"))

        assertFalse(File(filesDir, "library.json.tmp").exists())
    }

    @Test
    fun upload_songSurvivesAnAppRestart() {
        tagTitle = "Background Music"
        tagArtist = "JonasBlakewood"
        tagAlbum = "n/a"
        viewModel.uploadFile(pickFile("track.mp3", mimeType = "audio/mpeg"))
        val saved = library.getSongs().values.single()

        val reloaded = reloadFromDisk().getSongs().values.single()

        assertEquals(saved.id, reloaded.id)
        assertEquals("Background Music", reloaded.title)
        assertEquals("JonasBlakewood", reloaded.artist)
        assertEquals("n/a", reloaded.album)
        assertEquals("audio/mpeg", reloaded.mimeType)
        assertEquals(saved.uri.toString(), reloaded.uri.toString())
    }

    @Test
    fun upload_fallbackTitleAndMimeDefaultsSurviveAnAppRestart() {
        viewModel.uploadFile(pickFile("Night Drive.mp3", mimeType = null))

        val reloaded = reloadFromDisk().getSongs().values.single()

        assertEquals("Night Drive", reloaded.title)
        assertEquals("audio/mpeg", reloaded.mimeType)
    }

    @Test
    fun upload_severalSongsAllSurviveAnAppRestart() {
        viewModel.uploadFile(pickFile("one.mp3"))
        viewModel.uploadFile(pickFile("two.mp3"))
        viewModel.uploadFile(pickFile("three.mp3"))

        val reloaded = reloadFromDisk()

        assertEquals(library.getSongs().keys, reloaded.getSongs().keys)
        assertEquals(
            setOf("one", "two", "three"),
            reloaded.getSongs().values.map { it.title }.toSet()
        )
    }

    @Test
    fun upload_intoAnExistingLibrary_keepsTheOldSongsAndAddsTheNewOne() {
        val existing = Song("old-id", "Old Song", "Old Artist", "Old Album", fakeUri("/data/old.mp3"), "audio/mpeg")
        library.addSong(existing)
        storage.save(library)

        // Simulate the next app launch: load what is on disk and upload into it.
        val loaded = reloadFromDisk()
        val vm = newViewModel(loaded)
        vm.uploadFile(pickFile("new.mp3"))

        val afterRestart = reloadFromDisk()
        assertEquals(2, afterRestart.getSongs().size)
        assertEquals("Old Song", afterRestart.getSongs()["old-id"]!!.title)
        assertTrue(afterRestart.getSongs().values.any { it.title == "new" })
    }

    // --------------------------------------------------------------------------------------
    // Upload -> Library -> Playlists -> storage
    // --------------------------------------------------------------------------------------

    @Test
    fun uploadedSong_canBeAddedToAPlaylistAndTheLinkSurvivesARestart() {
        viewModel.uploadFile(pickFile("track.mp3"))
        val song = library.getSongs().values.single()

        val playlist = Playlist("Driving", library)
        library.addPlaylist(playlist)
        playlist.addSong(song)
        storage.save(library)

        val reloaded = reloadFromDisk()
        assertEquals(listOf(song.id), reloaded.getPlaylists()["Driving"]!!.getSongIDs())
    }

    @Test
    fun uploadingAnotherSong_doesNotDisturbExistingPlaylists() {
        viewModel.uploadFile(pickFile("first.mp3"))
        val first = library.getSongs().values.single()
        val playlist = Playlist("Driving", library)
        library.addPlaylist(playlist)
        playlist.addSong(first)
        storage.save(library)

        viewModel.uploadFile(pickFile("second.mp3"))

        val reloaded = reloadFromDisk()
        assertEquals(2, reloaded.getSongs().size)
        assertEquals(listOf(first.id), reloaded.getPlaylists()["Driving"]!!.getSongIDs())
    }

    @Test
    fun removingAnUploadedSong_removesItFromPlaylistsOnDisk() {
        viewModel.uploadFile(pickFile("track.mp3"))
        val song = library.getSongs().values.single()
        val playlist = Playlist("Driving", library)
        library.addPlaylist(playlist)
        playlist.addSong(song)
        storage.save(library)

        library.removeSong(song)
        storage.save(library)

        val reloaded = reloadFromDisk()
        assertTrue(reloaded.getSongs().isEmpty())
        assertTrue(reloaded.getPlaylists()["Driving"]!!.getSongIDs().isEmpty())
    }

    // --------------------------------------------------------------------------------------
    // Failed and cancelled uploads must not touch the library
    // --------------------------------------------------------------------------------------

    @Test
    fun failedUpload_fileNotFound_leavesLibraryStorageAndFilesUntouched() {
        val uri = pickFile("track.mp3")
        every { resolver.openInputStream(uri) } returns null // The file vanished.

        viewModel.uploadFile(uri)

        val state = viewModel.state.value
        assertEquals("File not found", state.errorMessage)
        assertFalse(state.isUploadComplete)
        assertTrue(library.getSongs().isEmpty())
        assertFalse(libraryJson.exists())
        assertTrue(storedCopies().isEmpty())
    }

    @Test
    fun failedUpload_readErrorMidCopy_deletesPartialCopyAndLeavesLibraryUntouched() {
        val uri = pickFile("track.mp3", bytes(200_000))
        every { resolver.openInputStream(uri) } returns object : InputStream() {
            private var calls = 0
            override fun read(): Int = throw IOException("unexpected")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (calls++ == 0) return len
                throw IOException("disk unplugged")
            }
        }

        viewModel.uploadFile(uri)

        val state = viewModel.state.value
        assertEquals("Couldn't copy file. Is your storage full?", state.errorMessage)
        assertTrue(library.getSongs().isEmpty())
        assertFalse(libraryJson.exists())
        assertTrue("Partial copy should be deleted", storedCopies().isEmpty())
    }

    @Test
    fun failedUpload_afterASuccessfulOne_keepsTheEarlierSongOnDisk() {
        viewModel.uploadFile(pickFile("good.mp3"))
        val badUri = pickFile("bad.mp3")
        every { resolver.openInputStream(badUri) } throws FileNotFoundException("gone")

        viewModel.uploadFile(badUri)

        assertNotNull(viewModel.state.value.errorMessage)
        val reloaded = reloadFromDisk()
        assertEquals(1, reloaded.getSongs().size)
        assertEquals("good", reloaded.getSongs().values.single().title)
        assertEquals(1, storedCopies().size)
    }

    @Test
    fun retryAfterAFailedUpload_succeedsAndClearsTheError() {
        val uri = pickFile("track.mp3")
        every { resolver.openInputStream(uri) } returns null
        viewModel.uploadFile(uri)
        assertNotNull(viewModel.state.value.errorMessage)

        every { resolver.openInputStream(uri) } answers { ByteArrayInputStream(bytes(1024)) }
        viewModel.uploadFile(uri)

        assertNull(viewModel.state.value.errorMessage)
        assertTrue(viewModel.state.value.isUploadComplete)
        assertEquals(1, reloadFromDisk().getSongs().size)
    }

    @Test
    fun cancelledUpload_deletesPartialCopyAndLeavesLibraryUntouched() {
        val uri = pickFile("track.mp3", bytes(300_000))
        var reads = 0
        every { resolver.openInputStream(uri) } returns object : ByteArrayInputStream(bytes(300_000)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (++reads == 2) viewModel.cancelUpload() // User taps cancel mid-copy.
                return super.read(b, off, len)
            }
        }

        val scheduler = useQueuedMainDispatcher()
        viewModel.uploadFile(uri)
        scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Upload cancelled", state.errorMessage)
        assertFalse(state.isUploadComplete)
        assertTrue(library.getSongs().isEmpty())
        assertFalse(libraryJson.exists())
        assertTrue("Partial copy should be deleted", storedCopies().isEmpty())
    }

    @Test
    fun uploadAfterCancelling_worksNormally() {
        val uri = pickFile("track.mp3", bytes(300_000))
        var reads = 0
        every { resolver.openInputStream(uri) } returns object : ByteArrayInputStream(bytes(300_000)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (++reads == 2) viewModel.cancelUpload()
                return super.read(b, off, len)
            }
        }
        val scheduler = useQueuedMainDispatcher()
        viewModel.uploadFile(uri)
        scheduler.advanceUntilIdle()
        assertEquals("Upload cancelled", viewModel.state.value.errorMessage)

        every { resolver.openInputStream(uri) } answers { ByteArrayInputStream(bytes(300_000)) }
        viewModel.uploadFile(uri)
        scheduler.advanceUntilIdle()

        assertTrue(viewModel.state.value.isUploadComplete)
        assertNull(viewModel.state.value.errorMessage)
        assertEquals(1, reloadFromDisk().getSongs().size)
        assertEquals(1, storedCopies().size)
    }

    @Test
    fun libraryJsonCannotBeWritten_uploadIsNotReportedAsComplete() {
        // A directory where the temp file should go makes LibraryStorage.save() fail.
        assertTrue(File(filesDir, "library.json.tmp").mkdirs())

        viewModel.uploadFile(pickFile("track.mp3"))

        val state = viewModel.state.value
        assertNotNull(state.errorMessage)
        assertFalse(state.isUploadComplete)
        assertFalse(state.isUploading)
        assertFalse(libraryJson.exists())
    }
}
