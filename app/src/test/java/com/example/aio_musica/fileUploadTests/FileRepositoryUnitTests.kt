package com.example.aio_musica.fileUploadTests

import android.net.Uri
import com.example.aio_musica.file_upload.FileInfo
import com.example.aio_musica.file_upload.FileReader
import com.example.aio_musica.file_upload.FileRepository
import com.example.aio_musica.file_upload.ImportedFile
import com.example.aio_musica.file_upload.ProgressUpdate
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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

/** Unit tests for [FileRepository]. */
@OptIn(ExperimentalCoroutinesApi::class)
class FileRepositoryUnitTests {

    /** Deterministic test data of the requested size. */
    private fun testBytes(size: Int): ByteArray = ByteArray(size) { (it % 251).toByte() }

    /** Runs [block] and returns the exception of type [T] it throws; fails the test otherwise. */
    private suspend inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return e
            throw e
        }
        throw AssertionError("Expected ${T::class.simpleName} to be thrown but nothing was thrown")
    }

    private val sampleFile = File("songs/track.mp3")


    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var fileReader: FileReader
    private lateinit var storageDir: File
    private lateinit var uri: Uri
    private lateinit var repository: FileRepository

    @Before
    fun setUp() {
        fileReader = mockk()
        uri = mockk()
        // Deliberately a folder that does not exist yet, so we can check it gets created.
        storageDir = File(tempFolder.root, "songs")
        repository = FileRepository(fileReader, storageDir, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun stubSource(
        data: ByteArray,
        name: String = "track.mp3",
        mimeType: String = "audio/mpeg",
        sizeBytes: Long = data.size.toLong()
    ) {
        coEvery { fileReader.readFileInfo(uri) } returns FileInfo(name, mimeType, sizeBytes)
        every { fileReader.openInputStream(uri) } returns ByteArrayInputStream(data)
    }

    /** Uploads a file called [name] and returns the name of the saved copy. */
    private suspend fun savedFileNameFor(name: String): String {
        stubSource(testBytes(16), name = name)
        val final = repository.uploadFile(uri).toList().last()
        return final.result!!.file.name
    }

    private fun savedFiles(): List<File> = storageDir.listFiles()?.toList() ?: emptyList()

    // ----- successful copy -----

    @Test
    fun uploadFile_copiesTheFileContentsExactly() = runTest {
        val data = testBytes(150_000) // Spans several 64 KiB chunks.
        stubSource(data)

        val final = repository.uploadFile(uri).toList().last()

        assertArrayEquals(data, final.result!!.file.readBytes())
    }

    @Test
    fun uploadFile_finalUpdateCarriesTheImportedFileDetails() = runTest {
        stubSource(testBytes(100), name = "my_song.mp3", mimeType = "audio/mpeg")

        val final = repository.uploadFile(uri).toList().last()

        val result = final.result
        assertNotNull(result)
        assertEquals("my_song.mp3", result!!.displayName)
        assertEquals("audio/mpeg", result.mimeType)
        assertEquals(storageDir, result.file.parentFile)
        assertTrue(result.file.exists())
    }

    @Test
    fun uploadFile_finalUpdateReportsBytesCopiedAsBothSentAndTotal() = runTest {
        stubSource(testBytes(1234))

        val final = repository.uploadFile(uri).toList().last()

        assertEquals(1234L, final.bytesSent)
        assertEquals(1234L, final.totalBytes)
    }

    @Test
    fun uploadFile_finalUpdateUsesActualBytesCopiedWhenReportedSizeIsWrong() = runTest {
        stubSource(testBytes(5), sizeBytes = 10L)

        val final = repository.uploadFile(uri).toList().last()

        assertEquals(5L, final.bytesSent)
        assertEquals(5L, final.totalBytes)
    }

    @Test
    fun uploadFile_createsTheStorageDirectoryWhenMissing() = runTest {
        stubSource(testBytes(10))
        assertFalse(storageDir.exists())

        repository.uploadFile(uri).toList()

        assertTrue(storageDir.isDirectory)
    }

    @Test
    fun uploadFile_worksWhenTheStorageDirectoryAlreadyExists() = runTest {
        assertTrue(storageDir.mkdirs())
        stubSource(testBytes(10))

        val final = repository.uploadFile(uri).toList().last()

        assertTrue(final.result!!.file.exists())
    }

    @Test
    fun uploadFile_emptyFile_producesEmptyCopyAndASingleFinalUpdate() = runTest {
        stubSource(ByteArray(0), sizeBytes = 0L)

        val updates = repository.uploadFile(uri).toList()

        assertEquals(1, updates.size)
        assertEquals(0L, updates.single().bytesSent)
        assertEquals(0L, updates.single().totalBytes)
        assertEquals(0L, updates.single().result!!.file.length())
    }

    @Test
    fun uploadFile_emptyMimeTypeIsPassedThrough() = runTest {
        stubSource(testBytes(10), mimeType = "")

        val final = repository.uploadFile(uri).toList().last()

        assertEquals("", final.result!!.mimeType)
    }

    @Test
    fun uploadFile_eachUploadGetsItsOwnDestinationFile() = runTest {
        stubSource(testBytes(10))
        val first = repository.uploadFile(uri).toList().last().result!!.file

        stubSource(testBytes(10))
        val second = repository.uploadFile(uri).toList().last().result!!.file

        assertNotEquals(first, second)
        assertEquals(2, savedFiles().size)
    }

    @Test
    fun uploadFile_isColdAndDoesNothingUntilCollected() = runTest {
        stubSource(testBytes(10))

        repository.uploadFile(uri) // Not collected.

        assertFalse(storageDir.exists())
        verify(exactly = 0) { fileReader.openInputStream(any()) }
    }

    // ----- progress reporting -----

    @Test
    fun uploadFile_largeFileReportsIntermediateProgressEvery256KiB() = runTest {
        val size = 600 * 1024 // 614,400 bytes
        stubSource(testBytes(size))

        val updates = repository.uploadFile(uri).toList()

        // Reports at 256 KiB and 512 KiB, then the final update.
        assertEquals(3, updates.size)
        assertEquals(256 * 1024L, updates[0].bytesSent)
        assertEquals(size.toLong(), updates[0].totalBytes)
        assertEquals(512 * 1024L, updates[1].bytesSent)
        assertEquals(size.toLong(), updates[1].totalBytes)
        assertNull(updates[0].result)
        assertNull(updates[1].result)
        assertEquals(size.toLong(), updates[2].bytesSent)
        assertNotNull(updates[2].result)
    }

    @Test
    fun uploadFile_smallFileOnlyEmitsTheFinalUpdate() = runTest {
        stubSource(testBytes(100 * 1024)) // Below the 256 KiB report interval.

        val updates = repository.uploadFile(uri).toList()

        assertEquals(1, updates.size)
        assertNotNull(updates.single().result)
    }

    @Test
    fun uploadFile_unknownSize_onlyEmitsTheFinalUpdate() = runTest {
        stubSource(testBytes(600 * 1024), sizeBytes = FileReader.UNKNOWN_SIZE)

        val updates = repository.uploadFile(uri).toList()

        assertEquals(1, updates.size)
        assertEquals(600 * 1024L, updates.single().bytesSent)
        assertNotNull(updates.single().result)
    }

    @Test
    fun uploadFile_zeroReportedSize_onlyEmitsTheFinalUpdate() = runTest {
        stubSource(testBytes(600 * 1024), sizeBytes = 0L)

        val updates = repository.uploadFile(uri).toList()

        assertEquals(1, updates.size)
        assertNotNull(updates.single().result)
    }

    // ----- extension handling (FileInfo.safeExtension) -----

    @Test
    fun uploadFile_keepsASimpleExtension() = runTest {
        assertTrue(savedFileNameFor("song.mp3").endsWith(".mp3"))
    }

    @Test
    fun uploadFile_noExtension_savedWithoutOne() = runTest {
        val saved = savedFileNameFor("song")
        assertTrue(saved.matches(Regex("[0-9a-f-]{36}")))
    }

    @Test
    fun uploadFile_trailingDot_savedWithoutExtension() = runTest {
        val saved = savedFileNameFor("song.")
        assertTrue(saved.matches(Regex("[0-9a-f-]{36}")))
    }

    @Test
    fun uploadFile_stripsNonAlphanumericCharactersFromTheExtension() = runTest {
        assertTrue(savedFileNameFor("song.m!p@3").endsWith(".mp3"))
    }

    @Test
    fun uploadFile_extensionMadeOnlyOfSymbols_savedWithoutExtension() = runTest {
        val saved = savedFileNameFor("song.!!!")
        assertTrue(saved.matches(Regex("[0-9a-f-]{36}")))
    }

    @Test
    fun uploadFile_extensionOfExactlyEightCharacters_isKept() = runTest {
        assertTrue(savedFileNameFor("song.12345678").endsWith(".12345678"))
    }

    @Test
    fun uploadFile_extensionLongerThanEightCharacters_isDropped() = runTest {
        val saved = savedFileNameFor("song.123456789")
        assertTrue(saved.matches(Regex("[0-9a-f-]{36}")))
    }

    @Test
    fun uploadFile_multipleDots_usesTheLastExtension() = runTest {
        assertTrue(savedFileNameFor("archive.tar.gz").endsWith(".gz"))
    }

    @Test
    fun uploadFile_pathTraversalInName_cannotEscapeTheStorageDirectory() = runTest {
        stubSource(testBytes(16), name = "../../evil.mp3")

        val file = repository.uploadFile(uri).toList().last().result!!.file

        assertEquals(storageDir, file.parentFile)
        assertTrue(file.name.endsWith(".mp3"))
    }

    // ----- failures -----

    @Test
    fun uploadFile_readFileInfoFails_exceptionPropagatesAndNothingIsSaved() = runTest {
        coEvery { fileReader.readFileInfo(uri) } throws IOException("provider died")

        val error = expectThrows<IOException> { repository.uploadFile(uri).toList() }

        assertEquals("provider died", error.message)
        assertTrue(savedFiles().isEmpty())
    }

    @Test
    fun uploadFile_cannotOpenInputStream_throwsFileNotFoundAndLeavesNoFile() = runTest {
        coEvery { fileReader.readFileInfo(uri) } returns FileInfo("track.mp3", "audio/mpeg", 10L)
        every { fileReader.openInputStream(uri) } throws FileNotFoundException("gone")

        val error = expectThrows<FileNotFoundException> { repository.uploadFile(uri).toList() }

        assertEquals("gone", error.message)
        assertTrue(savedFiles().isEmpty())
    }

    @Test
    fun uploadFile_readFailsHalfway_throwsAndDeletesThePartialFile() = runTest {
        coEvery { fileReader.readFileInfo(uri) } returns FileInfo("track.mp3", "audio/mpeg", 200_000L)
        every { fileReader.openInputStream(uri) } returns object : InputStream() {
            private var calls = 0
            override fun read(): Int = throw IOException("unexpected single-byte read")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (calls++ == 0) return len // First chunk succeeds (bytes written are zeros).
                throw IOException("disk unplugged")
            }
        }

        val error = expectThrows<IOException> { repository.uploadFile(uri).toList() }

        assertEquals("disk unplugged", error.message)
        assertTrue("Partial file should have been deleted", savedFiles().isEmpty())
    }

    @Test
    fun uploadFile_cancelledMidCopy_stopsAndDeletesThePartialFile() = runTest {
        coEvery { fileReader.readFileInfo(uri) } returns
                FileInfo("track.mp3", "audio/mpeg", 300_000L)

        lateinit var collector: Job
        var reads = 0
        every { fileReader.openInputStream(uri) } returns object : ByteArrayInputStream(testBytes(300_000)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                // Cancel the collector while the second chunk is being read, so the next
                // ensureActive() check in the copy loop stops the upload.
                if (++reads == 2) collector.cancel()
                return super.read(b, off, len)
            }
        }

        collector = launch { repository.uploadFile(uri).collect { } }
        collector.join()

        assertTrue(collector.isCancelled)
        assertTrue("Partial file should have been deleted", savedFiles().isEmpty())
    }

    @Test
    fun progressUpdate_equalityCopyAndToString() {
        val a = ProgressUpdate(10L, 100L)
        val b = ProgressUpdate(10L, 100L)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, a.copy(bytesSent = 11L))
        assertEquals(50L, a.copy(bytesSent = 50L).bytesSent)
        assertTrue(a.toString().contains("bytesSent=10"))
    }

    @Test
    fun progressUpdate_destructuring() {
        val imported = ImportedFile(sampleFile, "track.mp3", "audio/mpeg")
        val (sent, total, result) = ProgressUpdate(5L, 10L, imported)

        assertEquals(5L, sent)
        assertEquals(10L, total)
        assertEquals(imported, result)
    }

    @Test
    fun importedFile_equalityCopyAndToString() {
        val a = ImportedFile(sampleFile, "track.mp3", "audio/mpeg")
        val b = ImportedFile(sampleFile, "track.mp3", "audio/mpeg")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, a.copy(displayName = "other.mp3"))
        assertEquals("audio/wav", a.copy(mimeType = "audio/wav").mimeType)
        assertTrue(a.toString().contains("track.mp3"))
    }

    @Test
    fun importedFile_destructuring() {
        val (f, name, mime) = ImportedFile(sampleFile, "track.mp3", "audio/mpeg")

        assertEquals(sampleFile, f)
        assertEquals("track.mp3", name)
        assertEquals("audio/mpeg", mime)
    }
}
