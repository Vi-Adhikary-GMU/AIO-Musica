package com.example.aio_musica.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


data class StemState(
    val id: String,
    val name: String,
    val volume: Float = 1f,
    val muted: Boolean = false
)

class StemMixerViewModel : ViewModel() {
    private val _stems = MutableStateFlow(
        listOf("Vocals", "Instrumental", "Guitar", "Bass", "Drums", "Piano", "Other")
            .map { StemState(id = it.lowercase(), name = it) }
    )
    val stems: StateFlow<List<StemState>> = _stems.asStateFlow()
    fun setVolume(id: String, volume: Float) = updateStem(id) {
        it.copy(volume = volume.coerceIn(0f, 1f))
    }
    fun toggleMute(id: String) = updateStem(id) { it.copy(muted = !it.muted) }

    fun exportStem(id: String) {

    }
    private fun updateStem(id: String, change: (StemState) -> StemState) {
        _stems.update { list -> list.map { if (it.id == id) change(it) else it } }
    }
}