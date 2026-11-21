package com.example.aio_musica

import android.view.View
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.aio_musica.ui.MusicScreen
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
import com.example.aio_musica.ui.FileScreen
import com.example.aio_musica.ui.LibraryScreen
import com.example.aio_musica.ui.PlaylistScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

}
