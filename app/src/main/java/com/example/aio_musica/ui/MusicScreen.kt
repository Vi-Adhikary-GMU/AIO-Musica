package com.example.aio_musica.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.aio_musica.R
import com.google.android.material.tabs.TabLayout

/**
 * The Music tab. The Original tab (default) is your existing music_screen layout,
 * included directly in music_host_screen.xml. The Mixer tab shows [StemMixerScreen] instead.
 */
class MusicScreen : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.music_screen, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tabs = view.findViewById<TabLayout>(R.id.musicTabs)
        val originalContent = view.findViewById<View>(R.id.originalContent)
        val mixerContainer = view.findViewById<View>(R.id.mixerContainer)

        // The mixer fragment is added once; the tabs just switch which one is visible.
        if (childFragmentManager.findFragmentById(R.id.mixerContainer) == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.mixerContainer, StemMixerScreen())
                .commit()
        }

        fun showMixer(show: Boolean) {
            originalContent.visibility = if (show) View.GONE else View.VISIBLE
            mixerContainer.visibility = if (show) View.VISIBLE else View.GONE

        }

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showMixer(tab.position == TAB_MIXER)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        // Keep the screen in sync with the highlighted tab (Original by default).
        showMixer(tabs.selectedTabPosition == TAB_MIXER)
    }

    private companion object {
        const val TAB_MIXER = 1
    }
}