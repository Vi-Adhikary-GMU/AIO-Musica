package com.example.aio_musica

import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import com.google.android.material.tabs.TabLayout
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aio_musica.library.Library
import com.example.aio_musica.library.LibraryStorage
import com.example.aio_musica.library.Song
import com.example.aio_musica.ui.StemMixerViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Instrumented integration tests for the Original Music Player.
 *
 * Creates temporary playable WAV files so tests do not depend on songs
 * manually imported into the emulator. Saves and restores library.json
 * around every test to preserve the existing Library and playlists.
 *
 * Use a test emulator: a force-stopped test process could interrupt cleanup.
 */
@RunWith(AndroidJUnit4::class)
class MusicPlayerCoverageTest {

    private lateinit var appContext: Context
    private lateinit var libraryFile: File
    private lateinit var fixtureFolder: File
    private lateinit var firstAudio: File
    private lateinit var secondAudio: File
    private var savedLibrary: ByteArray? = null
    private var scenario: ActivityScenario<MainActivity>? = null

    /** Saves Library data and creates two valid audio fixtures before each test. */
    @Before
    fun setUp() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        libraryFile = File(appContext.filesDir, "library.json")
        savedLibrary = if (libraryFile.exists()) libraryFile.readBytes() else null

        fixtureFolder = File(appContext.cacheDir, "music-test-${UUID.randomUUID()}")
        check(fixtureFolder.mkdirs()) { "Could not create test fixture directory" }
        firstAudio = File(fixtureFolder, "first.wav")
        secondAudio = File(fixtureFolder, "second.wav")
        writeSilentWav(firstAudio)
        writeSilentWav(secondAudio)
    }

    /** Closes playback, restores the original Library, and removes test audio. */
    @After
    fun tearDown() {
        try {
            scenario?.close()
        } finally {
            val oldData = savedLibrary
            if (oldData == null) {
                libraryFile.delete()
            } else {
                libraryFile.writeBytes(oldData)
            }
            fixtureFolder.deleteRecursively()
        }
    }

    /** Checks the empty-Library path and the disabled playback controls. */
    @Test
    fun emptyLibraryDisablesPlayerControls() {
        saveSongs()
        launchMusic()

        scenario!!.onActivity { activity ->
            val picker = activity.findViewById<Spinner>(R.id.songPicker)
            assertEquals(0, picker.adapter.count)
            assertFalse(picker.isEnabled)
            assertFalse(activity.findViewById<Button>(R.id.playPauseButton).isEnabled)
            assertFalse(activity.findViewById<Button>(R.id.previousSongButton).isEnabled)
            assertFalse(activity.findViewById<Button>(R.id.nextSongButton).isEnabled)
            assertFalse(activity.findViewById<SeekBar>(R.id.songProgress).isEnabled)
        }
    }

    /** Checks Previous and Next, including wraparound at both list boundaries. */
    @Test
    fun previousAndNextWrapBetweenSongs() {
        saveSongs(song("a", "Alpha", firstAudio), song("b", "Bravo", secondAudio))
        launchMusic()

        scenario!!.onActivity { activity ->
            assertEquals(2, activity.findViewById<Spinner>(R.id.songPicker).adapter.count)
            assertEquals("Alpha", activity.findViewById<TextView>(R.id.songTitle).text.toString())
            activity.findViewById<Button>(R.id.previousSongButton).performClick()
        }
        waitForUi()

        scenario!!.onActivity { activity ->
            assertEquals("Bravo", activity.findViewById<TextView>(R.id.songTitle).text.toString())
            activity.findViewById<Button>(R.id.nextSongButton).performClick()
        }
        waitForUi()

        scenario!!.onActivity { activity ->
            assertEquals("Alpha", activity.findViewById<TextView>(R.id.songTitle).text.toString())
            activity.findViewById<Button>(R.id.nextSongButton).performClick()
        }
        waitForUi()

        scenario!!.onActivity { activity ->
            assertEquals("Bravo", activity.findViewById<TextView>(R.id.songTitle).text.toString())
        }
    }

    /** Switching songs releases playback and shares the new song with Mixer. */
    @Test
    fun switchingSongsResetsPlaybackAndUpdatesMixer() {
        saveSongs(song("a", "Alpha", firstAudio), song("b", "Bravo", secondAudio))
        launchMusic()

        scenario!!.onActivity { activity ->
            val mixer = ViewModelProvider(activity).get(StemMixerViewModel::class.java)
            assertEquals("a", mixer.currentSong.value?.id)
            val play = activity.findViewById<Button>(R.id.playPauseButton)
            assertTrue(play.isEnabled)
            play.performClick()
            assertEquals(activity.getString(R.string.player_pause), play.text.toString())
            activity.findViewById<Button>(R.id.nextSongButton).performClick()
        }
        waitForUi()

        scenario!!.onActivity { activity ->
            val mixer = ViewModelProvider(activity).get(StemMixerViewModel::class.java)
            assertEquals("b", mixer.currentSong.value?.id)
            assertEquals("Bravo", activity.findViewById<TextView>(R.id.songTitle).text.toString())
            assertEquals(0, activity.findViewById<SeekBar>(R.id.songProgress).progress)
            val play = activity.findViewById<Button>(R.id.playPauseButton)
            assertTrue(play.isEnabled)
            assertEquals(activity.getString(R.string.player_play), play.text.toString())
        }
    }

    /** A missing audio file must not leave an enabled player or selected Mixer song. */
    @Test
    fun invalidAudioDisablesPlaybackAndClearsMixerSelection() {
        val invalidFile = File(fixtureFolder, "missing.wav") // Never created.
        saveSongs(song("a", "Alpha", firstAudio), song("b", "Broken", invalidFile))
        launchMusic()

        scenario!!.onActivity { activity ->
            val mixer = ViewModelProvider(activity).get(StemMixerViewModel::class.java)
            assertNotNull(mixer.currentSong.value)
            activity.findViewById<Spinner>(R.id.songPicker).setSelection(1)
        }
        waitForUi()

        scenario!!.onActivity { activity ->
            val mixer = ViewModelProvider(activity).get(StemMixerViewModel::class.java)
            assertEquals("Broken", activity.findViewById<TextView>(R.id.songTitle).text.toString())
            assertFalse(activity.findViewById<Button>(R.id.playPauseButton).isEnabled)
            assertFalse(activity.findViewById<SeekBar>(R.id.songProgress).isEnabled)
            assertNull(mixer.currentSong.value)
        }
    }

    /** Ensures the Mixer shows its empty state and disables stem playback until separation. */
    @Test
    fun mixerWithoutSeparatedStemsShowsEmptyState() {
        saveSongs(song("a", "Alpha", firstAudio))
        launchMusic()

        scenario!!.onActivity { activity ->
            activity.findViewById<TabLayout>(R.id.musicTabs).getTabAt(1)!!.select()
        }
        waitForUi()

        scenario!!.onActivity { activity ->
            assertEquals(View.VISIBLE,
                activity.findViewById<TextView>(R.id.mixerEmptyMessage).visibility)
            assertFalse(activity.findViewById<Button>(R.id.stemPlayButton).isEnabled)
            assertFalse(activity.findViewById<SeekBar>(R.id.stemSeekBar).isEnabled)
        }
    }

    /** Leaving Music destroys its view and clears the shared Mixer song selection. */
    @Test
    fun leavingMusicClearsCurrentSong() {
        saveSongs(song("a", "Alpha", firstAudio))
        launchMusic()

        scenario!!.onActivity { activity ->
            val mixer = ViewModelProvider(activity).get(StemMixerViewModel::class.java)
            assertEquals("a", mixer.currentSong.value?.id)
            activity.findViewById<BottomNavigationView>(R.id.bottomNavigation)
                .selectedItemId = R.id.nav_file
            activity.supportFragmentManager.executePendingTransactions()
            assertNull(mixer.currentSong.value)
        }
    }

    /** Constructs a test Song referencing a local fixture file. */
    private fun song(id: String, title: String, file: File) = Song(
        id = id,
        title = title,
        artist = "Fixture Artist",
        album = "Fixture Album",
        uri = Uri.fromFile(file),
        mimeType = "audio/wav"
    )

    /** Persists only the supplied songs for the duration of one test. */
    private fun saveSongs(vararg songs: Song) {
        val library = Library()
        songs.forEach { library.addSong(it) }
        LibraryStorage(appContext).save(library)
    }

    /** Launches the real activity and opens the Original Music Player. */
    private fun launchMusic() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity { activity ->
            activity.findViewById<View>(R.id.brandingOverlay).visibility = View.GONE
            activity.findViewById<BottomNavigationView>(R.id.bottomNavigation)
                .selectedItemId = R.id.nav_music
            activity.supportFragmentManager.executePendingTransactions()
        }
        waitForUi()
    }

    /** Allows asynchronous Spinner callbacks to finish before asserting. */
    private fun waitForUi() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    /** Writes eight seconds of silent, valid PCM WAV audio without external assets. */
    private fun writeSilentWav(file: File) {
        val sampleRate = 8000
        val samples = sampleRate * 8
        val dataBytes = samples * 2 // Mono, 16-bit PCM.
        val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataBytes)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)           // Size of PCM format chunk.
        buffer.putShort(1.toShort()) // PCM format.
        buffer.putShort(1.toShort()) // One audio channel.
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2)
        buffer.putShort(2.toShort())  // Bytes per frame.
        buffer.putShort(16.toShort()) // Bits per sample.
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataBytes)
        buffer.put(ByteArray(dataBytes))
        file.writeBytes(buffer.array())
    }
}
