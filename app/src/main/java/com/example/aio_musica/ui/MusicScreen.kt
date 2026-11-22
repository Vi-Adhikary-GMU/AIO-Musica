package com.example.aio_musica.ui

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.aio_musica.R
import com.example.aio_musica.library.LibraryViewModel
import com.example.aio_musica.library.Song
import com.google.android.material.tabs.TabLayout

/**
 * Displays the Original Music Player and Mixer tabs.
 *
 * Reads imported songs from the shared LibraryViewModel and
 * plays their local audio files using Android MediaPlayer.
 *
 * Playback resources are released when the Music screen closes.
 */
class MusicScreen : Fragment() {

    private val libraryViewModel: LibraryViewModel by activityViewModels()
    private val stemMixerViewModel: StemMixerViewModel by activityViewModels()

    private var songs: List<Song> = emptyList()
    private var mediaPlayer: MediaPlayer? = null
    private var selectedSongId: String? = null
    private var isSeeking = false

    private lateinit var songPicker: Spinner
    private lateinit var songTitle: TextView
    private lateinit var songArtist: TextView
    private lateinit var songProgress: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var playPauseButton: Button

    private val progressHandler = Handler(Looper.getMainLooper())

    /**
     * Updates the progress bar during playback.
     *
     * Runs approximately every 500 milliseconds and stops
     * automatically when the player is no longer playing.
     */
    private val progressUpdater = object : Runnable {
        override fun run() {
            val player = mediaPlayer ?: return

            if (!player.isPlaying) return

            if (!isSeeking) {
                val position = player.currentPosition
                songProgress.progress = position
                currentTime.text = formatPlaybackTime(position)
            }

            progressHandler.postDelayed(this, 500L)
        }
    }

    /**
     * Creates the Music screen layout.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.music_screen, container, false)
    }

    /**
     * Initializes the tabs and Original Music Player.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabs(view)
        setupPlayer(view)
    }

    /**
     * Configures the existing Original and Mixer tabs.
     *
     * Creates the Mixer fragment once and switches visibility
     * between the two interfaces.
     */
    private fun setupTabs(view: View) {
        val tabs = view.findViewById<TabLayout>(R.id.musicTabs)
        val originalContent = view.findViewById<View>(R.id.originalContent)
        val mixerContainer = view.findViewById<View>(R.id.mixerContainer)

        if (childFragmentManager.findFragmentById(R.id.mixerContainer) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.mixerContainer, StemMixerScreen())
                .commit()
        }

        fun showMixer(show: Boolean) {
            originalContent.visibility = if (show) View.GONE else View.VISIBLE
            mixerContainer.visibility = if (show) View.VISIBLE else View.GONE

            // Pause the original song when opening the Mixer.
            if (show && mediaPlayer?.isPlaying == true) {
                pausePlayback()
            }
        }

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                showMixer(tab.position == 1)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        showMixer(tabs.selectedTabPosition == 1)
    }

    /**
     * Connects the Original player controls to the Library.
     *
     * Reloads the saved songs, populates the song selector,
     * and configures playback buttons and seeking.
     */
    private fun setupPlayer(view: View) {
        songPicker = view.findViewById(R.id.songPicker)
        songTitle = view.findViewById(R.id.songTitle)
        songArtist = view.findViewById(R.id.songArtist)
        songProgress = view.findViewById(R.id.songProgress)
        currentTime = view.findViewById(R.id.currentTime)
        totalTime = view.findViewById(R.id.totalTime)
        playPauseButton = view.findViewById(R.id.playPauseButton)

        val previousButton =
            view.findViewById<Button>(R.id.previousSongButton)
        val nextButton =
            view.findViewById<Button>(R.id.nextSongButton)

        // Load songs previously imported into the Library.
        libraryViewModel.reload()
        songs = libraryViewModel.state.value.songs

        val songNames = songs.map { "${it.title} - ${it.artist}" }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            songNames
        )

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )
        songPicker.adapter = adapter

        // Only enable controls when songs are available.
        songPicker.isEnabled = songs.isNotEmpty()
        previousButton.isEnabled = songs.size > 1
        nextButton.isEnabled = songs.size > 1
        playPauseButton.isEnabled = false
        songProgress.isEnabled = false

        // Prepare the selected song without automatically playing it.
        songPicker.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    selectedView: View?,
                    position: Int,
                    id: Long
                ) {
                    loadSong(position)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        playPauseButton.setOnClickListener {
            togglePlayback()
        }

        previousButton.setOnClickListener {
            if (songs.isNotEmpty()) {
                val index =
                    (songPicker.selectedItemPosition - 1 + songs.size) % songs.size
                songPicker.setSelection(index)
            }
        }

        nextButton.setOnClickListener {
            if (songs.isNotEmpty()) {
                val index =
                    (songPicker.selectedItemPosition + 1) % songs.size
                songPicker.setSelection(index)
            }
        }

        // Allow the user to move to a specific playback position.
        songProgress.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    if (fromUser) {
                        currentTime.text = formatPlaybackTime(progress)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    mediaPlayer?.seekTo(songProgress.progress)
                    isSeeking = false
                }
            }
        )

        // Load the first song when the Library is not empty.
        if (songs.isNotEmpty()) {
            loadSong(0)
        }
    }

    /**
     * Loads a selected Library song into MediaPlayer.
     *
     * Releases the previous player before preparing the new
     * audio file. The song does not begin playing automatically.
     *
     * @param position Index of the song in the Library list.
     */
    private fun loadSong(position: Int) {
        val song = songs.getOrNull(position) ?: return

        // Avoid preparing the same song twice.
        if (selectedSongId == song.id && mediaPlayer != null) {
            return
        }

        releasePlayer()

        songTitle.text = song.title
        songArtist.text = song.artist

        try {
            val player = MediaPlayer.create(requireContext(), song.uri)
                ?: throw IllegalStateException("Unable to open audio file")

            mediaPlayer = player
            selectedSongId = song.id

            // Tell the Mixer which song it can separate.
            stemMixerViewModel.setCurrentSong(song)

            songProgress.max = player.duration.coerceAtLeast(0)
            songProgress.isEnabled = true

            totalTime.text = formatPlaybackTime(player.duration)
            playPauseButton.isEnabled = true

            // Reset controls when playback reaches the end.
            player.setOnCompletionListener {
                progressHandler.removeCallbacks(progressUpdater)
                it.seekTo(0)

                songProgress.progress = 0
                currentTime.text = formatPlaybackTime(0)
                playPauseButton.setText(R.string.player_play)
            }

        } catch (e: Exception) {
            releasePlayer()

            Toast.makeText(
                requireContext(),
                R.string.player_load_error,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Starts or pauses the currently selected song.
     *
     * Updates the button label and playback progress timer.
     */
    private fun togglePlayback() {
        val player = mediaPlayer ?: return

        try {
            if (player.isPlaying) {
                pausePlayback()
            } else {
                player.start()
                playPauseButton.setText(R.string.player_pause)

                progressHandler.removeCallbacks(progressUpdater)
                progressHandler.post(progressUpdater)
            }
        } catch (e: IllegalStateException) {
            releasePlayer()

            Toast.makeText(
                requireContext(),
                R.string.player_load_error,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Pauses playback and stops progress updates.
     *
     * Does not discard the current song or playback position.
     */
    private fun pausePlayback() {
        mediaPlayer?.pause()
        progressHandler.removeCallbacks(progressUpdater)
        playPauseButton.setText(R.string.player_play)
    }

    /**
     * Releases MediaPlayer and resets playback controls.
     *
     * Ensures only one audio player is active at a time.
     */
    private fun releasePlayer() {
        progressHandler.removeCallbacks(progressUpdater)

        mediaPlayer?.release()
        mediaPlayer = null
        selectedSongId = null
        stemMixerViewModel.setCurrentSong(null)

        songProgress.progress = 0
        songProgress.max = 0
        songProgress.isEnabled = false

        currentTime.text = formatPlaybackTime(0)
        totalTime.text = formatPlaybackTime(0)

        playPauseButton.isEnabled = false
        playPauseButton.setText(R.string.player_play)
    }

    /**
     * Cleans up audio resources when leaving the Music screen.
     */
    override fun onDestroyView() {
        releasePlayer()
        super.onDestroyView()
    }
}

/**
 * Formats a playback position in minutes and seconds.
 *
 * @param milliseconds Playback time in milliseconds.
 * @return Formatted time such as "2:15". Negative values become "0:00".
 */
internal fun formatPlaybackTime(milliseconds: Int): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}