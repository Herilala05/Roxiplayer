package com.roxi.player.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.roxi.player.data.formatTime
import com.roxi.player.ui.components.LocalBottomPadding
import com.roxi.player.ui.theme.Roxi
import com.roxi.player.video.SecretVault
import com.roxi.player.video.VideoItem
import com.roxi.player.video.VideoPlayerActivity
import com.roxi.player.video.VideoRepository
import kotlinx.coroutines.launch

@Composable
fun VideoScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { VideoRepository(context) }
    val vault = remember { SecretVault(context) }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var secretVideos by remember { mutableStateOf(vault.list()) }
    var secretMode by rememberSaveable { mutableStateOf(false) }
    var unlocked by rememberSaveable { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    var showUrl by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val bottom = LocalBottomPadding.current

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { unlocked = false }

    fun refresh() {
        scope.launch {
            refreshing = true
            videos = repository.scan()
            secretVideos = vault.list()
            refreshing = false
        }
    }

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) refresh() }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) refresh()
        else permissionLauncher.launch(permission)
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            val imported = uris.count { vault.import(it) }
            secretVideos = vault.list()
            Toast.makeText(context, "$imported copie(s) ajoutée(s) au dossier secret", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(48.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Vidéos", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Roxi.Text, modifier = Modifier.weight(1f))
            IconButton(onClick = { showUrl = true }) { Icon(Icons.Rounded.Link, "Lire un lien", tint = Roxi.Text) }
            IconButton(onClick = ::refresh) { Icon(Icons.Rounded.Refresh, "Actualiser", tint = Roxi.Text) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !secretMode, onClick = { secretMode = false }, label = { Text("Toutes") }, leadingIcon = { Icon(Icons.Rounded.VideoLibrary, null) })
            FilterChip(selected = secretMode, onClick = {
                secretMode = true
                if (!unlocked) showPin = true
            }, label = { Text("Dossier secret") }, leadingIcon = { Icon(Icons.Rounded.Lock, null) })
        }
        Spacer(Modifier.height(12.dp))

        when {
            secretMode && !unlocked -> LockedPanel { showPin = true }
            secretMode -> {
                Button(onClick = { importer.launch(arrayOf("video/*")) }, colors = ButtonDefaults.buttonColors(containerColor = Roxi.VioletSoft)) {
                    Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Copier des vidéos ici")
                }
                VideoList(secretVideos, bottom, onPlay = { VideoPlayerActivity.play(context, it.uri) }, onDelete = {
                    vault.delete(it); secretVideos = vault.list()
                })
            }
            else -> {
                if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                VideoList(videos, bottom, onPlay = { VideoPlayerActivity.play(context, it.uri) })
            }
        }
    }

    if (showPin) PinDialog(
        creating = !vault.hasPin(),
        onDismiss = { showPin = false; if (!unlocked) secretMode = false },
        onConfirm = { pin ->
            val ok = if (vault.hasPin()) vault.verify(pin) else vault.setPin(pin)
            if (ok) { unlocked = true; showPin = false; secretMode = true }
            else Toast.makeText(context, if (vault.hasPin()) "Code incorrect" else "Utilise au moins 4 chiffres", Toast.LENGTH_SHORT).show()
        },
    )
    if (showUrl) UrlDialog(onDismiss = { showUrl = false }) { value ->
        runCatching { Uri.parse(value.trim()) }.getOrNull()?.takeIf { it.scheme == "http" || it.scheme == "https" }?.let {
            showUrl = false
            VideoPlayerActivity.play(context, it)
        } ?: Toast.makeText(context, "Lien invalide", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun LockedPanel(onUnlock: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Lock, null, tint = Roxi.VioletSoft, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp)); Text("Dossier secret verrouillé", color = Roxi.Text, fontWeight = FontWeight.Bold)
        TextButton(onClick = onUnlock) { Text("Déverrouiller") }
    }
}

@Composable
private fun VideoList(items: List<VideoItem>, bottom: androidx.compose.ui.unit.Dp, onPlay: (VideoItem) -> Unit, onDelete: ((VideoItem) -> Unit)? = null) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Aucune vidéo", color = Roxi.TextSub) }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = bottom + 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.id }) { video ->
            Surface(Modifier.fillMaxWidth().clickable { onPlay(video) }, shape = RoundedCornerShape(14.dp), color = Roxi.Surface) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Roxi.SurfaceHigh, modifier = Modifier.size(58.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayCircle, null, tint = Roxi.VioletSoft, modifier = Modifier.size(34.dp)) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(video.title, color = Roxi.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        val details = buildList {
                            if (video.durationMs > 0) add(formatTime(video.durationMs))
                            if (video.size > 0) add("%.1f Mo".format(video.size / 1_048_576.0))
                        }.joinToString(" • ")
                        Text(details, color = Roxi.TextSub, style = MaterialTheme.typography.bodySmall)
                    }
                    if (onDelete != null) IconButton(onClick = { onDelete(video) }) { Icon(Icons.Rounded.Delete, "Supprimer", tint = Roxi.TextSub) }
                }
            }
        }
    }
}

@Composable
private fun PinDialog(creating: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (creating) "Créer un code PIN" else "Code PIN") },
        text = { OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text("4 à 8 chiffres") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(pin) }) { Text(if (creating) "Créer" else "Déverrouiller") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } })
}

@Composable
private fun UrlDialog(onDismiss: () -> Unit, onPlay: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Lire une vidéo en ligne") },
        text = { OutlinedTextField(url, { url = it }, label = { Text("Lien direct MP4, HLS ou DASH") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onPlay(url) }) { Text("Lire") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } })
}
