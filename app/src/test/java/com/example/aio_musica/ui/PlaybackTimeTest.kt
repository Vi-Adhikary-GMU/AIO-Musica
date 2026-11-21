package com.example.aio_musica.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests playback time formatting without Android dependencies.
 */
class PlaybackTimeTest {

    /** Zero milliseconds should display as 0:00. */
    @Test
    fun zeroTime() {
        assertEquals("0:00", formatPlaybackTime(0))
    }

    /** Milliseconds should convert to minutes and seconds. */
    @Test
    fun minutesAndSeconds() {
        assertEquals("2:15", formatPlaybackTime(135000))
    }

    /** Negative values should be treated as zero. */
    @Test
    fun negativeTime() {
        assertEquals("0:00", formatPlaybackTime(-1000))
    }
}
