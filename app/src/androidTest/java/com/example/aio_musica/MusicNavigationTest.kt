package com.example.aio_musica

import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.example.aio_musica.ui.StemMixerViewModel
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.example.aio_musica.ui.MusicScreen
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
import com.example.aio_musica.ui.FileScreen
import com.example.aio_musica.ui.LibraryScreen
import com.example.aio_musica.ui.PlaylistScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for navigation within the Music screen.
 *
 * Launches the actual MainActivity and verifies that switching
 * between Original and Mixer updates the displayed interface.
 */
@RunWith(AndroidJUnit4::class)
class MusicNavigationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    /**
     * Tests navigation between the Original and Mixer tabs.
     *
     * Verifies that the Music fragment loads successfully and
     * only the selected tab's content is visible.
     */
    @Test
    fun switchingMusicTabsUpdatesVisibility() {
        activityRule.scenario.onActivity { activity ->

            // Open the Music screen using bottom navigation.
            val navigation = activity.findViewById<BottomNavigationView>(
                R.id.bottomNavigation
            )
            navigation.selectedItemId = R.id.nav_music

            // Finish the pending fragment transaction.
            activity.supportFragmentManager.executePendingTransactions()

            // Verify the Music fragment was loaded.
            val fragment = activity.supportFragmentManager
                .findFragmentById(R.id.fragmentContainer)
            assertTrue(fragment is MusicScreen)

            // Retrieve both tab layouts.
            val original = activity.findViewById<View>(
                R.id.originalContent
            )
            val mixer = activity.findViewById<View>(
                R.id.mixerContainer
            )
            val tabs = activity.findViewById<TabLayout>(
                R.id.musicTabs
            )

            // Original should be displayed initially.
            assertEquals(View.VISIBLE, original.visibility)
            assertEquals(View.GONE, mixer.visibility)

            // Select Mixer and verify its visibility.
            tabs.getTabAt(1)!!.select()

            assertEquals(View.GONE, original.visibility)
            assertEquals(View.VISIBLE, mixer.visibility)

            // Return to Original.
            tabs.getTabAt(0)!!.select()

            assertEquals(View.VISIBLE, original.visibility)
            assertEquals(View.GONE, mixer.visibility)
        }
    }


    /**
     * Verifies that each bottom-navigation button opens
     * the correct application screen.
     *
     * Tests File, Music, Playlist, and Library navigation.
     */
    @Test
    fun bottomNavigationOpensCorrectScreens() {
        activityRule.scenario.onActivity { activity ->
            val navigation =
                activity.findViewById<BottomNavigationView>(
                    R.id.bottomNavigation
                )

            // File is the default screen.
            activity.supportFragmentManager.executePendingTransactions()
            assertTrue(
                activity.supportFragmentManager
                    .findFragmentById(R.id.fragmentContainer) is FileScreen
            )

            // Navigate to Music.
            navigation.selectedItemId = R.id.nav_music
            activity.supportFragmentManager.executePendingTransactions()
            assertTrue(
                activity.supportFragmentManager
                    .findFragmentById(R.id.fragmentContainer) is MusicScreen
            )

            // Navigate to Playlist.
            navigation.selectedItemId = R.id.nav_playlist
            activity.supportFragmentManager.executePendingTransactions()
            assertTrue(
                activity.supportFragmentManager
                    .findFragmentById(R.id.fragmentContainer) is PlaylistScreen
            )

            // Navigate to Library.
            navigation.selectedItemId = R.id.nav_library
            activity.supportFragmentManager.executePendingTransactions()
            assertTrue(
                activity.supportFragmentManager
                    .findFragmentById(R.id.fragmentContainer) is LibraryScreen
            )

            // Return to File.
            navigation.selectedItemId = R.id.nav_file
            activity.supportFragmentManager.executePendingTransactions()
            assertTrue(
                activity.supportFragmentManager
                    .findFragmentById(R.id.fragmentContainer) is FileScreen
            )
        }
    }

    /**
     * Verifies that recreating MainActivity does not replay
     * the startup branding animation.
     *
     * The activity must hide the branding overlay when
     * restoring a previously saved instance state.
     *
     * Expected result: The overlay is hidden after recreation.
     */
    @Test
    fun activityRecreationSkipsBranding() {
        // Recreate the activity using a saved instance state.
        activityRule.scenario.recreate()

        activityRule.scenario.onActivity { activity ->
            val overlay = activity.findViewById<View>(
                R.id.brandingOverlay
            )

            // The splash overlay should not appear again.
            assertEquals(View.GONE, overlay.visibility)
        }
    }

    /**
     * Verifies that the Original Music Player can start and pause
     * a previously imported audio file.
     *
     * Requires at least one playable song in the application's Library.
     *
     * Expected behavior:
     * - The song can be loaded.
     * - Playback controls become enabled.
     * - Pressing Play changes the button to Pause.
     * - Pressing Pause changes the button back to Play.
     */
    @Test
    fun musicPlayerPlayAndPause() {
        activityRule.scenario.onActivity { activity ->

            // Hide the branding overlay during the test.
            activity.findViewById<View>(
                R.id.brandingOverlay
            ).visibility = View.GONE

            // Navigate to the Original Music Player.
            val navigation =
                activity.findViewById<BottomNavigationView>(
                    R.id.bottomNavigation
                )

            navigation.selectedItemId = R.id.nav_music
            activity.supportFragmentManager.executePendingTransactions()

            val songPicker =
                activity.findViewById<Spinner>(R.id.songPicker)

            val playPause =
                activity.findViewById<Button>(R.id.playPauseButton)

            val progress =
                activity.findViewById<SeekBar>(R.id.songProgress)

            // Verify that the test song is available.
            assertTrue(
                "Import a playable MP3 before running this test.",
                songPicker.adapter.count > 0
            )

            // Verify that a song was prepared successfully.
            assertTrue(playPause.isEnabled)
            assertTrue(progress.isEnabled)
            assertTrue(progress.max > 0)

            // Start playback.
            playPause.performClick()

            assertEquals(
                activity.getString(R.string.player_pause),
                playPause.text.toString()
            )

            // Pause playback.
            playPause.performClick()

            assertEquals(
                activity.getString(R.string.player_play),
                playPause.text.toString()
            )
        }
    }

    /**
     * Verifies that the Original Music Player shares its
     * selected song with the Stem Mixer.
     *
     * Also verifies that the selection is cleared when
     * the user leaves the Music screen.
     *
     * Requires one playable MP3 in the Library.
     */
    @Test
    fun selectedSongIsSharedWithMixerAndClearedOnExit() {
        activityRule.scenario.onActivity { activity ->

            val navigation =
                activity.findViewById<BottomNavigationView>(
                    R.id.bottomNavigation
                )

            // Open the Original Music Player.
            navigation.selectedItemId = R.id.nav_music
            activity.supportFragmentManager.executePendingTransactions()

            val songPicker =
                activity.findViewById<Spinner>(R.id.songPicker)

            // Confirm that a test song is available.
            assertTrue(
                "Import a playable MP3 before running this test.",
                songPicker.adapter.count > 0
            )

            // Access the ViewModel shared by Original and Mixer.
            val mixerViewModel = ViewModelProvider(activity)
                .get(StemMixerViewModel::class.java)

            // A successfully loaded song should be available.
            val selectedSong = mixerViewModel.currentSong.value
            assertNotNull(selectedSong)

            // Switch to Mixer.
            val tabs = activity.findViewById<TabLayout>(R.id.musicTabs)
            tabs.getTabAt(1)!!.select()

            // The Mixer should retain the selected song.
            assertEquals(
                selectedSong!!.id,
                mixerViewModel.currentSong.value?.id
            )

            // Leave Music to trigger player cleanup.
            navigation.selectedItemId = R.id.nav_file
            activity.supportFragmentManager.executePendingTransactions()

            // No song should remain selected after cleanup.
            assertNull(mixerViewModel.currentSong.value)
        }
    }


    /**
     * Verifies that opening Mixer pauses an actively playing song.
     *
     * Tests the interaction between Original playback and
     * the Mixer tab without starting audio separation.
     *
     * Requires one playable song in the Library.
     */
    @Test
    fun switchingToMixerPausesPlayback() {
        activityRule.scenario.onActivity { activity ->

            val navigation =
                activity.findViewById<BottomNavigationView>(
                    R.id.bottomNavigation
                )

            // Open the Original Music Player.
            navigation.selectedItemId = R.id.nav_music
            activity.supportFragmentManager.executePendingTransactions()

            val playPause =
                activity.findViewById<Button>(R.id.playPauseButton)

            val tabs =
                activity.findViewById<TabLayout>(R.id.musicTabs)

            // Confirm that a playable song is available.
            assertTrue(
                "Import a playable MP3 before running this test.",
                playPause.isEnabled
            )

            // Start playing the selected song.
            playPause.performClick()

            assertEquals(
                activity.getString(R.string.player_pause),
                playPause.text.toString()
            )

            // Opening Mixer should automatically pause playback.
            tabs.getTabAt(1)!!.select()

            assertEquals(
                activity.getString(R.string.player_play),
                playPause.text.toString()
            )

            // Returning to Original should not resume automatically.
            tabs.getTabAt(0)!!.select()

            assertEquals(
                activity.getString(R.string.player_play),
                playPause.text.toString()
            )
        }
    }

    /**
     * Verifies that the Original Music Player supports seeking.
     *
     * Selects an imported song and simulates a user tapping
     * the middle of the playback progress bar.
     *
     * Expected behavior:
     * - The progress bar is enabled for a loaded song.
     * - Tapping the bar changes the playback position.
     * - The elapsed-time display reflects the selected position.
     *
     * Requires a playable MP3 in the Library.
     */
    @Test
    fun seekingUpdatesPlaybackProgress() {
        activityRule.scenario.onActivity { activity ->

            // Hide the startup branding during this test.
            activity.findViewById<View>(
                R.id.brandingOverlay
            ).visibility = View.GONE

            // Open the Original Music Player.
            val navigation =
                activity.findViewById<BottomNavigationView>(
                    R.id.bottomNavigation
                )

            navigation.selectedItemId = R.id.nav_music
            activity.supportFragmentManager.executePendingTransactions()

            val progress =
                activity.findViewById<SeekBar>(R.id.songProgress)

            // Verify a playable song has been loaded.
            assertTrue(
                "Import a playable MP3 before running this test.",
                progress.isEnabled
            )
            assertTrue(progress.max > 0)
        }

        // Simulate a user tapping the middle of the seek bar.
        onView(withId(R.id.songProgress)).perform(click())

        activityRule.scenario.onActivity { activity ->
            val progress =
                activity.findViewById<SeekBar>(R.id.songProgress)

            val elapsed =
                activity.findViewById<TextView>(R.id.currentTime)

            // Seeking should move playback away from the start.
            assertTrue(progress.progress > 0)
            assertTrue(progress.progress < progress.max)

            // Verify the elapsed-time display matches the position.
            val seconds = progress.progress / 1000
            val expectedTime =
                "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

            assertEquals(expectedTime, elapsed.text.toString())
        }
    }

}
