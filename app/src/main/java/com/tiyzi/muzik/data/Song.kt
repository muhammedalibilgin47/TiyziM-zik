package com.tiyzi.muzik.data

import android.net.Uri
import org.json.JSONObject
import java.io.File

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val durationSec: Int = 0,
    /** İnternetteki kapak resmi */
    val artworkUrl: String? = null,
    /** Ses dosyası: uygulama içi dosya yolu ya da telefondaki müzik için content:// adresi */
    val localAudio: String? = null,
    /** Kapak resmi: dosya yolu ya da content:// adresi */
    val localArt: String? = null,
    val addedAt: Long = 0L,
) {
    /** Audius'tan gelen (internetten indirilebilir) şarkı mı? */
    val isOnlineSource: Boolean get() = !id.startsWith("local_") && !id.startsWith("dev_")

    fun audioUri(): Uri? = localAudio?.let { toUri(it) }

    fun artUri(): Uri? = localArt?.let { toUri(it) } ?: artworkUrl?.let { Uri.parse(it) }

    /** Kapak resmini göstermek için (Coil) */
    fun artModel(): Any? = localArt?.let { if (it.startsWith("/")) File(it) else it } ?: artworkUrl

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("artist", artist)
        put("durationSec", durationSec)
        put("artworkUrl", artworkUrl)
        put("localAudio", localAudio)
        put("localArt", localArt)
        put("addedAt", addedAt)
    }

    companion object {
        private fun toUri(s: String): Uri =
            if (s.startsWith("content://") || s.startsWith("http")) Uri.parse(s) else Uri.fromFile(File(s))

        fun fromJson(o: JSONObject) = Song(
            id = o.getString("id"),
            title = o.optString("title"),
            artist = o.optString("artist"),
            durationSec = o.optInt("durationSec"),
            artworkUrl = o.optString("artworkUrl").ifEmpty { null },
            localAudio = o.optString("localAudio").ifEmpty { null },
            localArt = o.optString("localArt").ifEmpty { null },
            addedAt = o.optLong("addedAt"),
        )
    }
}
