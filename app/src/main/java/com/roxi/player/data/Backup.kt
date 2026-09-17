package com.roxi.player.data

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sauvegarde / restauration des favoris, playlists, titres masqués, statistiques et réglages.
 * Les identifiants des chansons changent d'un téléphone à l'autre : on enregistre aussi
 * le chemin, le titre et l'artiste pour les retrouver.
 */
object Backup {

    enum class Result { OK, INVALID, ERROR }

    private val skipKeys = setOf("lyrics_tree", "last_pos", "last_uri")

    fun export(context: Context, uri: Uri, prefs: Prefs, songs: List<Song>): Boolean = try {
        val values = JSONObject()
        prefs.exportAll().forEach { (key, value) ->
            if (key in skipKeys || value == null) return@forEach
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("t", "b").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                is String -> entry.put("t", "s").put("v", value)
                is Set<*> -> entry.put("t", "set").put("v", JSONArray(value.map { it.toString() }))
                else -> return@forEach
            }
            values.put(key, entry)
        }
        val catalog = JSONObject()
        songs.forEach { s ->
            catalog.put(s.id.toString(), JSONObject().put("p", s.path).put("t", s.title).put("a", s.artist))
        }
        val root = JSONObject()
            .put("app", "Roxi Player")
            .put("format", 1)
            .put("date", java.time.LocalDateTime.now().toString())
            .put("prefs", values)
            .put("songs", catalog)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString(2).toByteArray()) }
        true
    } catch (e: Exception) {
        Log.e("RoxiBackup", "Sauvegarde impossible", e)
        false
    }

    fun import(context: Context, uri: Uri, current: List<Song>): Result {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: return Result.ERROR
            val root = JSONObject(text)
            if (root.optString("app") != "Roxi Player") return Result.INVALID
            val values = root.getJSONObject("prefs")
            val catalog = root.optJSONObject("songs") ?: JSONObject()

            // ancien identifiant → identifiant sur ce téléphone
            val byPath = current.filter { it.path.isNotEmpty() }.associateBy { it.path }
            val byName = current.associateBy { key(it.title, it.artist) }
            val map = HashMap<Long, Long>()
            catalog.keys().forEach { oldId ->
                val o = catalog.getJSONObject(oldId)
                val found = byPath[o.optString("p")] ?: byName[key(o.optString("t"), o.optString("a"))]
                val old = oldId.toLongOrNull()
                if (found != null && old != null) map[old] = found.id
            }
            fun remap(id: Long): Long? = map[id]

            val edit = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit()
            edit.remove("last_uri")
            values.keys().forEach { key ->
                val e = values.getJSONObject(key)
                when (key) {
                    "liked", "recent", "hidden" -> {
                        val arr = JSONArray(e.getString("v"))
                        val out = JSONArray()
                        (0 until arr.length()).mapNotNull { remap(arr.getLong(it)) }.distinct().forEach { out.put(it) }
                        edit.putString(key, out.toString())
                    }
                    "playlists" -> {
                        val arr = JSONArray(e.getString("v"))
                        for (i in 0 until arr.length()) {
                            val p = arr.getJSONObject(i)
                            val songsArr = p.getJSONArray("songs")
                            val out = JSONArray()
                            (0 until songsArr.length()).mapNotNull { remap(songsArr.getLong(it)) }.distinct().forEach { out.put(it) }
                            p.put("songs", out)
                        }
                        edit.putString(key, arr.toString())
                    }
                    "counts", "edits" -> {
                        val o = JSONObject(e.getString("v"))
                        val out = JSONObject()
                        o.keys().forEach { k -> k.toLongOrNull()?.let(::remap)?.let { out.put(it.toString(), o.get(k)) } }
                        edit.putString(key, out.toString())
                    }
                    "last_id" -> remap(e.getLong("v"))?.let { edit.putLong(key, it) }
                    else -> when (e.getString("t")) {
                        "b" -> edit.putBoolean(key, e.getBoolean("v"))
                        "i" -> edit.putInt(key, e.getInt("v"))
                        "l" -> edit.putLong(key, e.getLong("v"))
                        "f" -> edit.putFloat(key, e.getDouble("v").toFloat())
                        "s" -> edit.putString(key, e.getString("v"))
                        "set" -> {
                            val arr = e.getJSONArray("v")
                            edit.putStringSet(key, (0 until arr.length()).map { arr.getString(it) }.toSet())
                        }
                    }
                }
            }
            return if (edit.commit()) Result.OK else Result.ERROR
        } catch (e: Exception) {
            Log.e("RoxiBackup", "Restauration impossible", e)
            return Result.INVALID
        }
    }

    private fun key(title: String, artist: String) = "${title.trim().lowercase()}|${artist.trim().lowercase()}"
}
