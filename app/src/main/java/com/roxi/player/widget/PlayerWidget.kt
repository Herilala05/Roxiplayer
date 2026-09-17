package com.roxi.player.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.media3.session.MediaButtonReceiver
import com.roxi.player.R
import com.roxi.player.data.Prefs
import java.util.concurrent.Executors
import android.app.PendingIntent

/** Widget de l'écran d'accueil : pochette, titre, ⏮ ▶ ⏭. */
class PlayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WidgetUpdater.refresh(context)
    }
}

object WidgetUpdater {
    private val executor = Executors.newSingleThreadExecutor()

    private data class State(val title: String?, val artist: String?, val playing: Boolean, val mediaId: String?, val mediaUri: String?)

    @Volatile
    private var state = State(null, null, false, null, null)

    @Volatile
    private var artFor: String? = null

    @Volatile
    private var art: Bitmap? = null

    fun update(context: Context, title: String?, artist: String?, playing: Boolean, mediaId: String?, mediaUri: String?) {
        state = State(title, artist, playing, mediaId, mediaUri)
        refresh(context)
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
        executor.execute {
            try {
                val manager = AppWidgetManager.getInstance(app)
                val ids = manager.getAppWidgetIds(ComponentName(app, PlayerWidget::class.java))
                if (ids.isEmpty()) return@execute
                val s = state
                if (s.mediaId != artFor) {
                    artFor = s.mediaId
                    art = s.mediaUri?.let { Uri.parse(it) }?.let { loadArt(app, it) }
                        ?: s.mediaId?.toLongOrNull()?.let {
                            loadArt(app, ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it))
                        }
                }
                manager.updateAppWidget(ids, build(app, s))
            } catch (e: Exception) {
                android.util.Log.w("RoxiWidget", "Mise à jour impossible", e)
            }
        }
    }

    private fun build(context: Context, s: State): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_player)
        views.setTextViewText(R.id.widget_title, s.title ?: "Roxi Player")
        views.setTextViewText(R.id.widget_artist, s.artist ?: "Touchez ▶ pour écouter")
        val bmp = art
        if (bmp != null) views.setImageViewBitmap(R.id.widget_art, bmp)
        else views.setImageViewResource(R.id.widget_art, R.drawable.roxi_logo)
        views.setImageViewResource(R.id.widget_play, if (s.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play)

        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val openPi = open?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        if (openPi != null) {
            views.setOnClickPendingIntent(R.id.widget_root, openPi)
            views.setOnClickPendingIntent(R.id.widget_art, openPi)
        }

        // Jamais rien écouté : le bouton ▶ ouvre simplement l'appli
        val neverPlayed = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).getLong("last_id", -1L) < 0
        views.setOnClickPendingIntent(
            R.id.widget_play,
            if (neverPlayed && s.mediaId == null && openPi != null) openPi
            else mediaButton(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE),
        )
        views.setOnClickPendingIntent(R.id.widget_prev, mediaButton(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        views.setOnClickPendingIntent(R.id.widget_next, mediaButton(context, KeyEvent.KEYCODE_MEDIA_NEXT))
        return views
    }

    private fun mediaButton(context: Context, keyCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(context, MediaButtonReceiver::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        return PendingIntent.getBroadcast(
            context, keyCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun loadArt(context: Context, uri: Uri): Bitmap? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return context.contentResolver.loadThumbnail(uri, Size(240, 240), null)
            }
            // Android ≤ 9 : pochette de l'album via MediaStore
            val albumId = context.contentResolver.query(
                uri, arrayOf(MediaStore.Audio.Media.ALBUM_ID), null, null, null,
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null } ?: return null
            val art = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
            return context.contentResolver.openInputStream(art)?.use { input ->
                BitmapFactory.decodeStream(input)?.let { Bitmap.createScaledBitmap(it, 240, 240, true) }
            }
        } catch (e: Exception) {
            return null
        }
    }
}
