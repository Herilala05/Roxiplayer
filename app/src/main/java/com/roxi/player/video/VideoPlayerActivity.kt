package com.roxi.player.video

import android.app.AlertDialog
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Button
import android.widget.ScrollView
import android.widget.HorizontalScrollView
import androidx.media3.common.AudioAttributes
import java.util.UUID
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.abs
import kotlin.math.max

@OptIn(UnstableApi::class)
class VideoPlayerActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var controls: LinearLayout
    private lateinit var unlockButton: TextView
    private lateinit var playButton: TextView
    private lateinit var positionText: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var titleText: TextView
    private lateinit var feedback: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("roxi_video_player", Context.MODE_PRIVATE) }
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private var playlist = arrayListOf<Uri>()
    private var titles = arrayListOf<String>()
    private var currentUri: Uri? = null
    private var resumePlayback = true
    private var stopped = false
    private var queueToken: String? = null
    private var settingsDialog: AlertDialog? = null
    private val preferences by lazy { getSharedPreferences("roxi_video_controls", MODE_PRIVATE) }
    private var controlsVisible = true
    private var locked = false
    private var resizeIndex = 0
    private var downX = 0f
    private var downY = 0f
    private var startPosition = 0L
    private var startVolume = 0
    private var startBrightness = .5f
    private var gestureMode = GestureMode.NONE

    private val progressUpdater = object : Runnable {
        override fun run() {
            if (::player.isInitialized && !seekBar.isPressed) {
                val duration = max(0L, player.duration)
                val position = max(0L, player.currentPosition)
                seekBar.max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                seekBar.progress = position.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                positionText.text = "${formatTime(position)}  •  ${formatTime(duration)}"
                playButton.text = if (player.isPlaying) "❚❚" else "▶"
            }
            handler.postDelayed(this, 500)
        }
    }
    private val hideControls = Runnable { if (!locked && player.isPlaying) setControlsVisible(false) }
    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(::addSubtitle) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        hideSystemBars()

        volumeControlStream = AudioManager.STREAM_MUSIC
        val selected = (savedInstanceState?.getString("current_uri")
            ?: intent.getStringExtra(EXTRA_URI))?.let(Uri::parse) ?: return finish()
        queueToken = intent.getStringExtra(EXTRA_QUEUE)
        val queue = queueToken?.let { VideoPlaybackQueues.get(it) }
        playlist = queue?.mapTo(arrayListOf()) { it.uri } ?: arrayListOf(selected)
        titles = queue?.mapTo(arrayListOf()) { it.title } ?: arrayListOf()
        val startIndex = playlist.indexOf(selected).coerceAtLeast(0)
        savedInstanceState?.getFloat("brightness", -1f)?.let {
            window.attributes = window.attributes.apply { screenBrightness = it }
        }

        buildInterface()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(SEEK_STEP)
            .setSeekForwardIncrementMs(SEEK_STEP)
            .build().also { exo ->
                playerView.player = exo
                exo.repeatMode = prefs.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF)
                exo.shuffleModeEnabled = prefs.getBoolean(KEY_SHUFFLE, false)
                exo.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playerView.keepScreenOn = isPlaying
                        playButton.text = if (isPlaying) "❚❚" else "▶"
                        if (isPlaying) scheduleHide() else handler.removeCallbacks(hideControls)
                    }
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        currentUri = mediaItem?.localConfiguration?.uri
                        updateTitle()
                    }
                    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                        if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) {
                            playlist.getOrNull(oldPosition.mediaItemIndex)?.let { uri ->
                                val position = if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) 0L else oldPosition.positionMs.coerceAtLeast(0L)
                                prefs.edit().putLong(positionKey(uri), position).apply()
                            }
                        }
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(this@VideoPlayerActivity, "Cette vidéo ne peut pas être lue", Toast.LENGTH_LONG).show()
                    }
                })
                val saved = savedInstanceState?.getLong("position") ?: prefs.getLong(positionKey(selected), 0L)
                exo.setPlaybackSpeed(preferences.getFloat("speed", 1f).coerceIn(.5f, 2f))
                exo.setMediaItems(playlist.map { MediaItem.fromUri(it) }, startIndex, saved)
                currentUri = selected
                exo.prepare()
                resumePlayback = savedInstanceState?.getBoolean("playing") ?: true
                exo.playWhenReady = resumePlayback
            }
        updateTitle()
        handler.post(progressUpdater)
    }

    private fun buildInterface() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            useController = false
            keepScreenOn = false
            resizeMode = preferences.getInt("resize", AspectRatioFrameLayout.RESIZE_MODE_FIT)
        }
        root.addView(playerView)
        root.addView(View(this).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            setOnTouchListener(::handleTouch)
        })

        controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xB8000000.toInt(), 0x08000000, 0xD0000000.toInt()))
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        titleText = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 17f; maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(action("‹") { finish() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        top.addView(titleText)
        top.addView(action("PiP") { enterPip() }, LinearLayout.LayoutParams(dp(52), dp(48)))
        top.addView(action("Réglages") { showSettings() }, LinearLayout.LayoutParams(dp(76), dp(48)))
        top.addView(action("🔒") { setLocked(true) }, LinearLayout.LayoutParams(dp(48), dp(48)))
        controls.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))
        controls.addView(View(this), LinearLayout.LayoutParams(MATCH, 0, 1f))

        positionText = TextView(this).apply { setTextColor(Color.WHITE); textSize = 13f; text = "00:00  •  00:00" }
        seekBar = SeekBar(this).apply {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) positionText.text = "${formatTime(progress.toLong())}  •  ${formatTime(player.duration)}"
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = showControls()
                override fun onStopTrackingTouch(bar: SeekBar?) { player.seekTo(progress.toLong()); scheduleHide() }
            })
        }
        controls.addView(seekBar, LinearLayout.LayoutParams(MATCH, dp(36)))
        controls.addView(positionText, LinearLayout.LayoutParams(WRAP, dp(28)))

        val transport = LinearLayout(this).apply { gravity = Gravity.CENTER }
        transport.addView(action("|◀") { player.seekToPreviousMediaItem() })
        transport.addView(action("↶10") { seekBy(-SEEK_STEP) })
        playButton = action("▶") { if (player.isPlaying) player.pause() else player.play() }
        transport.addView(playButton, LinearLayout.LayoutParams(dp(72), dp(58)))
        transport.addView(action("10↷") { seekBy(SEEK_STEP) })
        transport.addView(action("▶|") { player.seekToNextMediaItem() })
        controls.addView(transport, LinearLayout.LayoutParams(MATCH, dp(62)))

        val tools = LinearLayout(this).apply { gravity = Gravity.CENTER }
        tools.addView(action("Écran") { cycleResizeMode() })
        tools.addView(action("1×") { cycleSpeed(it as TextView) })
        tools.addView(action(repeatLabel()) { cycleRepeat(it as TextView) })
        tools.addView(action(if (prefs.getBoolean(KEY_SHUFFLE, false)) "Aléa ✓" else "Aléa") { toggleShuffle(it as TextView) })
        tools.addView(action("Sous-titre") { subtitlePicker.launch(arrayOf("application/x-subrip", "text/*")) })
        tools.addView(action("Rotation") { rotateScreen() })
        for (i in 0 until tools.childCount) tools.getChildAt(i).layoutParams = LinearLayout.LayoutParams(dp(86), dp(52))
        controls.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tools)
        }, LinearLayout.LayoutParams(MATCH, dp(58)))
        root.addView(controls)

        feedback = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER; visibility = View.GONE
            setPadding(dp(18), dp(8), dp(18), dp(8)); background = rounded(0xC0202020.toInt(), 18)
        }
        root.addView(feedback, FrameLayout.LayoutParams(WRAP, dp(58), Gravity.CENTER))
        unlockButton = action("🔓 Déverrouiller") { setLocked(false) }.apply {
            visibility = View.GONE; background = rounded(0xB0202020.toInt(), 24)
        }
        root.addView(unlockButton, FrameLayout.LayoutParams(dp(170), dp(52), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(18); marginEnd = dp(16)
        })
        setContentView(root)
    }

    private val gestureDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!locked && gestureMode == GestureMode.NONE) {
                    if (controlsVisible) setControlsVisible(false) else showControls()
                }
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!locked) {
                    val delta = if (e.x < playerView.width / 2f) -SEEK_STEP else SEEK_STEP
                    seekBy(delta); showFeedback(if (delta < 0) "−10 secondes" else "+10 secondes")
                }
                return true
            }
        })
    }

    private fun handleTouch(view: View, event: MotionEvent): Boolean {
        if (locked || !::player.isInitialized) return true
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; startPosition = player.currentPosition
                startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                startBrightness = window.attributes.screenBrightness.takeIf { it >= 0f }
                    ?: Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
                gestureMode = GestureMode.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX; val dy = event.y - downY
                if (gestureMode == GestureMode.NONE && (abs(dx) > dp(24) || abs(dy) > dp(24))) {
                    gestureMode = if (abs(dx) > abs(dy)) GestureMode.SEEK else if (downX < view.width / 2f) GestureMode.BRIGHTNESS else GestureMode.VOLUME
                }
                when (gestureMode) {
                    GestureMode.SEEK -> {
                        val target = (startPosition + dx / view.width * 120_000L).toLong().coerceIn(0L, max(0L, player.duration))
                        player.seekTo(target)
                        showFeedback("${if (dx >= 0) "+" else "−"} ${formatTime(abs(target - startPosition))}  •  ${formatTime(target)}")
                    }
                    GestureMode.BRIGHTNESS -> {
                        val value = (startBrightness - dy / view.height).coerceIn(.02f, 1f)
                        window.attributes = window.attributes.apply { screenBrightness = value }
                        showFeedback("☀  ${(value * 100).toInt()} %")
                    }
                    GestureMode.VOLUME -> {
                        val maximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val value = (startVolume - dy / view.height * maximum).toInt().coerceIn(0, maximum)
                        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0) }
                        showFeedback("🔊  ${value * 100 / maximum.coerceAtLeast(1)} %")
                    }
                    else -> Unit
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (gestureMode != GestureMode.NONE) {
                handler.postDelayed({ feedback.visibility = View.GONE }, 700)
            }
        }
        return true
    }

    private fun showSettings() {
        if (settingsDialog?.isShowing == true) return
        if (!::player.isInitialized) return
        val p = player
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        fun slider(label: String, maximum: Int, initial: Int, onChange: (Int) -> Unit) {
            val caption = TextView(this).apply { text = "$label : $initial / $maximum" }
            panel.addView(caption)
            panel.addView(SeekBar(this).apply {
                max = maximum
                progress = initial
                contentDescription = label
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                        caption.text = "$label : $value / $maximum"
                        if (fromUser) onChange(value)
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                })
            }, LinearLayout.LayoutParams(-1, dp(48)))
        }
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        slider("Volume", audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            audio.getStreamVolume(AudioManager.STREAM_MUSIC)) { value ->
            try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0) }
            catch (error: SecurityException) {
                Toast.makeText(this, "Utilise les boutons de volume du téléphone", Toast.LENGTH_SHORT).show()
            }
        }
        val systemBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
        val brightness = window.attributes.screenBrightness.takeIf { it >= 0f } ?: systemBrightness
        slider("Luminosité", 100, (brightness * 100).toInt().coerceIn(0, 100)) { value ->
            window.attributes = window.attributes.apply { screenBrightness = (value / 100f).coerceAtLeast(0.01f) }
        }
        panel.addView(Button(this).apply {
            text = "Luminosité du téléphone"
            setOnClickListener {
                window.attributes = window.attributes.apply { screenBrightness = -1f }
                settingsDialog?.dismiss()
            }
        })
        panel.addView(Button(this).apply {
            text = "Vitesse : ${p.playbackParameters.speed}×"
            setOnClickListener {
                settingsDialog?.dismiss()
                val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                settingsDialog = AlertDialog.Builder(this@VideoPlayerActivity)
                    .setTitle("Vitesse de lecture")
                    .setSingleChoiceItems(speeds.map { "$it×" }.toTypedArray(),
                        speeds.indexOfFirst { it == p.playbackParameters.speed }) { dialog, which ->
                        p.setPlaybackSpeed(speeds[which])
                        preferences.edit().putFloat("speed", speeds[which]).apply()
                        dialog.dismiss()
                    }.setNegativeButton("Annuler", null).show()
            }
        })
        panel.addView(Button(this).apply {
            text = "Format de l’image"
            setOnClickListener {
                settingsDialog?.dismiss()
                val modes = intArrayOf(AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM, AspectRatioFrameLayout.RESIZE_MODE_FILL)
                settingsDialog = AlertDialog.Builder(this@VideoPlayerActivity).setTitle("Format de l’image")
                    .setSingleChoiceItems(arrayOf("Adapter (image entière)", "Zoom (bords coupés)", "Étirer"),
                        modes.indexOf(playerView.resizeMode ?: modes[0])) { dialog, which ->
                        playerView.resizeMode = modes[which]
                        preferences.edit().putInt("resize", modes[which]).apply()
                        dialog.dismiss()
                    }.setNegativeButton("Annuler", null).show()
            }
        })
        settingsDialog = AlertDialog.Builder(this).setTitle("Réglages vidéo")
            .setView(ScrollView(this).apply { addView(panel) })
            .setPositiveButton("Fermer", null).show()
    }

    private fun addSubtitle(uri: Uri) {
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val index = player.currentMediaItemIndex
        val position = player.currentPosition
        val items = playlist.mapIndexed { i, video ->
            if (i != index) MediaItem.fromUri(video) else MediaItem.Builder().setUri(video)
                .setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(uri)
                    .setMimeType(MimeTypes.APPLICATION_SUBRIP).setLanguage("fr")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()))
                .build()
        }
        player.setMediaItems(items, index, position); player.prepare(); player.play()
        Toast.makeText(this, "Sous-titres ajoutés", Toast.LENGTH_SHORT).show()
    }

    private fun cycleResizeMode() {
        resizeIndex = when (playerView.resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> 1
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> 2
            else -> 0
        }
        playerView.resizeMode = when (resizeIndex) {
            1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        preferences.edit().putInt("resize", playerView.resizeMode).apply()
        showFeedback(arrayOf("Ajuster", "Remplir", "Zoomer")[resizeIndex])
    }
    private fun cycleSpeed(label: TextView) {
        val speeds = floatArrayOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f)
        val next = speeds.firstOrNull { it > player.playbackParameters.speed + .01f } ?: speeds.first()
        preferences.edit().putFloat("speed", next).apply()
        player.setPlaybackSpeed(next); label.text = "${next}×".replace(".0", "")
        showFeedback("Vitesse ${label.text}")
    }
    private fun cycleRepeat(label: TextView) {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        prefs.edit().putInt(KEY_REPEAT, player.repeatMode).apply(); label.text = repeatLabel()
        showFeedback(when (player.repeatMode) { Player.REPEAT_MODE_ALL -> "Tout lire en boucle"; Player.REPEAT_MODE_ONE -> "Répéter cette vidéo"; else -> "Répétition désactivée" })
    }
    private fun toggleShuffle(label: TextView) {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        prefs.edit().putBoolean(KEY_SHUFFLE, player.shuffleModeEnabled).apply()
        label.text = if (player.shuffleModeEnabled) "Aléa ✓" else "Aléa"
    }
    private fun repeatLabel() = when (if (::player.isInitialized) player.repeatMode else prefs.getInt(KEY_REPEAT, 0)) {
        Player.REPEAT_MODE_ALL -> "Boucle tout"; Player.REPEAT_MODE_ONE -> "Boucle 1"; else -> "Sans boucle"
    }
    private fun rotateScreen() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }
    private fun setLocked(value: Boolean) {
        locked = value; controls.visibility = if (value) View.GONE else View.VISIBLE
        unlockButton.visibility = if (value) View.VISIBLE else View.GONE; controlsVisible = !value
        showFeedback(if (value) "Écran verrouillé" else "Écran déverrouillé")
        if (!value) scheduleHide()
    }
    private fun seekBy(delta: Long) {
        player.seekTo((player.currentPosition + delta).coerceIn(0L, max(0L, player.duration))); showControls()
    }
    private fun updateTitle() {
        val index = if (::player.isInitialized) player.currentMediaItemIndex else playlist.indexOf(currentUri)
        titleText.text = titles.getOrNull(index)?.takeIf(String::isNotBlank)
            ?: currentUri?.lastPathSegment?.substringBeforeLast('.') ?: "Vidéo"
    }
    private fun showControls() { setControlsVisible(true); scheduleHide() }
    private fun setControlsVisible(value: Boolean) {
        controlsVisible = value && !locked && !isInPictureInPictureMode
        controls.visibility = if (controlsVisible) View.VISIBLE else View.GONE
        if (value) hideSystemBars()
    }
    private fun scheduleHide() { handler.removeCallbacks(hideControls); handler.postDelayed(hideControls, 3_500) }
    private val hideFeedback = Runnable { feedback.visibility = View.GONE }
    private fun showFeedback(message: String) {
        feedback.text = message; feedback.visibility = View.VISIBLE
        handler.removeCallbacks(hideFeedback); handler.postDelayed(hideFeedback, 1_000)
    }
    private fun savePosition() {
        if (!::player.isInitialized) return
        val uri = currentUri ?: return
        val position = if (player.duration > 0 && player.currentPosition > player.duration - 5_000) 0L else player.currentPosition
        prefs.edit().putLong(positionKey(uri), position).apply()
    }
    private fun enterPip() {
        if (!::player.isInitialized || !packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        settingsDialog?.dismiss()
        val size = player.videoSize
        val aspect = if (size.width > 0 && size.height > 0) size.width.toFloat() / size.height else 16f / 9f
        val ratio = Rational((aspect.coerceIn(0.42f, 2.38f) * 1000).toInt(), 1000)
        runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build()) }
    }

    override fun onUserLeaveHint() { super.onUserLeaveHint(); if (::player.isInitialized && player.isPlaying) enterPip() }
    override fun onPictureInPictureModeChanged(inPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(inPip, newConfig)
        if (inPip) { controls.visibility = View.GONE; unlockButton.visibility = View.GONE; feedback.visibility = View.GONE }
        else if (!locked) showControls() else unlockButton.visibility = View.VISIBLE
    }
    override fun onStart() {
        super.onStart()
        stopped = false
        if (::player.isInitialized) {
            handler.removeCallbacks(progressUpdater)
            handler.post(progressUpdater)
            if (resumePlayback) player.play()
        }
    }
    override fun onStop() {
        savePosition()
        if (::player.isInitialized) { resumePlayback = player.playWhenReady; player.pause() }
        stopped = true
        handler.removeCallbacks(progressUpdater)
        settingsDialog?.dismiss()
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (::player.isInitialized) {
            outState.putString("current_uri", currentUri?.toString())
            outState.putLong("position", player.currentPosition)
            outState.putBoolean("playing", if (stopped) resumePlayback else player.playWhenReady)
            outState.putFloat("brightness", window.attributes.screenBrightness)
        }
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        savePosition()
        settingsDialog?.dismiss()
        if (isFinishing) queueToken?.let { VideoPlaybackQueues.remove(it) }
        handler.removeCallbacksAndMessages(null)
        if (::player.isInitialized) { playerView.player = null; player.release() }
        super.onDestroy()
    }
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        else @Suppress("DEPRECATION") run { window.decorView.systemUiVisibility = 5894 }
    }
    private fun action(label: String, click: (View) -> Unit) = TextView(this).apply {
        text = label; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER
        setPadding(dp(6), dp(4), dp(6), dp(4)); setOnClickListener { click(it); showControls() }
        layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f)
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun formatTime(ms: Long): String {
        val total = max(0L, ms) / 1000
        return if (total >= 3600) "%d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60)
        else "%02d:%02d".format(total / 60, total % 60)
    }
    private enum class GestureMode { NONE, SEEK, BRIGHTNESS, VOLUME }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        private const val SEEK_STEP = 10_000L
        private const val EXTRA_URI = "video_uri"
        private const val EXTRA_QUEUE = "video_queue"
        private const val KEY_REPEAT = "repeat_mode"
        private const val KEY_SHUFFLE = "shuffle_mode"
        private fun positionKey(uri: Uri) = "position_${uri.toString().hashCode()}"

        fun play(context: Context, uri: Uri) {
            context.startActivity(Intent(context, VideoPlayerActivity::class.java).putExtra(EXTRA_URI, uri.toString()))
        }
        fun play(context: Context, videos: List<VideoItem>, selected: VideoItem) {
            if (videos.none { it.id == selected.id }) return play(context, selected.uri)
            val token = VideoPlaybackQueues.put(videos)
            context.startActivity(Intent(context, VideoPlayerActivity::class.java)
                .putExtra(EXTRA_URI, selected.uri.toString()).putExtra(EXTRA_QUEUE, token))
        }
    }
}

private object VideoPlaybackQueues {
    private val queues = LinkedHashMap<String, List<VideoItem>>()
    @Synchronized fun put(videos: List<VideoItem>): String {
        while (queues.size >= 4) queues.remove(queues.keys.first())
        return UUID.randomUUID().toString().also { queues[it] = videos.toList() }
    }
    @Synchronized fun get(token: String): List<VideoItem>? = queues[token]
    @Synchronized fun remove(token: String) { queues.remove(token) }
}
