package com.example.aio_musica.library

import android.net.Uri

/**
 * Stores the song's identifying information, metadata, and file location.
 *
 * @property id Unique identifier for the song.
 * @property title Title of the song.
 * @property artist Artist name; defaults to "Unknown Artist".
 * @property album Album name; defaults to "Unknown Album".
 * @property uri URI pointing to the song's file or content location.
 * @property mimeType Media type of the song file, such as "audio/mpeg".
 */

data class Song(
    val id: String,
    var title: String,
    var artist: String = "Unknown Artist",
    var album: String = "Unknown Album",
    val uri: Uri,
    val mimeType: String
)
