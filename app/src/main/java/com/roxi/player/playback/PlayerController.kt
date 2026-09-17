package com.roxi.player.playback

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.roxi.player.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class QueueEntry(val index: Int, val songId: Long)

data class PlayerUi(
    val connected: Boolean = false,
    val currentId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueEntry> = emptyList(),
    val queueIndex: Int = -1,
    val speed: Float = 1f,
)

/** Pont entre l'interface et le service de lecture (MediaController Media3). */
class PlayerController(private val context: Context, private val scope: CoroutineScope) {

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var tickJob: Job? = null

    private val _state = MutableStateFlow(PlayerUi())
    val state: StateFlow<PlayerUi> = _state.asStateFlow()

    var onConnected: (() -> Unit)? = null
    var onSongListened: ((Long) -> Unit)? = null
    var onListenTime: ((Long) -> Unit)? = null
    var onProgressSave: ((Long, Long, String?) -> Unit)? = null

    private var listenedCurrent = 0L
    private var recordedCurrent = false
    private var lastSave = 0L

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                listenedCurrent = 0L
                recordedCurrent = false
            }
            refresh(
                queueChanged = events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                )
            )
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                c.addListener(listener)
                refresh(queueChanged = true)
                onConnected?.invoke()
            } catch (e: Exception) {
                android.util.Log.e("RoxiPlayer", "Connexion au service impossible", e)
            }
        }, ContextCompat.getMainExecutor(context))

        tickJob = scope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(500)
                val now = SystemClock.elapsedRealtime()
                val elapsed = now - last
                last = now
                tick(elapsed)
            }
        }
    }

    private fun tick(elapsed: Long) {
        val c = controller ?: return
        if (!c.isPlaying) return
        val id = c.currentMediaItem?.mediaId?.toLongOrNull()
        _state.value = _state.value.copy(
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = c.duration.coerceAtLeast(0L),
        )
        onListenTime?.invoke(elapsed)
        listenedCurrent += elapsed
        if (id != null && !recordedCurrent && listenedCurrent >= 30_000L) {
            recordedCurrent = true
            onSongListened?.invoke(id)
        }
        val now = SystemClock.elapsedRealtime()
        if (id != null && now - lastSave > 5_000L) {
            lastSave = now
            onProgressSave?.invoke(id, c.currentPosition, c.currentMediaItem?.localConfiguration?.uri?.toString())
        }
    }

    private fun refresh(queueChanged: Boolean) {
        val c = controller ?: return
        val queue = if (queueChanged) {
            // Ordre réel de lecture (tient compte du mode aléatoire)
            val timeline = c.currentTimeline
            val shuffle = c.shuffleModeEnabled
            val order = ArrayList<Int>()
            if (!timeline.isEmpty) {
                var i = timeline.getFirstWindowIndex(shuffle)
                while (i != C.INDEX_UNSET && order.size < timeline.windowCount) {
                    order += i
                    i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle)
                }
            }
            order.mapNotNull { idx ->
                if (idx < c.mediaItemCount) {
                    c.getMediaItemAt(idx).mediaId.toLongOrNull()?.let { QueueEntry(idx, it) }
                } else null
            }
        } else {
            _state.value.queue
        }
        _state.value = PlayerUi(
            connected = true,
            currentId = c.currentMediaItem?.mediaId?.toLongOrNull(),
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0L),
            durationMs = c.duration.coerceAtLeast(0L),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            queue = queue,
            queueIndex = c.currentMediaItemIndex,
            speed = c.playbackParameters.speed,
        )
    }

    // ----------------------------------------------------------- actions

    fun playSongs(songs: List<Song>, startIndex: Int, shuffle: Boolean = false) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        val start = if (shuffle) songs.indices.random() else startIndex.coerceIn(songs.indices)
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(songs.map { it.toMediaItem() }, start, 0L)
        c.prepare()
        c.play()
    }

    /** Prépare la dernière chanson écoutée sans lancer la lecture. */
    fun restore(songs: List<Song>, index: Int, positionMs: Long) {
        val c = controller ?: return
        if (c.mediaItemCount > 0 || songs.isEmpty()) return
        c.setMediaItems(songs.map { it.toMediaItem() }, index.coerceIn(songs.indices), positionMs)
        c.prepare()
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return playSongs(listOf(song), 0)
        c.addMediaItem((c.currentMediaItemIndex + 1).coerceAtMost(c.mediaItemCount), song.toMediaItem())
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return playSongs(listOf(song), 0)
        c.addMediaItem(song.toMediaItem())
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun pause() {
        controller?.pause()
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(ms: Long) {
        val c = controller ?: return
        c.seekTo(ms)
        _state.value = _state.value.copy(positionMs = ms)
    }

    fun skipTo(queueIndex: Int) {
        val c = controller ?: return
        if (queueIndex !in 0 until c.mediaItemCount) return
        c.seekToDefaultPosition(queueIndex)
        c.play()
    }

    fun removeFromQueue(queueIndex: Int) {
        val c = controller ?: return
        if (queueIndex in 0 until c.mediaItemCount) c.removeMediaItem(queueIndex)
    }

    /** Déplace un titre dans la file (indices réels, mode aléatoire désactivé). */
    fun moveQueueItem(from: Int, to: Int) {
        val c = controller ?: return
        val n = c.mediaItemCount
        if (from == to || from !in 0 until n || to !in 0 until n) return
        c.moveMediaItem(from, to)
    }

    /** Retire toutes les occurrences d'un titre de la file (masqué ou supprimé). */
    fun removeSong(songId: Long) {
        val c = controller ?: return
        val id = songId.toString()
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).mediaId == id) c.removeMediaItem(i)
        }
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun currentPosition(): Long = controller?.currentPosition ?: 0L

    fun release() {
        tickJob?.cancel()
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    private fun Song.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .build()
            )
            .build()
}
