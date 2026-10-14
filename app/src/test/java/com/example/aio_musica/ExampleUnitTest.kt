package com.example.aio_musica

import org.junit.Test

import org.junit.Assert.*

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

