package com.roxi.player.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/** Recherche des paroles sur LRCLIB (site gratuit, sans compte). */
object LyricsFetcher {
    private const val BASE = "https://lrclib.net/api"

    private val noise = Regex(
        """[\(\[【][^\)\]】]*(official|offici|video|vidéo|lyric|audio|mp3|kbps|\d+k\b|clip|visuali|hd|4k|remaster|topic|mv|m/v|version)[^\)\]】]*[\)\]】]""",
        RegexOption.IGNORE_CASE,
    )

    /** Nettoie un titre : « Zoltraak(MP3_160K) » → « Zoltraak ». */
    fun cleanTitle(raw: String): String = raw
        .replace(noise, " ")
        .replace('_', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '-', '|')

    suspend fun fetch(song: Song): String? = withContext(Dispatchers.IO) {
        var title = cleanTitle(song.title)
        var artist: String? = song.artist.takeIf { it != "Artiste inconnu" && !it.endsWith("- Topic") }
            ?: song.artist.removeSuffix("- Topic").trim().takeIf { it.isNotEmpty() && it != "Artiste inconnu" }
        // « Artiste - Titre » dans le titre
        if (title.contains(" - ")) {
            val left = title.substringBefore(" - ").trim()
            val right = title.substringAfter(" - ").trim()
            if (artist == null || left.equals(artist, ignoreCase = true)) {
                artist = left
                title = right
            }
        }
        if (title.isBlank()) return@withContext null
        val seconds = song.durationMs / 1000

        try {
            if (artist != null) {
                val url = "$BASE/get?track_name=${enc(title)}&artist_name=${enc(artist)}&duration=$seconds"
                getText(url)?.let { JSONObject(it) }?.let { pick(it) }?.let { return@withContext it }
            }
            val q = if (artist != null) "$title $artist" else title
            val arr = getText("$BASE/search?q=${enc(q)}")?.let { JSONArray(it) } ?: return@withContext null
            val results = (0 until arr.length()).map { arr.getJSONObject(it) }
            val close = results.filter { abs(it.optDouble("duration", 0.0) - seconds) <= 5 }
            val best = close.firstOrNull { has(it, "syncedLyrics") }
                ?: close.firstOrNull { has(it, "plainLyrics") }
                ?: results.firstOrNull { has(it, "syncedLyrics") }
            best?.let { pick(it) }
        } catch (e: Exception) {
            Log.w("RoxiLyrics", "Recherche en ligne impossible", e)
            null
        }
    }

    private fun has(o: JSONObject, key: String) = !o.isNull(key) && o.optString(key).isNotBlank()

    private fun pick(o: JSONObject): String? = when {
        has(o, "syncedLyrics") -> o.getString("syncedLyrics")
        has(o, "plainLyrics") -> o.getString("plainLyrics")
        else -> null
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun getText(url: String): String? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "RoxiPlayer/1.3 (https://github.com/Herilala05/Roxiplayer)")
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
