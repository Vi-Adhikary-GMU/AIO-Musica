package com.example.aio_musica.ui

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class StemPlayerTest {

    private lateinit var context: Context
    private lateinit var uri: Uri
    private lateinit var player: StemPlayer

    @Before
    fun setUp() {
        context = mockk()
        uri = mockk()
        mockkStatic(MediaPlayer::class)
        mockkStatic(Uri::class)
        every { Uri.fromFile(any()) } returns uri
        player = StemPlayer()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun newMediaPlayer(): MediaPlayer = mockk(relaxed = true)

    private fun stem(
        id: String,
        volume: Float = 1f,
        muted: Boolean = false,
        withFile: Boolean = true
    ) = StemState(
        id = id,
        name = id,
        volume = volume,
        muted = muted,
        audioFile = if (withFile) File("$id.mp3") else null
    )

    /** MediaPlayer.create returns these players, one per call, in order. */
    private fun createReturns(vararg players: MediaPlayer) {
        every { MediaPlayer.create(any<Context>(), any<Uri>()) } returnsMany players.toList()
    }

    @Test
    fun newPlayer_hasNothingLoaded() {
        assertFalse(player.hasStems)
        assertFalse(player.isPlaying)
        assertEquals(0, player.duration)
        assertEquals(0, player.position)
        assertTrue(player.loadedIds.isEmpty())
    }

    @Test
    fun controls_onEmptyPlayer_doNothing() {
        player.play()
        player.pause()
        player.seekTo(500)
        player.applyVolumes(listOf(stem("a")))
        player.release()

        assertFalse(player.hasStems)
    }

    @Test
    fun load_createsOnePlayerPerStem_inOrder() {
        createReturns(newMediaPlayer(), newMediaPlayer())

        player.load(context, listOf(stem("vocals"), stem("bass")))

        assertTrue(player.hasStems)
        assertEquals(listOf("vocals", "bass"), player.loadedIds)
    }

    @Test
    fun load_skipsStemsWithoutAFile() {
        createReturns(newMediaPlayer())

        player.load(context, listOf(stem("ghost", withFile = false), stem("bass")))

        assertEquals(listOf("bass"), player.loadedIds)
    }

    @Test
    fun load_skipsStemsThatCannotBeOpened() {
        every { MediaPlayer.create(any<Context>(), any<Uri>()) } returns null

        player.load(context, listOf(stem("broken")))

        assertFalse(player.hasStems)
        assertTrue(player.loadedIds.isEmpty())
    }

    @Test
    fun applyVolumes_mutedStemIsSilent_evenAtFullVolume() {
        val vocals = newMediaPlayer()
        createReturns(vocals)
        player.load(context, listOf(stem("vocals")))

        player.applyVolumes(listOf(stem("vocals", volume = 1f, muted = true)))

        verify { vocals.setVolume(0f, 0f) }
    }

    @Test
    fun play_startsEveryPlayer() {
        val a = newMediaPlayer()
        val b = newMediaPlayer()
        createReturns(a, b)
        player.load(context, listOf(stem("a"), stem("b")))

        player.play()

        verify(exactly = 1) { a.start() }
        verify(exactly = 1) { b.start() }
    }

    @Test
    fun pause_onlyPausesPlayersThatArePlaying() {
        val playing = newMediaPlayer()
        val stopped = newMediaPlayer()
        every { playing.isPlaying } returns true
        every { stopped.isPlaying } returns false
        createReturns(playing, stopped)
        player.load(context, listOf(stem("playing"), stem("stopped")))

        player.pause()

        verify(exactly = 1) { playing.pause() }
        verify(exactly = 0) { stopped.pause() }
    }

    @Test
    fun isPlaying_followsTheFirstPlayer() {
        val first = newMediaPlayer()
        every { first.isPlaying } returns true
        createReturns(first)
        player.load(context, listOf(stem("a")))

        assertTrue(player.isPlaying)

        every { first.isPlaying } returns false
        assertFalse(player.isPlaying)
    }

    @Test
    fun duration_ignoresShorterStemsAfterTheLongest() {
        val long = newMediaPlayer()
        val short = newMediaPlayer()
        every { long.duration } returns 5000
        every { short.duration } returns 1000
        createReturns(long, short)
        player.load(context, listOf(stem("long"), stem("short")))

        assertEquals(5000, player.duration)
    }

    @Test
    fun position_comesFromTheFirstPlayer() {
        val first = newMediaPlayer()
        val second = newMediaPlayer()
        every { first.currentPosition } returns 2500
        every { second.currentPosition } returns 9999
        createReturns(first, second)
        player.load(context, listOf(stem("a"), stem("b")))

        assertEquals(2500, player.position)
    }

    @Test
    fun release_releasesEveryPlayerAndClears() {
        val a = newMediaPlayer()
        val b = newMediaPlayer()
        createReturns(a, b)
        player.load(context, listOf(stem("a"), stem("b")))

        player.release()

        verify(exactly = 1) { a.release() }
        verify(exactly = 1) { b.release() }
        assertFalse(player.hasStems)
        assertTrue(player.loadedIds.isEmpty())
    }

    @Test
    fun songEnding_pausesRewindsAndNotifies() {
        val first = newMediaPlayer()
        val second = newMediaPlayer()
        every { first.isPlaying } returns true
        every { second.isPlaying } returns true
        val listener = slot<MediaPlayer.OnCompletionListener>()
        every { first.setOnCompletionListener(capture(listener)) } just Runs
        createReturns(first, second)
        var notified = 0
        player.onCompleted = { notified++ }
        player.load(context, listOf(stem("a"), stem("b")))

        listener.captured.onCompletion(first)

        verify { first.pause() }
        verify { second.pause() }
        verify { first.seekTo(0) }
        verify { second.seekTo(0) }
        assertEquals(1, notified)
    }

    @Test
    fun songEnding_withoutACallback_stillRewinds() {
        val first = newMediaPlayer()
        every { first.isPlaying } returns true
        val listener = slot<MediaPlayer.OnCompletionListener>()
        every { first.setOnCompletionListener(capture(listener)) } just Runs
        createReturns(first)
        player.load(context, listOf(stem("a")))

        listener.captured.onCompletion(first)

        verify { first.seekTo(0) }
    }
}