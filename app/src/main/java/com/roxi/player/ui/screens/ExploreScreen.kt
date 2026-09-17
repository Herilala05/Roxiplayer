package com.roxi.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.roxi.player.ui.components.LocalBottomPadding
import com.roxi.player.ui.navigateTab
import com.roxi.player.ui.components.LocalVm
import com.roxi.player.ui.components.ScreenHeader
import com.roxi.player.ui.components.SongRow
import com.roxi.player.ui.theme.Roxi

@Composable
fun ExploreScreen(nav: NavController) {
    val vm = LocalVm.current
    val songs by vm.songs.collectAsStateWithLifecycle()
    val playlists by vm.prefs.playlists.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val liked by vm.likedSongs.collectAsStateWithLifecycle()
    val bottom = LocalBottomPadding.current
    var query by rememberSaveable { mutableStateOf("") }

    val results = remember(query, songs) {
        val q = query.trim()
        if (q.isEmpty()) emptyList() else songs.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Roxi.Bg)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = bottom + 8.dp),
    ) {
        item { ScreenHeader("Explorer", onBack = null) }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Titre, artiste, album…") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Effacer") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Roxi.Surface,
                    focusedContainerColor = Roxi.Surface,
                    unfocusedBorderColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (query.isNotBlank()) {
            item {
                Text(
                    "${results.size} résultat(s)",
                    color = Roxi.TextSub,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            itemsIndexed(results, key = { _, s -> s.id }) { i, s ->
                SongRow(s, onClick = { vm.play(results, i) })
            }
        } else {
            item {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CategoryCard(Icons.Rounded.MusicNote, "Titres", songs.size, Roxi.Violet, Modifier.weight(1f)) {
                            nav.navigate("songs/all")
                        }
                        CategoryCard(Icons.AutoMirrored.Rounded.QueueMusic, "Playlists", playlists.size, Roxi.Pink, Modifier.weight(1f)) {
                            nav.navigateTab("library?tab=0", restore = false)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CategoryCard(Icons.Rounded.Person, "Artistes", artists.size, Color(0xFF2FB8A6), Modifier.weight(1f)) {
                            nav.navigateTab("library?tab=1", restore = false)
                        }
                        CategoryCard(Icons.Rounded.Album, "Albums", albums.size, Color(0xFFF08A5C), Modifier.weight(1f)) {
                            nav.navigateTab("library?tab=2", restore = false)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CategoryCard(Icons.Rounded.Folder, "Dossiers", folders.size, Roxi.Blue, Modifier.weight(1f)) {
                            nav.navigateTab("library?tab=3", restore = false)
                        }
                        CategoryCard(Icons.Rounded.Favorite, "Favoris", liked.size, Color(0xFFB0437A), Modifier.weight(1f)) {
                            nav.navigate("songs/liked")
                        }
                    }
                }
            }
            item {
                Text(
                    "Utilise la recherche ou choisis une catégorie pour parcourir toute ta collection.",
                    color = Roxi.TextSub,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(
    icon: ImageVector,
    title: String,
    count: Int,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Icon(icon, null, tint = Color.White)
        Spacer(Modifier.weight(1f))
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        Text("$count élément(s)", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
    }
}
