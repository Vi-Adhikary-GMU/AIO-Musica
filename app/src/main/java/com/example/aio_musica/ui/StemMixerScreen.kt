package com.example.aio_musica.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aio_musica.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * The Mixer tab inside the Music tab: a "Separate current song" button and one box per stem,
 * each with a volume slider, a mute button and an export button (which asks first).
 */
class StemMixerScreen : Fragment() {

    private val viewModel: StemMixerViewModel by activityViewModels()

    private lateinit var stemList: RecyclerView
    private lateinit var adapter: StemAdapter

    private val stemPlayer = StemPlayer()
    private var progressJob: Job? = null
    private var isSeeking = false

    /** Opens the system "Save as" screen; the stem is copied once the user picks a place. */
    private val saveStem =
        registerForActivityResult(ActivityResultContracts.CreateDocument("audio/*")) { uri ->
            val id = viewModel.pendingExportId
            viewModel.pendingExportId = null
            if (uri != null && id != null) {
                viewModel.exportStem(id, uri, requireContext().contentResolver)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.stem_mixer_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stemList = view.findViewById(R.id.stemList)
        val emptyMessage = view.findViewById<TextView>(R.id.mixerEmptyMessage)
        val separateButton = view.findViewById<MaterialButton>(R.id.separateButton)
        val separateProgress = view.findViewById<LinearProgressIndicator>(R.id.separateProgress)
        val separateStatus = view.findViewById<TextView>(R.id.separateStatus)
        val playButton = view.findViewById<MaterialButton>(R.id.stemPlayButton)
        val seekBar = view.findViewById<SeekBar>(R.id.stemSeekBar)
        val timeText = view.findViewById<TextView>(R.id.stemTime)

        playButton.isEnabled = false
        seekBar.isEnabled = false

        fun startProgress() {
            progressJob?.cancel()
            progressJob = viewLifecycleOwner.lifecycleScope.launch {
                while (isActive && stemPlayer.isPlaying) {
                    if (!isSeeking) {
                        seekBar.progress = stemPlayer.position
                        timeText.text = formatPlaybackTime(stemPlayer.position)
                    }
                    delay(300)
                }
            }
        }

        fun showStopped() {
            progressJob?.cancel()
            playButton.setText(R.string.player_play)
            seekBar.progress = stemPlayer.position
            timeText.text = formatPlaybackTime(stemPlayer.position)
        }

        stemPlayer.onCompleted = { showStopped() }

        playButton.setOnClickListener {
            if (stemPlayer.isPlaying) {
                stemPlayer.pause()
                showStopped()
            } else if (stemPlayer.hasStems) {
                stemPlayer.play()
                playButton.setText(R.string.player_pause)
                startProgress()
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) timeText.text = formatPlaybackTime(progress)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                stemPlayer.seekTo(seekBar.progress)
                isSeeking = false
            }
        })

        adapter = StemAdapter(
            onVolumeChange = { id, volume -> viewModel.setVolume(id, volume) },
            onMuteClick = { id -> viewModel.toggleMute(id) },
            onExportClick = { stem -> confirmExport(stem) }
        )
        // One column, one box per row. Scrolls when there are more boxes than fit.
        stemList.layoutManager = LinearLayoutManager(requireContext())
        stemList.adapter = adapter

        separateButton.setOnClickListener {
            val song = viewModel.currentSong.value
            val path = song?.uri?.path
            if (song == null || path == null) {
                Toast.makeText(
                    requireContext(), R.string.stem_separate_no_song, Toast.LENGTH_SHORT
                ).show()
            } else {
                viewModel.separateSong(
                    File(path),
                    File(requireContext().filesDir, "stems/${song.id}")
                )
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.stems.collect { stems ->
                        adapter.submitList(stems)

                        // New set of stems -> load them into the player. Same stems with a new
                        // volume/mute -> only update the volumes (no restart).
                        if (stems.map { it.id } != stemPlayer.loadedIds) {
                            stemPlayer.pause()
                            stemPlayer.load(requireContext(), stems)
                            showStopped()
                            seekBar.max = stemPlayer.duration
                            seekBar.progress = 0
                            playButton.isEnabled = stemPlayer.hasStems
                            seekBar.isEnabled = stemPlayer.hasStems
                        } else {
                            stemPlayer.applyVolumes(stems)
                        }
                        emptyMessage.visibility = if (stems.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.isProcessing.collect { busy ->
                        separateProgress.visibility = if (busy) View.VISIBLE else View.GONE
                        separateButton.isEnabled = !busy
                    }
                }
                launch { viewModel.status.collect { separateStatus.text = it } }
                launch {
                    viewModel.error.collect { message ->
                        if (message != null) {
                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                            viewModel.clearError()
                        }
                    }
                }
            }
        }
    }

    /** Frees the audio players when the Mixer view goes away. */
    override fun onDestroyView() {
        progressJob?.cancel()
        stemPlayer.release()
        super.onDestroyView()
    }

    /** Guards against accidental taps: the Save as screen only opens after the user confirms. */
    private fun confirmExport(stem: StemState) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.stem_export_title)
            .setMessage(getString(R.string.stem_export_message, stem.name))
            .setPositiveButton(R.string.stem_export_confirm) { _, _ ->
                val fileName = stem.audioFile?.name ?: "${stem.name}.mp3"
                viewModel.pendingExportId = stem.id
                saveStem.launch(fileName)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}