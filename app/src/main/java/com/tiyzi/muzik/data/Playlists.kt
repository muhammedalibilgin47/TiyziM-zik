package com.tiyzi.muzik.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Playlist(val id: String, val name: String, val songs: List<Song>)

/** Çalma listeleri. "Favoriler" de özel bir listedir. */
class Playlists(context: Context) {

    companion object {
        const val FAVORITES = "fav"
    }

    private val file = File(context.filesDir, "playlists.json")
    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<Playlist>> = _all

    private fun load(): List<Playlist> {
        val list = try {
            if (!file.exists()) emptyList() else {
                val arr = JSONArray(file.readText())
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val songs = o.optJSONArray("songs") ?: JSONArray()
                    Playlist(
                        id = o.getString("id"),
                        name = o.optString("name"),
                        songs = (0 until songs.length()).map { Song.fromJson(songs.getJSONObject(it)) },
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
        return if (list.any { it.id == FAVORITES }) list
        else listOf(Playlist(FAVORITES, "Favoriler", emptyList())) + list
    }

    @Synchronized
    private fun set(list: List<Playlist>) {
        _all.value = list
        val arr = JSONArray()
        list.forEach { p ->
            val songs = JSONArray()
            p.songs.forEach { songs.put(it.toJson()) }
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("songs", songs))
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    private fun modify(id: String, block: (Playlist) -> Playlist) =
        set(_all.value.map { if (it.id == id) block(it) else it })

    fun get(id: String): Playlist? = _all.value.find { it.id == id }

    fun create(name: String): Playlist {
        val p = Playlist(UUID.randomUUID().toString(), name.trim().ifEmpty { "Yeni liste" }, emptyList())
        set(_all.value + p)
        return p
    }

    fun rename(id: String, name: String) = modify(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    fun delete(id: String) {
        if (id == FAVORITES) return
        set(_all.value.filter { it.id != id })
    }

    fun contains(id: String, songId: String) = get(id)?.songs?.any { it.id == songId } == true

    /** @return false: zaten listedeydi */
    fun add(id: String, song: Song): Boolean {
        if (contains(id, song.id)) return false
        modify(id) { it.copy(songs = it.songs + song) }
        return true
    }

    fun remove(id: String, songId: String) = modify(id) { p -> p.copy(songs = p.songs.filter { it.id != songId }) }

    fun isFavorite(songId: String) = contains(FAVORITES, songId)

    fun toggleFavorite(song: Song): Boolean =
        if (isFavorite(song.id)) {
            remove(FAVORITES, song.id); false
        } else {
            add(FAVORITES, song); true
        }

    /** İndirilen/silinen şarkının bilgisini listelerde güncelle */
    fun updateSong(song: Song) {
        if (_all.value.none { p -> p.songs.any { it.id == song.id } }) return
        set(_all.value.map { p -> p.copy(songs = p.songs.map { if (it.id == song.id) song else it }) })
    }
}
