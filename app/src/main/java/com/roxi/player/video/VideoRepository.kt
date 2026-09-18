package com.roxi.player.video

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class VideoRepository(private val context: Context) {
    suspend fun scan(): List<VideoItem> = withContext(Dispatchers.IO) {
        val result = ArrayList<VideoItem>()
        val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(context).map { MediaStore.Video.Media.getContentUri(it) }
        } else listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
        )
        volumes.forEach { collection ->
            coroutineContext.ensureActive()
            try {
                context.contentResolver.query(collection, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
                    val id = c.getColumnIndexOrThrow(projection[0])
                    val name = c.getColumnIndexOrThrow(projection[1])
                    val duration = c.getColumnIndexOrThrow(projection[2])
                    val size = c.getColumnIndexOrThrow(projection[3])
                    val date = c.getColumnIndexOrThrow(projection[4])
                    val folder = c.getColumnIndex(projection[5])
                    while (c.moveToNext()) {
                        coroutineContext.ensureActive()
                        val mediaId = c.getLong(id)
                        val uri = ContentUris.withAppendedId(collection, mediaId)
                        result += VideoItem(
                            id = uri.toString(),
                            uri = uri,
                            title = c.getString(name)?.substringBeforeLast('.') ?: "Vidéo",
                            durationMs = c.getLong(duration),
                            size = c.getLong(size),
                            dateAdded = c.getLong(date),
                            folder = if (folder >= 0) c.getString(folder)?.takeIf { it.isNotBlank() } ?: "Autres" else "Autres",
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("RoxiVideoScan", "Volume vidéo inaccessible", error)
            }
        }
        result.distinctBy { it.uri }.sortedByDescending { it.dateAdded }
    }
}

class SecretVault(private val context: Context) {
    private val prefs = context.getSharedPreferences("roxi_secret", Context.MODE_PRIVATE)
    private val directory = File(context.filesDir, "secret_videos").apply { mkdirs() }

    fun hasPin(): Boolean = prefs.contains("pin_hash")

    fun setPin(pin: String): Boolean {
        if (pin.length < 4 || pin.any { !it.isDigit() }) return false
        val salt = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
        prefs.edit().putString("pin_salt", android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
            .putString("pin_hash", hash(pin, salt)).apply()
        return true
    }

    fun verify(pin: String): Boolean {
        val salt = prefs.getString("pin_salt", null)?.let { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) } ?: return false
        val expected = prefs.getString("pin_hash", null) ?: return false
        return java.security.MessageDigest.isEqual(expected.toByteArray(), hash(pin, salt).toByteArray())
    }

    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val display = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?.replace(Regex("[^a-zA-Z0-9._ -]"), "_") ?: "video_${System.currentTimeMillis()}.mp4"
            val target = File(directory, "${System.currentTimeMillis()}_$display")
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
                ?: error("Fichier inaccessible")
            true
        }.getOrDefault(false)
    }

    fun list(): List<VideoItem> = directory.listFiles()?.filter { it.isFile }?.map { file ->
        VideoItem(
            id = file.absolutePath,
            uri = Uri.fromFile(file),
            title = file.name.substringAfter('_').substringBeforeLast('.'),
            durationMs = 0L,
            size = file.length(),
            dateAdded = file.lastModified() / 1000L,
            folder = "Dossier secret",
            secret = true,
        )
    }?.sortedByDescending { it.dateAdded } ?: emptyList()

    fun delete(item: VideoItem): Boolean {
        val path = item.uri.path ?: return false
        return item.secret && File(path).delete()
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        val bytes = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }
}

