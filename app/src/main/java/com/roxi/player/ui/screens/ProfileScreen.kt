package com.roxi.player.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.roxi.player.R
import com.roxi.player.data.Backup
import com.roxi.player.data.formatListenTime
import com.roxi.player.ui.components.LocalBottomPadding
import com.roxi.player.ui.components.LocalVm
import com.roxi.player.ui.player.SleepTimerDialog
import com.roxi.player.ui.theme.Roxi
import java.time.LocalDate
import kotlin.system.exitProcess

@Composable
fun ProfileScreen(nav: NavController) {
    val vm = LocalVm.current
    val context = LocalContext.current
    val songs by vm.songs.collectAsStateWithLifecycle()
    val liked by vm.prefs.liked.collectAsStateWithLifecycle()
    val listenMs by vm.prefs.listenMs.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val minDuration by vm.prefs.minDurationSec.collectAsStateWithLifecycle()
    val lyricsTree by vm.prefs.lyricsTree.collectAsStateWithLifecycle()
    val sleepEnd by vm.sleepTimerEnd.collectAsStateWithLifecycle()
    val hiddenSongs by vm.hiddenSongs.collectAsStateWithLifecycle()
    val normalize by vm.prefs.normalize.collectAsStateWithLifecycle()
    val normalizeLevel by vm.prefs.normalizeLevel.collectAsStateWithLifecycle()
    val crossfade by vm.prefs.crossfadeSec.collectAsStateWithLifecycle()
    val autoLyrics by vm.prefs.autoLyrics.collectAsStateWithLifecycle()
    val themeMode by vm.prefs.themeMode.collectAsStateWithLifecycle()
    val accent by vm.prefs.accent.collectAsStateWithLifecycle()
    val bottom = LocalBottomPadding.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.3.2"
        } catch (e: Exception) {
            "1.3.2"
        }
    }

    var dialog by remember { mutableStateOf<String?>(null) }
    var restoreDone by remember { mutableStateOf(false) }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                vm.prefs.setLyricsTree(uri.toString())
                Toast.makeText(context, "Dossier des paroles enregistré", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Impossible d'utiliser ce dossier", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = vm.exportBackup(uri)
            Toast.makeText(
                context,
                if (ok) "Sauvegarde enregistrée" else "Sauvegarde impossible",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            when (vm.importBackup(uri)) {
                Backup.Result.OK -> restoreDone = true
                Backup.Result.INVALID -> Toast.makeText(context, "Ce fichier n'est pas une sauvegarde Roxi Player", Toast.LENGTH_LONG).show()
                Backup.Result.ERROR -> Toast.makeText(context, "Restauration impossible", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Roxi.Bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painterResource(R.drawable.roxi_logo), null,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(10.dp))
            Text("Roxi Player", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard("${songs.size}", "Titres", Modifier.weight(1f))
            StatCard("${liked.size}", "Favoris", Modifier.weight(1f))
            StatCard(formatListenTime(listenMs), "Écoute", Modifier.weight(1f))
        }

        Group("Musique")
        SettingRow(Icons.Rounded.BarChart, "Statistiques", "Temps d'écoute, top titres et artistes") {
            nav.navigate("stats")
        }
        SettingRow(
            Icons.Rounded.Refresh, "Rechercher la musique",
            if (scanning) "Recherche en cours…" else "${songs.size} titre(s) trouvé(s)",
        ) {
            vm.rescan()
            Toast.makeText(context, "Recherche lancée", Toast.LENGTH_SHORT).show()
        }
        SettingRow(
            Icons.Rounded.HourglassEmpty, "Durée minimale des fichiers",
            if (minDuration == 0) "Tous les fichiers" else "Ignorer les fichiers de moins de $minDuration s",
        ) { dialog = "duration" }
        SettingRow(
            Icons.Rounded.VisibilityOff, "Titres masqués",
            if (hiddenSongs.isEmpty()) "Aucun" else "${hiddenSongs.size} titre(s)",
        ) { dialog = "hidden" }
        SettingRow(Icons.Rounded.Timer, "Minuterie de sommeil", vm.sleepTimerLabel(sleepEnd)) { dialog = "sleep" }

        Group("Son")
        SwitchRow(
            Icons.Rounded.VolumeUp, "Normaliser le volume",
            "Toutes les chansons au même niveau (aussi en Bluetooth)",
            normalize,
        ) { vm.setNormalize(it) }
        if (normalize) {
            SettingRow(Icons.Rounded.GraphicEq, "Niveau", levelNames[normalizeLevel]) { dialog = "level" }
        }
        SettingRow(
            Icons.Rounded.SwapHoriz, "Fondu entre les chansons",
            if (crossfade == 0) "Désactivé" else "$crossfade secondes",
        ) { dialog = "crossfade" }
        SettingRow(Icons.Rounded.Equalizer, "Égaliseur", "Préréglages, fréquences et basses") {
            nav.navigate("equalizer")
        }

        Group("Paroles")
        SwitchRow(
            Icons.Rounded.CloudDownload, "Paroles automatiques",
            "Cherche en ligne les paroles manquantes (Internet requis)",
            autoLyrics,
        ) { vm.prefs.setAutoLyrics(it) }
        SettingRow(
            Icons.Rounded.FolderOpen, "Dossier des paroles (.lrc)",
            lyricsTree?.let { Uri.decode(it).substringAfterLast(':') }?.ifBlank { "Choisi" } ?: "Aucun dossier choisi",
        ) { treePicker.launch(null) }
        if (lyricsTree != null) {
            SettingRow(Icons.Rounded.LinkOff, "Oublier le dossier des paroles", "") {
                vm.prefs.setLyricsTree(null)
            }
        }

        Group("Apparence")
        SettingRow(Icons.Rounded.DarkMode, "Thème", themeNames[themeMode]) { dialog = "theme" }
        SettingRow(Icons.Rounded.Palette, "Couleur", Roxi.accents[accent].name) { dialog = "accent" }

        Group("Données")
        SettingRow(Icons.Rounded.Save, "Sauvegarder", "Favoris, playlists, statistiques et réglages") {
            exporter.launch("RoxiPlayer-sauvegarde-${LocalDate.now()}.json")
        }
        SettingRow(Icons.Rounded.Restore, "Restaurer une sauvegarde", "Remplace les données actuelles") {
            dialog = "restore"
        }
        SettingRow(Icons.Rounded.Info, "À propos", "Version $version") { dialog = "about" }

        Spacer(Modifier.height(bottom + 16.dp))
    }

    val close = { dialog = null }
    when (dialog) {
        "duration" -> ChoiceDialog(
            "Durée minimale",
            listOf(0, 15, 30, 60).map { if (it == 0) "Tous les fichiers" else "$it secondes" },
            listOf(0, 15, 30, 60).indexOf(minDuration),
            close,
        ) { vm.prefs.setMinDuration(listOf(0, 15, 30, 60)[it]) }
        "level" -> ChoiceDialog("Niveau du volume", levelNames, normalizeLevel, close) { vm.setNormalizeLevel(it) }
        "crossfade" -> ChoiceDialog(
            "Fondu entre les chansons",
            fadeOptions.map { if (it == 0) "Désactivé" else "$it secondes" },
            fadeOptions.indexOf(crossfade),
            close,
        ) { vm.prefs.setCrossfade(fadeOptions[it]) }
        "theme" -> ChoiceDialog("Thème", themeNames, themeMode, close) { vm.prefs.setThemeMode(it) }
        "accent" -> AccentDialog(accent, close) { vm.prefs.setAccent(it) }
        "sleep" -> SleepTimerDialog(close)
        "hidden" -> HiddenDialog(close)
        "restore" -> AlertDialog(
            onDismissRequest = close,
            containerColor = Roxi.Surface,
            title = { Text("Restaurer une sauvegarde ?") },
            text = { Text("Tes favoris, playlists, statistiques et réglages actuels seront remplacés par ceux du fichier.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    importer.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                }) { Text("Choisir le fichier") }
            },
            dismissButton = { TextButton(onClick = close) { Text("Annuler") } },
        )
        "about" -> AlertDialog(
            onDismissRequest = close,
            containerColor = Roxi.Surface,
            title = { Text("Roxi Player") },
            text = {
                Text(
                    "Lecteur de musique locale — version $version\n\n" +
                        "Nouveautés 1.3 : normalisation du volume, égaliseur, fondu, paroles automatiques, " +
                        "sélection multiple, widget, modification des infos, sauvegarde, thèmes, statistiques " +
                        "et animation d'entrée.\n" +
                        "1.2 : nouveau lecteur. 1.1 : file d'attente réorganisable, couleur de pochette."
                )
            },
            confirmButton = { TextButton(onClick = close) { Text("OK") } },
        )
    }

    if (restoreDone) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = Roxi.Surface,
            title = { Text("Sauvegarde restaurée") },
            text = { Text("Roxi Player va redémarrer pour appliquer tes données.") },
            confirmButton = {
                TextButton(onClick = {
                    val activity = context as? Activity
                    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    if (activity != null && launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        activity.startActivity(launch)
                        activity.finishAffinity()
                    }
                    exitProcess(0)
                }) { Text("Redémarrer") }
            },
        )
    }
}

private val levelNames = listOf("Doux", "Normal", "Fort")
private val themeNames = listOf("Sombre", "Clair", "Comme le téléphone")
private val fadeOptions = listOf(0, 2, 4, 6, 8, 10, 12)

@Composable
private fun Group(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Roxi.VioletSoft,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Roxi.Surface,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, label ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onSelect(i)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            color = if (i == selected) Roxi.VioletSoft else Roxi.Text,
                            fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (i == selected) Icon(Icons.Rounded.Check, null, tint = Roxi.VioletSoft)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun AccentDialog(selected: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Roxi.Surface,
        title = { Text("Couleur de l'appli") },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Roxi.accents.forEachIndexed { i, a ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(a.main)
                                .border(3.dp, if (i == selected) Roxi.Text else Color.Transparent, CircleShape)
                                .clickable { onSelect(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (i == selected) Icon(Icons.Rounded.Check, null, tint = Color.White)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(a.name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun HiddenDialog(onDismiss: () -> Unit) {
    val vm = LocalVm.current
    val hiddenSongs by vm.hiddenSongs.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Roxi.Surface,
        title = { Text("Titres masqués") },
        text = {
            if (hiddenSongs.isEmpty()) {
                Text("Aucun titre masqué.")
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(hiddenSongs, key = { it.id }) { song ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall, color = Roxi.TextSub,
                                )
                            }
                            TextButton(onClick = { vm.unhide(song) }) { Text("Afficher") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Roxi.Surface)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Roxi.TextSub)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Roxi.VioletSoft)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall, color = Roxi.TextSub,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Roxi.VioletSoft)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Roxi.TextSub)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Roxi.Violet),
        )
    }
}
