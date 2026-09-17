package com.roxi.player.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Couleurs d'accent proposées dans « Moi → Apparence ». */
enum class AccentColor(val label: String, val main: Color, val soft: Color) {
    VIOLET("Violet", Color(0xFF7B5CF0), Color(0xFFB9A8FF)),
    ROSE("Rose", Color(0xFFE84C88), Color(0xFFFF9CC2)),
    BLEU("Bleu", Color(0xFF3D8BF2), Color(0xFF9CC7FF)),
    VERT("Vert", Color(0xFF22A06B), Color(0xFF8BE0B8)),
    ORANGE("Orange", Color(0xFFF07B2E), Color(0xFFFFC49A)),
}

enum class ThemeMode(val label: String) {
    SYSTEM("Comme le téléphone"),
    DARK("Sombre"),
    LIGHT("Clair"),
}

@Immutable
data class RoxiPalette(
    val dark: Boolean,
    val accentColor: AccentColor,
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val accent: Color,
    val accentSoft: Color,
    val pink: Color,
    val blue: Color,
    val text: Color,
    val textSub: Color,
)

fun roxiPalette(accent: AccentColor, dark: Boolean): RoxiPalette =
    if (dark) {
        RoxiPalette(
            dark = true,
            accentColor = accent,
            bg = Color(0xFF0E0D14),
            surface = Color(0xFF1C1B24),
            surfaceHigh = Color(0xFF282634),
            accent = accent.main,
            accentSoft = accent.soft,
            pink = Color(0xFFE84C88),
            blue = Color(0xFF4FA3F7),
            text = Color(0xFFF2F1F7),
            textSub = Color(0xFF9E9CAB),
        )
    } else {
        RoxiPalette(
            dark = false,
            accentColor = accent,
            bg = Color(0xFFF6F5FA),
            surface = Color(0xFFFFFFFF),
            surfaceHigh = Color(0xFFE9E7F1),
            accent = accent.main,
            accentSoft = accent.main, // sur fond clair, la version foncée se lit mieux
            pink = Color(0xFFD63B77),
            blue = Color(0xFF2F7FE0),
            text = Color(0xFF1B1A22),
            textSub = Color(0xFF6B6979),
        )
    }

val LocalRoxiPalette = staticCompositionLocalOf { roxiPalette(AccentColor.VIOLET, true) }

/** Raccourcis vers les couleurs du thème actuel. */
object Roxi {
    val accents: List<AccentColor> = AccentColor.entries.toList()
    val Bg: Color
        @Composable @ReadOnlyComposable get() = LocalRoxiPalette.current.bg
    val Surface: Color
        @Composable @ReadOnlyComposable get() = LocalRoxiPalette.current.surface
    val SurfaceHigh: Color
        @Composable @ReadOnlyComposable get() = LocalRoxiPalette.current.surfaceHigh
    val Violet: Color
        @Composable @ReadOnlyComposable get() = LocalRoxiPalette.current.accent
    val VioletSoft: Color
        @Composable @ReadOnlyComposable get() = LocalRoxiPalette.current.accentSoft
    val Pink: Color
        @Composable @ReadOnlyComposable get() = LocalRoxiPalette.current.pink
    val Blue: Color
        @Composable @ReadOnlyComposable get() = LocalRoxiPalette.current.blue
    val Text: Color
        @Composable @ReadOnlyComposable get() = LocalRoxiPalette.current.text
    val TextSub: Color
        @Composable @ReadOnlyComposable get() = LocalRoxiPalette.current.textSub
    val IsDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalRoxiPalette.current.dark
    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = IsDark
    val AccentOnDark: Color
        @Composable @ReadOnlyComposable get() = LocalRoxiPalette.current.accentSoft
    val OnAccentOnDark: Color
        @Composable @ReadOnlyComposable get() = Color(0xFF1B1530)
    val OnAccentSoft: Color
        @Composable @ReadOnlyComposable get() = Color(0xFF1B1530)

    /** Fond de l'écran de démarrage (toujours sombre). */
    val SplashBg = Color(0xFF0E0D14)
}

@Composable
fun RoxiTheme(
    accent: AccentColor = AccentColor.VIOLET,
    dark: Boolean = true,
    content: @Composable () -> Unit,
) {
    val p = remember(accent, dark) { roxiPalette(accent, dark) }
    val scheme = remember(p) {
        if (p.dark) {
            darkColorScheme(
                primary = p.accentSoft,
                onPrimary = Color(0xFF1B1530),
                primaryContainer = p.accent,
                onPrimaryContainer = Color.White,
                secondary = p.pink,
                onSecondary = Color.White,
                background = p.bg,
                onBackground = p.text,
                surface = p.bg,
                onSurface = p.text,
                surfaceVariant = p.surfaceHigh,
                onSurfaceVariant = p.textSub,
                surfaceContainerLowest = p.bg,
                surfaceContainerLow = p.surface,
                surfaceContainer = p.surface,
                surfaceContainerHigh = p.surfaceHigh,
                surfaceContainerHighest = p.surfaceHigh,
                outline = Color(0xFF4A4858),
            )
        } else {
            lightColorScheme(
                primary = p.accent,
                onPrimary = Color.White,
                primaryContainer = p.accent,
                onPrimaryContainer = Color.White,
                secondary = p.pink,
                onSecondary = Color.White,
                background = p.bg,
                onBackground = p.text,
                surface = p.bg,
                onSurface = p.text,
                surfaceVariant = p.surfaceHigh,
                onSurfaceVariant = p.textSub,
                surfaceContainerLowest = p.surface,
                surfaceContainerLow = p.surface,
                surfaceContainer = p.surface,
                surfaceContainerHigh = p.surface,
                surfaceContainerHighest = p.surfaceHigh,
                outline = Color(0xFFC9C6D6),
            )
        }
    }
    CompositionLocalProvider(LocalRoxiPalette provides p) {
        MaterialTheme(colorScheme = scheme) {
            // Sans cette ligne, les textes sans couleur précise seraient noirs (bug de la 1.0)
            CompositionLocalProvider(LocalContentColor provides p.text, content = content)
        }
    }
}
