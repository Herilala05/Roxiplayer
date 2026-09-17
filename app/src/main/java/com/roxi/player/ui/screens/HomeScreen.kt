package com.roxi.player.ui.screens

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.roxi.player.R
import com.roxi.player.data.Group
import com.roxi.player.data.Song
import com.roxi.player.ui.components.AlbumArt
import com.roxi.player.ui.components.LocalBottomPadding
import com.roxi.player.ui.navigateTab
import com.roxi.player.ui.components.LocalVm
import com.roxi.player.ui.components.SectionHeader
import com.roxi.player.ui.components.SongRow
import com.roxi.player.ui.theme.Roxi

@Composable
fun HomeScreen(nav: NavController) {
    val vm = LocalVm.current
    val songs by vm.songs.collectAsStateWithLifecycle()
    val recentAdded by vm.recentlyAdded.collectAsStateWithLifecycle()
    val played by vm.recentlyPlayed.collectAsStateWithLifecycle()
    val most by vm.mostPlayed.collectAsStateWithLifecycle()
    val liked by vm.likedSongs.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val playlists by vm.prefs.playlists.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val hasScanned by vm.hasScanned.collectAsStateWithLifecycle()
    val bottom = LocalBottomPadding.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Roxi.Bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(R.drawable.roxi_logo), null,
                modifier = Modifier.size(34.dp).clip(CircleShape).background(Color.White),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Roxi Player",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { nav.navigateTab("explore") }) { Icon(Icons.Rounded.Search, "Rechercher") }
        }

        // Bannière
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Roxi.Violet, Roxi.Pink)))
                .padding(20.dp)
        ) {
            Column(Modifier.padding(end = 90.dp)) {
                Text(
                    "Ta musique,\nau même endroit",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${songs.size} titre(s) · ${playlists.size} playlist(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { vm.shuffle(songs) },
                    enabled = songs.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Roxi.Violet),
                ) {
                    Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tout mélanger", fontWeight = FontWeight.SemiBold)
                }
            }
            Image(
                painterResource(R.drawable.roxi_logo), null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(84.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop,
            )
        }

        when {
            songs.isEmpty() && (scanning || !hasScanned) -> Box(
                Modifier
                    .fillMaxWidth()
                    .padding(48.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            songs.isEmpty() -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Aucune musique trouvée sur ce téléphone.", color = Roxi.TextSub)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.rescan() }) { Text("Rechercher à nouveau") }
            }

            else -> {
                // Raccourcis
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuickCard(Icons.Rounded.Favorite, "Favoris", "${liked.size}", Roxi.Pink, Modifier.weight(1f)) {
                        nav.navigate("songs/liked")
                    }
                    QuickCard(Icons.AutoMirrored.Rounded.QueueMusic, "Playlists", "${playlists.size}", Roxi.Violet, Modifier.weight(1f)) {
                        nav.navigateTab("library?tab=0", restore = false)
                    }
                    QuickCard(Icons.Rounded.Folder, "Dossiers", "${folders.size}", Roxi.Blue, Modifier.weight(1f)) {
                        nav.navigateTab("library?tab=3", restore = false)
                    }
                }

                SectionHeader("Ajoutés récemment") { nav.navigate("songs/recent") }
                SongCarousel(recentAdded.take(15))

                if (played.isNotEmpty()) {
                    SectionHeader("Écoutés récemment") { nav.navigate("songs/played") }
                    SongCarousel(played.take(15))
                }
                if (most.isNotEmpty()) {
                    SectionHeader("Les plus écoutés") { nav.navigate("songs/most") }
                    SongCarousel(most.take(15))
                }
                if (liked.isNotEmpty()) {
                    SectionHeader("Chansons aimées") { nav.navigate("songs/liked") }
                    SongCarousel(liked.take(15))
                }

                SectionHeader("Dossiers") { nav.navigateTab("library?tab=3", restore = false) }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(folders.take(8), key = { it.key }) { g -> FolderCard(g, nav) }
                }

                SectionHeader("Tous les titres (${songs.size})") { nav.navigate("songs/all") }
                recentAdded.take(6).forEachIndexed { i, s ->
                    SongRow(s, onClick = { vm.play(recentAdded, i) })
                }
            }
        }
        Spacer(Modifier.height(bottom + 16.dp))
    }
}

@Composable
private fun SongCarousel(list: List<Song>) {
    val vm = LocalVm.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
            Column(
                Modifier
                    .width(124.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { vm.play(list, i) }
            ) {
                AlbumArt(s, Modifier.size(124.dp).clip(RoundedCornerShape(12.dp)), size = 320)
                Spacer(Modifier.height(6.dp))
                Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(
                    s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = Roxi.TextSub,
                )
            }
        }
    }
}

@Composable
private fun QuickCard(
    icon: ImageVector,
    title: String,
    count: String,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Roxi.Surface)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(count, style = MaterialTheme.typography.bodySmall, color = Roxi.TextSub)
    }
}

@Composable
private fun FolderCard(g: Group, nav: NavController) {
    Column(
        Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Roxi.Surface)
            .clickable { nav.navigate("songs/folder?key=${Uri.encode(g.key)}") }
            .padding(12.dp)
    ) {
        Icon(Icons.Rounded.Folder, null, tint = Roxi.Blue, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(6.dp))
        Text(g.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        Text("${g.songs.size} titre(s)", style = MaterialTheme.typography.bodySmall, color = Roxi.TextSub)
    }
}
