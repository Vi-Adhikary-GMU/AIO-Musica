package com.example.aio_musica

import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.aio_musica.file_upload.FileRepository
import com.example.aio_musica.file_upload.ImportedFile
import com.example.aio_musica.file_upload.ProgressUpdate
import com.example.aio_musica.file_upload.UploadFileViewModel
import com.example.aio_musica.file_upload.UploadSate
import com.example.aio_musica.library.Library
import com.example.aio_musica.library.LibraryStorage
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** Unit tests for [UploadFileViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class UploadFileViewModelUnitTests {

    private val sampleFile = File("songs/track.mp3")


    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repository: FileRepository
    private lateinit var libraryStorage: LibraryStorage
    private lateinit var library: Library
    private lateinit var viewModel: UploadFileViewModel
    private lateinit var pickedUri: Uri
    private lateinit var savedFileUri: Uri

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        repository = mockk()
        libraryStorage = mockk(relaxed = true)
        library = Library()
        pickedUri = mockk()

        // The ViewModel turns the saved File into a Uri and Metadata reads its tags with a
        // MediaMetadataRetriever. Neither exists on the host JVM, so both are faked.
        savedFileUri = mockk { every { path } returns "/data/songs/saved.mp3" }
        mockkStatic(Uri::class)
        every { Uri.fromFile(any()) } returns savedFileUri
        stubTagReader()

        viewModel = UploadFileViewModel(repository, library, libraryStorage, testDispatcher)
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun stubTagReader(title: String? = null, artist: String? = null, album: String? = null) {
        mockkConstructor(MediaMetadataRetriever::class)
        every { anyConstructed<MediaMetadataRetriever>().setDataSource(any<String>()) } just Runs
        every {
            anyConstructed<MediaMetadataRetriever>()
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        } returns title
        every {
            anyConstructed<MediaMetadataRetriever>()
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        } returns artist
        every {
            anyConstructed<MediaMetadataRetriever>()
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
        } returns album
        every { anyConstructed<MediaMetadataRetriever>().release() } just Runs
    }

    private fun imported(
        displayName: String = "my_song.mp3",
        mimeType: String = "audio/mpeg"
    ) = ImportedFile(File("/data/songs/saved.mp3"), displayName, mimeType)

    private fun finalUpdate(file: ImportedFile = imported()) =
        ProgressUpdate(bytesSent = 1000L, totalBytes = 1000L, result = file)

    private fun uploadReturning(flow: Flow<ProgressUpdate>) {
        every { repository.uploadFile(pickedUri) } returns flow
    }

    // ----- initial state -----

    @Test
    fun initialState_isIdle() {
        val state = viewModel.state.value

        assertFalse(state.isUploading)
        assertFalse(state.isUploadComplete)
        assertEquals(0f, state.progress, 0.0001f)
        assertNull(state.errorMessage)
        assertNull(state.importedFile)
    }

    @Test
    fun constructor_defaultIoDispatcherIsAccepted() {
        // Exercises the default argument (Dispatchers.IO) for the ioDispatcher parameter.
        val vm = UploadFileViewModel(repository, library, libraryStorage)

        assertEquals(UploadSate(), vm.state.value)
    }

    // ----- success -----

    @Test
    fun uploadFile_success_marksTheUploadCompleteWithTheImportedFile() {
        val file = imported()
        uploadReturning(flowOf(ProgressUpdate(500L, 1000L), finalUpdate(file)))

        viewModel.uploadFile(pickedUri)

        val state = viewModel.state.value
        assertFalse(state.isUploading)
        assertTrue(state.isUploadComplete)
        assertEquals(1f, state.progress, 0.0001f)
        assertNull(state.errorMessage)
        assertEquals(file, state.importedFile)
    }

    @Test
    fun uploadFile_success_addsTheSongToTheLibraryAndSavesIt() {
        uploadReturning(flowOf(finalUpdate(imported(displayName = "my_song.mp3"))))

        viewModel.uploadFile(pickedUri)

        val songs = library.getSongs().values.toList()
        assertEquals(1, songs.size)
        val song = songs.single()
        assertEquals("my_song", song.title) // Extension removed from the file name.
        assertEquals("audio/mpeg", song.mimeType)
        assertSame(savedFileUri, song.uri)
        verify(exactly = 1) { libraryStorage.save(library) }
    }

    @Test
    fun uploadFile_success_createsTheSongUriFromTheSavedFile() {
        val file = imported()
        uploadReturning(flowOf(finalUpdate(file)))

        viewModel.uploadFile(pickedUri)

        verify(exactly = 1) { Uri.fromFile(file.file) }
    }

    @Test
    fun uploadFile_success_usesEmbeddedTagsWhenPresent() {
        stubTagReader(title = "Tag Title", artist = "Tag Artist", album = "Tag Album")
        uploadReturning(flowOf(finalUpdate()))

        viewModel.uploadFile(pickedUri)

        val song = library.getSongs().values.single()
        assertEquals("Tag Title", song.title)
        assertEquals("Tag Artist", song.artist)
        assertEquals("Tag Album", song.album)
    }

    @Test
    fun uploadFile_success_blankMimeTypeDefaultsToAudioMpeg() {
        uploadReturning(flowOf(finalUpdate(imported(mimeType = ""))))

        viewModel.uploadFile(pickedUri)

        assertEquals("audio/mpeg", library.getSongs().values.single().mimeType)
    }

    @Test
    fun uploadFile_success_whitespaceMimeTypeDefaultsToAudioMpeg() {
        uploadReturning(flowOf(finalUpdate(imported(mimeType = "   "))))

        viewModel.uploadFile(pickedUri)

        assertEquals("audio/mpeg", library.getSongs().values.single().mimeType)
    }

    @Test
    fun uploadFile_success_keepsAnExplicitMimeType() {
        uploadReturning(flowOf(finalUpdate(imported(mimeType = "audio/wav"))))

        viewModel.uploadFile(pickedUri)

        assertEquals("audio/wav", library.getSongs().values.single().mimeType)
    }

    @Test
    fun uploadFile_success_displayNameWithoutExtensionIsUsedAsIs() {
        uploadReturning(flowOf(finalUpdate(imported(displayName = "no_extension"))))

        viewModel.uploadFile(pickedUri)

        assertEquals("no_extension", library.getSongs().values.single().title)
    }

    @Test
    fun uploadFile_success_displayNameWithSeveralDotsOnlyDropsTheLastPart() {
        uploadReturning(flowOf(finalUpdate(imported(displayName = "my.cool.song.mp3"))))

        viewModel.uploadFile(pickedUri)

        assertEquals("my.cool.song", library.getSongs().values.single().title)
    }

    @Test
    fun uploadFile_success_withoutAResult_completesWithoutTouchingTheLibrary() {
        uploadReturning(flowOf(ProgressUpdate(500L, 1000L))) // Never emits a final result.

        viewModel.uploadFile(pickedUri)

        val state = viewModel.state.value
        assertTrue(state.isUploadComplete)
        assertFalse(state.isUploading)
        assertNull(state.importedFile)
        assertTrue(library.getSongs().isEmpty())
        verify(exactly = 0) { libraryStorage.save(any()) }
    }

    @Test
    fun uploadFile_emptyFlow_completesWithoutTouchingTheLibrary() {
        uploadReturning(flowOf())

        viewModel.uploadFile(pickedUri)

        assertTrue(viewModel.state.value.isUploadComplete)
        assertTrue(library.getSongs().isEmpty())
    }

    // ----- progress -----

    @Test
    fun uploadFile_whileRunning_stateShowsUploadingAndTracksProgress() {
        val channel = Channel<ProgressUpdate>(Channel.UNLIMITED)
        uploadReturning(channel.receiveAsFlow())

        viewModel.uploadFile(pickedUri)

        var state = viewModel.state.value
        assertTrue(state.isUploading)
        assertFalse(state.isUploadComplete)
        assertEquals(0f, state.progress, 0.0001f)
        assertNull(state.errorMessage)
        assertNull(state.importedFile)

        channel.trySend(ProgressUpdate(250L, 1000L))
        state = viewModel.state.value
        assertTrue(state.isUploading)
        assertEquals(0.25f, state.progress, 0.0001f)

        channel.trySend(ProgressUpdate(750L, 1000L))
        assertEquals(0.75f, viewModel.state.value.progress, 0.0001f)

        channel.trySend(finalUpdate())
        channel.close()
        state = viewModel.state.value
        assertFalse(state.isUploading)
        assertTrue(state.isUploadComplete)
        assertEquals(1f, state.progress, 0.0001f)
    }

    @Test
    fun uploadFile_updateWithZeroTotalBytes_keepsThePreviousProgress() {
        val channel = Channel<ProgressUpdate>(Channel.UNLIMITED)
        uploadReturning(channel.receiveAsFlow())

        viewModel.uploadFile(pickedUri)
        channel.trySend(ProgressUpdate(250L, 1000L))
        channel.trySend(ProgressUpdate(0L, 0L))

        assertEquals(0.25f, viewModel.state.value.progress, 0.0001f)
        channel.close()
    }

    @Test
    fun uploadFile_intermediateUpdatesKeepTheImportedFileUnset() {
        val channel = Channel<ProgressUpdate>(Channel.UNLIMITED)
        uploadReturning(channel.receiveAsFlow())

        viewModel.uploadFile(pickedUri)
        channel.trySend(ProgressUpdate(100L, 1000L)) // result == null

        assertNull(viewModel.state.value.importedFile)
        channel.close()
    }

    // ----- errors -----

    @Test
    fun uploadFile_fileNotFound_showsFileNotFoundMessage() {
        uploadReturning(flow { throw FileNotFoundException("missing") })

        viewModel.uploadFile(pickedUri)

        val state = viewModel.state.value
        assertEquals("File not found", state.errorMessage)
        assertFalse(state.isUploading)
        assertFalse(state.isUploadComplete)
    }

    @Test
    fun uploadFile_ioException_showsStorageFullMessage() {
        uploadReturning(flow { throw IOException("No space left on device") })

        viewModel.uploadFile(pickedUri)

        val state = viewModel.state.value
        assertEquals("Couldn't copy file. Is your storage full?", state.errorMessage)
        assertFalse(state.isUploading)
        assertFalse(state.isUploadComplete)
    }

    @Test
    fun uploadFile_unexpectedException_showsGenericMessage() {
        uploadReturning(flow { throw IllegalStateException("boom") })

        viewModel.uploadFile(pickedUri)

        val state = viewModel.state.value
        assertEquals("Something went wrong", state.errorMessage)
        assertFalse(state.isUploading)
        assertFalse(state.isUploadComplete)
    }

    @Test
    fun uploadFile_failureAfterSomeProgress_keepsProgressButStopsUploading() {
        uploadReturning(flow {
            emit(ProgressUpdate(400L, 1000L))
            throw IOException("disk full")
        })

        viewModel.uploadFile(pickedUri)

        val state = viewModel.state.value
        assertFalse(state.isUploading)
        assertEquals("Couldn't copy file. Is your storage full?", state.errorMessage)
        assertEquals(0.4f, state.progress, 0.0001f)
        assertTrue(library.getSongs().isEmpty())
    }

    @Test
    fun uploadFile_savingTheLibraryFails_reportsAnErrorAndDoesNotMarkComplete() {
        every { libraryStorage.save(any()) } throws IOException("can't write library.json")
        uploadReturning(flowOf(finalUpdate()))

        viewModel.uploadFile(pickedUri)

        val state = viewModel.state.value
        assertEquals("Couldn't copy file. Is your storage full?", state.errorMessage)
        assertFalse(state.isUploading)
        assertFalse(state.isUploadComplete)
    }

    // ----- cancellation -----

    @Test
    fun cancelUpload_whileRunning_setsCancelledState() {
        val channel = Channel<ProgressUpdate>(Channel.UNLIMITED)
        uploadReturning(channel.receiveAsFlow())

        viewModel.uploadFile(pickedUri)
        channel.trySend(ProgressUpdate(500L, 1000L))
        viewModel.cancelUpload()

        val state = viewModel.state.value
        assertFalse(state.isUploading)
        assertFalse(state.isUploadComplete)
        assertEquals("Upload cancelled", state.errorMessage)
        assertEquals(0f, state.progress, 0.0001f)
    }

    @Test
    fun cancelUpload_doesNotAddAnythingToTheLibrary() {
        val channel = Channel<ProgressUpdate>(Channel.UNLIMITED)
        uploadReturning(channel.receiveAsFlow())

        viewModel.uploadFile(pickedUri)
        viewModel.cancelUpload()

        assertTrue(library.getSongs().isEmpty())
        verify(exactly = 0) { libraryStorage.save(any()) }
    }

    @Test
    fun cancelUpload_withoutAnUpload_doesNothing() {
        viewModel.cancelUpload()

        assertEquals(UploadSate(), viewModel.state.value)
    }

    @Test
    fun cancelUpload_afterCompletion_leavesTheCompletedStateAlone() {
        uploadReturning(flowOf(finalUpdate()))
        viewModel.uploadFile(pickedUri)
        val completed = viewModel.state.value

        viewModel.cancelUpload()

        assertEquals(completed, viewModel.state.value)
    }

    // ----- starting uploads -----

    @Test
    fun uploadFile_whileAnotherUploadIsRunning_isIgnored() {
        val channel = Channel<ProgressUpdate>(Channel.UNLIMITED)
        uploadReturning(channel.receiveAsFlow())

        viewModel.uploadFile(pickedUri)
        viewModel.uploadFile(pickedUri)

        verify(exactly = 1) { repository.uploadFile(any()) }
        channel.close()
    }

    @Test
    fun uploadFile_afterAFailure_startsFreshAndClearsTheError() {
        every { repository.uploadFile(pickedUri) } returnsMany listOf(
            flow { throw IOException("disk full") },
            flowOf(finalUpdate())
        )

        viewModel.uploadFile(pickedUri)
        assertNotNull(viewModel.state.value.errorMessage)

        viewModel.uploadFile(pickedUri)

        val state = viewModel.state.value
        assertNull(state.errorMessage)
        assertTrue(state.isUploadComplete)
        assertFalse(state.isUploading)
        assertEquals(1, library.getSongs().size)
    }

    @Test
    fun uploadFile_afterCancelling_canStartAgain() {
        val channel = Channel<ProgressUpdate>(Channel.UNLIMITED)
        every { repository.uploadFile(pickedUri) } returnsMany listOf(
            channel.receiveAsFlow(),
            flowOf(finalUpdate())
        )

        viewModel.uploadFile(pickedUri)
        viewModel.cancelUpload()
        assertEquals("Upload cancelled", viewModel.state.value.errorMessage)

        viewModel.uploadFile(pickedUri)

        val state = viewModel.state.value
        assertNull(state.errorMessage)
        assertTrue(state.isUploadComplete)
        verify(exactly = 2) { repository.uploadFile(pickedUri) }
    }

    @Test
    fun uploadFile_afterACompletedUpload_resetsStateAndImportsAgain() {
        every { repository.uploadFile(pickedUri) } returnsMany listOf(
            flowOf(finalUpdate(imported(displayName = "first.mp3"))),
            flowOf(finalUpdate(imported(displayName = "second.mp3")))
        )

        viewModel.uploadFile(pickedUri)
        viewModel.uploadFile(pickedUri)

        assertEquals(2, library.getSongs().size)
        assertEquals("second.mp3", viewModel.state.value.importedFile?.displayName)
        verify(exactly = 2) { libraryStorage.save(library) }
    }

    @Test
    fun uploadFile_passesTheChosenUriToTheRepository() {
        uploadReturning(flowOf(finalUpdate()))

        viewModel.uploadFile(pickedUri)

        verify(exactly = 1) { repository.uploadFile(pickedUri) }
    }

    @Test
    fun uploadState_equalityCopyAndToString() {
        val a = UploadSate()
        val b = UploadSate()

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, a.copy(isUploading = true))
        assertNotEquals(a, a.copy(isUploadComplete = true))
        assertNotEquals(a, a.copy(progress = 0.5f))
        assertNotEquals(a, a.copy(errorMessage = "oops"))
        assertNotEquals(a, a.copy(importedFile = ImportedFile(sampleFile, "t.mp3", "audio/mpeg")))
        assertTrue(a.toString().contains("isUploading=false"))
    }

    @Test
    fun uploadState_destructuring() {
        val imported = ImportedFile(sampleFile, "track.mp3", "audio/mpeg")
        val (uploading, complete, progress, error, importedFile) =
            UploadSate(true, false, 0.5f, "oops", imported)

        assertTrue(uploading)
        assertFalse(complete)
        assertEquals(0.5f, progress, 0.0001f)
        assertEquals("oops", error)
        assertEquals(imported, importedFile)
    }
}
