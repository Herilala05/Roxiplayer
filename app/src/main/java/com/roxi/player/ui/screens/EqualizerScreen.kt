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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.roxi.player.playback.AudioFx
import com.roxi.player.ui.components.LocalBottomPadding
import com.roxi.player.ui.components.ScreenHeader
import com.roxi.player.ui.components.SectionHeader
import com.roxi.player.ui.theme.Roxi

/** Égaliseur intégré (marche aussi en Bluetooth). */
@Composable
fun EqualizerScreen(nav: NavController) {
    val info by AudioFx.eqInfo.collectAsStateWithLifecycle()
    val enabled by AudioFx.eqEnabled.collectAsStateWithLifecycle()
    val bands by AudioFx.bands.collectAsStateWithLifecycle()
    val preset by AudioFx.preset.collectAsStateWithLifecycle()
    val bass by AudioFx.bass.collectAsStateWithLifecycle()
    val bottom = LocalBottomPadding.current
    val sliderColors = SliderDefaults.colors(
        thumbColor = Roxi.VioletSoft,
        activeTrackColor = Roxi.VioletSoft,
        inactiveTrackColor = Roxi.SurfaceHigh,
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Roxi.Bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Égaliseur", onBack = { nav.popBackStack() })

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Roxi.Surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Activer l'égaliseur", fontWeight = FontWeight.SemiBold)
                Text(
                    "Fonctionne sur le haut-parleur, les écouteurs et le Bluetooth",
                    style = MaterialTheme.typography.bodySmall,
                    color = Roxi.TextSub,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { AudioFx.setEqEnabled(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Roxi.Violet),
            )
        }

        val eq = info
        if (eq == null) {
            Text(
                "Lance une chanson pour afficher les réglages de l'égaliseur.",
                color = Roxi.TextSub,
                modifier = Modifier.padding(24.dp),
            )
        } else Column(Modifier.alpha(if (enabled) 1f else 0.45f)) {
            if (eq.presets.isNotEmpty()) {
                SectionHeader("Préréglages")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { PresetChip("Personnalisé", preset == -1) {} }
                    itemsIndexed(eq.presets) { i, name ->
                        PresetChip(name, preset == i) {
                            if (!enabled) AudioFx.setEqEnabled(true)
                            AudioFx.usePreset(i)
                        }
                    }
                }
            }

            SectionHeader("Fréquences")
            eq.centersHz.forEachIndexed { i, hz ->
                val level = bands.getOrElse(i) { 0 }
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(64.dp),
                    )
                    Slider(
                        value = level.toFloat(),
                        onValueChange = { AudioFx.setBand(i, it.toInt()) },
                        valueRange = eq.minLevel.toFloat()..eq.maxLevel.toFloat(),
                        enabled = enabled,
                        colors = sliderColors,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "%+.0f dB".format(level / 100f),
                        style = MaterialTheme.typography.labelMedium,
                        color = Roxi.TextSub,
                        modifier = Modifier.width(52.dp),
                    )
                }
            }

            SectionHeader("Renforcement des basses")
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = bass.toFloat(),
                    onValueChange = { AudioFx.setBass(it.toInt()) },
                    valueRange = 0f..1000f,
                    enabled = enabled,
                    colors = sliderColors,
                    modifier = Modifier.weight(1f),
                )
                Text("${bass / 10} %", color = Roxi.TextSub, modifier = Modifier.width(52.dp))
            }
        }
        Spacer(Modifier.height(bottom + 16.dp))
    }
}

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Roxi.OnAccentSoft else Roxi.Text,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Roxi.VioletSoft else Roxi.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
