package com.example.aio_musica

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.example.aio_musica.file_upload.FileInfo
import com.example.aio_musica.file_upload.FileReader
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException

/** Unit tests for [FileReader]. Android classes are mocked with MockK. */
@OptIn(ExperimentalCoroutinesApi::class)
class FileReaderUnitTests {

    private lateinit var context: Context
    private lateinit var resolver: ContentResolver
    private lateinit var reader: FileReader

    @Before
    fun setUp() {
        context = mockk()
        resolver = mockk()
        every { context.contentResolver } returns resolver
        reader = FileReader(context, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun mockUri(
        lastSegment: String? = "fallback_segment.mp3",
        asString: String = "content://media/external/audio/42"
    ): Uri = mockk {
        every { lastPathSegment } returns lastSegment
        every { this@mockk.toString() } returns asString
    }

    /** Builds a cursor whose columns behave as described by the parameters. */
    private fun mockCursor(
        hasRow: Boolean = true,
        nameIndex: Int = 0,
        sizeIndex: Int = 1,
        name: String? = "my_song.mp3",
        size: Long? = 2048L
    ): Cursor = mockk(relaxed = true) {
        every { moveToFirst() } returns hasRow
        every { getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns nameIndex
        every { getColumnIndex(OpenableColumns.SIZE) } returns sizeIndex
        every { isNull(nameIndex) } returns (name == null)
        every { isNull(sizeIndex) } returns (size == null)
        every { getString(nameIndex) } returns name
        every { getLong(sizeIndex) } returns (size ?: 0L)
    }

    // ----- readFileInfo -----

    @Test
    fun readFileInfo_returnsNameMimeTypeAndSizeFromProvider() = runTest {
        val uri = mockUri()
        val cursor = mockCursor(name = "my_song.mp3", size = 2048L)
        every { resolver.query(uri, any(), null, null, null) } returns cursor
        every { resolver.getType(uri) } returns "audio/mpeg"

        val info = reader.readFileInfo(uri)

        assertEquals("my_song.mp3", info.name)
        assertEquals("audio/mpeg", info.mimeType)
        assertEquals(2048L, info.sizeBytes)
    }

    @Test
    fun readFileInfo_requestsDisplayNameAndSizeColumns() = runTest {
        val uri = mockUri()
        val projection = slot<Array<String>>()
        every { resolver.query(uri, capture(projection), null, null, null) } returns mockCursor()
        every { resolver.getType(uri) } returns "audio/mpeg"

        reader.readFileInfo(uri)

        assertArrayEquals(
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            projection.captured
        )
    }

    @Test
    fun readFileInfo_closesTheCursor() = runTest {
        val uri = mockUri()
        val cursor = mockCursor()
        every { resolver.query(uri, any(), null, null, null) } returns cursor
        every { resolver.getType(uri) } returns "audio/mpeg"

        reader.readFileInfo(uri)

        verify(exactly = 1) { cursor.close() }
    }

    @Test
    fun readFileInfo_closesTheCursorEvenWhenItHasNoRows() = runTest {
        val uri = mockUri()
        val cursor = mockCursor(hasRow = false)
        every { resolver.query(uri, any(), null, null, null) } returns cursor
        every { resolver.getType(uri) } returns null

        reader.readFileInfo(uri)

        verify(exactly = 1) { cursor.close() }
    }

    @Test
    fun readFileInfo_nullCursor_fallsBackToLastPathSegmentAndUnknownSize() = runTest {
        val uri = mockUri(lastSegment = "from_path.mp3")
        every { resolver.query(uri, any(), null, null, null) } returns null
        every { resolver.getType(uri) } returns "audio/mpeg"

        val info = reader.readFileInfo(uri)

        assertEquals("from_path.mp3", info.name)
        assertEquals("audio/mpeg", info.mimeType)
        assertEquals(FileReader.UNKNOWN_SIZE, info.sizeBytes)
    }

    @Test
    fun readFileInfo_emptyCursor_fallsBackToLastPathSegmentAndUnknownSize() = runTest {
        val uri = mockUri(lastSegment = "from_path.mp3")
        every { resolver.query(uri, any(), null, null, null) } returns mockCursor(hasRow = false)
        every { resolver.getType(uri) } returns "audio/mpeg"

        val info = reader.readFileInfo(uri)

        assertEquals("from_path.mp3", info.name)
        assertEquals(FileReader.UNKNOWN_SIZE, info.sizeBytes)
    }

    @Test
    fun readFileInfo_noNameAndNoLastPathSegment_usesUnknown() = runTest {
        val uri = mockUri(lastSegment = null)
        every { resolver.query(uri, any(), null, null, null) } returns null
        every { resolver.getType(uri) } returns "audio/mpeg"

        val info = reader.readFileInfo(uri)

        assertEquals("Unknown", info.name)
    }

    @Test
    fun readFileInfo_nullMimeType_becomesEmptyString() = runTest {
        val uri = mockUri()
        every { resolver.query(uri, any(), null, null, null) } returns mockCursor()
        every { resolver.getType(uri) } returns null

        val info = reader.readFileInfo(uri)

        assertEquals("", info.mimeType)
    }

    @Test
    fun readFileInfo_nameColumnMissing_fallsBackToLastPathSegment() = runTest {
        val uri = mockUri(lastSegment = "from_path.mp3")
        every { resolver.query(uri, any(), null, null, null) } returns
                mockCursor(nameIndex = -1, sizeIndex = 1, size = 500L)
        every { resolver.getType(uri) } returns "audio/mpeg"

        val info = reader.readFileInfo(uri)

        assertEquals("from_path.mp3", info.name)
        assertEquals(500L, info.sizeBytes) // The size column is still honoured.
    }

    @Test
    fun readFileInfo_sizeColumnMissing_usesUnknownSize() = runTest {
        val uri = mockUri()
        every { resolver.query(uri, any(), null, null, null) } returns
                mockCursor(nameIndex = 0, sizeIndex = -1, name = "my_song.mp3")
        every { resolver.getType(uri) } returns "audio/mpeg"

        val info = reader.readFileInfo(uri)

        assertEquals("my_song.mp3", info.name)
        assertEquals(FileReader.UNKNOWN_SIZE, info.sizeBytes)
    }

    @Test
    fun readFileInfo_nameIsNull_fallsBackToLastPathSegment() = runTest {
        val uri = mockUri(lastSegment = "from_path.mp3")
        every { resolver.query(uri, any(), null, null, null) } returns
                mockCursor(name = null, size = 77L)
        every { resolver.getType(uri) } returns "audio/mpeg"

        val info = reader.readFileInfo(uri)

        assertEquals("from_path.mp3", info.name)
        assertEquals(77L, info.sizeBytes)
    }

    @Test
    fun readFileInfo_sizeIsNull_usesUnknownSize() = runTest {
        val uri = mockUri()
        every { resolver.query(uri, any(), null, null, null) } returns
                mockCursor(name = "my_song.mp3", size = null)
        every { resolver.getType(uri) } returns "audio/mpeg"

        val info = reader.readFileInfo(uri)

        assertEquals("my_song.mp3", info.name)
        assertEquals(FileReader.UNKNOWN_SIZE, info.sizeBytes)
    }

    @Test
    fun readFileInfo_zeroSizeIsReportedAsZeroNotUnknown() = runTest {
        val uri = mockUri()
        every { resolver.query(uri, any(), null, null, null) } returns mockCursor(size = 0L)
        every { resolver.getType(uri) } returns "audio/mpeg"

        val info = reader.readFileInfo(uri)

        assertEquals(0L, info.sizeBytes)
    }

    // ----- openInputStream -----

    @Test
    fun openInputStream_returnsTheStreamFromTheResolver() {
        val uri = mockUri()
        val stream = ByteArrayInputStream(ByteArray(10))
        every { resolver.openInputStream(uri) } returns stream

        assertSame(stream, reader.openInputStream(uri))
    }

    @Test
    fun openInputStream_nullStream_throwsFileNotFoundWithUriInMessage() {
        val uri = mockUri(asString = "content://media/external/audio/42")
        every { resolver.openInputStream(uri) } returns null

        val error = try {
            reader.openInputStream(uri)
            null
        } catch (e: FileNotFoundException) {
            e
        }

        assertNotNull("Expected FileNotFoundException", error)
        assertTrue(error!!.message!!.contains("content://media/external/audio/42"))
    }

    // ----- constants -----

    @Test
    fun unknownSize_isMinusOne() {
        assertEquals(-1L, FileReader.UNKNOWN_SIZE)
    }


    @Test
    fun fileInfo_storesAllProperties() {
        val info = FileInfo("track.mp3", "audio/mpeg", 99L)

        assertEquals("track.mp3", info.name)
        assertEquals("audio/mpeg", info.mimeType)
        assertEquals(99L, info.sizeBytes)
    }
}
