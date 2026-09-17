package com.roxi.player.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.roxi.player.data.Group
import com.roxi.player.ui.components.AlbumArt
import com.roxi.player.ui.components.LocalBottomPadding
import com.roxi.player.ui.components.LocalVm
import com.roxi.player.ui.components.ScreenHeader
import com.roxi.player.ui.theme.Roxi

private val libraryTabs = listOf("Playlists", "Artistes", "Albums", "Dossiers", "Titres")

@Composable
fun LibraryScreen(nav: NavController, initialTab: Int) {
    val vm = LocalVm.current
    val songs by vm.songs.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab.coerceIn(libraryTabs.indices)) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Roxi.Bg)
            .statusBarsPadding()
    ) {
        ScreenHeader("Bibliothèque", onBack = null)
        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = Roxi.Bg,
            contentColor = Roxi.VioletSoft,
            edgePadding = 8.dp,
        ) {
            libraryTabs.forEachIndexed { i, t ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(t) },
                    unselectedContentColor = Roxi.TextSub,
                )
            }
        }
        when (tab) {
            0 -> PlaylistsTab(nav)
            1 -> GroupList(artists, "artist", Icons.Rounded.Person, Color(0xFF2FB8A6), nav)
            2 -> GroupList(albums, "album", null, Color.Unspecified, nav)
            3 -> GroupList(folders, "folder", Icons.Rounded.Folder, Roxi.Blue, nav)
            else -> SongListContent(songs, sortable = true, playlistId = null)
        }
    }
}

@Composable
private fun GroupList(groups: List<Group>, kind: String, icon: ImageVector?, tint: Color, nav: NavController) {
    val bottom = LocalBottomPadding.current
    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Rien à afficher", color = Roxi.TextSub)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 8.dp)) {
        items(groups, key = { it.key }) { g ->
            val subtitle = if (kind == "folder") "${g.songs.size} titre(s) · ${g.subtitle}"
            else if (kind == "album") "${g.subtitle} · ${g.songs.size} titre(s)"
            else g.subtitle
            GroupRow(
                title = g.name,
                subtitle = subtitle,
                leading = {
                    if (icon == null) {
                        AlbumArt(g.songs.firstOrNull(), Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)))
                    } else {
                        IconTile(icon, tint)
                    }
                },
                onClick = { nav.navigate("songs/$kind?key=${Uri.encode(g.key)}") },
            )
        }
    }
}

@Composable
private fun PlaylistsTab(nav: NavController) {
    val vm = LocalVm.current
    val playlists by vm.prefs.playlists.collectAsStateWithLifecycle()
    val liked by vm.likedSongs.collectAsStateWithLifecycle()
    val songMap by vm.songMap.collectAsStateWithLifecycle()
    val bottom = LocalBottomPadding.current
    var create by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom + 88.dp)) {
            item {
                GroupRow(
                    title = "Chansons aimées",
                    subtitle = "${liked.size} élément(s)",
                    leading = { IconTile(Icons.Rounded.Favorite, Roxi.Pink) },
                    onClick = { nav.navigate("songs/liked") },
                )
            }
            items(playlists, key = { it.id }) { p ->
                val first = p.songIds.firstNotNullOfOrNull { songMap[it] }
                GroupRow(
                    title = p.name,
                    subtitle = "${p.songIds.size} élément(s)",
                    leading = {
                        if (first != null) {
                            AlbumArt(first, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)))
                        } else {
                            IconTile(Icons.AutoMirrored.Rounded.QueueMusic, Roxi.Violet)
                        }
                    },
                    onClick = { nav.navigate("songs/playlist?key=${p.id}") },
                )
            }
            if (playlists.isEmpty()) {
                item {
                    Text(
                        "Aucune playlist. Appuie sur + pour en créer une.",
                        color = Roxi.TextSub,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = { create = true },
            containerColor = Roxi.Violet,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = bottom + 16.dp),
        ) { Icon(Icons.Rounded.Add, "Nouvelle playlist") }
    }

    if (create) {
        NameDialog("Nouvelle playlist", "", onDismiss = { create = false }) {
            vm.prefs.createPlaylist(it)
            create = false
        }
    }
}

@Composable
private fun GroupRow(title: String, subtitle: String, leading: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = Roxi.TextSub,
            )
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, tint: Color) {
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Roxi.Surface),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint) }
}
