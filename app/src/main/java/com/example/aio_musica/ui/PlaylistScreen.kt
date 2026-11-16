package com.example.aio_musica.ui
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

class PlaylistScreen : Fragment() {

    private val viewModel: LibraryViewModel by activityViewModels()

    private lateinit var playlistList: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var newPlaylistButton: MaterialButton

    // Built in onViewCreated because it needs string resources.
    private lateinit var rowAdapter: RowAdapter


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        return inflater.inflate(
            R.layout.playlist_screen,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        playlistList = view.findViewById(R.id.playlistList)
        emptyText = view.findViewById(R.id.emptyText)
        newPlaylistButton = view.findViewById(R.id.newPlaylistButton)

        rowAdapter = RowAdapter(
            actionLabel = getString(R.string.playlist_delete),
            onRowClick = { name -> openPlaylist(name) },
            onActionClick = { name -> confirmDelete(name) }
        )
        playlistList.layoutManager = LinearLayoutManager(requireContext())
        playlistList.adapter = rowAdapter

        newPlaylistButton.setOnClickListener { showCreateDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The File tab may have added songs since we were last visible.
        viewModel.reload()
    }

    private fun render(state: LibraryState) {
        val rows = state.playlists.map { playlist ->
            Row(
                key = playlist.name,
                title = playlist.name,
                subtitle = resources.getQuantityString(
                    R.plurals.playlist_song_count,
                    playlist.songIds.size,
                    playlist.songIds.size
                )
            )
        }
        rowAdapter.submitList(rows)
        emptyText.isVisible = rows.isEmpty()
    }

    private fun openPlaylist(name: String) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, SpecificPlaylistScreen.newInstance(name))
            .addToBackStack(null)
            .commit()
    }

    /**
     * Asks for a name. The dialog stays open if the name is empty or already used,
     * so the user can fix it without starting over.
     */
    private fun showCreateDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.playlist_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
        }
        val padding = (20 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(requireContext()).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }

        val dialog: AlertDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.playlist_new)
            .setView(container)
            .setPositiveButton(R.string.playlist_create, null) // Click handled below to control closing.
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val error = viewModel.createPlaylist(input.text.toString())
                if (error == null) {
                    dialog.dismiss()
                } else {
                    input.error = error
                }
            }
        }
        dialog.show()
    }

    private fun confirmDelete(name: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.playlist_delete_title)
            .setMessage(getString(R.string.playlist_delete_message, name))
            .setPositiveButton(R.string.playlist_delete) { _, _ ->
                viewModel.deletePlaylist(name)?.let { showMessage(it) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showMessage(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }


}