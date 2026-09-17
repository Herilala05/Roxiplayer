package com.roxi.player.video

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class VideoPlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var view: PlayerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.getStringExtra(EXTRA_URI)?.let { Uri.parse(it) } ?: return finish()
        view = PlayerView(this).also {
            it.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            it.keepScreenOn = true
            setContentView(it)
        }
        player = ExoPlayer.Builder(this).build().also { p ->
            view?.player = p
            p.addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Toast.makeText(this@VideoPlayerActivity, "Cette vidéo ne peut pas être lue", Toast.LENGTH_LONG).show()
                }
            })
            p.setMediaItem(MediaItem.fromUri(uri))
            p.prepare()
            p.playWhenReady = true
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player?.isPlaying == true) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }
    }

    override fun onPictureInPictureModeChanged(inPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(inPip, newConfig)
        view?.useController = !inPip
    }

    override fun onDestroy() {
        view?.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        fun play(context: android.content.Context, uri: Uri) {
            context.startActivity(Intent(context, VideoPlayerActivity::class.java).putExtra(EXTRA_URI, uri.toString()))
        }

        private const val EXTRA_URI = "video_uri"
    }
}
