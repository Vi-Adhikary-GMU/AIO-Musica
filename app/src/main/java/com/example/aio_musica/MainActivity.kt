package com.example.aio_musica

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.aio_musica.mvsep.MvsepClient
import com.example.aio_musica.ui.FileScreen
import com.example.aio_musica.ui.LibraryScreen
import com.example.aio_musica.ui.MusicScreen
import com.example.aio_musica.ui.PlaylistScreen
import com.example.aio_musica.ui.StemMixerViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Main activity for AIO Musica.
 *
 * Hosts the application's navigation screens, initializes MVSEP
 * integration, and displays the startup branding animation.
 *
 * Navigation between File, Music, Playlist, and Library screens
 * is handled using fragments.
 */
class MainActivity : AppCompatActivity() {

    /**
     * Initializes the main application interface.
     *
     * Installs the system splash screen, loads the main layout,
     * configures MVSEP integration and bottom navigation, and
     * plays the branding animation on initial launch.
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

        // Initialize MVSEP integration using the configured API token.
        initializeMvsep()

        // Play branding only on initial launch.
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

        // Configure navigation between application screens.
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

        // Display the File screen on initial launch.
        if (savedInstanceState == null) {
            bottomNavigation.selectedItemId = R.id.nav_file
        }
    }

    /**
     * Displays the AIO Musica startup branding animation.
     *
     * Randomly rotates the logo to one of five preset angles
     * (0, 15, 30, 45, or 60 degrees) on each initial launch.
     * The logo and title fade into view together, remain visible,
     * and then fade out to reveal the main application.
     *
     * Precondition: The main layout and branding views are loaded.
     * Postcondition: The branding overlay is hidden after animation.
     */
    private fun showBrandingAnimation() {
        val overlay = findViewById<FrameLayout>(R.id.brandingOverlay)
        val title = findViewById<TextView>(R.id.brandingTitle)
        val logo = findViewById<ImageView>(R.id.brandingLogo)

        // Pick a random logo rotation for this startup.
        logo.rotation = listOf(0f, 15f, 30f, 45f, 60f).random()

        // Start with both branding elements invisible.
        title.alpha = 0f
        logo.alpha = 0f

        // Fade in the logo and title.
        logo.animate().alpha(1f).setDuration(700L).start()

        title.animate().alpha(1f).setDuration(700L).withEndAction {

            // Hold the branding briefly before fading out.
            overlay.postDelayed({
                overlay.animate().alpha(0f).setDuration(1000L)
                    .withEndAction {
                        overlay.visibility = View.GONE
                    }.start()
            }, 1000L)
        }.start()
    }

    /**
     * Configures the stem mixer with the MVSEP repository.
     *
     * Reads the API token from the build configuration. If the
     * token is blank, initialization is skipped. Otherwise,
     * creates the repository and configures the shared mixer
     * ViewModel for track separation.
     *
     * Precondition: The activity has been created.
     * Postcondition: The mixer ViewModel is configured when
     * a nonblank API token is available.
     */
    private fun initializeMvsep() {
        val token = BuildConfig.MVSEP_API_TOKEN

        if (token.isBlank()) {
            return
        }

        val repository = MvsepClient.createRepository(token)

        val viewModel = ViewModelProvider(this)
            .get(StemMixerViewModel::class.java)

        viewModel.configure(repository)
    }
}