package com.roxi.player.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.palette.graphics.Palette
import com.roxi.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Couleur de fond du lecteur tirée de la pochette (comme Lark Player). */
object ArtColors {
    val Fallback = Color(0xFF2A2440)
    private val cache = ConcurrentHashMap<Long, Color>()

    fun peek(id: Long): Color? = cache[id]

    suspend fun get(context: Context, song: Song): Color = withContext(Dispatchers.IO) {
        cache[song.id]?.let { return@withContext it }
        val image = ArtCache.load(context, song, 720)
        val color = image?.let { fromBitmap(it.asAndroidBitmap()) } ?: Fallback
        cache[song.id] = color
        color
    }

    private fun fromBitmap(source: Bitmap): Color? = try {
        val bitmap = if (source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            source
        }
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val rgb = palette.dominantSwatch?.rgb
            ?: palette.vibrantSwatch?.rgb
            ?: palette.mutedSwatch?.rgb
        rgb?.let { toBackground(it) }
    } catch (e: Exception) {
        null
    }

    /** Assombrit la couleur pour que le texte blanc reste lisible. */
    private fun toBackground(rgb: Int): Color {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(rgb, hsv)
        hsv[1] = hsv[1].coerceAtMost(0.65f)
        hsv[2] = hsv[2].coerceIn(0.18f, 0.42f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    }
}
