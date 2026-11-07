package com.tiyzi.muzik.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

private val quickSearches = listOf(
    "Türkçe rap", "Türkçe pop", "Arabesk", "Türkü", "Anadolu rock", "Slow",
    "Lo-fi", "Remix", "Pop", "Hip hop", "Elektronik", "Akustik",
)

@Composable
fun SearchScreen(vm: MusicViewModel, onSettings: () -> Unit) {
    val focus = LocalFocusManager.current
    val err = vm.error
    val results = vm.results

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Keşfet", "Ücretsiz ve yasal müzik") {
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Ayarlar") }
        }
        OutlinedTextField(
            value = vm.query,
            onValueChange = {
                vm.query = it
                if (it.isEmpty()) vm.clearSearch()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            placeholder = { Text("Şarkı veya sanatçı ara") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = { vm.clearSearch() }) { Icon(Icons.Rounded.Close, "Temizle") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                vm.search()
            }),
        )

        when {
            vm.searching -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

            results.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                itemsIndexed(results, key = { _, s -> s.id }) { i, s ->
                    SongRow(song = s, playing = vm.player.mediaId == s.id, onClick = { playSearch(vm, results, i) }) {
                        DownloadIndicator(s, vm)
                        SongMenuButton(s, vm)
                    }
                }
                item { ExternalSearchCard(vm.query) }
            }

            err != null -> LazyColumn(Modifier.fillMaxSize()) {
                item { CenterText(err, Modifier.fillMaxWidth()) }
                item { ExternalSearchCard(vm.query) }
            }

            else -> DiscoverPane(vm)
        }
    }
}

private fun playSearch(vm: MusicViewModel, list: List<com.tiyzi.muzik.data.Song>, index: Int) = vm.playList(list, index)

@Composable
private fun DiscoverPane(vm: MusicViewModel) {
    val trending = vm.trending
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(quickSearches) { q ->
                    AssistChip(onClick = { vm.searchFor(q) }, label = { Text(q) })
                }
            }
        }
        item {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudDownload, null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Şarkıları ⬇ ile indirin, internet olmadan Kitaplığım'dan dinleyin. " +
                            "Türk radyoları için Radyo sekmesine bakın.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (trending.isNotEmpty()) {
            item { SectionTitle("Bu hafta popüler") }
            itemsIndexed(trending, key = { _, s -> "t" + s.id }) { i, s ->
                SongRow(song = s, playing = vm.player.mediaId == s.id, onClick = { vm.playList(trending, i) }) {
                    DownloadIndicator(s, vm)
                    SongMenuButton(s, vm)
                }
            }
        } else {
            item {
                TextButton(onClick = { vm.loadTrending() }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Popüler şarkıları yükle")
                }
            }
        }
        item { ExternalSearchCard("") }
    }
}

/** Popüler (telifli) şarkılar için resmi uygulamalara yönlendirir. */
@Composable
fun ExternalSearchCard(query: String) {
    val uri = LocalUriHandler.current
    Card(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (query.isBlank()) "Popüler şarkılar mı arıyorsunuz?" else "Aradığınızı bulamadınız mı?",
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Ünlü sanatçıların şarkılarını resmi uygulamalarda dinleyebilirsiniz.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    uri.safeOpen(if (query.isBlank()) "https://music.youtube.com" else youtubeMusicSearch(query))
                }) { Text("YouTube Music") }
                OutlinedButton(onClick = {
                    uri.safeOpen(if (query.isBlank()) "https://open.spotify.com" else spotifySearch(query))
                }) { Text("Spotify") }
            }
        }
    }
}
