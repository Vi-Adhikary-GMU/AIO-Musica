package com.example.aio_musica.ui

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

/**
 * Plays all separated stems at the same time, one MediaPlayer per stem.
 * Each stem's volume comes from its [StemState] (muted = silent), so moving a slider
 * or tapping Mute changes what you hear right away.
 *
 * Note: MediaPlayers are started one after another, so stems can drift by a few
 * milliseconds. That is fine for a first version.
 */
class StemPlayer {

    private val players = LinkedHashMap<String, MediaPlayer>()

    /** Called when the song reaches its end (after the player has reset to 0:00). */
    var onCompleted: (() -> Unit)? = null

    val hasStems: Boolean get() = players.isNotEmpty()

    val isPlaying: Boolean
        get() = players.values.firstOrNull()?.isPlaying == true

    /** Length of the longest stem in milliseconds. */
    val duration: Int
        get() {
            var longest = 0
            for (mediaPlayer in players.values) {
                val length = mediaPlayer.duration
                if (length > longest) longest = length
            }
            return longest
        }

    val position: Int
        get() = players.values.firstOrNull()?.currentPosition ?: 0

    /** The ids of the stems currently loaded, used to tell when the list really changed. */
    val loadedIds: List<String> get() = players.keys.toList()

    /** Replaces the loaded stems. Stems without an audio file are skipped. */
    fun load(context: Context, stems: List<StemState>) {
        release()
        for (stem in stems) {
            val file = stem.audioFile ?: continue
            val player = MediaPlayer.create(context, Uri.fromFile(file)) ?: continue
            players[stem.id] = player
        }
        // Reset everything when the first stem ends.
        players.values.firstOrNull()?.setOnCompletionListener {
            pause()
            seekTo(0)
            onCompleted?.invoke()
        }
        applyVolumes(stems)
    }

    /** Sets every player's volume from the current stem states. */
    fun applyVolumes(stems: List<StemState>) {
        for (stem in stems) {
            val level = if (stem.muted) 0f else stem.volume.coerceIn(0f, 1f)
            players[stem.id]?.setVolume(level, level)
        }
    }

    fun play() {
        players.values.forEach { it.start() }
    }

    fun pause() {
        players.values.forEach { if (it.isPlaying) it.pause() }
    }

    fun seekTo(ms: Int) {
        players.values.forEach { it.seekTo(ms) }
    }

    fun release() {
        players.values.forEach { it.release() }
        players.clear()
    }
}