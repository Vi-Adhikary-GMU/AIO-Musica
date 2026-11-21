package com.example.aio_musica.library

import android.media.MediaMetadataRetriever
import android.net.Uri
import java.util.UUID

/**
 * Extracts song metadata from an audio file using its URI.
 * Stores the song's title, artist, album, and a unique ID.
 *
 * If metadata cannot be found, default values are used.
 *
 * @param uri the URI of the audio file
 * @param fallbackTitle the default title to use if no title is found
 * @param mimeType the MIME type of the audio file
 */

class Metadata(
    private val uri: Uri,
    private val fallbackTitle: String = "Unknown Title",
    val mimeType: String = "audio/mpeg"
) {
    // Every song needs its own id: Library.addSong rejects duplicates, so a shared id
    // meant only one song could ever be imported.
    private val id: String = UUID.randomUUID().toString()

    //the title for the song
    @get:JvmName("titleValue")
    var title: String = fallbackTitle

    //the artist
    @get:JvmName("artistValue")
    var artist: String = "Unknown Artist"

    //the album
    @get:JvmName("albumValue")
    var album: String = "Unknown Album"
    

    init{
        extractMetadata()
    }

    /**
     * Extracts the title, artist, and album from the audio file.
     * Uses default values when metadata is unavailable.
     */
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

    /**
     * Getter for title
     * @return title of song
     */
    fun getTitle(): String {
        return title
    }
    /**
     * Getter for artist
     * @return artist for song
     */
    fun getArtist(): String {
        return artist
    }
    /**
     * Getter for album
     * @return album
     */
    fun getAlbum(): String {
        return album
    }

    /**
     * Creates a Song object using the extracted metadata.
     * @return a Song containing the song's ID, metadata, URI, and MIME type
     */
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