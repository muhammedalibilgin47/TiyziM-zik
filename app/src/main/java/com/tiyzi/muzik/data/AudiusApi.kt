package com.tiyzi.muzik.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Audius: sanatçıların müziklerini ücretsiz ve yasal olarak paylaştığı açık müzik platformu.
 * API anahtarı gerektirmez.
 */
object AudiusApi {
    private const val APP_NAME = "TiyziMuzik"
    private const val FALLBACK_HOST = "https://api.audius.co"

    @Volatile
    private var host: String? = null

    private fun resolveHost(): String {
        host?.let { return it }
        val h = try {
            val arr = JSONObject(Http.getText(FALLBACK_HOST)).getJSONArray("data")
            if (arr.length() > 0) arr.getString((0 until arr.length()).random()) else FALLBACK_HOST
        } catch (e: Exception) {
            FALLBACK_HOST
        }
        host = h
        return h
    }

    private fun fetch(path: String): String = try {
        Http.getText(resolveHost() + path)
    } catch (e: IOException) {
        host = FALLBACK_HOST
        Http.getText(FALLBACK_HOST + path)
    }

    private fun parse(body: String): List<Song> {
        val data = JSONObject(body).getJSONArray("data")
        return (0 until data.length()).mapNotNull { i ->
            val t = data.getJSONObject(i)
            if (!t.optBoolean("is_streamable", true)) return@mapNotNull null
            val art = t.optJSONObject("artwork")
            Song(
                id = t.getString("id"),
                title = t.optString("title"),
                artist = t.optJSONObject("user")?.optString("name") ?: "",
                durationSec = t.optInt("duration"),
                artworkUrl = art?.optString("480x480")?.ifEmpty { null }
                    ?: art?.optString("150x150")?.ifEmpty { null },
            )
        }
    }

    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query, "UTF-8")
        parse(fetch("/v1/tracks/search?query=$q&app_name=$APP_NAME"))
    }

    suspend fun trending(): List<Song> = withContext(Dispatchers.IO) {
        parse(fetch("/v1/tracks/trending?time=week&app_name=$APP_NAME"))
    }

    fun streamUrl(id: String): String =
        "${host ?: FALLBACK_HOST}/v1/tracks/$id/stream?app_name=$APP_NAME"
}
