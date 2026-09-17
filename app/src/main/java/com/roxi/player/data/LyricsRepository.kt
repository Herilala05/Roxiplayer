package com.roxi.player.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

data class LyricLine(val timeMs: Long, val text: String)

sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>) : Lyrics
    data class Plain(val text: String) : Lyrics
    data object None : Lyrics
}

/**
 * UNE seule source de vérité pour les paroles : utilisée par le badge PAROLES
 * de la liste ET par le panneau des paroles du lecteur.
 *
 * Ordre de recherche :
 *  1. fichier .lrc de même nom, à côté du fichier audio (Android ≤ 12)
 *  2. fichier .lrc de même nom dans le « dossier des paroles » choisi par l'utilisateur
 *  3. tag ID3 USLT intégré au MP3
 */
class LyricsRepository(private val context: Context) {

    private val cache = ConcurrentHashMap<Long, Lyrics>()
    private val privateDir = File(context.filesDir, "lyrics")
    private val triedOnline = ConcurrentHashMap.newKeySet<Long>()

    @Volatile
    private var treeIndex: Map<String, Uri> = emptyMap()

    @Volatile
    private var treeUri: Uri? = null

    /** Change à chaque nouvelle parole enregistrée (pour rafraîchir les badges). */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    suspend fun setLyricsTree(tree: String?) = withContext(Dispatchers.IO) {
        treeUri = tree?.let { Uri.parse(it) }
        treeIndex = treeUri?.let { buildIndex(it) } ?: emptyMap()
        cache.clear()
        _version.value++
    }

    fun clearCache() = cache.clear()

    /** Cherche en ligne et enregistre. Renvoie true si des paroles ont été trouvées. */
    suspend fun fetchOnline(song: Song, force: Boolean = false): Boolean {
        if (!force && !triedOnline.add(song.id)) return false
        val text = LyricsFetcher.fetch(song) ?: return false
        save(song, text)
        return true
    }

    private suspend fun save(song: Song, text: String) = withContext(Dispatchers.IO) {
        // 1. copie privée (toujours possible)
        privateDir.mkdirs()
        File(privateDir, "${song.id}.lrc").writeText(text)
        // 2. copie dans le dossier des paroles choisi (si possible)
        val tree = treeUri
        if (tree != null) {
            try {
                val base = if (song.path.isNotEmpty()) File(song.path).nameWithoutExtension else song.title
                val dir = DocumentFile.fromTreeUri(context, tree)
                if (dir != null && dir.findFile("$base.lrc") == null) {
                    dir.createFile("application/octet-stream", "$base.lrc")?.let { f ->
                        context.contentResolver.openOutputStream(f.uri)?.use { it.write(text.toByteArray()) }
                    }
                }
            } catch (e: Exception) {
                Log.w("RoxiLyrics", "Écriture dans le dossier des paroles impossible", e)
            }
        }
        cache.remove(song.id)
        _version.value++
    }

    suspend fun hasLyrics(song: Song): Boolean = getLyrics(song) !is Lyrics.None

    suspend fun getLyrics(song: Song): Lyrics = withContext(Dispatchers.IO) {
        cache[song.id]?.let { return@withContext it }
        val result = try {
            findLrcText(song)?.let { parseLrc(it) }
                ?: readUslt(song)?.let { Lyrics.Plain(it) }
                ?: Lyrics.None
        } catch (e: Exception) {
            Log.w("RoxiLyrics", "Lecture des paroles impossible pour ${song.title}", e)
            Lyrics.None
        }
        cache[song.id] = result
        result
    }

    // ------------------------------------------------------------ .lrc

    private fun findLrcText(song: Song): String? {
        // 0. Paroles téléchargées par Roxi Player
        val saved = File(privateDir, "${song.id}.lrc")
        if (saved.exists()) return saved.readText()

        val base = if (song.path.isNotEmpty()) File(song.path).nameWithoutExtension else song.title

        // 1. À côté du fichier audio (fonctionne surtout sur Android ≤ 12)
        if (song.path.isNotEmpty()) {
            try {
                val lrc = File(File(song.path).parentFile, "$base.lrc")
                if (lrc.exists() && lrc.canRead()) return decode(lrc.readBytes())
            } catch (e: Exception) {
            }
        }

        // 2. Dossier des paroles choisi dans « Moi »
        val uri = treeIndex[base.lowercase()] ?: treeIndex[song.title.lowercase()] ?: return null
        return context.contentResolver.openInputStream(uri)?.use { decode(it.readBytes()) }
    }

    private fun buildIndex(tree: Uri): Map<String, Uri> {
        val map = HashMap<String, Uri>()
        fun walk(dir: DocumentFile, depth: Int) {
            if (depth > 4) return
            for (f in dir.listFiles()) {
                val name = f.name ?: continue
                if (f.isDirectory) walk(f, depth + 1)
                else if (name.endsWith(".lrc", ignoreCase = true)) {
                    map[name.substringBeforeLast('.').lowercase()] = f.uri
                }
            }
        }
        try {
            DocumentFile.fromTreeUri(context, tree)?.let { walk(it, 0) }
        } catch (e: Exception) {
            Log.w("RoxiLyrics", "Dossier des paroles inaccessible", e)
        }
        return map
    }

    private fun decode(bytes: ByteArray): String {
        var text = String(bytes, Charsets.UTF_8)
        if (text.contains('\uFFFD')) text = String(bytes, Charset.forName("windows-1252"))
        return text.removePrefix("\uFEFF")
    }

    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    private fun parseLrc(text: String): Lyrics? {
        val lines = mutableListOf<LyricLine>()
        val plain = StringBuilder()
        for (raw in text.lines()) {
            val tags = timeTag.findAll(raw).toList()
            if (tags.isEmpty()) {
                // lignes de méta-données [ar:...] ignorées
                if (!raw.trim().matches(Regex("""\[[a-zA-Z]+:.*]"""))) plain.appendLine(raw)
                continue
            }
            val lyric = raw.substring(tags.last().range.last + 1).trim()
            for (t in tags) {
                val min = t.groupValues[1].toLong()
                val sec = t.groupValues[2].toLong()
                val fracStr = t.groupValues[3]
                val frac = when (fracStr.length) {
                    0 -> 0L
                    1 -> fracStr.toLong() * 100
                    2 -> fracStr.toLong() * 10
                    else -> fracStr.toLong()
                }
                lines += LyricLine(min * 60_000 + sec * 1000 + frac, lyric)
            }
        }
        return when {
            lines.isNotEmpty() -> Lyrics.Synced(lines.sortedBy { it.timeMs })
            plain.isNotBlank() -> Lyrics.Plain(plain.toString().trim())
            else -> null
        }
    }

    // ------------------------------------------------------------ ID3 USLT

    private fun readUslt(song: Song): String? =
        context.contentResolver.openInputStream(song.uri)?.use { Id3Lyrics.read(it) }
}

/** Petit lecteur ID3v2 qui ne cherche que la trame des paroles (USLT / ULT). */
internal object Id3Lyrics {

    fun read(input: InputStream): String? =
        scan(input, setOf("USLT", "ULT")) { _, body -> decodeUslt(body)?.takeIf { it.isNotBlank() } }

    /** Gain ReplayGain du titre (ex. « -6.54 dB » → -6.54), ou null. */
    fun readReplayGain(input: InputStream): Float? =
        scan(input, setOf("TXXX", "TXX")) { _, body -> decodeReplayGain(body) }

    private fun <T> scan(input: InputStream, wanted: Set<String>, handle: (String, ByteArray) -> T?): T? {
        val header = ByteArray(10)
        if (readFully(input, header) < 10) return null
        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null
        val major = header[3].toInt()
        if (major !in 2..4) return null
        val flags = header[5].toInt() and 0xFF
        val tagSize = syncSafe(header, 6)
        if (tagSize <= 0) return null

        // Tag entier « désynchronisé » (rare) : on lit tout en mémoire.
        if (flags and 0x80 != 0) {
            if (tagSize > 30_000_000) return null
            val all = ByteArray(tagSize)
            val n = readFully(input, all)
            return parseFrames(removeUnsync(all.copyOf(n)).inputStream(), major, flags, Int.MAX_VALUE, wanted, handle)
        }
        return parseFrames(input, major, flags, tagSize, wanted, handle)
    }

    private fun <T> parseFrames(
        input: InputStream,
        major: Int,
        flags: Int,
        tagSize: Int,
        wanted: Set<String>,
        handle: (String, ByteArray) -> T?,
    ): T? {
        var consumed = 0L
        if (flags and 0x40 != 0 && major >= 3) {
            val ext = ByteArray(4)
            if (readFully(input, ext) < 4) return null
            val extSize = if (major == 4) syncSafe(ext, 0) - 4 else bigEndian(ext, 0, 4)
            if (extSize < 0 || skipFully(input, extSize.toLong()) < extSize) return null
            consumed += 4 + extSize
        }
        val headerLen = if (major == 2) 6 else 10
        val idLen = if (major == 2) 3 else 4
        val fh = ByteArray(headerLen)
        while (consumed + headerLen <= tagSize) {
            if (readFully(input, fh) < headerLen) return null
            consumed += headerLen
            if (fh[0].toInt() == 0) return null // zone de remplissage
            val id = String(fh, 0, idLen, Charsets.ISO_8859_1)
            val size = when (major) {
                2 -> bigEndian(fh, 3, 3)
                3 -> bigEndian(fh, 4, 4)
                else -> syncSafe(fh, 4)
            }
            if (size <= 0 || consumed + size > tagSize.toLong()) return null
            if (id in wanted && size <= 2_000_000) {
                val body = ByteArray(size)
                if (readFully(input, body) < size) return null
                handle(id, body)?.let { return it }
            } else if (skipFully(input, size.toLong()) < size) {
                return null
            }
            consumed += size
        }
        return null
    }

    private fun decodeReplayGain(b: ByteArray): Float? {
        if (b.size < 3) return null
        val charset = charsetOf(b[0].toInt())
        val text = String(b, 1, b.size - 1, charset).replace("\uFEFF", "")
        val parts = text.split('\u0000').filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        if (!parts[0].trim().equals("REPLAYGAIN_TRACK_GAIN", ignoreCase = true)) return null
        return parts[1].replace("dB", "", ignoreCase = true).trim().replace(',', '.').toFloatOrNull()
    }

    private fun charsetOf(enc: Int) = when (enc) {
        0 -> Charsets.ISO_8859_1
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        else -> Charsets.UTF_8
    }

    private fun decodeUslt(b: ByteArray): String? {
        if (b.size < 5) return null
        val enc = b[0].toInt()
        val charset = charsetOf(enc)
        val wide = enc == 1 || enc == 2
        // 1 octet d'encodage + 3 octets de langue, puis description terminée par 0 (ou 00 00)
        var p = 4
        if (wide) {
            while (p + 1 < b.size && !(b[p].toInt() == 0 && b[p + 1].toInt() == 0)) p += 2
            p += 2
        } else {
            while (p < b.size && b[p].toInt() != 0) p++
            p += 1
        }
        if (p >= b.size) return null
        return String(b, p, b.size - p, charset).trimEnd('\u0000').trim()
    }

    private fun removeUnsync(data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(data.size)
        var i = 0
        while (i < data.size) {
            out.write(data[i].toInt())
            if ((data[i].toInt() and 0xFF) == 0xFF && i + 1 < data.size && data[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }

    private fun syncSafe(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or
            ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun bigEndian(b: ByteArray, o: Int, len: Int): Int {
        var v = 0
        for (i in 0 until len) v = (v shl 8) or (b[o + i].toInt() and 0xFF)
        return v
    }

    private fun readFully(input: InputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val n = input.read(buf, total, buf.size - total)
            if (n <= 0) break
            total += n
        }
        return total
    }

    private fun skipFully(input: InputStream, count: Long): Long {
        var left = count
        val tmp = ByteArray(8192)
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped > 0) {
                left -= skipped
            } else {
                val n = input.read(tmp, 0, minOf(tmp.size.toLong(), left).toInt())
                if (n <= 0) break
                left -= n
            }
        }
        return count - left
    }
}
