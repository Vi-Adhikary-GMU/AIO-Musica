package com.example.aio_musica.library

import android.media.MediaMetadataRetriever
import android.net.Uri
import java.util.UUID

class Metadata(
    private val uri: Uri,
    private val fallbackTitle: String = "Unknown Title",
    val mimeType: String = "audio/mpeg"
) {
    // Every song needs its own id: Library.addSong rejects duplicates, so a shared id
    // meant only one song could ever be imported.
    private val id: String = UUID.randomUUID().toString()

    @get:JvmName("titleValue")
    var title: String = fallbackTitle

    @get:JvmName("artistValue")
    var artist: String = "Unknown Artist"

    @get:JvmName("albumValue")
    var album: String = "Unknown Album"
    

    init{
        extractMetadata()
    }

    private fun extractMetadata()
    {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(uri.path!!)

            title = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_TITLE
            ) ?.takeIf { it.isNotBlank() } ?: fallbackTitle

            artist = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_ARTIST
            ) ?: "Unknown Artist"

            album = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_ALBUM
            ) ?: "Unknown Album"

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            retriever.release()
        }
    }

    fun getTitle(): String {
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
            artist = artist,
            album = album,
            uri = uri,
            mimeType = mimeType
        )
        return song
    }
}