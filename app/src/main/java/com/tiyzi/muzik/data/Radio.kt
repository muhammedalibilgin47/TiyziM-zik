package com.tiyzi.muzik.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

data class Station(
    val id: String,
    val name: String,
    val url: String,
    val favicon: String?,
    val tags: String,
    val bitrate: Int,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("url", url)
        put("favicon", favicon); put("tags", tags); put("bitrate", bitrate)
    }

    companion object {
        fun fromJson(o: JSONObject) = Station(
            id = o.getString("id"),
            name = o.optString("name"),
            url = o.optString("url"),
            favicon = o.optString("favicon").ifEmpty { null },
            tags = o.optString("tags"),
            bitrate = o.optInt("bitrate"),
        )
    }
}

/**
 * Radio Browser: dünyadaki radyoların ücretsiz, açık listesi. Radyolar kendi yayınlarını
 * herkese açık sunduğu için canlı dinlemek yasaldır.
 */
object RadioApi {
    private val hosts = listOf(
        "https://de1.api.radio-browser.info",
        "https://fi1.api.radio-browser.info",
        "https://de2.api.radio-browser.info",
        "https://all.api.radio-browser.info",
    )

    suspend fun turkish(query: String, tag: String): List<Station> = withContext(Dispatchers.IO) {
        val sb = StringBuilder("/json/stations/search?countrycode=TR&hidebroken=true&order=votes&reverse=true&limit=200")
        if (query.isNotBlank()) sb.append("&name=").append(URLEncoder.encode(query.trim(), "UTF-8"))
        if (tag.isNotBlank()) sb.append("&tag=").append(URLEncoder.encode(tag, "UTF-8"))
        var last: Exception? = null
        for (h in hosts) {
            try {
                return@withContext parse(Http.getText(h + sb))
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException()
    }

    private fun parse(body: String): List<Station> {
        val arr = JSONArray(body)
        val seen = HashSet<String>()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val url = o.optString("url_resolved").ifEmpty { o.optString("url") }
            val name = o.optString("name").trim()
            if (url.isEmpty() || name.isEmpty() || !seen.add(name.lowercase())) return@mapNotNull null
            Station(
                id = o.optString("stationuuid"),
                name = name,
                url = url,
                favicon = o.optString("favicon").ifEmpty { null },
                tags = o.optString("tags").split(',').filter { it.isNotBlank() }.take(3).joinToString(", "),
                bitrate = o.optInt("bitrate"),
            )
        }
    }
}

/** Favori radyolar */
class RadioFavorites(context: Context) {
    private val file = File(context.filesDir, "stations.json")
    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<Station>> = _all

    private fun load(): List<Station> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { Station.fromJson(arr.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun isFavorite(id: String) = _all.value.any { it.id == id }

    @Synchronized
    fun toggle(st: Station) {
        val list = if (isFavorite(st.id)) _all.value.filter { it.id != st.id } else _all.value + st
        _all.value = list
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString())
    }
}
