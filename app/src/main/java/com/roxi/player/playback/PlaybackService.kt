package com.roxi.player.playback

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.provider.MediaStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.roxi.player.data.Prefs
import com.roxi.player.widget.WidgetUpdater

/**
 * Service de lecture : continue la musique écran éteint, affiche la notification,
 * répond à l'écran de verrouillage, aux écouteurs Bluetooth et au widget.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    private val widgetListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                )
            ) {
                val md = player.mediaMetadata
                WidgetUpdater.update(
                    this@PlaybackService,
                    title = md.title?.toString(),
                    artist = md.artist?.toString(),
                    playing = player.isPlaying,
                    mediaId = player.currentMediaItem?.mediaId,
                    mediaUri = player.currentMediaItem?.localConfiguration?.uri?.toString(),
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause si on débranche les écouteurs
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        // Lecture continue par défaut : à la fin de la file, elle repart du début.
        // L'utilisateur peut toujours choisir « ce titre » ou désactiver la répétition.
        player.repeatMode = Player.REPEAT_MODE_ALL
        audioSessionId = player.audioSessionId
        AudioFx.attach(this, player)
        player.addListener(widgetListener)

        val builder = MediaSession.Builder(this, player).setCallback(SessionCallback())
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            val pi = PendingIntent.getActivity(
                this, 0, launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setSessionActivity(pi)
        }
        session = builder.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        if (controllerInfo.packageName == packageName || controllerInfo.isTrusted) session else null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.removeListener(widgetListener)
            WidgetUpdater.update(this@PlaybackService, null, null, false, null, null)
            AudioFx.detach()
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
        /** Remet l'URI des morceaux (elle n'est pas toujours transmise par le contrôleur). */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val updated = mediaItems.map { item ->
                val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
                if (uri != null) item.buildUpon().setUri(uri).build() else item
            }.toMutableList()
            return Futures.immediateFuture(updated)
        }

        /** Bouton lecture du widget / du Bluetooth alors que l'appli est fermée. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val sp = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
            val id = sp.getLong("last_id", -1L)
            if (id < 0) return Futures.immediateFailedFuture(IllegalStateException("Aucune lecture précédente"))
            val uri = sp.getString("last_uri", null)?.let { android.net.Uri.parse(it) }
                ?: ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            val item = MediaItem.Builder().setMediaId(id.toString()).setUri(uri).build()
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(listOf(item), 0, sp.getLong("last_pos", 0L))
            )
        }
    }

    companion object {
        @Volatile
        var audioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    }
}
