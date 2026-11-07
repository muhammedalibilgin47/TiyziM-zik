package com.tiyzi.muzik.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiyzi.muzik.data.DeviceMusic
import com.tiyzi.muzik.data.Playlists

@Composable
fun LibraryScreen(vm: MusicViewModel) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf("İndirilenler", "Telefonum", "Favoriler", "Listelerim")
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importFiles(uris)
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Kitaplığım", "İnternetsiz dinlenebilen müzikleriniz") {
            IconButton(onClick = { picker.launch(arrayOf("audio/*")) }) {
                Icon(Icons.Rounded.Add, "Dosyadan şarkı ekle")
            }
        }
        ScrollableTabRow(selectedTabIndex = sub, edgePadding = 16.dp) {
            titles.forEachIndexed { i, t ->
                Tab(selected = sub == i, onClick = { sub = i }, text = { Text(t) })
            }
        }
        when (sub) {
            0 -> SongListPane(
                vm, vm.downloaded,
                emptyText = "Henüz indirilen şarkı yok.\nKeşfet sekmesinden şarkıları ⬇ ile indirin " +
                    "ya da sağ üstteki + ile dosya ekleyin.",
                allowDelete = true,
            )
            1 -> DevicePane(vm)
            2 -> SongListPane(
                vm, vm.favorites,
                emptyText = "Favori şarkınız yok.\nŞarkıların ⋮ menüsünden \"Favorilere ekle\" deyin.",
                playlistId = Playlists.FAVORITES,
                showDownloadAll = true,
            )
            else -> PlaylistsPane(vm)
        }
    }
}

@Composable
private fun DevicePane(vm: MusicViewModel) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.scanDevice() else vm.message("İzin verilmeden telefondaki müzikler bulunamaz")
    }

    when {
        vm.deviceScanning && !vm.deviceScanned -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator()
        }
        !vm.deviceScanned -> Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.PhoneAndroid, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(
                "Telefonunuzdaki bütün MP3 ve müzik dosyalarını bulup burada listeleyebilirim.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                if (DeviceMusic.hasPermission(context)) vm.scanDevice() else launcher.launch(DeviceMusic.permission())
            }) { Text("Telefondaki müzikleri bul") }
        }
        else -> Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${vm.deviceSongs.size} şarkı bulundu",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.scanDevice() }, enabled = !vm.deviceScanning) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Yenile")
                }
            }
            SongListPane(vm, vm.deviceSongs, emptyText = "Telefonda müzik dosyası bulunamadı.")
        }
    }
}

@Composable
private fun PlaylistsPane(vm: MusicViewModel) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val open = vm.playlists.find { it.id == openId }

    BackHandler(enabled = open != null) { openId = null }

    if (open != null) {
        var menu by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { openId = null }) { Icon(Icons.Rounded.ArrowBack, "Geri") }
                Column(Modifier.weight(1f)) {
                    Text(
                        open.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${open.songs.size} şarkı",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Liste seçenekleri") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Yeniden adlandır") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { menu = false; renaming = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Tümünü indir") },
                            leadingIcon = { Icon(Icons.Rounded.DownloadForOffline, null) },
                            onClick = { menu = false; vm.downloadAll(open.songs) },
                        )
                        DropdownMenuItem(
                            text = { Text("Listeyi sil") },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                            onClick = { menu = false; deleting = true },
                        )
                    }
                }
            }
            SongListPane(
                vm, open.songs,
                emptyText = "Bu liste boş.\nŞarkıların ⋮ menüsünden \"Listeye ekle\" deyin.",
                playlistId = open.id,
                showDownloadAll = true,
            )
        }
        if (renaming) {
            NameDialog("Yeniden adlandır", open.name, "Kaydet", onDismiss = { renaming = false }) {
                vm.renamePlaylist(open.id, it)
                renaming = false
            }
        }
        if (deleting) {
            AlertDialog(
                onDismissRequest = { deleting = false },
                title = { Text("Liste silinsin mi?") },
                text = { Text("\"${open.name}\" listesi silinecek. Şarkılar silinmez.") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deletePlaylist(open.id)
                        deleting = false
                        openId = null
                    }) { Text("Sil") }
                },
                dismissButton = { TextButton(onClick = { deleting = false }) { Text("Vazgeç") } },
            )
        }
        return
    }

    val lists = vm.playlists.filter { it.id != Playlists.FAVORITES }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            ListItem(
                headlineContent = { Text("Yeni liste oluştur", fontWeight = FontWeight.Medium) },
                leadingContent = {
                    Artwork(null, Modifier.size(52.dp), fallback = Icons.Rounded.Add)
                },
                modifier = Modifier.clickable { creating = true },
            )
        }
        items(lists, key = { it.id }) { p ->
            ListItem(
                headlineContent = { Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${p.songs.size} şarkı") },
                leadingContent = {
                    Artwork(p.songs.firstOrNull()?.artModel(), Modifier.size(52.dp), fallback = Icons.Rounded.QueueMusic)
                },
                modifier = Modifier.clickable { openId = p.id },
            )
        }
        if (lists.isEmpty()) {
            item {
                CenterText(
                    "Henüz listeniz yok. Bir liste oluşturup şarkıların ⋮ menüsünden ekleyin.",
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (creating) {
        NameDialog("Yeni liste", "", "Oluştur", onDismiss = { creating = false }) {
            val p = vm.createPlaylist(it)
            creating = false
            openId = p.id
        }
    }
}
