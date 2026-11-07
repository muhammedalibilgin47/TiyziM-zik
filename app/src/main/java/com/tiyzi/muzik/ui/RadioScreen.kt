package com.tiyzi.muzik.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiyzi.muzik.data.Station

private val radioTags = listOf(
    "" to "Tümü", "pop" to "Pop", "arabesk" to "Arabesk", "türkü" to "Türkü", "halk" to "Halk",
    "rap" to "Rap", "rock" to "Rock", "slow" to "Slow", "fantezi" to "Fantezi",
    "sanat" to "Sanat müziği", "dini" to "Dini", "news" to "Haber", "jazz" to "Caz",
)

@Composable
fun RadioScreen(vm: MusicViewModel) {
    val focus = LocalFocusManager.current
    LaunchedEffect(Unit) {
        if (vm.stations.isEmpty() && !vm.radioLoading) vm.loadRadio()
    }
    val favs = vm.favStations
    val err = vm.radioError

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Radyo", "Türk radyoları canlı • internet gerekir")
        OutlinedTextField(
            value = vm.radioQuery,
            onValueChange = { vm.radioQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Radyo ara (ör. Kral, Power, TRT)") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (vm.radioQuery.isNotEmpty()) IconButton(onClick = {
                    vm.radioQuery = ""
                    vm.loadRadio()
                }) { Icon(Icons.Rounded.Close, "Temizle") }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                vm.loadRadio()
            }),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(radioTags) { (key, label) ->
                FilterChip(
                    selected = vm.radioTag == key,
                    onClick = {
                        vm.radioTag = key
                        vm.loadRadio()
                    },
                    label = { Text(label) },
                )
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (favs.isNotEmpty()) {
                item { SectionTitle("Favori radyolarım") }
                items(favs, key = { "f" + it.id }) { st -> StationRow(vm, st) }
            }
            item { SectionTitle("Türk radyoları") }
            when {
                vm.radioLoading -> item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp), Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                err != null -> item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(err, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { vm.loadRadio() }) { Text("Tekrar dene") }
                    }
                }
                else -> items(vm.stations, key = { "s" + it.id }) { st -> StationRow(vm, st) }
            }
        }
    }
}

@Composable
private fun StationRow(vm: MusicViewModel, st: Station) {
    val playing = vm.player.mediaId == RADIO_PREFIX + st.id
    val fav = vm.isFavStation(st.id)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { vm.playStation(st) }
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(st.favicon, Modifier.size(52.dp), fallback = Icons.Rounded.Radio)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                st.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            val sub = listOfNotNull(
                st.tags.ifBlank { null },
                if (st.bitrate > 0) "${st.bitrate} kbps" else null,
            ).joinToString(" • ")
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = { vm.toggleFavStation(st) }) {
            Icon(
                if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                if (fav) "Favorilerden çıkar" else "Favorilere ekle",
                tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
