package com.roxi.player.ui.components

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.roxi.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

/** Chargement et cache des pochettes (sans bibliothèque externe). */
object ArtCache {
    private val cache = LruCache<String, ImageBitmap>(120)
    private val missing = Collections.synchronizedSet(HashSet<String>())

    private fun key(id: Long, size: Int) = "$id-$size"

    fun peek(id: Long, size: Int): ImageBitmap? = cache.get(key(id, size))

    fun isMissing(id: Long, size: Int) = missing.contains(key(id, size))

    fun load(context: Context, song: Song, size: Int): ImageBitmap? {
        val k = key(song.id, size)
        cache.get(k)?.let { return it }
        if (missing.contains(k)) return null
        val bmp: Bitmap? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(song.uri, Size(size, size), null)
            } else {
                val artUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId)
                context.contentResolver.openInputStream(artUri)?.use { input ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
                    BitmapFactory.decodeStream(input, null, opts)
                }?.let { Bitmap.createScaledBitmap(it, size, size, true) }
            }
        } catch (e: Exception) {
            null
        }
        return if (bmp == null) {
            missing.add(k)
            null
        } else {
            bmp.asImageBitmap().also { cache.put(k, it) }
        }
    }
}

private val placeholderColors = listOf(
    listOf(Color(0xFF7B5CF0), Color(0xFF4FA3F7)),
    listOf(Color(0xFFE84C88), Color(0xFF7B5CF0)),
    listOf(Color(0xFF2FB8A6), Color(0xFF4FA3F7)),
    listOf(Color(0xFFF08A5C), Color(0xFFE84C88)),
)

/**
 * Pochette d'un morceau. [size] = 160 pour les listes, 720 pour le lecteur.
 * Le [modifier] doit fixer la taille et, si besoin, l'arrondi (clip).
 */
@Composable
fun AlbumArt(song: Song?, modifier: Modifier = Modifier, size: Int = 160) {
    val context = LocalContext.current
    val bitmap by produceState(
        initialValue = song?.let { ArtCache.peek(it.id, size) },
        key1 = song?.id,
        key2 = size,
    ) {
        value = song?.let { ArtCache.peek(it.id, size) }
        if (song != null && value == null && !ArtCache.isMissing(song.id, size)) {
            value = withContext(Dispatchers.IO) { ArtCache.load(context, song, size) }
        }
    }
    val colors = placeholderColors[((song?.id ?: 0L) % placeholderColors.size).toInt().let { if (it < 0) -it else it }]
    Box(
        modifier.background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.fillMaxSize(0.45f),
            )
        }
    }
}
