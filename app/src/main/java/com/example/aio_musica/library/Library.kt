package com.example.aio_musica.library



class Library {
    //fields:

    // Map where the song ID is the key and the Song object is the value
    private val songs: MutableMap<String, Song> = mutableMapOf()

    // Map of playlists where the playlist name is the key and the Playlist object is the value
    private val playlists: MutableMap<String, Playlist> = mutableMapOf()


    /**
     * Takes in a song object and adds it to the map
     * @param song object
     * @throws IllegalArgumentException when the song is already in the library
     */
    fun addSong(song: Song) {
        if (songs.containsKey(song.id)) {
            throw IllegalArgumentException("Song '${song.id}' is already in the library")
        }
        songs[song.id] = song
    }


    /**
     * Takes in a song object and removes it from the map and will also remove it from any playlists it is in
     * @param song object
     * @throws NoSuchElementException when the song id cant be found in the library
     */
    fun removeSong(song: Song) {
        if (!songs.containsKey(song.id)) {
            throw NoSuchElementException("Song '${song.id}' is not in the library")
        }
        songs.remove(song.id)
        // Remove the song from every playlist that contains it

        for (playlist in playlists.values) {
            if (song.id in playlist.getSongIDs()) {
                playlist.removeSong(song)
            }
        }
    }

    /**
     * Takes in a playlist object and adds it to map
     * @param playlist object
     * @throws IllegalArgumentException when the playlist is already in the library
     */
    fun addPlaylist(playlist: Playlist) {
        val name = playlist.getName()
        if (playlists.containsKey(name)) {
            throw IllegalArgumentException("Playlist '$name' is already in the library")
        }
        playlists[name] = playlist
    }


    /**
     * Takes in playlist object and removes it from map
     * @param playlist object
     * @throws NoSuchElementException when the playlist name cant be found in the library
     */
    fun removePlaylist(playlist: Playlist) {
        val name = playlist.getName()
        if (!playlists.containsKey(name)) {
            throw NoSuchElementException("Playlist '$name' is not in the library")
        }
        playlists.remove(name)
    }

    /**
     * Changes the name of the playlist by calling playlist.setName and updating the map accordingly
     * @param newName String is the new name of the playlist
     * @param playlist the object we will be changing
     * @throws IllegalArgumentException when the new name is already in the map
     * @throws NoSuchElementException when the playlist name cant be found in the library
     */
    fun renamePlaylist(newName: String, playlist: Playlist) {
        val oldName = playlist.getName()
        if (!playlists.containsKey(oldName)) {
            throw NoSuchElementException("Playlist '$oldName' is not in the library")
        }
        if (playlists.containsKey(newName)) {
            throw IllegalArgumentException("Playlist '$newName' is already in the library")
        }
        playlists.remove(oldName)
        playlist.setName(newName)
        playlists[newName] = playlist
    }

    /**
     * Function to clear map of songs
     */
    fun clearSongs() {
        songs.clear()
    }
    /**
     * Function to clear map of playlists
     */
    fun clearPlaylists() {
        playlists.clear()
    }

    /**
     * Getter for songs map
     * @return map of songs
     */
    fun getSongs(): Map<String, Song> = songs.toMap()

    /**
     * Getter for playlist map
     * @return the playlist
     */
    fun getPlaylists(): Map<String, Playlist> = playlists.toMap()
    /**
     * Checks whether a song with the given id is in the library
     * @param songId the id of the song to look for
     * @return true if the song exists in the library, false otherwise
     */
    fun containsSong(songId: String): Boolean = songs.containsKey(songId)

}