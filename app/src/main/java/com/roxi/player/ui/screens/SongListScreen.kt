package com.roxi.player.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.roxi.player.MainViewModel
import com.roxi.player.data.Song
import com.roxi.player.data.SortOrder
import com.roxi.player.data.indexLetter
import com.roxi.player.data.sortSongs
import com.roxi.player.ui.components.AddToPlaylistDialog
import com.roxi.player.ui.components.ConfirmDeleteDialog
import com.roxi.player.ui.components.DragHandle
import com.roxi.player.ui.components.rememberSongDeleter
import com.roxi.player.ui.components.LocalBottomPadding
import com.roxi.player.ui.components.ReorderState
import com.roxi.player.ui.components.draggedItem
import com.roxi.player.ui.components.LocalVm
import com.roxi.player.ui.components.ScreenHeader
import com.roxi.player.ui.components.SongRow
import com.roxi.player.ui.theme.Roxi
import kotlinx.coroutines.launch

/** Écran liste générique : tous les titres, récents, favoris, playlist, dossier, artiste, album. */
@Composable
fun SongListScreen(nav: NavController, kind: String, key: String?) {
    val vm = LocalVm.current
    val songs by vm.songs.collectAsStateWithLifecycle()
    val recentAdded by vm.recentlyAdded.collectAsStateWithLifecycle()
    val played by vm.recentlyPlayed.collectAsStateWithLifecycle()
    val most by vm.mostPlayed.collectAsStateWithLifecycle()
    val liked by vm.likedSongs.collectAsStateWithLifecycle()
    val playlists by vm.prefs.playlists.collectAsStateWithLifecycle()
    val songMap by vm.songMap.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val artists by vm.artists.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()

    val playlist = if (kind == "playlist") playlists.find { it.id.toString() == key } else null
    val (title, base, sortable) = when (kind) {
        "all" -> Triple("Tous les titres", songs, true)
        "recent" -> Triple("Ajoutés récemment", recentAdded, false)
        "played" -> Triple("Écoutés récemment", played, false)
        "most" -> Triple("Les plus écoutés", most, false)
        "liked" -> Triple("Chansons aimées", liked, false)
        "playlist" -> Triple(playlist?.name ?: "Playlist", playlist?.songIds?.mapNotNull { songMap[it] } ?: emptyList(), false)
        "folder" -> folders.find { it.key == key }.let { Triple(it?.name ?: "Dossier", it?.songs ?: emptyList(), true) }
        "artist" -> artists.find { it.key == key }.let { Triple(it?.name ?: "Artiste", it?.songs ?: emptyList(), true) }
        "album" -> albums.find { it.key == key }.let { Triple(it?.name ?: "Album", it?.songs ?: emptyList(), false) }
        else -> Triple("Titres", songs, true)
    }

    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Roxi.Bg)
            .statusBarsPadding()
    ) {
        ScreenHeader("$title (${base.size})", onBack = { nav.popBackStack() }) {
            if (playlist != null) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Renommer") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { menu = false; rename = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Supprimer la playlist") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { menu = false; delete = true },
                        )
                    }
                }
            }
        }
        if (playlist != null) {
            PlaylistSongList(base, playlist.id)
        } else {
            SongListContent(base, sortable = sortable, playlistId = null)
        }
    }

    if (rename && playlist != null) {
        NameDialog("Renommer la playlist", playlist.name, onDismiss = { rename = false }) {
            vm.prefs.renamePlaylist(playlist.id, it)
            rename = false
        }
    }
    if (delete && playlist != null) {
        AlertDialog(
            onDismissRequest = { delete = false },
            containerColor = Roxi.Surface,
            title = { Text("Supprimer « ${playlist.name} » ?") },
            text = { Text("Les chansons restent sur ton téléphone.") },
            confirmButton = {
                TextButton(onClick = {
                    delete = false
                    nav.popBackStack()
                    vm.prefs.deletePlaylist(playlist.id)
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { delete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
fun SongListContent(base: List<Song>, sortable: Boolean, playlistId: Long?) {
    val vm = LocalVm.current
    val order by vm.prefs.sortOrder.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val bottom = LocalBottomPadding.current
    val list = remember(base, order, sortable) { if (sortable) base.sortSongs(order) else base }
    val listState = rememberLazyListState()

    // Sélection multiple : appui long sur un titre
    val selected = remember { mutableStateListOf<Long>() }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selected.clear() }
    LaunchedEffect(list) {
        val ids = list.map { it.id }.toSet()
        selected.retainAll { it in ids }
    }
    fun toggle(id: Long) {
        if (id in selected) selected.remove(id) else selected.add(id)
    }

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            SelectionBar(
                count = selected.size,
                total = list.size,
                chosen = { list.filter { it.id in selected } },
                onSelectAll = {
                    if (selected.size == list.size) selected.clear()
                    else {
                        selected.clear()
                        selected.addAll(list.map { it.id })
                    }
                },
                onClose = { selected.clear() },
            )
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { vm.shuffle(list) },
                    enabled = list.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Roxi.Violet, contentColor = Color.White),
                ) {
                    Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Lecture aléatoire")
                }
                Spacer(Modifier.weight(1f))
                if (sortable) SortButton(order) { vm.prefs.setSortOrder(it) }
            }
        }

        if (list.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = bottom),
                contentAlignment = Alignment.Center,
            ) {
                if (scanning) CircularProgressIndicator() else Text("Aucun titre ici pour l'instant", color = Roxi.TextSub)
            }
        } else Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = bottom + 8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                    SongRow(
                        song = s,
                        onClick = { if (selecting) toggle(s.id) else vm.play(list, i) },
                        onLongClick = { toggle(s.id) },
                        selectionMode = selecting,
                        selected = s.id in selected,
                        extraMenu = if (playlistId != null) playlistMenu(vm, playlistId, s) else null,
                    )
                }
            }
            if (!selecting && sortable && (order == SortOrder.AZ || order == SortOrder.ZA) && list.size > 20) {
                AlphabetIndex(
                    list, listState,
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(bottom = bottom),
                )
            }
        }
    }
}

/** Barre affichée pendant la sélection multiple. */
@Composable
private fun SelectionBar(
    count: Int,
    total: Int,
    chosen: () -> List<Song>,
    onSelectAll: () -> Unit,
    onClose: () -> Unit,
) {
    val vm = LocalVm.current
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf<List<Song>?>(null) }
    var deleteDialog by remember { mutableStateOf<List<Song>?>(null) }
    val deleter = rememberSongDeleter { vm.onSongsDeleted(it) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Roxi.SurfaceHigh)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Annuler la sélection") }
        Text("$count sélectionné(s)", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        IconButton(onClick = onSelectAll) {
            Icon(
                if (count == total) Icons.Rounded.RemoveDone else Icons.Rounded.DoneAll,
                if (count == total) "Tout désélectionner" else "Tout sélectionner",
            )
        }
        IconButton(onClick = { addDialog = chosen() }) {
            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Ajouter à une playlist")
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Actions") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Lire maintenant") },
                    leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                    onClick = {
                        menu = false
                        vm.play(chosen(), 0)
                        onClose()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Lire ensuite") },
                    leadingIcon = { Icon(Icons.Rounded.SkipNext, null) },
                    onClick = {
                        menu = false
                        vm.playNextAll(chosen())
                        Toast.makeText(context, "Seront lus ensuite", Toast.LENGTH_SHORT).show()
                        onClose()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Ajouter à la file d'attente") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                    onClick = {
                        menu = false
                        vm.queueAll(chosen())
                        Toast.makeText(context, "Ajoutés à la file d'attente", Toast.LENGTH_SHORT).show()
                        onClose()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Masquer") },
                    leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) },
                    onClick = {
                        menu = false
                        vm.hideAll(chosen())
                        Toast.makeText(context, "Titres masqués", Toast.LENGTH_SHORT).show()
                        onClose()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Supprimer du téléphone") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = {
                        menu = false
                        deleteDialog = chosen()
                    },
                )
            }
        }
    }

    addDialog?.let { songs ->
        AddToPlaylistDialog(songs) {
            addDialog = null
            onClose()
        }
    }
    deleteDialog?.let { songs ->
        ConfirmDeleteDialog(songs, onDismiss = { deleteDialog = null }) {
            deleteDialog = null
            deleter(songs)
            onClose()
        }
    }
}

/** Playlist : on réorganise en tenant la poignée ≡ et en glissant. */
@Composable
private fun PlaylistSongList(base: List<Song>, playlistId: Long) {
    val vm = LocalVm.current
    val bottom = LocalBottomPadding.current
    val items = remember { mutableStateListOf<Song>().apply { addAll(base) } }
    val listState = rememberLazyListState()
    val reorder = remember(listState) {
        ReorderState(
            listState,
            onMove = { from, to -> items.add(to, items.removeAt(from)) },
            onDrop = { _, _ -> vm.prefs.reorderPlaylist(playlistId, items.map { it.id }) },
        )
    }
    LaunchedEffect(base) {
        if (reorder.draggingKey == null) {
            items.clear()
            items.addAll(base)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { vm.shuffle(items.toList()) },
                enabled = items.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Roxi.Violet, contentColor = Color.White),
            ) {
                Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Lecture aléatoire")
            }
            Spacer(Modifier.weight(1f))
            if (items.size > 1) {
                Text("Tiens ≡ pour déplacer", style = MaterialTheme.typography.bodySmall, color = Roxi.TextSub)
            }
        }
        if (items.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = bottom),
                contentAlignment = Alignment.Center,
            ) {
                Text("Playlist vide : ajoute des titres avec le menu ⋮", color = Roxi.TextSub)
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = bottom + 8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items, key = { _, s -> s.id }) { i, s ->
                    val dragging = reorder.isDragging(s.id)
                    SongRow(
                        song = s,
                        onClick = { if (reorder.draggingKey == null) vm.play(items.toList(), i) },
                        modifier = Modifier
                            .then(if (dragging) Modifier else Modifier.animateItem())
                            .draggedItem(reorder, dragging, Roxi.SurfaceHigh),
                        extraMenu = playlistMenu(vm, playlistId, s),
                        trailing = { DragHandle(reorder, s.id, Roxi.TextSub) },
                    )
                }
            }
        }
    }
}

private fun playlistMenu(vm: MainViewModel, playlistId: Long, song: Song): @Composable ColumnScope.(() -> Unit) -> Unit =
    { close ->
        DropdownMenuItem(
            text = { Text("Retirer de la playlist") },
            leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) },
            onClick = { vm.prefs.removeFromPlaylist(playlistId, song.id); close() },
        )
    }

@Composable
private fun SortButton(order: SortOrder, onSelect: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Trier")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortOrder.entries.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o.label) },
                    trailingIcon = { if (o == order) Icon(Icons.Rounded.Check, null, tint = Roxi.VioletSoft) },
                    onClick = {
                        onSelect(o)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AlphabetIndex(list: List<Song>, state: LazyListState, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    val firstIndex = remember(list) {
        val m = LinkedHashMap<Char, Int>()
        list.forEachIndexed { i, s ->
            val c = indexLetter(s.title)
            if (c !in m) m[c] = i
        }
        m
    }
    Column(
        modifier
            .fillMaxHeight()
            .padding(end = 2.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEach { ch ->
            val target = firstIndex[ch]
            Text(
                ch.toString(),
                fontSize = 10.sp,
                style = MaterialTheme.typography.labelSmall,
                color = if (target != null) Roxi.VioletSoft else Roxi.TextSub.copy(alpha = 0.35f),
                modifier = Modifier
                    .clickable(enabled = target != null) {
                        if (target != null) scope.launch { state.scrollToItem(target) }
                    }
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Roxi.Surface,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Nom") })
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
