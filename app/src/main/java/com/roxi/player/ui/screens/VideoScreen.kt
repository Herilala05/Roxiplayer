package com.roxi.player.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.text.format.DateUtils
import android.util.LruCache
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class VideoSort(val label: String) {
    RECENT("Plus récentes"), NAME("Nom"), SIZE("Taille")
}

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
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFolder by rememberSaveable { mutableStateOf("Toutes") }
    var gridMode by rememberSaveable { mutableStateOf(true) }
    var sort by rememberSaveable { mutableStateOf(VideoSort.RECENT) }
    var showSort by remember { mutableStateOf(false) }
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

    val folders = remember(videos) {
        videos.groupingBy { it.folder }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase() })
    }
    val source = if (secretMode) secretVideos else videos
    val displayed = remember(source, secretMode, selectedFolder, query, sort) {
        source.asSequence()
            .filter { secretMode || selectedFolder == "Toutes" || it.folder == selectedFolder }
            .filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
            .let { sequence ->
                when (sort) {
                    VideoSort.RECENT -> sequence.sortedByDescending { it.dateAdded }
                    VideoSort.NAME -> sequence.sortedBy { it.title.lowercase() }
                    VideoSort.SIZE -> sequence.sortedByDescending { it.size }
                }
            }.toList()
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(44.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (secretMode) "Dossier secret" else "Vidéos",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Roxi.Text,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showUrl = true }) { Icon(Icons.Rounded.Link, "Lire un lien", tint = Roxi.Text) }
            IconButton(onClick = ::refresh) { Icon(Icons.Rounded.Refresh, "Actualiser", tint = Roxi.Text) }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Rechercher dans les vidéos") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Effacer") }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Roxi.Surface,
                unfocusedContainerColor = Roxi.Surface,
                focusedBorderColor = Roxi.VioletSoft,
                unfocusedBorderColor = Roxi.SurfaceHigh,
            ),
        )

        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = !secretMode && selectedFolder == "Toutes",
                    onClick = { secretMode = false; selectedFolder = "Toutes" },
                    label = { Text("Toutes") },
                    leadingIcon = { Icon(Icons.Rounded.VideoLibrary, null) },
                )
            }
            items(folders, key = { it.key }) { entry ->
                FilterChip(
                    selected = !secretMode && selectedFolder == entry.key,
                    onClick = { secretMode = false; selectedFolder = entry.key },
                    label = { Text("${entry.key}  ${entry.value}") },
                    leadingIcon = { Icon(Icons.Rounded.Folder, null) },
                )
            }
            item {
                FilterChip(
                    selected = secretMode,
                    onClick = { secretMode = true; if (!unlocked) showPin = true },
                    label = { Text("Secret") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            val totalSize = displayed.sumOf { it.size }
            Text(
                "${displayed.size} vidéo${if (displayed.size > 1) "s" else ""} • ${formatSize(totalSize)}",
                color = Roxi.TextSub,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Box {
                TextButton(onClick = { showSort = true }) {
                    Icon(Icons.Rounded.Sort, null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(sort.label)
                }
                DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                    VideoSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = { if (sort == option) Icon(Icons.Rounded.Check, null) },
                            onClick = { sort = option; showSort = false },
                        )
                    }
                }
            }
            IconButton(onClick = { gridMode = !gridMode }) {
                Icon(if (gridMode) Icons.Rounded.ViewList else Icons.Rounded.GridView, if (gridMode) "Afficher en liste" else "Afficher en grille")
            }
        }

        if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())

        when {
            secretMode && !unlocked -> LockedPanel { showPin = true }
            else -> {
                if (secretMode) {
                    Button(
                        onClick = { importer.launch(arrayOf("video/*")) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Roxi.Violet),
                    ) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Ajouter des vidéos")
                    }
                }
                VideoBrowser(
                    items = displayed,
                    gridMode = gridMode,
                    bottom = bottom,
                    emptyMessage = if (query.isNotBlank()) "Aucun résultat" else "Aucune vidéo trouvée",
                    onPlay = { selected ->
                        VideoPlayerActivity.play(
                            context = context,
                            uri = selected.uri,
                            playlist = displayed.map { it.uri },
                            titles = displayed.map { it.title },
                        )
                    },
                    onDelete = if (secretMode) ({ video -> vault.delete(video); secretVideos = vault.list() }) else null,
                )
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
private fun VideoBrowser(
    items: List<VideoItem>,
    gridMode: Boolean,
    bottom: androidx.compose.ui.unit.Dp,
    emptyMessage: String,
    onPlay: (VideoItem) -> Unit,
    onDelete: ((VideoItem) -> Unit)?,
) {
    if (items.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(bottom = bottom + 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.VideoLibrary, null, tint = Roxi.TextSub, modifier = Modifier.size(58.dp))
            Spacer(Modifier.height(12.dp))
            Text(emptyMessage, color = Roxi.TextSub)
        }
        return
    }
    if (gridMode) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottom + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items, key = { it.id }) { video -> VideoGridCard(video, onPlay, onDelete) }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = bottom + 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items, key = { it.id }) { video -> VideoListCard(video, onPlay, onDelete) }
        }
    }
}

@Composable
private fun VideoGridCard(video: VideoItem, onPlay: (VideoItem) -> Unit, onDelete: ((VideoItem) -> Unit)?) {
    Column(Modifier.fillMaxWidth().clickable { onPlay(video) }) {
        Box {
            VideoThumbnail(video, Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(RoundedCornerShape(14.dp)))
            if (video.durationMs > 0) {
                Surface(
                    color = androidx.compose.ui.graphics.Color.Black.copy(alpha = .72f),
                    shape = RoundedCornerShape(5.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                ) {
                    Text(formatTime(video.durationMs), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 7.dp, start = 2.dp)) {
                Text(video.title, color = Roxi.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                Text("${formatSize(video.size)} • ${relativeDate(video.dateAdded)}", color = Roxi.TextSub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            }
            VideoMenu(video, onPlay, onDelete)
        }
    }
}

@Composable
private fun VideoListCard(video: VideoItem, onPlay: (VideoItem) -> Unit, onDelete: ((VideoItem) -> Unit)?) {
    Surface(modifier = Modifier.fillMaxWidth().clickable { onPlay(video) }, shape = RoundedCornerShape(14.dp), color = Roxi.Surface) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                VideoThumbnail(video, Modifier.width(116.dp).aspectRatio(16f / 10f).clip(RoundedCornerShape(10.dp)))
                if (video.durationMs > 0) {
                    Text(
                        formatTime(video.durationMs),
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomEnd).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .72f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, color = Roxi.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text("${formatSize(video.size)} • ${relativeDate(video.dateAdded)}", color = Roxi.TextSub, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                Text(video.folder, color = Roxi.VioletSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            }
            VideoMenu(video, onPlay, onDelete)
        }
    }
}

@Composable
private fun VideoMenu(video: VideoItem, onPlay: (VideoItem) -> Unit, onDelete: ((VideoItem) -> Unit)?) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.MoreVert, "Options", tint = Roxi.TextSub) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Lire") },
                leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                onClick = { expanded = false; onPlay(video) },
            )
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text("Supprimer du dossier secret") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = { expanded = false; onDelete(video) },
                )
            }
        }
    }
}

private object VideoThumbCache {
    private val cache = LruCache<String, ImageBitmap>(80)

    fun peek(id: String): ImageBitmap? = cache.get(id)

    fun load(context: Context, video: VideoItem): ImageBitmap? {
        cache.get(video.id)?.let { return it }
        val bitmap: Bitmap? = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) context.contentResolver.loadThumbnail(video.uri, Size(640, 400), null) else null
        }.getOrNull() ?: runCatching {
            MediaMetadataRetriever().let { retriever ->
                try {
                    retriever.setDataSource(context, video.uri)
                    retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } finally {
                    retriever.release()
                }
            }
        }.getOrNull()
        return bitmap?.asImageBitmap()?.also { cache.put(video.id, it) }
    }
}

@Composable
private fun VideoThumbnail(video: VideoItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState(initialValue = VideoThumbCache.peek(video.id), key1 = video.id) {
        if (value == null) value = withContext(Dispatchers.IO) { VideoThumbCache.load(context, video) }
    }
    Box(modifier.background(Roxi.SurfaceHigh), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) Image(image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.PlayCircle, null, tint = Roxi.VioletSoft, modifier = Modifier.size(42.dp))
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format(Locale.getDefault(), "%.1f Go", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> String.format(Locale.getDefault(), "%.1f Mo", bytes / 1_048_576.0)
    bytes >= 1024L -> String.format(Locale.getDefault(), "%.0f Ko", bytes / 1024.0)
    else -> "$bytes o"
}

private fun relativeDate(seconds: Long): String {
    if (seconds <= 0L) return "Date inconnue"
    return DateUtils.getRelativeTimeSpanString(seconds * 1000L, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
}

@Composable
private fun LockedPanel(onUnlock: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.Lock, null, tint = Roxi.VioletSoft, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Dossier secret verrouillé", color = Roxi.Text, fontWeight = FontWeight.Bold)
        Text("Tes vidéos privées restent protégées par ton code PIN.", color = Roxi.TextSub, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onUnlock) { Text("Déverrouiller") }
    }
}

@Composable
private fun PinDialog(creating: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "Créer un code PIN" else "Code PIN") },
        text = { OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8) }, label = { Text("4 à 8 chiffres") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(pin) }) { Text(if (creating) "Créer" else "Déverrouiller") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun UrlDialog(onDismiss: () -> Unit, onPlay: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lire une vidéo en ligne") },
        text = { OutlinedTextField(url, { url = it }, label = { Text("Lien direct MP4, HLS ou DASH") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onPlay(url) }) { Text("Lire") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
