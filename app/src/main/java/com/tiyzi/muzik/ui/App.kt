package com.tiyzi.muzik.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tiyzi.muzik.data.Playlists
import kotlinx.coroutines.flow.receiveAsFlow

enum class Sheet { None, Player, Queue, Equalizer, Sleep, Settings }

@Composable
fun App(vm: MusicViewModel = viewModel()) {
    TiyziTheme(glass = vm.glass) {
        AppContent(vm)
    }
}

@Composable
private fun RowScope.NavItems(tab: Int, onTab: (Int) -> Unit) {
    NavigationBarItem(
        selected = tab == 0, onClick = { onTab(0) },
        icon = { Icon(Icons.Rounded.Search, null) }, label = { Text("Keşfet") },
    )
    NavigationBarItem(
        selected = tab == 1, onClick = { onTab(1) },
        icon = { Icon(Icons.Rounded.Radio, null) }, label = { Text("Radyo") },
    )
    NavigationBarItem(
        selected = tab == 2, onClick = { onTab(2) },
        icon = { Icon(Icons.Rounded.LibraryMusic, null) }, label = { Text("Kitaplığım") },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppContent(vm: MusicViewModel) {
    val context = LocalContext.current
    val glass = LocalGlass.current

    // Android 13+ için bildirim izni (arka planda çalan müziğin bildirimi)
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf(Sheet.None) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.receiveAsFlow().collect {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(it)
        }
    }

    BackHandler(enabled = tab != 0) { tab = 0 }

    Box(Modifier.fillMaxSize()) {
        if (glass) LiquidBackground()

        Scaffold(
            containerColor = if (glass) Color.Transparent else MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (glass) {
                    // Yüzen cam alt çubuk
                    Column(
                        Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (vm.player.mediaId != null) {
                            MiniPlayer(
                                vm, onOpen = { sheet = Sheet.Player },
                                modifier = Modifier.glass(RoundedCornerShape(22.dp)),
                            )
                        }
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            modifier = Modifier
                                .glass(RoundedCornerShape(28.dp))
                                .height(72.dp),
                        ) { NavItems(tab) { tab = it } }
                    }
                } else {
                    Column {
                        if (vm.player.mediaId != null) {
                            MiniPlayer(vm, onOpen = { sheet = Sheet.Player })
                        }
                        NavigationBar { NavItems(tab) { tab = it } }
                    }
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                when (tab) {
                    0 -> SearchScreen(vm, onSettings = { sheet = Sheet.Settings })
                    1 -> RadioScreen(vm)
                    else -> LibraryScreen(vm)
                }
            }
        }
    }

    if (sheet != Sheet.None) {
        ModalBottomSheet(
            onDismissRequest = { sheet = Sheet.None },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            when (sheet) {
                Sheet.Player -> FullPlayer(vm, onOpen = { sheet = it })
                Sheet.Queue -> QueueSheet(vm)
                Sheet.Equalizer -> EqualizerSheet()
                Sheet.Sleep -> SleepSheet(onDone = { sheet = Sheet.Player })
                Sheet.Settings -> SettingsSheet(vm)
                Sheet.None -> Unit
            }
        }
    }

    // Listeye ekle
    vm.pickPlaylistFor?.let { song ->
        var creating by remember { mutableStateOf(false) }
        if (creating) {
            NameDialog(
                title = "Yeni liste", initial = "", confirm = "Oluştur",
                onDismiss = { creating = false },
                onConfirm = { name ->
                    val p = vm.createPlaylist(name)
                    vm.addToPlaylist(p.id, song)
                    creating = false
                    vm.pickPlaylistFor = null
                },
            )
        } else {
            val lists = vm.playlists.filter { it.id != Playlists.FAVORITES }
            AlertDialog(
                onDismissRequest = { vm.pickPlaylistFor = null },
                title = { Text("Listeye ekle") },
                text = {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        item {
                            ListItem(
                                headlineContent = { Text("Yeni liste oluştur") },
                                leadingContent = { Icon(Icons.Rounded.Add, null) },
                                modifier = Modifier.clickable { creating = true },
                            )
                        }
                        items(lists, key = { it.id }) { p ->
                            ListItem(
                                headlineContent = { Text(p.name) },
                                supportingContent = { Text("${p.songs.size} şarkı") },
                                leadingContent = { Icon(Icons.Rounded.QueueMusic, null) },
                                modifier = Modifier.clickable {
                                    vm.addToPlaylist(p.id, song)
                                    vm.pickPlaylistFor = null
                                },
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { vm.pickPlaylistFor = null }) { Text("Kapat") } },
            )
        }
    }

    // Silme onayı
    vm.confirmDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { vm.confirmDelete = null },
            title = { Text("Şarkı silinsin mi?") },
            text = { Text("\"${s.title}\" telefondan silinecek.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(s)
                    vm.confirmDelete = null
                }) { Text("Sil") }
            },
            dismissButton = { TextButton(onClick = { vm.confirmDelete = null }) { Text("Vazgeç") } },
        )
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String, action: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action()
    }
}

@Composable
fun SettingsSheet(vm: MusicViewModel) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Text(
            "Ayarlar",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        ListItem(
            headlineContent = { Text("Liquid Glass görünüm") },
            supportingContent = { Text("Cam efektli paneller ve hareketli renkli arka plan") },
            leadingContent = { Icon(Icons.Rounded.AutoAwesome, null) },
            trailingContent = { Switch(checked = vm.glass, onCheckedChange = { vm.updateGlass(it) }) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { vm.updateGlass(!vm.glass) },
        )
        ListItem(
            headlineContent = { Text("Sürüm") },
            supportingContent = { Text("Tiyzi Müzik 2.1") },
            leadingContent = { Icon(Icons.Rounded.Info, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
