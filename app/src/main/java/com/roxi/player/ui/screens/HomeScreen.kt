package com.roxi.player.ui.screens

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.roxi.player.ui.components.LocalVm
import com.roxi.player.ui.components.SectionHeader
import com.roxi.player.ui.components.SongRow
import com.roxi.player.ui.navigateTab
import com.roxi.player.ui.theme.Roxi
import java.util.Calendar

@Composable
fun HomeScreen(nav: NavController) {
    val vm = LocalVm.current
    val songs by vm.songs.collectAsStateWithLifecycle()
    val recentAdded by vm.recentlyAdded.collectAsStateWithLifecycle()
    val played by vm.recentlyPlayed.collectAsStateWithLifecycle()
    val most by vm.mostPlayed.collectAsStateWithLifecycle()
    val liked by vm.likedSongs.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val playlists by vm.prefs.playlists.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val hasScanned by vm.hasScanned.collectAsStateWithLifecycle()
    val bottom = LocalBottomPadding.current
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Bonjour"
            in 12..17 -> "Bon après-midi"
            else -> "Bonsoir"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Roxi.Bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        HomeTopBar(greeting, nav)
        SearchPill { nav.navigateTab("explore") }

        when {
            songs.isEmpty() && (scanning || !hasScanned) -> Box(
                Modifier.fillMaxWidth().padding(64.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            songs.isEmpty() -> EmptyLibrary { vm.rescan() }

            else -> {
                val mix = when {
                    most.isNotEmpty() -> most
                    played.isNotEmpty() -> played
                    liked.isNotEmpty() -> liked
                    else -> recentAdded
                }
                FeaturedMix(mix, songs.size, playlists.size) { vm.shuffle(mix) }

                SectionHeader("Ta bibliothèque")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        LibraryShortcut(Icons.Rounded.MusicNote, "Titres", songs.size, Roxi.Violet) {
                            nav.navigate("songs/all")
                        }
                    }
                    item {
                        LibraryShortcut(Icons.Rounded.Album, "Albums", albums.size, Roxi.Pink) {
                            nav.navigateTab("library?tab=2", restore = false)
                        }
                    }
                    item {
                        LibraryShortcut(Icons.Rounded.Person, "Artistes", artists.size, Roxi.Blue) {
                            nav.navigateTab("library?tab=1", restore = false)
                        }
                    }
                    item {
                        LibraryShortcut(Icons.Rounded.Folder, "Dossiers", folders.size, Color(0xFFFFA45B)) {
                            nav.navigateTab("library?tab=3", restore = false)
                        }
                    }
                }

                SectionHeader("Accès rapide")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        QuickCollection(Icons.Rounded.Favorite, "Favoris", "${liked.size} titres", Roxi.Pink) {
                            nav.navigate("songs/liked")
                        }
                    }
                    item {
                        QuickCollection(Icons.AutoMirrored.Rounded.QueueMusic, "Playlists", "${playlists.size} listes", Roxi.Violet) {
                            nav.navigateTab("library?tab=0", restore = false)
                        }
                    }
                    item {
                        QuickCollection(Icons.Rounded.History, "Écoutés", "Reprendre", Roxi.Blue) {
                            nav.navigate("songs/played")
                        }
                    }
                    item {
                        QuickCollection(Icons.Rounded.BarChart, "Statistiques", "Ton activité", Color(0xFF52B788)) {
                            nav.navigate("stats")
                        }
                    }
                }

                if (played.isNotEmpty()) {
                    SectionHeader("Continuer l’écoute") { nav.navigate("songs/played") }
                    SongCarousel(played.take(12), featured = true)
                }

                SectionHeader("Ajoutés récemment") { nav.navigate("songs/recent") }
                SongCarousel(recentAdded.take(15))

                if (most.isNotEmpty()) {
                    SectionHeader("Tes titres populaires") { nav.navigate("songs/most") }
                    SongCarousel(most.take(15))
                }

                if (liked.isNotEmpty()) {
                    SectionHeader("Chansons aimées") { nav.navigate("songs/liked") }
                    SongCarousel(liked.take(15))
                }

                if (folders.isNotEmpty()) {
                    SectionHeader("Dossiers") { nav.navigateTab("library?tab=3", restore = false) }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(folders.take(8), key = { it.key }) { group -> FolderCard(group, nav) }
                    }
                }

                SectionHeader("Tous les titres") { nav.navigate("songs/all") }
                recentAdded.take(6).forEachIndexed { index, song ->
                    SongRow(song, onClick = { vm.play(recentAdded, index) })
                }
            }
        }
        Spacer(Modifier.height(bottom + 18.dp))
    }
}

@Composable
private fun HomeTopBar(greeting: String, nav: NavController) {
    Row(
        Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(R.drawable.roxi_logo),
            contentDescription = null,
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.bodySmall, color = Roxi.TextSub)
            Text("Roxi Player", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = { nav.navigateTab("profile") }) {
            Icon(Icons.Rounded.Settings, "Paramètres", tint = Roxi.Text)
        }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable(onClick = onClick),
        color = Roxi.Surface,
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Roxi.SurfaceHigh),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = Roxi.TextSub)
            Spacer(Modifier.width(12.dp))
            Text("Rechercher un titre, artiste ou album", color = Roxi.TextSub, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.Tune, null, tint = Roxi.VioletSoft, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun FeaturedMix(mix: List<Song>, songCount: Int, playlistCount: Int, onPlay: () -> Unit) {
    val featured = mix.firstOrNull() ?: return
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Roxi.Violet, Roxi.Pink)))
            .clickable(onClick = onPlay),
    ) {
        Column(Modifier.padding(start = 20.dp, top = 20.dp, bottom = 20.dp, end = 142.dp)) {
            Text("MIX ROXI", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = .82f))
            Spacer(Modifier.height(5.dp))
            Text("Une sélection rien que pour toi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2)
            Spacer(Modifier.height(7.dp))
            Text("$songCount titres • $playlistCount playlists", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .82f))
            Spacer(Modifier.height(16.dp))
            Surface(color = Color.White, shape = RoundedCornerShape(22.dp), onClick = onPlay) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Shuffle, null, tint = Roxi.Violet, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Lancer le mix", color = Roxi.Violet, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 18.dp)) {
            Box(
                Modifier.offset(x = (-12).dp, y = 10.dp).size(100.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = .18f)),
            )
            AlbumArt(
                featured,
                Modifier.size(112.dp).clip(RoundedCornerShape(24.dp)),
                size = 360,
            )
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 5.dp, y = 5.dp),
                color = Color.White,
                shape = CircleShape,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Roxi.Violet, modifier = Modifier.padding(8.dp).size(22.dp))
            }
        }
    }
}

@Composable
private fun LibraryShortcut(icon: ImageVector, title: String, count: Int, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(112.dp).clickable(onClick = onClick),
        color = Roxi.Surface,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(color.copy(alpha = .17f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("$count", color = Roxi.TextSub, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun QuickCollection(icon: ImageVector, title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(168.dp).clickable(onClick = onClick),
        color = Roxi.Surface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Roxi.SurfaceHigh),
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(subtitle, color = Roxi.TextSub, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Roxi.TextSub, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun SongCarousel(list: List<Song>, featured: Boolean = false) {
    val vm = LocalVm.current
    val width = if (featured) 156.dp else 132.dp
    val art = if (featured) 156.dp else 132.dp
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(list, key = { _, song -> song.id }) { index, song ->
            Column(Modifier.width(width).clip(RoundedCornerShape(16.dp)).clickable { vm.play(list, index) }) {
                Box {
                    AlbumArt(song, Modifier.size(art).clip(RoundedCornerShape(16.dp)), size = 360)
                    if (featured) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                            color = Color.Black.copy(alpha = .68f),
                            shape = CircleShape,
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.padding(7.dp).size(20.dp))
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Roxi.TextSub)
            }
        }
    }
}

@Composable
private fun FolderCard(group: Group, nav: NavController) {
    Surface(
        modifier = Modifier.width(164.dp).clickable { nav.navigate("songs/folder?key=${Uri.encode(group.key)}") },
        color = Roxi.Surface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Roxi.SurfaceHigh),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Roxi.Blue.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Folder, null, tint = Roxi.Blue, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text("${group.songs.size} titres", style = MaterialTheme.typography.bodySmall, color = Roxi.TextSub)
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onRescan: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(92.dp).clip(CircleShape).background(Roxi.Surface), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.LibraryMusic, null, tint = Roxi.VioletSoft, modifier = Modifier.size(46.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("Ta bibliothèque est vide", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("Ajoute des fichiers audio sur ton téléphone puis lance une nouvelle recherche.", color = Roxi.TextSub, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(18.dp))
        Button(onClick = onRescan, colors = ButtonDefaults.buttonColors(containerColor = Roxi.Violet)) {
            Icon(Icons.Rounded.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("Rechercher à nouveau")
        }
    }
}
