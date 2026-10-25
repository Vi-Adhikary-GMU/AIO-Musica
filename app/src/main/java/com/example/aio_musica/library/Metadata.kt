package com.example.aio_musica.library

import android.net.Uri

class Metadata(
    private val uri: Uri
) {
    private val id: String = "-1"
    var title: String = "Unknown Title"
    var artist: String = "Unknown Artist"
    var album: String = "Unknown Album"
    val mimeType: String = "audio/mpeg"

    fun getTitle(): String {
        // parse title :)
        return title
    }
    fun getArtist(): String {
        return artist
    }
    fun getAlbum(): String {
        return album
    }

    fun generateSong(): Song {
        val song = Song(
            id = id,
            title = title,
            uri = uri,
            mimeType = mimeType
        )
        return song
    }
}