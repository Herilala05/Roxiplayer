package com.roxi.player.ui.components

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roxi.player.MainViewModel
import com.roxi.player.data.Song
import com.roxi.player.data.formatTime
import com.roxi.player.ui.theme.Roxi
import java.util.Date

val LocalVm = staticCompositionLocalOf<MainViewModel> { error("ViewModel non fourni") }

/** Espace à laisser en bas des listes (barre de navigation + mini-lecteur). */
val LocalBottomPadding = staticCompositionLocalOf { 0.dp }

@Composable
fun SectionHeader(title: String, onSeeAll: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) { Text("Voir tout", color = Roxi.VioletSoft) }
        }
    }
}

@Composable
fun LyricsBadge() {
    Text(
        "PAROLES",
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = Roxi.TextSub,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Roxi.SurfaceHigh)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/**
 * Ligne d'un morceau avec pochette, badge PAROLES, artiste et menu « ⋮ ».
 * En mode sélection, une case à cocher remplace le menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    extraMenu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val vm = LocalVm.current
    val current by vm.currentSong.collectAsStateWithLifecycle()
    val lyricsVersion by vm.lyrics.version.collectAsStateWithLifecycle()
    val isCurrent = current?.id == song.id
    val hasLyrics by produceState(false, song.id, lyricsVersion) { value = vm.lyrics.hasLyrics(song) }
    var menu by remember { mutableStateOf(false) }

    Row(
        modifier
            .fillMaxWidth()
            .background(if (selected) Roxi.VioletSoft.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AlbumArt(song, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) Roxi.VioletSoft else Roxi.Text,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasLyrics) {
                    LyricsBadge()
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (song.album == "Album inconnu") song.artist else "${song.artist} · ${song.album}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = Roxi.TextSub,
                )
            }
        }
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(checkedColor = Roxi.VioletSoft, checkmarkColor = Roxi.OnAccentSoft),
            )
        } else {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Options", tint = Roxi.TextSub)
                }
                SongMenu(song, expanded = menu, onDismiss = { menu = false }, extraMenu = extraMenu)
            }
            trailing?.invoke()
        }
    }
}

/**
 * Suppression de fichiers (un ou plusieurs). Sur Android 11+, c'est Android qui affiche
 * sa propre fenêtre de confirmation.
 */
@Composable
fun rememberSongDeleter(onDeleted: (List<Song>) -> Unit): (List<Song>) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<List<Song>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val songs = pending
        pending = emptyList()
        if (result.resultCode != Activity.RESULT_OK || songs.isEmpty()) return@rememberLauncherForActivityResult
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Android 10 : l'autorisation est donnée, on relance la suppression
            songs.forEach {
                try {
                    context.contentResolver.delete(it.uri, null, null)
                } catch (e: Exception) {
                }
            }
        }
        onDeleted(songs)
        Toast.makeText(context, "${songs.size} titre(s) supprimé(s)", Toast.LENGTH_SHORT).show()
    }

    return remember(launcher) {
        { songs: List<Song> ->
            if (songs.isNotEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        pending = songs
                        val request = MediaStore.createDeleteRequest(context.contentResolver, songs.map { it.uri })
                        launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    } catch (e: Exception) {
                        pending = emptyList()
                        Toast.makeText(context, "Suppression impossible", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val done = ArrayList<Song>()
                    var needsPermission: RecoverableSecurityException? = null
                    var needsPermissionSong: Song? = null
                    for (song in songs) {
                        try {
                            if (context.contentResolver.delete(song.uri, null, null) > 0) done += song
                        } catch (e: SecurityException) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                e is RecoverableSecurityException && needsPermission == null
                            ) {
                                needsPermission = e
                                needsPermissionSong = song
                            }
                        } catch (e: Exception) {
                            // on passe au suivant
                        }
                    }
                    if (done.isNotEmpty()) {
                        onDeleted(done)
                        Toast.makeText(context, "${done.size} titre(s) supprimé(s)", Toast.LENGTH_SHORT).show()
                    }
                    val rse = needsPermission
                    val rsSong = needsPermissionSong
                    if (rse != null && rsSong != null) {
                        pending = listOf(rsSong)
                        launcher.launch(IntentSenderRequest.Builder(rse.userAction.actionIntent.intentSender).build())
                    } else if (done.isEmpty()) {
                        Toast.makeText(context, "Suppression impossible", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

/** Menu d'options d'un morceau (réutilisé dans le lecteur). */
@Composable
fun SongMenu(
    song: Song,
    expanded: Boolean,
    onDismiss: () -> Unit,
    showQueueActions: Boolean = true,
    extraMenu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null,
) {
    val vm = LocalVm.current
    val context = LocalContext.current
    val liked by vm.prefs.liked.collectAsStateWithLifecycle()
    var addDialog by remember { mutableStateOf(false) }
    var infoDialog by remember { mutableStateOf(false) }
    var editDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    val deleter = rememberSongDeleter { vm.onSongsDeleted(it) }

    fun setAsRingtone() {
        if (!Settings.System.canWrite(context)) {
            Toast.makeText(
                context,
                "Autorise « Modifier les paramètres système » pour Roxi Player, puis réessaie",
                Toast.LENGTH_LONG,
            ).show()
            try {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) {
                // réglage introuvable sur ce téléphone
            }
            return
        }
        try {
            RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, song.uri)
            Toast.makeText(context, "« ${song.title} » est ta nouvelle sonnerie", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Impossible de définir la sonnerie", Toast.LENGTH_SHORT).show()
        }
    }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (showQueueActions) {
            DropdownMenuItem(
                text = { Text("Lire ensuite") },
                leadingIcon = { Icon(Icons.Rounded.SkipNext, null) },
                onClick = {
                    vm.player.playNext(song)
                    Toast.makeText(context, "Sera lu ensuite", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text("Ajouter à la file d'attente") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                onClick = {
                    vm.player.addToQueue(song)
                    Toast.makeText(context, "Ajouté à la file d'attente", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
            )
        }
        DropdownMenuItem(
            text = { Text("Ajouter à une playlist") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
            onClick = {
                addDialog = true
                onDismiss()
            },
        )
        val isLiked = song.id in liked
        DropdownMenuItem(
            text = { Text(if (isLiked) "Retirer des favoris" else "Ajouter aux favoris") },
            leadingIcon = {
                Icon(if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null)
            },
            onClick = {
                vm.toggleLike(song)
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text("Modifier les infos") },
            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
            onClick = {
                editDialog = true
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text("Infos") },
            leadingIcon = { Icon(Icons.Rounded.Info, null) },
            onClick = {
                infoDialog = true
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text("Partager") },
            leadingIcon = { Icon(Icons.Rounded.Share, null) },
            onClick = {
                onDismiss()
                try {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "audio/*"
                        putExtra(Intent.EXTRA_STREAM, song.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, "Partager ${song.title}"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Partage impossible", Toast.LENGTH_SHORT).show()
                }
            },
        )
        DropdownMenuItem(
            text = { Text("Définir comme sonnerie") },
            leadingIcon = { Icon(Icons.Rounded.NotificationsActive, null) },
            onClick = {
                onDismiss()
                setAsRingtone()
            },
        )
        DropdownMenuItem(
            text = { Text("Masquer") },
            leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) },
            onClick = {
                onDismiss()
                vm.hide(song)
                Toast.makeText(context, "Titre masqué (réaffichable dans Moi)", Toast.LENGTH_SHORT).show()
            },
        )
        DropdownMenuItem(
            text = { Text("Supprimer du téléphone") },
            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
            onClick = {
                onDismiss()
                deleteDialog = true
            },
        )
        if (extraMenu != null) {
            HorizontalDivider()
            extraMenu(onDismiss)
        }
    }

    if (deleteDialog) {
        ConfirmDeleteDialog(listOf(song), onDismiss = { deleteDialog = false }) {
            deleteDialog = false
            deleter(listOf(song))
        }
    }
    if (addDialog) AddToPlaylistDialog(listOf(song)) { addDialog = false }
    if (infoDialog) SongInfoDialog(song) { infoDialog = false }
    if (editDialog) EditSongDialog(song) { editDialog = false }
}

@Composable
fun ConfirmDeleteDialog(songs: List<Song>, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Roxi.Surface,
        title = { Text(if (songs.size == 1) "Supprimer ce titre ?" else "Supprimer ${songs.size} titres ?") },
        text = {
            Text(
                if (songs.size == 1) "« ${songs[0].title} » sera effacé définitivement de ton téléphone."
                else "Ces ${songs.size} fichiers seront effacés définitivement de ton téléphone."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Supprimer", color = Roxi.Pink) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun AddToPlaylistDialog(songs: List<Song>, onDismiss: () -> Unit) {
    val vm = LocalVm.current
    val context = LocalContext.current
    val playlists by vm.prefs.playlists.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }
    val label = if (songs.size == 1) "Ajouté" else "${songs.size} titres ajoutés"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Roxi.Surface,
        title = { Text(if (songs.size == 1) "Ajouter à une playlist" else "Ajouter ${songs.size} titres à une playlist") },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        Text(
                            "${p.name}  (${p.songIds.size})",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    vm.addAllToPlaylist(p.id, songs)
                                    Toast.makeText(context, "$label à ${p.name}", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nouvelle playlist") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            val id = vm.prefs.createPlaylist(newName)
                            vm.addAllToPlaylist(id, songs)
                            Toast.makeText(context, "Playlist créée", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                    ) { Icon(Icons.Rounded.Add, "Créer") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

/** Modifier titre / artiste / album (enregistré dans Roxi Player, le fichier n'est pas touché). */
@Composable
fun EditSongDialog(song: Song, onDismiss: () -> Unit) {
    val vm = LocalVm.current
    val edits by vm.prefs.edits.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    val edited = song.id in edits

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Roxi.Surface,
        title = { Text("Modifier les infos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Titre") }, singleLine = true)
                OutlinedTextField(artist, { artist = it }, label = { Text("Artiste") }, singleLine = true)
                OutlinedTextField(album, { album = it }, label = { Text("Album") }, singleLine = true)
                Text(
                    "Les changements sont visibles dans Roxi Player (le fichier n'est pas modifié).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Roxi.TextSub,
                )
                if (edited) {
                    TextButton(onClick = {
                        vm.resetEdit(song.id)
                        onDismiss()
                    }) { Text("Rétablir les infos d'origine") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.editSong(song.id, title, artist, album)
                onDismiss()
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun SongInfoDialog(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val date = DateFormat.getDateFormat(context).format(Date(song.dateAdded * 1000))
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Roxi.Surface,
        title = { Text(song.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoLine("Artiste", song.artist)
                InfoLine("Album", song.album)
                InfoLine("Durée", formatTime(song.durationMs))
                InfoLine("Taille", "%.1f Mo".format(song.size / 1_048_576f))
                InfoLine("Ajouté le", date)
                InfoLine("Emplacement", song.path)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Roxi.TextSub)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Retour",
                )
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}
