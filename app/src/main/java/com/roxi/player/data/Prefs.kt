package com.roxi.player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

/** Stockage local simple : favoris, playlists, historique, réglages. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---------- Favoris (plus récent en premier) ----------
    private val _liked = MutableStateFlow(readIdList("liked"))
    val liked: StateFlow<List<Long>> = _liked.asStateFlow()

    fun toggleLike(id: Long) {
        val cur = _liked.value
        val next = if (id in cur) cur - id else listOf(id) + cur
        _liked.value = next
        writeIdList("liked", next)
    }

    // ---------- Playlists ----------
    private val _playlists = MutableStateFlow(readPlaylists())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    fun createPlaylist(name: String): Long {
        val id = System.currentTimeMillis()
        savePlaylists(_playlists.value + Playlist(id, name.trim().ifEmpty { "Nouvelle playlist" }, emptyList()))
        return id
    }

    fun renamePlaylist(id: Long, name: String) = savePlaylists(
        _playlists.value.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it }
    )

    fun deletePlaylist(id: Long) = savePlaylists(_playlists.value.filterNot { it.id == id })

    fun addToPlaylist(id: Long, songId: Long) = savePlaylists(
        _playlists.value.map {
            if (it.id == id && songId !in it.songIds) it.copy(songIds = it.songIds + songId) else it
        }
    )

    fun removeFromPlaylist(id: Long, songId: Long) = savePlaylists(
        _playlists.value.map { if (it.id == id) it.copy(songIds = it.songIds - songId) else it }
    )

    /** Nouvel ordre après un glisser-déposer (les titres introuvables restent à la fin). */
    fun reorderPlaylist(id: Long, visibleOrder: List<Long>) = savePlaylists(
        _playlists.value.map { p ->
            if (p.id != id) p else {
                val kept = visibleOrder.filter { it in p.songIds }
                p.copy(songIds = kept + p.songIds.filter { it !in kept })
            }
        }
    )

    fun moveInPlaylist(id: Long, songId: Long, delta: Int) = savePlaylists(
        _playlists.value.map { p ->
            if (p.id != id) return@map p
            val list = p.songIds.toMutableList()
            val i = list.indexOf(songId)
            val j = i + delta
            if (i < 0 || j !in list.indices) p else {
                Collections.swap(list, i, j)
                p.copy(songIds = list)
            }
        }
    )

    private fun savePlaylists(list: List<Playlist>) {
        _playlists.value = list
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("songs", JSONArray().apply { p.songIds.forEach { put(it) } })
            )
        }
        sp.edit().putString("playlists", arr.toString()).apply()
    }

    private fun readPlaylists(): List<Playlist> = try {
        val arr = JSONArray(sp.getString("playlists", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val songs = o.getJSONArray("songs")
            Playlist(o.getLong("id"), o.getString("name"), (0 until songs.length()).map { songs.getLong(it) })
        }
    } catch (e: Exception) {
        emptyList()
    }

    // ---------- Titres masqués ----------
    private val _hidden = MutableStateFlow(readIdList("hidden").toSet())
    val hidden: StateFlow<Set<Long>> = _hidden.asStateFlow()

    fun hide(id: Long) {
        val next = _hidden.value + id
        _hidden.value = next
        writeIdList("hidden", next.toList())
    }

    fun unhide(id: Long) {
        val next = _hidden.value - id
        _hidden.value = next
        writeIdList("hidden", next.toList())
    }

    // ---------- Historique d'écoute ----------
    private val _recent = MutableStateFlow(readIdList("recent"))
    val recent: StateFlow<List<Long>> = _recent.asStateFlow()

    private val _playCounts = MutableStateFlow(readCounts())
    val playCounts: StateFlow<Map<Long, Int>> = _playCounts.asStateFlow()

    fun recordPlay(id: Long) {
        val next = (listOf(id) + (_recent.value - id)).take(50)
        _recent.value = next
        writeIdList("recent", next)
        val counts = _playCounts.value.toMutableMap()
        counts[id] = (counts[id] ?: 0) + 1
        _playCounts.value = counts
        val o = JSONObject()
        counts.forEach { (k, v) -> o.put(k.toString(), v) }
        sp.edit().putString("counts", o.toString()).apply()
    }

    private fun readCounts(): Map<Long, Int> = try {
        val o = JSONObject(sp.getString("counts", "{}") ?: "{}")
        o.keys().asSequence().mapNotNull { k -> k.toLongOrNull()?.let { it to o.getInt(k) } }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }

    // ---------- Temps d'écoute total ----------
    private val _listenMs = MutableStateFlow(sp.getLong("listen_ms", 0L))
    val listenMs: StateFlow<Long> = _listenMs.asStateFlow()
    private var pendingListen = 0L

    /** Temps d'écoute par jour (clé = "2026-09-15"), conservé 90 jours. */
    private val _daily = MutableStateFlow(readDaily())
    val daily: StateFlow<Map<String, Long>> = _daily.asStateFlow()

    fun addListen(ms: Long) {
        _listenMs.value += ms
        val today = java.time.LocalDate.now().toString()
        val map = _daily.value.toMutableMap()
        map[today] = (map[today] ?: 0L) + ms
        _daily.value = map
        pendingListen += ms
        if (pendingListen >= 10_000L) flushListen()
    }

    fun flushListen() {
        pendingListen = 0L
        val limit = java.time.LocalDate.now().minusDays(90).toString()
        val o = JSONObject()
        _daily.value.filterKeys { it >= limit }.forEach { (k, v) -> o.put(k, v) }
        sp.edit().putLong("listen_ms", _listenMs.value).putString("daily", o.toString()).apply()
    }

    private fun readDaily(): Map<String, Long> = try {
        val o = JSONObject(sp.getString("daily", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getLong(it) }
    } catch (e: Exception) {
        emptyMap()
    }

    // ---------- Infos modifiées (titre / artiste / album propres à Roxi Player) ----------
    data class TagEdit(val title: String, val artist: String, val album: String)

    private val _edits = MutableStateFlow(readEdits())
    val edits: StateFlow<Map<Long, TagEdit>> = _edits.asStateFlow()

    fun setEdit(id: Long, edit: TagEdit?) {
        val map = _edits.value.toMutableMap()
        if (edit == null) map.remove(id) else map[id] = edit
        _edits.value = map
        val o = JSONObject()
        map.forEach { (k, v) ->
            o.put(k.toString(), JSONObject().put("t", v.title).put("a", v.artist).put("b", v.album))
        }
        sp.edit().putString("edits", o.toString()).apply()
    }

    private fun readEdits(): Map<Long, TagEdit> = try {
        val o = JSONObject(sp.getString("edits", "{}") ?: "{}")
        o.keys().asSequence().mapNotNull { k ->
            val e = o.getJSONObject(k)
            k.toLongOrNull()?.let { it to TagEdit(e.getString("t"), e.getString("a"), e.getString("b")) }
        }.toMap()
    } catch (e: Exception) {
        emptyMap()
    }

    // ---------- Réglages ----------
    private val _sortOrder = MutableStateFlow(
        SortOrder.entries.getOrElse(sp.getInt("sort", 0)) { SortOrder.DATE_DESC }
    )
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()
    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        sp.edit().putInt("sort", order.ordinal).apply()
    }

    private val _minDurationSec = MutableStateFlow(sp.getInt("min_duration", 30))
    val minDurationSec: StateFlow<Int> = _minDurationSec.asStateFlow()
    fun setMinDuration(sec: Int) {
        _minDurationSec.value = sec
        sp.edit().putInt("min_duration", sec).apply()
    }

    private val _lyricsTree = MutableStateFlow(sp.getString("lyrics_tree", null))
    val lyricsTree: StateFlow<String?> = _lyricsTree.asStateFlow()
    fun setLyricsTree(uri: String?) {
        _lyricsTree.value = uri
        sp.edit().putString("lyrics_tree", uri).apply()
    }

    var playbackSpeed: Float
        get() = sp.getFloat("speed", 1f)
        set(value) = sp.edit().putFloat("speed", value).apply()

    // ---------- Son (lus aussi par le service de lecture) ----------
    private val _normalize = MutableStateFlow(sp.getBoolean(KEY_NORMALIZE, false))
    val normalize: StateFlow<Boolean> = _normalize.asStateFlow()
    fun setNormalize(on: Boolean) {
        _normalize.value = on
        sp.edit().putBoolean(KEY_NORMALIZE, on).apply()
    }

    /** 0 = doux, 1 = normal, 2 = fort */
    private val _normalizeLevel = MutableStateFlow(sp.getInt(KEY_NORMALIZE_LEVEL, 1))
    val normalizeLevel: StateFlow<Int> = _normalizeLevel.asStateFlow()
    fun setNormalizeLevel(level: Int) {
        _normalizeLevel.value = level
        sp.edit().putInt(KEY_NORMALIZE_LEVEL, level).apply()
    }

    private val _crossfadeSec = MutableStateFlow(sp.getInt(KEY_CROSSFADE, 0))
    val crossfadeSec: StateFlow<Int> = _crossfadeSec.asStateFlow()
    fun setCrossfade(sec: Int) {
        _crossfadeSec.value = sec
        sp.edit().putInt(KEY_CROSSFADE, sec).apply()
    }

    private val _autoLyrics = MutableStateFlow(sp.getBoolean("auto_lyrics", true))
    val autoLyrics: StateFlow<Boolean> = _autoLyrics.asStateFlow()
    fun setAutoLyrics(on: Boolean) {
        _autoLyrics.value = on
        sp.edit().putBoolean("auto_lyrics", on).apply()
    }

    // ---------- Thème ----------
    /** 0 = sombre, 1 = clair, 2 = comme le téléphone */
    private val _themeMode = MutableStateFlow(sp.getInt("theme_mode", 0))
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()
    fun setThemeMode(mode: Int) {
        _themeMode.value = mode
        sp.edit().putInt("theme_mode", mode).apply()
    }

    private val _accent = MutableStateFlow(sp.getInt("accent", 0))
    val accent: StateFlow<Int> = _accent.asStateFlow()
    fun setAccent(index: Int) {
        _accent.value = index
        sp.edit().putInt("accent", index).apply()
    }

    // ---------- Sauvegarde ----------
    /** Toutes les préférences brutes (pour la sauvegarde). */
    fun exportAll(): Map<String, *> = sp.all

    // ---------- Reprise de la dernière lecture ----------
    var lastSongId: Long
        get() = sp.getLong("last_id", -1L)
        set(value) = sp.edit().putLong("last_id", value).apply()

    var lastPosition: Long
        get() = sp.getLong("last_pos", 0L)
        set(value) = sp.edit().putLong("last_pos", value).apply()

    var lastSongUri: String?
        get() = sp.getString("last_uri", null)
        set(value) = sp.edit().putString("last_uri", value).apply()

    companion object {
        const val FILE = "roxi_prefs"
        const val KEY_NORMALIZE = "normalize"
        const val KEY_NORMALIZE_LEVEL = "normalize_level"
        const val KEY_CROSSFADE = "crossfade"
        const val KEY_EQ_ENABLED = "eq_enabled"
        const val KEY_EQ_BANDS = "eq_bands"
        const val KEY_EQ_PRESET = "eq_preset"
        const val KEY_BASS = "bass_boost"
    }

    // ---------- Utilitaires ----------
    private fun readIdList(key: String): List<Long> = try {
        val arr = JSONArray(sp.getString(key, "[]"))
        (0 until arr.length()).map { arr.getLong(it) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeIdList(key: String, list: List<Long>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        sp.edit().putString(key, arr.toString()).apply()
    }
}
