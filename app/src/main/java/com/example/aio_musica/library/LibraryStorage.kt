package com.example.aio_musica.library


import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.sql.Timestamp

class LibraryStorage(context: Context) {

    private val file = File(context.filesDir, "library.json")

    /**
     * Saves the library to a JSON File
     * @param library is the library object we need to save
     */
    fun save(library: Library) {
        val data = JSONObject()
        val songs = JSONArray()
        val playlists = JSONArray()

        for (song in library.getSongs().values) {
            val item = JSONObject()

            item.put("id", song.id)
            item.put("title", song.title)
            item.put("artist", song.artist)
            item.put("album", song.album)
            item.put("uri", song.uri.toString())
            item.put("mimeType", song.mimeType)

            songs.put(item)
        }

        for (playlist in library.getPlaylists().values) {
            val item = JSONObject()
            val songIDs = JSONArray()

            item.put("name", playlist.getName())
            item.put("date", playlist.getDateCreated().time)

            for (id in playlist.getSongIDs()) {
                songIDs.put(id)
            }

            item.put("songIDs", songIDs)
            playlists.put(item)
        }

        data.put("songs", songs)
        data.put("playlists", playlists)

        // Write to a temporary file first and then swap it in, so a crash or a full disk
        // halfway through saving can never leave a corrupted library.json behind.
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(data.toString(2))
        if (!temp.renameTo(file)) {
            file.delete()
            if (!temp.renameTo(file)) {
                temp.delete()
                throw java.io.IOException("Couldn't save the library to ${file.absolutePath}")
            }
        }
    }

    /**
     * Going to load the JSON file back into our library object
     * @return a library object with the saved values from previous runs.
     */
    fun load(): Library {
        val library = Library()

        if (!file.exists()) return library

        val data = JSONObject(file.readText())
        val songs = data.getJSONArray("songs")
        val playlists = data.getJSONArray("playlists")

        // Load songs first
        for (i in 0 until songs.length()) {
            val item = songs.getJSONObject(i)
            val uri = Uri.parse(item.getString("uri"))

            val song = Song(
                id = item.getString("id"),
                title = item.getString("title"),
                artist = item.getString("artist"),
                album = item.getString("album"),
                uri = uri,
                mimeType = item.getString("mimeType")
            )

            library.addSong(song)
        }

        // Then load playlists
        for (i in 0 until playlists.length()) {
            val item = playlists.getJSONObject(i)

            val playlist = Playlist(
                item.getString("name"),
                library,
                Timestamp(item.getLong("date"))
            )

            library.addPlaylist(playlist)

            val songIDs = item.getJSONArray("songIDs")

            for (j in 0 until songIDs.length()) {
                val song = library.getSongs()[songIDs.getString(j)]

                if (song != null) {
                    playlist.addSong(song)
                }
            }
        }

        return library
    }
}
