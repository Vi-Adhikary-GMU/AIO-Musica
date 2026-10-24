package com.example.aio_musica.library
import java.sql.Timestamp

/**
 * Some things to note / Design consideration. We should never call setName on a playlist object directly but should always do it through library object.
 */

class Playlist(

    private var name: String,
    private val library: Library,
    // No setter: only set when the playlist object is created
    private val dateCreated: Timestamp = Timestamp(System.currentTimeMillis()),


) {
    // Always starts empty, No public setter: the list can only be changed through addSong, removeSong, and reset
    private val songIDs: MutableList<String> = mutableListOf()
    /**
     * Function will add a song's ID to the songIDs list
     * @param song a song object
     * @throws NoSuchElementException when the song is not in the library
     * @throws IllegalArgumentException when the song is already in the playlist
     */
    fun addSong(song: Song) {
        if (!library.containsSong(song.id)) {
            throw NoSuchElementException("Song '${song.id}' is not in the library")
        }
        if (songIDs.contains(song.id)) {
            throw IllegalArgumentException("Song '${song.id}' is already in playlist '$name'")
        }
        songIDs.add(song.id)
    }

    /**
     * Function to remove a song ID from the songIDs list
     * @param song a song object
     * @throws NoSuchElementException when the song id cant be found in the list
     */
    fun removeSong(song: Song) {
        if (!songIDs.contains(song.id)) {
            throw NoSuchElementException("Song ${song.id} is not in playlist '$name'")
        }
        songIDs.remove(song.id)
    }

    /**
     * reset function clears the entire list of songIDs
     */
    fun reset() {
        songIDs.clear()
    }

    /**
     * Getter to get the name of the playlist
     */
    fun getName(): String = name

    /**
     * sets the name of the playlist in case the user wants to change anything
     * @param newName the new name of the playlist
     */
    fun setName(newName: String) {
        name = newName
    }

    /**
     * A way to get when the playlist was created
     */
    fun getDateCreated(): Timestamp = dateCreated

    /**
     * A way to get back all the songIDs
     */
    fun getSongIDs(): List<String> = songIDs.toList()
}
