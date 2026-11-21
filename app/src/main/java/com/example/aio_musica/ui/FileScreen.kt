package com.example.aio_musica.ui

import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.aio_musica.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.aio_musica.file_upload.FileReader
import com.example.aio_musica.file_upload.FileRepository
import com.example.aio_musica.file_upload.UploadFileViewModel
import com.example.aio_musica.file_upload.UploadSate
import com.example.aio_musica.library.LibraryStorage
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class FileScreen : Fragment() {

    /**
     * Saves and loads the library data from storage when it's first accessed.
     */
    private val libraryStorage by lazy {
        LibraryStorage(requireContext().applicationContext)
    }

    /**
     * The previously saved library that loads when it's first accessed.
     */
    private val library by lazy {
        libraryStorage.load()
    }

    /**
     * The ViewModel needs a FileRepository, so it's created through a factory.
     * Picked files are copied into `filesDir/songs`.
     */
    private val viewModel: UploadFileViewModel by viewModels {
        viewModelFactory {
            initializer {
                UploadFileViewModel(
                    FileRepository(
                        fileReader = FileReader(requireContext().applicationContext, Dispatchers.IO),
                        storageDir = File(requireContext().applicationContext.filesDir, "songs"),
                        ioDispatcher = Dispatchers.IO
                    ),
                    library = library,
                    libraryStorage = libraryStorage
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.file_screen,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        selectFileButton = view.findViewById(R.id.selectFileButton)
        cancelButton = view.findViewById(R.id.cancelButton)
        progressIndicator = view.findViewById(R.id.uploadProgress)
        statusText = view.findViewById(R.id.statusText)

        selectFileButton.setOnClickListener {
            pickFile.launch(arrayOf("audio/*"))
        }

        cancelButton.setOnClickListener {
            viewModel.cancelUpload()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect {
                    render(it)
                }
            }
        }
    }

    private fun render(state: UploadSate) {
        selectFileButton.isEnabled = !state.isUploading
        cancelButton.isVisible = state.isUploading

        val percent = (state.progress * 100).toInt().coerceIn(0, 100)
        progressIndicator.isInvisible = !state.isUploading
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