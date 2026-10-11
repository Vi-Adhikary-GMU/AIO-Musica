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



