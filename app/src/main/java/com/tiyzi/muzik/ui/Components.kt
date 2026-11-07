package com.tiyzi.muzik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tiyzi.muzik.data.Playlists
import com.tiyzi.muzik.data.Song
import java.io.File
import java.net.URLEncoder

@Composable
fun Artwork(model: Any?, modifier: Modifier, corner: Dp = 8.dp, fallback: ImageVector = Icons.Rounded.MusicNote) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(fallback, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (model != null) {
            AsyncImage(
                model = if (model is String && model.startsWith("/")) File(model) else model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun CenterText(text: String, modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
fun SongRow(song: Song, playing: Boolean, onClick: () -> Unit, trailing: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song.artModel(), Modifier.size(52.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playing) {
                    Icon(
                        Icons.Rounded.GraphicEq, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp),
                    )
                }
                Text(
                    song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            val sub = listOfNotNull(
                song.artist.ifBlank { null },
                if (song.durationSec > 0) formatSec(song.durationSec) else null,
            ).joinToString(" • ")
            Text(
                sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

/** İndirme düğmesi / ilerlemesi / indirildi işareti */
@Composable
fun DownloadIndicator(song: Song, vm: MusicViewModel) {
    val progress = vm.downloads[song.id]
    when {
        !song.isOnlineSource -> Unit
        vm.isDownloaded(song.id) -> Icon(
            Icons.Rounded.DownloadDone, "İndirildi",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        progress != null -> Box(Modifier.size(40.dp), Alignment.Center) {
            CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
        }
        else -> IconButton(onClick = { vm.download(song) }) { Icon(Icons.Rounded.Download, "İndir") }
    }
}

fun UriHandler.safeOpen(url: String) {
    try {
        openUri(url)
    } catch (_: Exception) {
    }
}

fun youtubeMusicSearch(q: String) = "https://music.youtube.com/search?q=" + URLEncoder.encode(q, "UTF-8")
fun spotifySearch(q: String) = "https://open.spotify.com/search/" + URLEncoder.encode(q, "UTF-8").replace("+", "%20")

/** Şarkının ⋮ menüsü */
@Composable
fun SongMenuButton(song: Song, vm: MusicViewModel, playlistId: String? = null, allowDelete: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val fav = vm.isFavorite(song.id)

    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "Seçenekler") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem("Sıradaki olarak çal", Icons.Rounded.PlaylistPlay) { open = false; vm.playNext(song) }
            MenuItem("Sıraya ekle", Icons.Rounded.AddToQueue) { open = false; vm.addToQueue(song) }
            MenuItem(
                if (fav) "Favorilerden çıkar" else "Favorilere ekle",
                if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            ) { open = false; vm.toggleFavorite(song) }
            MenuItem("Listeye ekle", Icons.Rounded.PlaylistAdd) { open = false; vm.pickPlaylistFor = song }
            if (vm.canDownload(song)) {
                MenuItem("İndir (internetsiz dinle)", Icons.Rounded.Download) { open = false; vm.download(song) }
            }
            if (playlistId != null && playlistId != Playlists.FAVORITES) {
                MenuItem("Listeden çıkar", Icons.Rounded.RemoveCircleOutline) {
                    open = false; vm.removeFromPlaylist(playlistId, song.id)
                }
            }
            if (allowDelete) {
                MenuItem("Sil", Icons.Rounded.DeleteOutline) { open = false; vm.confirmDelete = song }
            }
            HorizontalDivider()
            MenuItem("YouTube Music'te aç", Icons.Rounded.OpenInNew) {
                open = false; uri.safeOpen(youtubeMusicSearch("${song.artist} ${song.title}"))
            }
            MenuItem("Spotify'da aç", Icons.Rounded.OpenInNew) {
                open = false; uri.safeOpen(spotifySearch("${song.artist} ${song.title}"))
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

/** Listeyi çal / karışık çal / sıralama / filtre + şarkılar */
@Composable
fun SongListPane(
    vm: MusicViewModel,
    songs: List<Song>,
    emptyText: String,
    playlistId: String? = null,
    allowDelete: Boolean = false,
    showDownloadAll: Boolean = false,
) {
    var filter by remember { mutableStateOf("") }
    var sort by remember { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    val shown = remember(songs, filter, sort) {
        songs.filter {
            filter.isBlank() || it.title.contains(filter, true) || it.artist.contains(filter, true)
        }.let { l ->
            when (sort) {
                1 -> l.sortedBy { it.title.lowercase() }
                2 -> l.sortedBy { it.artist.lowercase() }
                3 -> l.sortedByDescending { it.addedAt }
                else -> l
            }
        }
    }

    if (songs.isEmpty()) {
        CenterText(emptyText)
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { vm.playList(shown, 0, shuffle = false) }) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(4.dp))
                Text("Çal")
            }
            OutlinedButton(onClick = { vm.shuffleAll(shown) }) {
                Icon(Icons.Rounded.Shuffle, null)
                Spacer(Modifier.width(4.dp))
                Text("Karışık")
            }
            Spacer(Modifier.weight(1f))
            if (showDownloadAll && songs.any { vm.canDownload(it) }) {
                IconButton(onClick = { vm.downloadAll(songs) }) { Icon(Icons.Rounded.DownloadForOffline, "Tümünü indir") }
            }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Rounded.Sort, "Sırala") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    listOf("Varsayılan", "Şarkı adına göre", "Sanatçıya göre", "Yeni eklenen").forEachIndexed { i, t ->
                        DropdownMenuItem(
                            text = { Text(t, fontWeight = if (sort == i) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { sort = i; sortMenu = false },
                        )
                    }
                }
            }
        }
        if (songs.size > 6) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("${songs.size} şarkıda ara") },
                leadingIcon = { Icon(Icons.Rounded.FilterList, null) },
                trailingIcon = {
                    if (filter.isNotEmpty()) IconButton(onClick = { filter = "" }) { Icon(Icons.Rounded.Close, "Temizle") }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(shown, key = { it.id }) { s ->
                SongRow(song = s, playing = vm.player.mediaId == s.id, onClick = { vm.playList(shown, shown.indexOf(s)) }) {
                    if (showDownloadAll) DownloadIndicator(s, vm)
                    SongMenuButton(s, vm, playlistId, allowDelete)
                }
            }
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, placeholder = { Text("Liste adı") })
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

fun formatSec(totalSec: Int): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatMs(ms: Long): String = formatSec((ms / 1000).toInt())
