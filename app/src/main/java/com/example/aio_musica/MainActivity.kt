package com.example.aio_musica

import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.aio_musica.file_upload.FileReader
import com.example.aio_musica.file_upload.FileRepository
import com.example.aio_musica.file_upload.UploadFileViewModel
import com.example.aio_musica.file_upload.UploadSate
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    /**
     * The ViewModel needs a FileRepository, so it's created through a factory.
     * Picked files are copied into `filesDir/songs`.
     */
    private val viewModel: UploadFileViewModel by viewModels {
        viewModelFactory {
            initializer {
                UploadFileViewModel(
                    FileRepository(
                        fileReader = FileReader(applicationContext, Dispatchers.IO),
                        storageDir = File(applicationContext.filesDir, "songs"),
                        ioDispatcher = Dispatchers.IO
                    )
                )
            }
        }
    }

    /**
     * Opens the system file picker (audio files only) and hands the chosen file to the ViewModel.
     * The result is null if the user backed out without choosing anything.
     */
    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.uploadFile(uri)
        }
    }

    private lateinit var selectFileButton: MaterialButton
    private lateinit var cancelButton: MaterialButton
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var statusText: TextView

    /**
     * Initializes main screen for AIO Musica.
     *
     * Installs the startup splash screen before creating the activity,
     * fades out the screen once the app is ready, and initializes
     * the existing file-upload interface.
     *
     * @param savedInstanceState Previously saved activity state,
     * or null when first created.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // fade splash screen out when main screen is ready
        splashScreen.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(350L)
                .withEndAction { provider.remove() }
                .start()
        }
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            showBrandingAnimation()
        } else {
            findViewById<FrameLayout>(R.id.brandingOverlay).visibility = View.GONE
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        selectFileButton = findViewById(R.id.selectFileButton)
        cancelButton = findViewById(R.id.cancelButton)
        progressIndicator = findViewById(R.id.uploadProgress)
        statusText = findViewById(R.id.statusText)

        selectFileButton.setOnClickListener { pickFile.launch(arrayOf("audio/*")) }
        cancelButton.setOnClickListener { viewModel.cancelUpload() }

        // Redraw the screen every time the upload state changes (only while the screen is visible).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    /**
     * Displays AIO Musica branding animation.
     *
     * The application title starts invisible, fades into view,
     * remains visible briefly, and then fades out to reveal the
     * existing file-upload screen.
     */
    private fun showBrandingAnimation() {
        val overlay = findViewById<FrameLayout>(R.id.brandingOverlay)
        val title = findViewById<TextView>(R.id.brandingTitle)

        title.alpha = 0f
        title.animate().alpha(1f).setDuration(600L).withEndAction {
            overlay.postDelayed({
                overlay.animate().alpha(0f).setDuration(400L).withEndAction {
                    overlay.visibility = View.GONE
                }.start()
            }, 600L)
        }.start()
    }
    private fun render(state: UploadSate) {
        selectFileButton.isEnabled = !state.isUploading
        cancelButton.isVisible = state.isUploading

        val percent = (state.progress * 100).toInt().coerceIn(0, 100)
        progressIndicator.isInvisible = !(state.isUploading || state.isUploadComplete)
        progressIndicator.setProgressCompat(percent, true)

        statusText.text = when {
            state.errorMessage != null -> state.errorMessage
            state.isUploading -> getString(R.string.status_uploading, percent)
            state.isUploadComplete -> getString(
                R.string.status_complete,
                state.importedFile?.displayName ?: ""
            )
            else -> getString(R.string.status_idle)
        }
    }
}
