package com.example.aio_musica

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.aio_musica.ui.FileScreen
import com.example.aio_musica.ui.LibraryScreen
import com.example.aio_musica.ui.MusicScreen
import com.example.aio_musica.ui.PlaylistScreen
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Main activity for AIO Musica.
 *
 * Hosts the application's navigation screens and displays the
 * startup branding animation when the application first opens.
 *
 * Navigation between File, Music, Playlist, and Library screens
 * is handled using fragments.
 */
class MainActivity : AppCompatActivity() {

    /**
     * Initializes the main application interface.
     *
     * Installs the system splash screen, loads the main layout,
     * configures bottom navigation, and plays the branding
     * animation on initial launch.
     *
     * @param savedInstanceState Previously saved activity state,
     * or null when the activity is first created.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Fade out Android's system splash screen.
        splashScreen.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(350L)
                .withEndAction { provider.remove() }
                .start()
        }

        setContentView(R.layout.activity_main)

        // Play the branding animation only on initial launch.
        if (savedInstanceState == null) {
            showBrandingAnimation()
        } else {
            findViewById<FrameLayout>(
                R.id.brandingOverlay
            ).visibility = View.GONE
        }

        // Apply system bar spacing to the main layout.
        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                0
            )
            insets
        }

        // Configure navigation between the application's screens.
        val bottomNavigation =
            findViewById<BottomNavigationView>(R.id.bottomNavigation)

        bottomNavigation.setOnItemSelectedListener { item ->
            val selectedScreen: Fragment = when (item.itemId) {
                R.id.nav_file -> FileScreen()
                R.id.nav_music -> MusicScreen()
                R.id.nav_playlist -> PlaylistScreen()
                R.id.nav_library -> LibraryScreen()
                else -> return@setOnItemSelectedListener false
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, selectedScreen)
                .commit()

            true
        }

        // Display the File screen by default on initial launch.
        if (savedInstanceState == null) {
            bottomNavigation.selectedItemId = R.id.nav_file
        }
    }

    /**
     * Displays AIO Musica's startup branding animation.
     *
     * The title starts invisible, fades into view, remains
     * visible briefly, then fades out with its overlay to
     * reveal the application's main interface.
     *
     * Precondition: activity_main.xml has been loaded and
     * contains brandingOverlay and brandingTitle.
     *
     * Postcondition: The branding overlay becomes hidden
     * after the animation completes.
     */
    private fun showBrandingAnimation() {
        val overlay = findViewById<FrameLayout>(R.id.brandingOverlay)
        val title = findViewById<TextView>(R.id.brandingTitle)

        // Initially hide the application title.
        title.alpha = 0f

        // Fade the title into view.
        title.animate().alpha(1f).setDuration(600L).withEndAction {

            // Briefly display the title before fading out.
            overlay.postDelayed({

                // Reveal the main application interface.
                overlay.animate().alpha(0f).setDuration(400L)
                    .withEndAction {
                        overlay.visibility = View.GONE
                    }.start()

            }, 600L)
        }.start()
    }
}
