package com.roxi.player.data

import android.net.Uri
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val dateAdded: Long,
    val path: String,
    val folder: String,
    val size: Long,
)

data class Playlist(val id: Long, val name: String, val songIds: List<Long>)

/** Regroupement générique : artiste, album ou dossier. */
data class Group(val key: String, val name: String, val subtitle: String, val songs: List<Song>)

enum class SortOrder(val label: String) {
    DATE_DESC("Date d'ajout (récent → ancien)"),
    DATE_ASC("Date d'ajout (ancien → récent)"),
    AZ("Titre A → Z"),
    ZA("Titre Z → A"),
    ARTIST("Artiste"),
    DURATION("Durée"),
}

private val collator: Collator = Collator.getInstance(Locale.FRENCH).apply { strength = Collator.PRIMARY }

fun List<Song>.sortSongs(order: SortOrder): List<Song> = when (order) {
    SortOrder.DATE_DESC -> sortedByDescending { it.dateAdded }
    SortOrder.DATE_ASC -> sortedBy { it.dateAdded }
    SortOrder.AZ -> sortedWith(compareBy(collator) { it.title })
    SortOrder.ZA -> sortedWith(compareBy(collator) { it.title }).reversed()
    SortOrder.ARTIST -> sortedWith(compareBy<Song, String>(collator) { it.artist }.thenBy(collator) { it.title })
    SortOrder.DURATION -> sortedByDescending { it.durationMs }
}

/** Première lettre sans accent, en majuscule ; '#' si ce n'est pas une lettre latine. */
fun indexLetter(text: String): Char {
    val first = text.trim().firstOrNull() ?: return '#'
    val plain = Normalizer.normalize(first.toString(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .uppercase(Locale.ROOT)
        .firstOrNull() ?: return '#'
    return if (plain in 'A'..'Z') plain else '#'
}

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun formatListenTime(ms: Long): String {
    val minutes = ms / 60_000L
    return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
}
