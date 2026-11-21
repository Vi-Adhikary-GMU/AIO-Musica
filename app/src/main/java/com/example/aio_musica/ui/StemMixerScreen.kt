package com.example.aio_musica.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aio_musica.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class StemMixerScreen : Fragment() {
    private val viewModel: StemMixerViewModel by activityViewModels()
    private lateinit var stemList: RecyclerView
    private lateinit var adapter: StemAdapter

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

        adapter = StemAdapter(
            onVolumeChange = { id, volume -> viewModel.setVolume(id, volume) },
            onMuteClick = { id -> viewModel.toggleMute(id) },
            onExportClick = { stem -> confirmExport(stem) }
        )
        stemList.layoutManager = LinearLayoutManager(requireContext())
        stemList.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Update the mixer controls and show a message when no stems exist.
                viewModel.stems.collect { stems ->
                    adapter.submitList(stems)

                    emptyMessage.visibility =
                        if (stems.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun confirmExport(stem: StemState) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.stem_export_title)
            .setMessage(getString(R.string.stem_export_message, stem.name))
            .setPositiveButton(R.string.stem_export_confirm) { _, _ ->
                viewModel.exportStem(stem.id)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.stem_export_started, stem.name),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}