package com.example.aio_musica.ui

import android.content.ContentResolver
import android.net.Uri
import com.example.aio_musica.library.Song
import com.example.aio_musica.mvsep.DownloadedStem
import com.example.aio_musica.mvsep.MvsepDownloader
import com.example.aio_musica.mvsep.MvsepRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException


@OptIn(ExperimentalCoroutinesApi::class)
class StemMixerViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var downloader: MvsepDownloader
    private lateinit var repository: MvsepRepository
    private lateinit var viewModel: StemMixerViewModel
    private lateinit var songFile: File
    private lateinit var outDir: File

    @Before
    fun setUp() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        downloader = mockk()
        repository = mockk()
        viewModel = StemMixerViewModel(downloader, testDispatcher)
        songFile = tmp.newFile("song.mp3").also { it.writeText("original") }
        outDir = File(tmp.root, "stems")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun stubDownload(vararg names: String) {
        val stems = names.map { name ->
            DownloadedStem(name, tmp.newFile("$name.mp3").also { it.writeText("audio-$name") })
        }
        coEvery { repository.separateSong(any()) } returns emptyList()
        coEvery { downloader.downloadStems(any(), any()) } returns stems
    }

    private fun separate(vararg names: String) {
        stubDownload(*names)
        viewModel.configure(repository)
        viewModel.separateSong(songFile, outDir)
    }

    private fun stemNamed(name: String) = viewModel.stems.value.first { it.name == name }
    private fun idOf(name: String) = stemNamed(name).id
    private val allStems = arrayOf("Vocals", "Instrum", "Guitar", "Piano", "Bass", "Drums", "Other")


    @Test
    fun initialState_isEmpty() {
        assertTrue(viewModel.stems.value.isEmpty())
        assertFalse(viewModel.isProcessing.value)
        assertEquals("", viewModel.status.value)
        assertNull(viewModel.error.value)
        assertNull(viewModel.currentSong.value)
        assertNull(viewModel.pendingExportId)
    }

    @Test
    fun setCurrentSong_storesAndClearsSong() {
        val song = mockk<Song>()
        viewModel.setCurrentSong(song)
        assertEquals(song, viewModel.currentSong.value)

        viewModel.setCurrentSong(null)
        assertNull(viewModel.currentSong.value)
    }

    @Test
    fun clearError_removesError() {
        viewModel.separateSong(songFile, outDir)
        assertNotNull(viewModel.error.value)

        viewModel.clearError()
        assertNull(viewModel.error.value)
    }

    @Test
    fun configure_clearsExistingError() {
        viewModel.separateSong(songFile, outDir)
        assertNotNull(viewModel.error.value)

        viewModel.configure(repository)
        assertNull(viewModel.error.value)
    }

    @Test
    fun separateSong_withoutRepository_setsError() {
        viewModel.separateSong(songFile, outDir)

        assertEquals("MVSEP repository is not configured.", viewModel.error.value)
        assertFalse(viewModel.isProcessing.value)
    }

    @Test
    fun separateSong_missingFile_setsError() {
        viewModel.configure(repository)
        viewModel.separateSong(File(tmp.root, "missing.mp3"), outDir)

        assertEquals("Selected audio file does not exist.", viewModel.error.value)
    }

    @Test
    fun separateSong_directoryAsInput_setsError() {
        viewModel.configure(repository)
        viewModel.separateSong(tmp.newFolder("aFolder"), outDir)

        assertEquals("Selected audio file does not exist.", viewModel.error.value)
    }

    @Test
    fun separateSong_destinationIsAFile_setsError() {
        viewModel.configure(repository)
        val notADirectory = tmp.newFile("not_a_dir")
        viewModel.separateSong(songFile, notADirectory)

        assertEquals("Destination path is not a directory.", viewModel.error.value)
    }

    @Test
    fun separateSong_success_buildsSortedStems() {
        separate("Other", "Drums", "Bass", "Piano", "Guitar", "Instrum", "Vocals")

        assertEquals(
            listOf("Vocals", "Instrum", "Guitar", "Piano", "Bass", "Drums", "Other"),
            viewModel.stems.value.map { it.name }
        )
        assertEquals("Separation complete!", viewModel.status.value)
        assertNull(viewModel.error.value)
        assertFalse(viewModel.isProcessing.value)
        // Every stem got its own id and its downloaded file.
        assertEquals(7, viewModel.stems.value.map { it.id }.toSet().size)
        assertTrue(viewModel.stems.value.all { it.audioFile?.exists() == true })
    }

    @Test
    fun separateSong_success_startsWithInstrumentalMutedOnly() {
        separate(*allStems)

        assertTrue(stemNamed("Instrum").muted)
        assertTrue(viewModel.stems.value.filter { it.name != "Instrum" }.none { it.muted })
        assertTrue(viewModel.stems.value.all { it.volume == 1f })
    }

    @Test
    fun separateSong_unknownStemName_isSortedLast() {
        separate("Mystery", "Other", "Vocals")

        assertEquals(listOf("Vocals", "Other", "Mystery"), viewModel.stems.value.map { it.name })
    }

    @Test
    fun separateSong_failureWithMessage_usesThatMessage() {
        coEvery { repository.separateSong(any()) } throws IOException("MVSEP is down")
        viewModel.configure(repository)

        viewModel.separateSong(songFile, outDir)

        assertEquals("MVSEP is down", viewModel.error.value)
        assertEquals("Separation failed", viewModel.status.value)
        assertTrue(viewModel.stems.value.isEmpty())
        assertFalse(viewModel.isProcessing.value)
    }

    @Test
    fun separateSong_failureWithoutMessage_usesDefaultMessage() {
        coEvery { repository.separateSong(any()) } throws RuntimeException()
        viewModel.configure(repository)

        viewModel.separateSong(songFile, outDir)

        assertEquals("An error occurred during audio separation.", viewModel.error.value)
        assertEquals("Separation failed", viewModel.status.value)
    }

    @Test
    fun separateSong_cancelled_setsCancelledStatus() {
        coEvery { repository.separateSong(any()) } throws CancellationException("stop")
        viewModel.configure(repository)

        viewModel.separateSong(songFile, outDir)

        assertEquals("Separation cancelled", viewModel.status.value)
        assertNull(viewModel.error.value)
        assertFalse(viewModel.isProcessing.value)
    }

    @Test
    fun separateSong_whileAlreadyRunning_isIgnored() {
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.separateSong(any()) } coAnswers {
            gate.await()
            emptyList()
        }
        coEvery { downloader.downloadStems(any(), any()) } returns
                listOf(DownloadedStem("Vocals", tmp.newFile("v.mp3")))
        viewModel.configure(repository)

        viewModel.separateSong(songFile, outDir)
        assertTrue(viewModel.isProcessing.value)
        assertEquals("Uploading song...", viewModel.status.value)

        viewModel.separateSong(songFile, outDir) // ignored: first one still running
        gate.complete(Unit)

        coVerify(exactly = 1) { repository.separateSong(any()) }
        assertEquals(1, viewModel.stems.value.size)
    }

    @Test
    fun separateSong_runsAgainAfterFinishing_andReplacesOldStems() {
        separate("Vocals", "Other")
        assertEquals(2, viewModel.stems.value.size)

        stubDownload("Bass")
        viewModel.separateSong(songFile, outDir)

        assertEquals(listOf("Bass"), viewModel.stems.value.map { it.name })
    }



    @Test
    fun setVolume_changesOnlyThatStem() {
        separate(*allStems)

        viewModel.setVolume(idOf("Bass"), 0.4f)

        assertEquals(0.4f, stemNamed("Bass").volume, 0.0001f)
        assertEquals(1f, stemNamed("Drums").volume, 0.0001f)
    }

    @Test
    fun setVolume_isClampedToZeroAndOne() {
        separate(*allStems)

        viewModel.setVolume(idOf("Bass"), 1.5f)
        assertEquals(1f, stemNamed("Bass").volume, 0.0001f)

        viewModel.setVolume(idOf("Bass"), -0.5f)
        assertEquals(0f, stemNamed("Bass").volume, 0.0001f)
    }

    @Test
    fun setVolume_unknownId_changesNothing() {
        separate(*allStems)
        val before = viewModel.stems.value

        viewModel.setVolume("no-such-id", 0.2f)

        assertEquals(before, viewModel.stems.value)
    }



    @Test
    fun toggleMute_vocals_mutesThenUnmutes_withoutTouchingOthers() {
        separate(*allStems)
        val vocals = idOf("Vocals")

        viewModel.toggleMute(vocals)
        assertTrue(stemNamed("Vocals").muted)
        assertTrue(stemNamed("Instrum").muted)
        assertFalse(stemNamed("Drums").muted)

        viewModel.toggleMute(vocals)
        assertFalse(stemNamed("Vocals").muted)
        assertTrue(stemNamed("Instrum").muted)
        assertFalse(stemNamed("Drums").muted)
    }

    @Test
    fun toggleMute_unmuteInstrumental_mutesBackingStems_andKeepsVocals() {
        separate(*allStems)

        viewModel.toggleMute(idOf("Instrum"))

        assertFalse(stemNamed("Instrum").muted)
        for (name in listOf("Guitar", "Piano", "Bass", "Drums", "Other")) {
            assertTrue("$name should be muted", stemNamed(name).muted)
        }
        assertFalse(stemNamed("Vocals").muted)
    }

    @Test
    fun toggleMute_muteInstrumental_unmutesBackingStems() {
        separate(*allStems)
        viewModel.toggleMute(idOf("Instrum"))

        viewModel.toggleMute(idOf("Instrum"))

        assertTrue(stemNamed("Instrum").muted)
        for (name in listOf("Guitar", "Piano", "Bass", "Drums", "Other")) {
            assertFalse("$name should be unmuted", stemNamed(name).muted)
        }
    }

    @Test
    fun toggleMute_unmuteBackingStem_mutesInstrumental() {
        separate(*allStems)
        viewModel.toggleMute(idOf("Instrum"))
        assertFalse(stemNamed("Instrum").muted)

        viewModel.toggleMute(idOf("Drums"))

        assertFalse(stemNamed("Drums").muted)
        assertTrue(stemNamed("Instrum").muted)
    }

    @Test
    fun toggleMute_muteBackingStem_leavesInstrumentalAlone() {
        separate(*allStems)

        viewModel.toggleMute(idOf("Drums"))

        assertTrue(stemNamed("Drums").muted)
        assertTrue(stemNamed("Instrum").muted)
        assertFalse(stemNamed("Bass").muted)
    }

    @Test
    fun toggleMute_unknownId_changesNothing() {
        separate(*allStems)
        val before = viewModel.stems.value

        viewModel.toggleMute("no-such-id")

        assertEquals(before, viewModel.stems.value)
    }


    @Test
    fun getStemFile_returnsTheDownloadedFile() {
        separate(*allStems)

        val file = viewModel.getStemFile(idOf("Vocals"))

        assertNotNull(file)
        assertEquals("Vocals.mp3", file!!.name)
    }

    @Test
    fun getStemFile_unknownId_returnsNull() {
        separate(*allStems)

        assertNull(viewModel.getStemFile("no-such-id"))
    }


    @Test
    fun exportStem_unknownStem_setsError() {
        val resolver = mockk<ContentResolver>()

        viewModel.exportStem("no-such-id", mockk<Uri>(), resolver)

        assertEquals("Stem audio file not found.", viewModel.error.value)
    }

    @Test
    fun exportStem_fileDeletedFromDisk_setsError() {
        separate(*allStems)
        viewModel.getStemFile(idOf("Vocals"))!!.delete()

        viewModel.exportStem(idOf("Vocals"), mockk<Uri>(), mockk<ContentResolver>())

        assertEquals("Stem audio file not found.", viewModel.error.value)
    }

    @Test
    fun exportStem_success_copiesFileContents() {
        separate(*allStems)
        val destination = mockk<Uri>()
        val out = ByteArrayOutputStream()
        val resolver = mockk<ContentResolver>()
        every { resolver.openOutputStream(destination) } returns out

        viewModel.exportStem(idOf("Vocals"), destination, resolver)

        assertEquals("Saved Vocals.mp3", viewModel.status.value)
        assertEquals("audio-Vocals", out.toString())
        assertNull(viewModel.error.value)
    }

    @Test
    fun exportStem_cannotOpenDestination_setsError() {
        separate(*allStems)
        val destination = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openOutputStream(destination) } returns null

        viewModel.exportStem(idOf("Vocals"), destination, resolver)

        assertEquals("Couldn't export: Could not open the chosen location.", viewModel.error.value)
    }

    @Test
    fun exportStem_resolverThrows_setsError() {
        separate(*allStems)
        val destination = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openOutputStream(destination) } throws SecurityException("denied")

        viewModel.exportStem(idOf("Vocals"), destination, resolver)

        assertEquals("Couldn't export: denied", viewModel.error.value)
    }

    @Test
    fun pendingExportId_canBeSetAndCleared() {
        viewModel.pendingExportId = "abc"
        assertEquals("abc", viewModel.pendingExportId)
        viewModel.pendingExportId = null
        assertNull(viewModel.pendingExportId)
    }


    @Test
    fun separateSong_destinationFolderAlreadyExists_stillWorks() {
        stubDownload("Vocals")
        viewModel.configure(repository)

        viewModel.separateSong(songFile, tmp.newFolder("existingStems"))

        assertNull(viewModel.error.value)
        assertEquals(listOf("Vocals"), viewModel.stems.value.map { it.name })
    }

    @Test
    fun defaultConstructor_createsViewModelWithEmptyState() {
        val vm = StemMixerViewModel()
        assertTrue(vm.stems.value.isEmpty())
    }

    @Test
    fun exportStem_cancelled_doesNotReportAnError() {
        separate(*allStems)
        val destination = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        every { resolver.openOutputStream(destination) } throws CancellationException("stop")

        viewModel.exportStem(idOf("Vocals"), destination, resolver)

        assertNull(viewModel.error.value)
        assertEquals("Exporting Vocals.mp3...", viewModel.status.value)
    }
}
