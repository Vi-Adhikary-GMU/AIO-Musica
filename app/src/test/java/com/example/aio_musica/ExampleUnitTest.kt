package com.example.aio_musica

import com.example.aio_musica.file_upload.FileInfo
import com.example.aio_musica.file_upload.FileReader
import com.example.aio_musica.file_upload.ImportedFile
import com.example.aio_musica.file_upload.ProgressUpdate
import com.example.aio_musica.file_upload.UploadSate
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}


class TestToggleMute {
    @Test
    fun testMutesUnmutedStem() {
        val result = toggleMute(false)
        assertTrue(result)
    }

    @Test
    fun testUnmutesMutedStem() {
        val result = toggleMute(true)
        assertFalse(result)
    }

    @Test
    fun testToggleMuteTwice() {
        val mutedState = toggleMute(false)
        val unmutedState = toggleMute(mutedState)
        assertFalse(unmutedState)
    }

}

/**
 * Toggles the mute state of a stem in an audio.
 * @param isMuted is the current mute state of the stem (true/false)
 * If the stem is unmuted it becomes  muted.
 * If the stem is muted it becomes unmuted.
 * @return the updated mute state (true if muted, false if unmuted)
 */
fun toggleMute(isMuted: Boolean): Boolean {
    return !isMuted
}

class TestValidationFileUpload {
    @Test
    fun testFileAcceptsMp3() {
        assertTrue(validateFileUpload("song.mp3"))
    }

    @Test
    fun testFileAcceptsUppercaseMp3() {
        assertTrue(validateFileUpload("song.MP3"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFileDeniesOtherExtension() {
        validateFileUpload("song.wav")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFileDeniesEmptyFile() {
        validateFileUpload("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFileMissingExtension() {
        validateFileUpload("song")
    }
}

/**
 * Function checks whether a filename is a valid MP3 upload by the user
 * @param fileName is the name of the selected file chosen by the user
 * @throws IllegalArgumentException if the filename is empty or if the file does not end in .mp3
 * @return true if the filename ends in .mp3
 */
fun validateFileUpload(fileName: String): Boolean {
    if (fileName.isBlank()) {
        throw IllegalArgumentException("File cannot be empty")
    }
    if (!fileName.lowercase().endsWith(".mp3")) {
        throw IllegalArgumentException("Only mp3 files are supported")
    }
    return true
}

class TestFileInfo {
    @Test
    fun testFileInfoCreationAndProperties() {
        val fileInfo = FileInfo(
            name = "favorite_song.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = 2048000L
        )
        assertEquals("favorite_song.mp3", fileInfo.name)
        assertEquals("audio/mpeg", fileInfo.mimeType)
        assertEquals(2048000L, fileInfo.sizeBytes)
    }

    @Test
    fun testFileInfoWithUnknownSize() {
        val fileInfo = FileInfo(
            name = "stream_track.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = FileReader.UNKNOWN_SIZE
        )
        assertEquals(-1L, fileInfo.sizeBytes)
        assertEquals(FileReader.UNKNOWN_SIZE, fileInfo.sizeBytes)
    }
}

class TestProgressUpdateAndImportedFile {
    @Test
    fun testProgressUpdateDefaultsAndState() {
        val update = ProgressUpdate(bytesSent = 500L, totalBytes = 1000L)
        assertEquals(500L, update.bytesSent)
        assertEquals(1000L, update.totalBytes)
        assertNull(update.result)
    }

    @Test
    fun testProgressUpdateWithResult() {
        val testFile = File("/storage/emulated/0/Music/track.mp3")
        val imported = ImportedFile(
            file = testFile,
            displayName = "track.mp3",
            mimeType = "audio/mpeg"
        )
        val update = ProgressUpdate(
            bytesSent = 1000L,
            totalBytes = 1000L,
            result = imported
        )
        assertEquals(1000L, update.bytesSent)
        assertEquals(1000L, update.totalBytes)
        assertNotNull(update.result)
        assertEquals(testFile, update.result?.file)
        assertEquals("track.mp3", update.result?.displayName)
        assertEquals("audio/mpeg", update.result?.mimeType)
    }

    @Test
    fun testImportedFileProperties() {
        val file = File("test_dir/song.mp3")
        val importedFile = ImportedFile(
            file = file,
            displayName = "song.mp3",
            mimeType = "audio/mpeg"
        )
        assertEquals(file, importedFile.file)
        assertEquals("song.mp3", importedFile.displayName)
        assertEquals("audio/mpeg", importedFile.mimeType)
    }
}

class TestUploadState {
    @Test
    fun testUploadStateInitialDefaults() {
        val state = UploadSate()
        assertFalse(state.isUploading)
        assertFalse(state.isUploadComplete)
        assertEquals(0f, state.progress, 0.0001f)
        assertNull(state.errorMessage)
        assertNull(state.importedFile)
    }

    @Test
    fun testUploadStateStartUpload() {
        val initialState = UploadSate()
        val uploadingState = initialState.copy(
            isUploading = true,
            isUploadComplete = false,
            progress = 0f,
            errorMessage = null,
            importedFile = null
        )
        assertTrue(uploadingState.isUploading)
        assertFalse(uploadingState.isUploadComplete)
        assertEquals(0f, uploadingState.progress, 0.0001f)
    }

    @Test
    fun testUploadStateProgressUpdate() {
        val progressUpdate = ProgressUpdate(bytesSent = 250L, totalBytes = 1000L)
        val calculatedProgress = progressUpdate.bytesSent.toFloat() / progressUpdate.totalBytes.toFloat()

        val state = UploadSate(isUploading = true).copy(
            progress = calculatedProgress
        )
        assertTrue(state.isUploading)
        assertEquals(0.25f, state.progress, 0.0001f)
    }

    @Test
    fun testUploadStateCompletion() {
        val imported = ImportedFile(
            file = File("saved/song.mp3"),
            displayName = "song.mp3",
            mimeType = "audio/mpeg"
        )
        val state = UploadSate(isUploading = true).copy(
            isUploading = false,
            isUploadComplete = true,
            progress = 1.0f,
            importedFile = imported
        )
        assertFalse(state.isUploading)
        assertTrue(state.isUploadComplete)
        assertEquals(1.0f, state.progress, 0.0001f)
        assertEquals(imported, state.importedFile)
    }

    @Test
    fun testUploadStateError() {
        val state = UploadSate(isUploading = true).copy(
            isUploading = false,
            errorMessage = "File not found"
        )
        assertFalse(state.isUploading)
        assertFalse(state.isUploadComplete)
        assertEquals("File not found", state.errorMessage)
    }
}
