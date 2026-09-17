package com.roxi.player

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.res.Configuration
import com.roxi.player.data.Prefs
import com.roxi.player.ui.RoxiApp
import com.roxi.player.ui.theme.Roxi
import androidx.compose.ui.graphics.toArgb

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Thème choisi appliqué dès le départ (évite un changement de couleur visible)
        val sp = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
        val mode = sp.getInt("theme_mode", 0)
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        Roxi.apply(sp.getInt("accent", 0), if (mode == 1) false else if (mode == 2) systemDark else true)

        val barStyle = if (Roxi.isDark) SystemBarStyle.dark(Color.TRANSPARENT)
        else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        // Fond de la fenêtre = fond de l'appli : aucun flash pendant les animations
        window.decorView.setBackgroundColor(Roxi.Bg.toArgb())
        setContent { RoxiApp() }
    }
}
