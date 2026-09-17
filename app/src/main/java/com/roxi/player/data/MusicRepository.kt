package com.roxi.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Scanne TOUTE la musique du téléphone (mémoire interne + carte SD), sans limite. */
class MusicRepository(private val context: Context) {

    suspend fun scan(minDurationMs: Long): List<Song> = withContext(Dispatchers.IO) {
        val byId = LinkedHashMap<Long, Song>()
        val seenPaths = HashSet<String>()

        val collections: List<Pair<Uri, Boolean>> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(context).map { volume ->
                MediaStore.Audio.Media.getContentUri(volume) to (volume == MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
        } else {
            listOf(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to true)
        }

        @Suppress("DEPRECATION")
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )

        for ((collection, isPrimary) in collections) {
            try {
                context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
                    val iId = c.getColumnIndexOrThrow(projection[0])
                    val iTitle = c.getColumnIndexOrThrow(projection[1])
                    val iArtist = c.getColumnIndexOrThrow(projection[2])
                    val iAlbum = c.getColumnIndexOrThrow(projection[3])
                    val iAlbumId = c.getColumnIndexOrThrow(projection[4])
                    val iDur = c.getColumnIndexOrThrow(projection[5])
                    val iDate = c.getColumnIndexOrThrow(projection[6])
                    val iData = c.getColumnIndexOrThrow(projection[7])
                    val iSize = c.getColumnIndexOrThrow(projection[8])
                    val iMusic = c.getColumnIndexOrThrow(projection[9])
                    val iName = c.getColumnIndexOrThrow(projection[10])

                    while (c.moveToNext()) {
                        val mediaId = c.getLong(iId)
                        val itemUri = ContentUris.withAppendedId(collection, mediaId)
                        // Les identifiants MediaStore peuvent être identiques sur deux volumes.
                        // On conserve les anciens ID sur la mémoire principale pour ne pas perdre
                        // les favoris, et on attribue un ID stable négatif aux autres volumes.
                        val id = if (isPrimary) mediaId
                        else stableVolumeId(itemUri.toString())
                        if (byId.containsKey(id)) continue
                        val duration = c.getLong(iDur)
                        val isMusic = c.getInt(iMusic) != 0
                        if (duration <= 0L) continue
                        if (!isMusic && duration < 30_000L) continue
                        if (duration < minDurationMs) continue

                        val path = c.getString(iData).orEmpty()
                        if (path.isNotEmpty() && !seenPaths.add(path)) continue

                        val displayName = c.getString(iName).orEmpty()
                        val title = c.getString(iTitle)?.takeIf { it.isNotBlank() }
                            ?: displayName.substringBeforeLast('.').ifBlank { "Titre inconnu" }
                        val artist = c.getString(iArtist)
                            ?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Artiste inconnu"
                        val album = c.getString(iAlbum)
                            ?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Album inconnu"
                        val folder = if (path.isNotEmpty()) File(path).parent ?: "/" else "Inconnu"

                        byId[id] = Song(
                            id = id,
                            uri = itemUri,
                            title = title,
                            artist = artist,
                            album = album,
                            albumId = c.getLong(iAlbumId),
                            durationMs = duration,
                            dateAdded = c.getLong(iDate),
                            path = path,
                            folder = folder,
                            size = c.getLong(iSize),
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w("RoxiScan", "Échec du scan de $collection", e)
            }
        }
        byId.values.toList()
    }

    private fun stableVolumeId(value: String): Long {
        var hash = -3750763034362895579L
        value.forEach { hash = (hash xor it.code.toLong()) * 1099511628211L }
        return if (hash == Long.MIN_VALUE) Long.MIN_VALUE + 1 else -kotlin.math.abs(hash)
    }
}
