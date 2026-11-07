package com.tiyzi.muzik.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** İndirilen şarkıları telefonun içinde (uygulamaya özel klasörde) saklar. */
class Library(private val context: Context) {

    private val musicDir = File(context.filesDir, "music").apply { mkdirs() }
    private val artDir = File(context.filesDir, "art").apply { mkdirs() }
    private val indexFile = File(context.filesDir, "library.json")
    private val lock = Any()

    private val _songs = MutableStateFlow(load())
    val songs: StateFlow<List<Song>> = _songs

    private fun load(): List<Song> = try {
        if (!indexFile.exists()) emptyList() else {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length())
                .map { Song.fromJson(arr.getJSONObject(it)) }
                .filter { s -> s.localAudio?.let { File(it).exists() } == true }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun save(list: List<Song>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        val tmp = File(indexFile.path + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(indexFile)
    }

    private fun add(song: Song) = synchronized(lock) {
        val newList = listOf(song) + _songs.value.filter { it.id != song.id }
        _songs.value = newList
        save(newList)
    }

    fun delete(song: Song) = synchronized(lock) {
        song.localAudio?.let { File(it).delete() }
        song.localArt?.let { File(it).delete() }
        val newList = _songs.value.filter { it.id != song.id }
        _songs.value = newList
        save(newList)
    }

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    /** Şarkıyı internetten indirir; bitince internetsiz çalınabilir. */
    suspend fun download(song: Song, onProgress: (Float) -> Unit): Song = withContext(Dispatchers.IO) {
        val name = safe(song.id)
        val out = File(musicDir, "$name.mp3")
        val part = File(musicDir, "$name.part")
        httpToFile(AudiusApi.streamUrl(song.id), part, onProgress)
        if (!part.renameTo(out)) throw IOException("Dosya kaydedilemedi")

        var art: String? = null
        song.artworkUrl?.let { url ->
            try {
                val f = File(artDir, "$name.jpg")
                httpToFile(url, f) {}
                art = f.absolutePath
            } catch (_: Exception) {
            }
        }
        val saved = song.copy(localAudio = out.absolutePath, localArt = art, addedAt = System.currentTimeMillis())
        add(saved)
        saved
    }

    private fun httpToFile(url: String, file: File, onProgress: (Float) -> Unit) {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            if (c.responseCode !in 200..299) throw IOException("İndirme hatası: ${c.responseCode}")
            val total = c.contentLengthLong
            c.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - last > 100_000) {
                            last = done
                            onProgress(done.toFloat() / total)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            c.disconnect()
        }
    }

    /** Telefondaki müzik dosyalarını kitaplığa kopyalar. */
    suspend fun importFromPhone(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (uri in uris) {
            try {
                val id = "local_" + UUID.randomUUID().toString().replace("-", "").take(12)
                val fileName = queryName(uri) ?: "Şarkı.mp3"
                val ext = fileName.substringAfterLast('.', "mp3").take(5)
                val out = File(musicDir, "$id.$ext")
                val input = context.contentResolver.openInputStream(uri) ?: continue
                input.use { i -> out.outputStream().use { o -> i.copyTo(o) } }

                var title: String? = null
                var artist: String? = null
                var duration = 0
                var art: String? = null
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(out.absolutePath)
                    title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    duration = ((r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L) / 1000).toInt()
                    r.embeddedPicture?.let { bytes ->
                        val f = File(artDir, "$id.jpg")
                        f.writeBytes(bytes)
                        art = f.absolutePath
                    }
                } catch (_: Exception) {
                } finally {
                    try { r.release() } catch (_: Exception) { }
                }

                add(
                    Song(
                        id = id,
                        title = title?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.'),
                        artist = artist?.takeIf { it.isNotBlank() } ?: "Telefonumdan",
                        durationSec = duration,
                        localAudio = out.absolutePath,
                        localArt = art,
                        addedAt = System.currentTimeMillis(),
                    )
                )
                count++
            } catch (_: Exception) {
            }
        }
        count
    }

    private fun queryName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}
