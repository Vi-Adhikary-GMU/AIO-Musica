package com.example.aio_musica.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import kotlinx.coroutines.launch

/**
 * The Library tab: a read-only list of every song that has been added from the File tab.
 * The songs come from library.json (through [LibraryViewModel]), so they are still here
 * after the app is closed and reopened.
 */
class LibraryScreen : Fragment() {

    private val viewModel: LibraryViewModel by activityViewModels()

    private lateinit var songList: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var rowAdapter: RowAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        return inflater.inflate(
            R.layout.library_screen,
            container,
            false
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        songList = view.findViewById(R.id.songList)
        emptyText = view.findViewById(R.id.emptyText)

        // No tap handling or action button yet: this tab only displays the songs.
        rowAdapter = RowAdapter(
            actionLabel = null,
            onRowClick = null,
            onActionClick = null
        )
        songList.layoutManager = LinearLayoutManager(requireContext())
        songList.adapter = rowAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The File tab saves new songs straight to disk, so re-read the file when this tab appears.
        viewModel.reload()
    }

    private fun render(state: LibraryState) {
        val rows = state.songs.map { song ->
            Row(
                key = song.id,
                title = song.title,
                subtitle = getString(R.string.library_song_subtitle, song.artist, song.album)
            )
        }
        rowAdapter.submitList(rows)
        emptyText.isVisible = rows.isEmpty()
    }
}
