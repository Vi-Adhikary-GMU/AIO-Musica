package com.example.aio_musica.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aio_musica.R
import com.example.aio_musica.library.LibraryState
import com.example.aio_musica.library.LibraryViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Shows the songs inside one playlist and lets the user add songs from the library
 * or remove songs from the playlist. Create it with [newInstance].
 */
class SpecificPlaylistScreen : Fragment() {

    private val viewModel: LibraryViewModel by activityViewModels()

    /** Playlists are identified by name in [com.example.aio_musica.library.Library]. */
    private val playlistName: String by lazy { requireArguments().getString(ARG_NAME).orEmpty() }

    private lateinit var titleText: TextView
    private lateinit var songList: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var addSongsButton: MaterialButton
    private lateinit var rowAdapter: RowAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(
            R.layout.specific_playlist_screen,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        titleText = view.findViewById(R.id.playlistTitle)
        songList = view.findViewById(R.id.songList)
        emptyText = view.findViewById(R.id.emptyText)
        addSongsButton = view.findViewById(R.id.addSongsButton)

        titleText.text = playlistName

        rowAdapter = RowAdapter(
            actionLabel = getString(R.string.playlist_remove_song),
            onRowClick = null,
            onActionClick = { songId ->
                viewModel.removeSong(playlistName, songId)?.let { showMessage(it) }
            }
        )
        songList.layoutManager = LinearLayoutManager(requireContext())
        songList.adapter = rowAdapter

        addSongsButton.setOnClickListener { showAddSongsDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: LibraryState) {
        val playlist = state.playlists.firstOrNull { it.name == playlistName }
        if (playlist == null) {
            // The playlist no longer exists (e.g. it was deleted), so go back to the list.
            parentFragmentManager.popBackStack()
            return
        }

        val songsById = state.songs.associateBy { it.id }
        val rows = playlist.songIds.mapNotNull { id ->
            songsById[id]?.let { song ->
                Row(key = song.id, title = song.title, subtitle = song.artist)
            }
        }
        rowAdapter.submitList(rows)
        emptyText.isVisible = rows.isEmpty()
    }

    /**
     * Lists every library song that isn't in this playlist yet, with check boxes,
     * and adds the ticked ones.
     */
    private fun showAddSongsDialog() {
        val state = viewModel.state.value
        val playlist = state.playlists.firstOrNull { it.name == playlistName } ?: return

        if (state.songs.isEmpty()) {
            showMessage(getString(R.string.playlist_library_empty))
            return
        }

        val available = state.songs.filter { it.id !in playlist.songIds }
        if (available.isEmpty()) {
            showMessage(getString(R.string.playlist_all_songs_added))
            return
        }

        val labels = available.map { "${it.title} - ${it.artist}" }.toTypedArray()
        val checked = BooleanArray(available.size)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.playlist_add_songs)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(R.string.playlist_add) { _, _ ->
                val ids = available.filterIndexed { index, _ -> checked[index] }.map { it.id }
                if (ids.isNotEmpty()) {
                    viewModel.addSongs(playlistName, ids)?.let { showMessage(it) }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val ARG_NAME = "playlist_name"

        /** Creates the screen for the playlist with the given name. */
        fun newInstance(playlistName: String): SpecificPlaylistScreen {
            return SpecificPlaylistScreen().apply {
                arguments = bundleOf(ARG_NAME to playlistName)
            }
        }
    }
}
