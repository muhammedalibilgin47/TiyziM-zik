package com.tiyzi.muzik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.tiyzi.muzik.playback.AudioFx
import com.tiyzi.muzik.playback.SleepTimer
import kotlinx.coroutines.delay

@Composable
fun MiniPlayer(vm: MusicViewModel, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val p = vm.player
    val glass = LocalGlass.current
    Surface(
        color = if (glass) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column {
            if (!p.isRadio) {
                LinearProgressIndicator(
                    progress = {
                        if (p.durationMs > 0) (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f) else 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                )
            }
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    p.artwork, Modifier.size(44.dp),
                    fallback = if (p.isRadio) Icons.Rounded.Radio else Icons.Rounded.MusicNote,
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(
                        p.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (p.buffering) {
                    Box(Modifier.size(48.dp), Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else {
                    IconButton(onClick = vm::togglePlay) {
                        Icon(
                            if (p.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (p.isPlaying) "Duraklat" else "Çal",
                        )
                    }
                }
                if (!p.isRadio) {
                    IconButton(onClick = vm::next) { Icon(Icons.Rounded.SkipNext, "Sonraki") }
                }
            }
        }
    }
}

@Composable
fun FullPlayer(vm: MusicViewModel, onOpen: (Sheet) -> Unit) {
    val p = vm.player
    var dragging by remember { mutableStateOf<Float?>(null) }
    val dur = p.durationMs.coerceAtLeast(1L)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val sleepEnds by SleepTimer.endsAt.collectAsStateWithLifecycle()
    val sleepEndOfTrack by SleepTimer.endOfTrack.collectAsStateWithLifecycle()
    val sleepActive = sleepEnds != null || sleepEndOfTrack
    val fav = p.mediaId?.let { vm.isFavorite(it) } == true
    var speedMenu by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            p.artwork,
            Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f),
            corner = 16.dp,
            fallback = if (p.isRadio) Icons.Rounded.Radio else Icons.Rounded.MusicNote,
        )
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    p.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(p.artist, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!p.isRadio) {
                IconButton(onClick = vm::toggleFavoriteCurrent) {
                    Icon(
                        if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (fav) "Favorilerden çıkar" else "Favorilere ekle",
                        tint = if (fav) accent else muted,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (p.isRadio) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.error)
                )
                Spacer(Modifier.width(8.dp))
                Text("CANLI YAYIN", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
        } else {
            Slider(
                value = dragging ?: (p.positionMs.toFloat() / dur).coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { vm.seekTo((it * dur).toLong()) }
                    dragging = null
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatMs(dragging?.let { (it * dur).toLong() } ?: p.positionMs),
                    style = MaterialTheme.typography.bodySmall, color = muted,
                )
                Text(formatMs(p.durationMs), style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = vm::toggleShuffle, enabled = !p.isRadio) {
                Icon(Icons.Rounded.Shuffle, "Karışık çal", tint = if (p.shuffle) accent else muted)
            }
            IconButton(onClick = vm::previous, modifier = Modifier.size(56.dp), enabled = !p.isRadio) {
                Icon(Icons.Rounded.SkipPrevious, "Önceki", Modifier.size(36.dp))
            }
            FilledIconButton(onClick = vm::togglePlay, modifier = Modifier.size(72.dp)) {
                if (p.buffering) {
                    CircularProgressIndicator(
                        Modifier.size(32.dp), strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        if (p.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (p.isPlaying) "Duraklat" else "Çal",
                        Modifier.size(40.dp),
                    )
                }
            }
            IconButton(onClick = vm::next, modifier = Modifier.size(56.dp), enabled = !p.isRadio) {
                Icon(Icons.Rounded.SkipNext, "Sonraki", Modifier.size(36.dp))
            }
            IconButton(onClick = vm::cycleRepeat, enabled = !p.isRadio) {
                Icon(
                    if (p.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "Tekrarla",
                    tint = if (p.repeatMode != Player.REPEAT_MODE_OFF) accent else muted,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // Alt araçlar: uyku, ekolayzır, sıra, hız
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(Icons.Rounded.Bedtime, "Uyku", active = sleepActive) { onOpen(Sheet.Sleep) }
            ToolButton(Icons.Rounded.Equalizer, "Ekolayzır") { onOpen(Sheet.Equalizer) }
            if (!p.isRadio) {
                ToolButton(Icons.Rounded.QueueMusic, "Sıra") { onOpen(Sheet.Queue) }
                Box {
                    ToolButton(Icons.Rounded.Speed, "${formatSpeed(p.speed)}x", active = p.speed != 1f) { speedMenu = true }
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                            DropdownMenuItem(
                                text = { Text("${formatSpeed(s)}x", fontWeight = if (s == p.speed) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { vm.setSpeed(s); speedMenu = false },
                            )
                        }
                    }
                }
            }
        }
        if (sleepActive) {
            SleepRemainingText(sleepEnds, sleepEndOfTrack)
        }
    }
}

private fun formatSpeed(s: Float): String =
    if (s % 1f == 0f) s.toInt().toString() else s.toString().trimEnd('0')

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun SleepRemainingText(endsAt: Long?, endOfTrack: Boolean) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (endsAt != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val text = when {
        endOfTrack -> "Şarkı bitince duracak"
        endsAt != null -> "Müzik ${formatMs((endsAt - now).coerceAtLeast(0))} sonra duracak"
        else -> ""
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
fun SleepSheet(onDone: () -> Unit) {
    val endsAt by SleepTimer.endsAt.collectAsStateWithLifecycle()
    val endOfTrack by SleepTimer.endOfTrack.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Text(
            "Uyku zamanlayıcısı",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (endsAt != null || endOfTrack) {
            SleepRemainingText(endsAt, endOfTrack)
            ListItem(
                headlineContent = { Text("Zamanlayıcıyı kapat", color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Rounded.TimerOff, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { SleepTimer.cancel(); onDone() },
            )
        }
        listOf(5, 10, 15, 30, 45, 60, 90).forEach { m ->
            ListItem(
                headlineContent = { Text(if (m < 60) "$m dakika" else if (m == 60) "1 saat" else "1,5 saat") },
                leadingContent = { Icon(Icons.Rounded.Timer, null) },
                modifier = Modifier.clickable { SleepTimer.start(m); onDone() },
            )
        }
        ListItem(
            headlineContent = { Text("Çalan şarkı bitince") },
            leadingContent = { Icon(Icons.Rounded.MusicOff, null) },
            modifier = Modifier.clickable { SleepTimer.stopAtEndOfTrack(); onDone() },
        )
    }
}

@Composable
fun QueueSheet(vm: MusicViewModel) {
    val q = vm.queue
    val state = rememberLazyListState(initialFirstVisibleItemIndex = vm.queueIndex.coerceIn(0, (q.size - 1).coerceAtLeast(0)))
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Çalma sırası (${q.size})",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (q.isEmpty()) {
            CenterText("Sıra boş", Modifier.fillMaxWidth())
        }
        LazyColumn(state = state, modifier = Modifier.heightIn(max = 520.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(q) { i, item ->
                val current = i == vm.queueIndex
                ListItem(
                    headlineContent = {
                        Text(
                            item.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    supportingContent = { Text(item.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = {
                        if (current) Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                        else Text("${i + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = {
                        if (!current) {
                            IconButton(onClick = { vm.removeFromQueue(i) }) { Icon(Icons.Rounded.Close, "Sıradan çıkar") }
                        }
                    },
                    modifier = Modifier.clickable { vm.jumpTo(i) },
                )
            }
        }
    }
}

@Composable
fun EqualizerSheet() {
    val fx by AudioFx.state.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text("Ekolayzır", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        val s = fx
        if (s == null) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Ekolayzır, bir şarkı çalmaya başladıktan sonra açılır. " +
                    "Bazı telefonlar ekolayzırı desteklemeyebilir.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Açık", modifier = Modifier.weight(1f))
            Switch(checked = s.enabled, onCheckedChange = { AudioFx.setEnabled(it) })
        }
        if (s.presets.isNotEmpty()) {
            Text("Hazır ayarlar", style = MaterialTheme.typography.labelLarge)
            LazyRow(
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(s.presets) { i, name ->
                    FilterChip(
                        selected = s.preset == i,
                        onClick = { AudioFx.usePreset(i) },
                        label = { Text(presetTr(name)) },
                        enabled = s.enabled,
                    )
                }
            }
        }
        s.bands.forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
                Slider(
                    value = b.level.toFloat(),
                    onValueChange = { AudioFx.setBand(b.index, it.toInt()) },
                    valueRange = s.minLevel.toFloat()..s.maxLevel.toFloat(),
                    enabled = s.enabled,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "%+d dB".format(b.level / 100),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(52.dp),
                )
            }
        }
        if (s.bassSupported) {
            Spacer(Modifier.height(8.dp))
            Text("Bas güçlendirme", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = s.bass.toFloat(),
                onValueChange = { AudioFx.setBass(it.toInt()) },
                valueRange = 0f..1000f,
                enabled = s.enabled,
            )
        }
    }
}

private fun presetTr(name: String): String = when (name.lowercase()) {
    "normal" -> "Normal"
    "classical" -> "Klasik"
    "dance" -> "Dans"
    "flat" -> "Düz"
    "folk" -> "Halk"
    "heavy metal" -> "Metal"
    "hip hop" -> "Hip hop"
    "jazz" -> "Caz"
    "pop" -> "Pop"
    "rock" -> "Rock"
    else -> name
}
