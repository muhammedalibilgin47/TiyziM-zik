package com.tiyzi.muzik.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.tiyzi.muzik.data.AudiusApi
import com.tiyzi.muzik.data.DeviceMusic
import com.tiyzi.muzik.data.Library
import com.tiyzi.muzik.data.Playlist
import com.tiyzi.muzik.data.Playlists
import com.tiyzi.muzik.data.RadioApi
import com.tiyzi.muzik.data.RadioFavorites
import com.tiyzi.muzik.data.Song
import com.tiyzi.muzik.data.Station
import com.tiyzi.muzik.playback.PlaybackService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlayerUi(
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val artwork: Uri? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
) {
    val isRadio: Boolean get() = mediaId?.startsWith(RADIO_PREFIX) == true
}

data class QueueItem(val id: String, val title: String, val artist: String)

const val RADIO_PREFIX = "radio_"

class MusicViewModel(app: Application) : AndroidViewModel(app) {

    private val library = Library(app)
    private val playlistStore = Playlists(app)
    private val radioFavs = RadioFavorites(app)
    private val settings = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ---- Ayarlar ----
    var glass by mutableStateOf(settings.getBoolean("glass", true))
        private set

    fun updateGlass(on: Boolean) {
        glass = on
        settings.edit().putBoolean("glass", on).apply()
    }

    // ---- Kitaplık ----
    var downloaded by mutableStateOf<List<Song>>(emptyList())
        private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    val favorites: List<Song> get() = playlists.find { it.id == Playlists.FAVORITES }?.songs ?: emptyList()
    var deviceSongs by mutableStateOf<List<Song>>(emptyList())
        private set
    var deviceScanned by mutableStateOf(false)
        private set
    var deviceScanning by mutableStateOf(false)
        private set

    // ---- Arama ----
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Song>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var trending by mutableStateOf<List<Song>>(emptyList())
        private set
    val downloads = mutableStateMapOf<String, Float>()

    // ---- Radyo ----
    var radioQuery by mutableStateOf("")
    var radioTag by mutableStateOf("")
    var stations by mutableStateOf<List<Station>>(emptyList())
        private set
    var radioLoading by mutableStateOf(false)
        private set
    var radioError by mutableStateOf<String?>(null)
        private set
    var favStations by mutableStateOf<List<Station>>(emptyList())
        private set

    // ---- Oynatıcı ----
    var player by mutableStateOf(PlayerUi())
        private set
    var queue by mutableStateOf<List<QueueItem>>(emptyList())
        private set
    var queueIndex by mutableStateOf(0)
        private set

    // ---- Diyaloglar ----
    var pickPlaylistFor by mutableStateOf<Song?>(null)
    var confirmDelete by mutableStateOf<Song?>(null)

    val messages = Channel<String>(Channel.BUFFERED)

    private var controller: MediaController? = null
    private val controllerFuture: ListenableFuture<MediaController>
    private var searchJob: Job? = null
    private var radioJob: Job? = null

    init {
        viewModelScope.launch { library.songs.collect { downloaded = it } }
        viewModelScope.launch { playlistStore.all.collect { playlists = it } }
        viewModelScope.launch { radioFavs.all.collect { favStations = it } }

        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(app, token).buildAsync()
        controllerFuture.addListener({
            val c = try {
                controllerFuture.get()
            } catch (e: Exception) {
                return@addListener
            }
            controller = c
            c.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                        rebuildQueue()
                    }
                    refresh()
                }

                override fun onPlayerError(error: PlaybackException) {
                    message("Çalınamadı. İnternet bağlantınızı kontrol edin.")
                }
            })
            rebuildQueue()
            refresh()
        }, ContextCompat.getMainExecutor(app))

        // Çalarken ilerleme çubuğunu güncelle
        viewModelScope.launch {
            while (isActive) {
                delay(500)
                if (controller?.isPlaying == true) refresh()
            }
        }

        loadTrending()
        if (DeviceMusic.hasPermission(app)) scanDevice()
    }

    private fun refresh() {
        val c = controller ?: return
        val md = c.mediaMetadata
        player = PlayerUi(
            mediaId = c.currentMediaItem?.mediaId,
            title = md.title?.toString() ?: "",
            artist = md.artist?.toString() ?: "",
            artwork = md.artworkUri,
            isPlaying = c.isPlaying,
            buffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition,
            durationMs = c.duration.takeIf { it > 0 } ?: 0L,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            speed = c.playbackParameters.speed,
        )
    }

    private fun rebuildQueue() {
        val c = controller ?: return
        queue = (0 until c.mediaItemCount).map { i ->
            val m = c.getMediaItemAt(i)
            QueueItem(m.mediaId, m.mediaMetadata.title?.toString() ?: "", m.mediaMetadata.artist?.toString() ?: "")
        }
        queueIndex = c.currentMediaItemIndex
    }

    fun message(text: String) {
        messages.trySend(text)
    }

    // ================= Arama ve indirme =================

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searching = true
            error = null
            try {
                results = AudiusApi.search(q)
                if (results.isEmpty()) error = "\"$q\" için sonuç bulunamadı."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Arama yapılamadı. İnternet bağlantınızı kontrol edin."
            } finally {
                if (isActive) searching = false
            }
        }
    }

    fun searchFor(text: String) {
        query = text
        search()
    }

    fun clearSearch() {
        searchJob?.cancel()
        query = ""
        results = emptyList()
        error = null
        searching = false
    }

    fun loadTrending() {
        viewModelScope.launch {
            try {
                trending = AudiusApi.trending().take(30)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun isDownloaded(id: String) = downloaded.any { it.id == id }

    fun canDownload(song: Song) = song.isOnlineSource && !isDownloaded(song.id)

    fun download(song: Song) {
        if (!canDownload(song) || downloads.containsKey(song.id)) return
        downloads[song.id] = 0f
        viewModelScope.launch {
            try {
                val saved = library.download(song) { p ->
                    viewModelScope.launch {
                        if (downloads.containsKey(song.id)) downloads[song.id] = p
                    }
                }
                playlistStore.updateSong(saved)
                message("İndirildi: ${song.title}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message("İndirilemedi: ${song.title}")
            } finally {
                downloads.remove(song.id)
            }
        }
    }

    fun downloadAll(songs: List<Song>) {
        val todo = songs.filter { canDownload(it) }
        if (todo.isEmpty()) {
            message("Hepsi zaten indirilmiş")
            return
        }
        todo.forEach { download(it) }
        message("${todo.size} şarkı indiriliyor")
    }

    fun importFiles(uris: List<Uri>) {
        viewModelScope.launch {
            val n = library.importFromPhone(uris)
            message(if (n > 0) "$n şarkı eklendi" else "Dosyalar eklenemedi")
        }
    }

    fun scanDevice() {
        if (deviceScanning) return
        viewModelScope.launch {
            deviceScanning = true
            deviceSongs = DeviceMusic.scan(getApplication<Application>())
            deviceScanned = true
            deviceScanning = false
        }
    }

    fun delete(song: Song) {
        removeFromQueueById(song.id)
        library.delete(song)
        // Listelerde kalsın ama artık internetten çalınsın
        if (song.isOnlineSource) playlistStore.updateSong(song.copy(localAudio = null, localArt = null))
        message("Silindi: ${song.title}")
    }

    // ================= Çalma listeleri =================

    fun isFavorite(id: String) = favorites.any { it.id == id }

    fun toggleFavorite(song: Song) {
        val added = playlistStore.toggleFavorite(song)
        message(if (added) "Favorilere eklendi" else "Favorilerden çıkarıldı")
    }

    fun toggleFavoriteCurrent() {
        val id = player.mediaId ?: return
        findSong(id)?.let { toggleFavorite(it) }
    }

    fun createPlaylist(name: String): Playlist = playlistStore.create(name)

    fun renamePlaylist(id: String, name: String) = playlistStore.rename(id, name)

    fun deletePlaylist(id: String) = playlistStore.delete(id)

    fun addToPlaylist(playlistId: String, song: Song) {
        val name = playlistStore.get(playlistId)?.name ?: ""
        message(if (playlistStore.add(playlistId, song)) "\"$name\" listesine eklendi" else "Zaten \"$name\" listesinde")
    }

    fun removeFromPlaylist(playlistId: String, songId: String) = playlistStore.remove(playlistId, songId)

    /** Çalan şarkının bilgisini bul */
    fun findSong(id: String): Song? =
        downloaded.find { it.id == id }
            ?: deviceSongs.find { it.id == id }
            ?: results.find { it.id == id }
            ?: trending.find { it.id == id }
            ?: playlists.firstNotNullOfOrNull { p -> p.songs.find { it.id == id } }

    // ================= Radyo =================

    fun loadRadio() {
        radioJob?.cancel()
        radioJob = viewModelScope.launch {
            radioLoading = true
            radioError = null
            try {
                stations = RadioApi.turkish(radioQuery, radioTag)
                if (stations.isEmpty()) radioError = "Radyo bulunamadı."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                radioError = "Radyolar yüklenemedi. İnternet bağlantınızı kontrol edin."
            } finally {
                if (isActive) radioLoading = false
            }
        }
    }

    fun isFavStation(id: String) = favStations.any { it.id == id }

    fun toggleFavStation(st: Station) = radioFavs.toggle(st)

    fun playStation(st: Station) {
        val c = controller ?: return notReady()
        val uri = Uri.parse(st.url)
        val mime = if (st.url.contains(".m3u8", ignoreCase = true)) MimeTypes.APPLICATION_M3U8 else null
        val extras = Bundle().apply { mime?.let { putString(PlaybackService.EXTRA_MIME, it) } }
        val item = MediaItem.Builder()
            .setMediaId(RADIO_PREFIX + st.id)
            .setUri(uri)
            .setMimeType(mime)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).setExtras(extras).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(st.name)
                    .setArtist("Canlı radyo")
                    .setArtworkUri(st.favicon?.let { Uri.parse(it) })
                    .build()
            )
            .build()
        c.setMediaItem(item)
        c.prepare()
        c.play()
    }

    // ================= Çalma =================

    private fun notReady() = message("Oynatıcı hazırlanıyor, tekrar deneyin")

    /** Şarkının en güncel hâli: indirildiyse internetsiz, silindiyse internetten. */
    private fun resolve(s: Song, downloadedMap: Map<String, Song>): Song {
        downloadedMap[s.id]?.let { return it }
        val p = s.localAudio
        if (p != null && p.startsWith("/") && !File(p).exists() && s.isOnlineSource) {
            return s.copy(localAudio = null, localArt = null)
        }
        return s
    }

    private fun Song.toMediaItem(): MediaItem {
        val uri = audioUri() ?: Uri.parse(AudiusApi.streamUrl(id))
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(artUri())
                    .build()
            )
            .build()
    }

    private fun itemsFor(list: List<Song>): List<MediaItem> {
        val map = downloaded.associateBy { it.id }
        return list.map { resolve(it, map).toMediaItem() }
    }

    /** shuffle = null ise mevcut karışık çalma ayarı korunur. */
    fun playList(list: List<Song>, index: Int, shuffle: Boolean? = null) {
        val c = controller ?: return notReady()
        if (list.isEmpty()) return
        c.setMediaItems(itemsFor(list), index.coerceIn(0, list.lastIndex), 0L)
        shuffle?.let { c.shuffleModeEnabled = it }
        c.prepare()
        c.play()
    }

    fun shuffleAll(list: List<Song>) {
        if (list.isNotEmpty()) playList(list, list.indices.random(), shuffle = true)
    }

    fun playNext(song: Song) {
        val c = controller ?: return notReady()
        if (c.mediaItemCount == 0 || player.isRadio) {
            playList(listOf(song), 0)
            return
        }
        c.addMediaItem(c.currentMediaItemIndex + 1, itemsFor(listOf(song)).first())
        message("Sıradaki: ${song.title}")
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return notReady()
        if (c.mediaItemCount == 0 || player.isRadio) {
            playList(listOf(song), 0)
            return
        }
        c.addMediaItem(itemsFor(listOf(song)).first())
        message("Sıraya eklendi: ${song.title}")
    }

    fun jumpTo(index: Int) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        c.seekTo(index, 0L)
        c.play()
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    private fun removeFromQueueById(id: String) {
        val c = controller ?: return
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).mediaId == id) c.removeMediaItem(i)
        }
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            when (c.playbackState) {
                Player.STATE_IDLE -> c.prepare()
                Player.STATE_ENDED -> c.seekTo(c.currentMediaItemIndex, 0L)
                else -> Unit
            }
            c.play()
        }
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        refresh()
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        refresh()
    }

    override fun onCleared() {
        MediaController.releaseFuture(controllerFuture)
        super.onCleared()
    }
}
