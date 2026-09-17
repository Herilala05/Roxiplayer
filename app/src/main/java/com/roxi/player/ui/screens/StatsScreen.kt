package com.roxi.player.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.roxi.player.data.formatListenTime
import com.roxi.player.ui.components.AlbumArt
import com.roxi.player.ui.components.LocalBottomPadding
import com.roxi.player.ui.components.LocalVm
import com.roxi.player.ui.components.ScreenHeader
import com.roxi.player.ui.components.SectionHeader
import com.roxi.player.ui.theme.Roxi
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen(nav: NavController) {
    val vm = LocalVm.current
    val daily by vm.prefs.daily.collectAsStateWithLifecycle()
    val total by vm.prefs.listenMs.collectAsStateWithLifecycle()
    val counts by vm.prefs.playCounts.collectAsStateWithLifecycle()
    val songMap by vm.songMap.collectAsStateWithLifecycle()
    val bottom = LocalBottomPadding.current

    val today = LocalDate.now()
    val week = remember(daily) {
        (6 downTo 0).map { d ->
            val day = today.minusDays(d.toLong())
            day to (daily[day.toString()] ?: 0L)
        }
    }
    val weekTotal = week.sumOf { it.second }
    val topSongs = remember(counts, songMap) {
        counts.entries.sortedByDescending { it.value }.mapNotNull { e -> songMap[e.key]?.let { it to e.value } }.take(10)
    }
    val topArtists = remember(counts, songMap) {
        counts.entries.mapNotNull { e -> songMap[e.key]?.let { it.artist to e.value } }
            .filter { it.first != "Artiste inconnu" }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }
            .entries.sortedByDescending { it.value }.take(5)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Roxi.Bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Statistiques", onBack = { nav.popBackStack() })

        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatBox(formatListenTime(daily[today.toString()] ?: 0L), "Aujourd'hui", Modifier.weight(1f))
            StatBox(formatListenTime(weekTotal), "7 jours", Modifier.weight(1f))
            StatBox(formatListenTime(total), "Au total", Modifier.weight(1f))
        }

        SectionHeader("Cette semaine")
        val max = (week.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
        val accent = Roxi.VioletSoft
        val track = Roxi.SurfaceHigh
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = 24.dp)
        ) {
            val slot = size.width / week.size
            val barW = slot * 0.5f
            week.forEachIndexed { i, (_, ms) ->
                val x = slot * i + (slot - barW) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(12f, 12f))
                val h = size.height * (ms.toFloat() / max)
                if (h > 0f) {
                    drawRoundRect(accent, Offset(x, size.height - h), Size(barW, h), CornerRadius(12f, 12f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            week.forEach { (day, _) ->
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.FRENCH).removeSuffix("."),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day == today) Roxi.VioletSoft else Roxi.TextSub,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SectionHeader("Titres les plus écoutés")
        if (topSongs.isEmpty()) {
            Text(
                "Écoute des chansons (au moins 30 secondes) pour voir ton classement.",
                color = Roxi.TextSub,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        topSongs.forEachIndexed { i, (song, n) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${i + 1}",
                    fontWeight = FontWeight.Bold,
                    color = if (i < 3) Roxi.VioletSoft else Roxi.TextSub,
                    modifier = Modifier.width(28.dp),
                )
                AlbumArt(song, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Roxi.TextSub)
                }
                Text("$n écoute(s)", style = MaterialTheme.typography.labelMedium, color = Roxi.TextSub)
            }
        }

        if (topArtists.isNotEmpty()) {
            SectionHeader("Artistes préférés")
            val best = topArtists.first().value.coerceAtLeast(1)
            topArtists.forEach { (artist, n) ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row {
                        Text(artist, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$n", color = Roxi.TextSub)
                    }
                    Spacer(Modifier.height(4.dp))
                    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                        drawRoundRect(track, cornerRadius = CornerRadius(6f, 6f))
                        drawRoundRect(accent, size = Size(size.width * n / best, size.height), cornerRadius = CornerRadius(6f, 6f))
                    }
                }
            }
        }
        Spacer(Modifier.height(bottom + 16.dp))
    }
}

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier) {
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
