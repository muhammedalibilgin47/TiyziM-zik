package com.tiyzi.muzik.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Telefonda kayıtlı tüm müzikleri bulur. */
object DeviceMusic {

    fun permission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permission()) == PackageManager.PERMISSION_GRANTED

    suspend fun scan(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val artBase = Uri.parse("content://media/external/audio/albumart")
        val projection = arrayOf("_id", "title", "artist", "duration", "album_id", "date_added")
        val selection = "is_music != 0 AND duration >= 30000"
        try {
            context.contentResolver.query(base, projection, selection, null, "date_added DESC")?.use { c ->
                val iId = c.getColumnIndexOrThrow("_id")
                val iTitle = c.getColumnIndexOrThrow("title")
                val iArtist = c.getColumnIndexOrThrow("artist")
                val iDur = c.getColumnIndexOrThrow("duration")
                val iAlbum = c.getColumnIndexOrThrow("album_id")
                val iDate = c.getColumnIndexOrThrow("date_added")
                val list = ArrayList<Song>()
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    val artist = c.getString(iArtist)
                    list.add(
                        Song(
                            id = "dev_$id",
                            title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: "Bilinmeyen şarkı",
                            artist = artist?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Bilinmeyen sanatçı",
                            durationSec = (c.getLong(iDur) / 1000).toInt(),
                            localAudio = ContentUris.withAppendedId(base, id).toString(),
                            localArt = ContentUris.withAppendedId(artBase, c.getLong(iAlbum)).toString(),
                            addedAt = c.getLong(iDate) * 1000,
                        )
                    )
                }
                list
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
