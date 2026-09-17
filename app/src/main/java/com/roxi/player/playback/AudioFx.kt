package com.roxi.player.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.roxi.player.data.Id3Lyrics
import com.roxi.player.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.pow

data class EqInfo(
    val minLevel: Int,
    val maxLevel: Int,
    val centersHz: List<Int>,
    val presets: List<String>,
)

/**
 * Tout le traitement du son de Roxi Player, appliqué AVANT la sortie
 * (haut-parleur, écouteurs, Bluetooth) :
 *  - normalisation du volume
 *  - égaliseur + renforcement des basses
 *  - fondu entre les chansons
 */
object AudioFx {
    private const val TAG = "RoxiAudioFx"

    private var appContext: Context? = null
    private var sp: SharedPreferences? = null
    private var loudPrefs: SharedPreferences? = null
    private var player: ExoPlayer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var enhancer: LoudnessEnhancer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val analyzer = Executors.newSingleThreadExecutor()
    private val analyzing = HashSet<String>()

    private var normTarget = 1f
    private var normCurrent = 1f
    private var fadingIn = false

    private val _eqInfo = MutableStateFlow<EqInfo?>(null)
    val eqInfo: StateFlow<EqInfo?> = _eqInfo.asStateFlow()
    private val _eqEnabled = MutableStateFlow(false)
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()
    private val _bands = MutableStateFlow<List<Int>>(emptyList())
    val bands: StateFlow<List<Int>> = _bands.asStateFlow()
    private val _preset = MutableStateFlow(-1)
    val preset: StateFlow<Int> = _preset.asStateFlow()
    private val _bass = MutableStateFlow(0)
    val bass: StateFlow<Int> = _bass.asStateFlow()

    fun init(context: Context) {
        if (sp != null) return
        appContext = context.applicationContext
        sp = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).also {
            _eqEnabled.value = it.getBoolean(Prefs.KEY_EQ_ENABLED, false)
            _preset.value = it.getInt(Prefs.KEY_EQ_PRESET, -1)
            _bass.value = it.getInt(Prefs.KEY_BASS, 0)
            _bands.value = it.getString(Prefs.KEY_EQ_BANDS, "")
                ?.split(',')?.mapNotNull { v -> v.toIntOrNull() } ?: emptyList()
        }
        loudPrefs = context.getSharedPreferences("roxi_loudness", Context.MODE_PRIVATE)
    }

    // ------------------------------------------------------------ service

    fun attach(context: Context, exo: ExoPlayer) {
        init(context)
        player = exo
        val session = exo.audioSessionId
        try {
            equalizer = Equalizer(1000, session).also { eq ->
                val range = eq.bandLevelRange
                val centers = (0 until eq.numberOfBands).map { eq.getCenterFreq(it.toShort()) / 1000 }
                val presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
                _eqInfo.value = EqInfo(range[0].toInt(), range[1].toInt(), centers, presets)
                if (_bands.value.size != centers.size) {
                    _bands.value = centers.indices.map { eq.getBandLevel(it.toShort()).toInt() }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Égaliseur indisponible", e)
        }
        try {
            bassBoost = BassBoost(1000, session)
        } catch (e: Exception) {
            Log.w(TAG, "Basses indisponibles", e)
        }
        try {
            enhancer = LoudnessEnhancer(session)
        } catch (e: Exception) {
            Log.w(TAG, "Amplificateur indisponible", e)
        }
        applyEq()
        exo.addListener(listener)
        handler.post(tick)
        refreshNormalization()
    }

    fun detach() {
        handler.removeCallbacks(tick)
        player?.removeListener(listener)
        player = null
        listOf(equalizer, bassBoost, enhancer).forEach {
            try {
                it?.release()
            } catch (e: Exception) {
            }
        }
        equalizer = null
        bassBoost = null
        enhancer = null
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            fadingIn = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            refreshNormalization()
        }
    }

    // ------------------------------------------------------------ égaliseur

    fun setEqEnabled(on: Boolean) {
        _eqEnabled.value = on
        sp?.edit()?.putBoolean(Prefs.KEY_EQ_ENABLED, on)?.apply()
        applyEq()
    }

    fun setBand(index: Int, level: Int) {
        val list = _bands.value.toMutableList()
        if (index !in list.indices) return
        list[index] = level
        _bands.value = list
        _preset.value = -1
        saveEq()
        try {
            equalizer?.setBandLevel(index.toShort(), level.toShort())
        } catch (e: Exception) {
        }
    }

    fun usePreset(index: Int) {
        val eq = equalizer ?: return
        try {
            eq.usePreset(index.toShort())
            _preset.value = index
            _bands.value = (0 until eq.numberOfBands).map { eq.getBandLevel(it.toShort()).toInt() }
            saveEq()
        } catch (e: Exception) {
            Log.w(TAG, "Préréglage impossible", e)
        }
    }

    fun setBass(strength: Int) {
        _bass.value = strength
        sp?.edit()?.putInt(Prefs.KEY_BASS, strength)?.apply()
        applyEq()
    }

    private fun saveEq() {
        sp?.edit()
            ?.putString(Prefs.KEY_EQ_BANDS, _bands.value.joinToString(","))
            ?.putInt(Prefs.KEY_EQ_PRESET, _preset.value)
            ?.apply()
    }

    private fun applyEq() {
        val on = _eqEnabled.value
        try {
            equalizer?.let { eq ->
                eq.enabled = on
                if (on) _bands.value.forEachIndexed { i, lvl -> eq.setBandLevel(i.toShort(), lvl.toShort()) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Application de l'égaliseur impossible", e)
        }
        try {
            bassBoost?.let { b ->
                val s = _bass.value
                b.enabled = on && s > 0
                if (b.strengthSupported && s > 0) b.setStrength(s.toShort())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Application des basses impossible", e)
        }
    }

    // ------------------------------------------------------------ normalisation

    /** À appeler quand un réglage change (activer, niveau). */
    fun refreshNormalization() {
        handler.post { computeNormalization() }
    }

    private fun computeNormalization() {
        val p = player ?: return
        val prefs = sp ?: return
        if (!prefs.getBoolean(Prefs.KEY_NORMALIZE, false)) {
            setGain(0f)
            return
        }
        val id = p.currentMediaItem?.mediaId ?: return
        val stored = loudPrefs?.getFloat(id, Float.NaN) ?: Float.NaN
        if (!stored.isNaN()) {
            setGain(gainFor(stored, prefs.getInt(Prefs.KEY_NORMALIZE_LEVEL, 1)))
            return
        }
        // Inconnue : on garde le niveau actuel et on mesure en arrière-plan
        val ctx = appContext ?: return
        val uri = p.currentMediaItem?.localConfiguration?.uri ?: return
        synchronized(analyzing) { if (!analyzing.add(id)) return }
        analyzer.execute {
            val loudness = readReplayGain(ctx, uri) ?: LoudnessAnalyzer.measure(ctx, uri)
            synchronized(analyzing) { analyzing.remove(id) }
            if (loudness != null) {
                loudPrefs?.edit()?.putFloat(id, loudness)?.apply()
                handler.post {
                    if (player?.currentMediaItem?.mediaId == id) computeNormalization()
                }
            }
        }
    }

    /** ReplayGain : « baisser de X dB » ⇒ force équivalente = -18 - X. */
    private fun readReplayGain(context: Context, uri: android.net.Uri): Float? = try {
        context.contentResolver.openInputStream(uri)?.use { Id3Lyrics.readReplayGain(it) }?.let { -18f - it }
    } catch (e: Exception) {
        null
    }

    private fun gainFor(loudness: Float, level: Int): Float {
        val target = when (level) {
            0 -> -21f
            2 -> -13f
            else -> -17f
        }
        return (target - loudness).coerceIn(-12f, 8f)
    }

    private fun setGain(db: Float) {
        if (db <= 0f) {
            normTarget = 10f.pow(db / 20f)
            try {
                enhancer?.setTargetGain(0)
                enhancer?.enabled = false
            } catch (e: Exception) {
            }
        } else {
            normTarget = 1f
            try {
                enhancer?.setTargetGain((db * 100).toInt())
                enhancer?.enabled = true
            } catch (e: Exception) {
            }
        }
    }

    // ------------------------------------------------------------ volume final (normalisation × fondu)

    private val tick = object : Runnable {
        override fun run() {
            val p = player ?: return
            // Rapprochement doux vers le volume de normalisation
            normCurrent += (normTarget - normCurrent) * 0.15f

            var fade = 1f
            val fadeMs = (sp?.getInt(Prefs.KEY_CROSSFADE, 0) ?: 0) * 1000L
            if (fadeMs > 0) {
                val half = fadeMs / 2f
                val pos = p.currentPosition
                val dur = p.duration
                if (dur > fadeMs * 2 && p.repeatMode != Player.REPEAT_MODE_ONE && p.hasNextMediaItem()) {
                    fade = minOf(fade, ((dur - pos) / half).coerceIn(0f, 1f))
                }
                if (fadingIn) {
                    val v = pos / half
                    if (v >= 1f) fadingIn = false else fade = minOf(fade, v.coerceIn(0f, 1f))
                }
            } else {
                fadingIn = false
            }
            // courbe douce (l'oreille perçoit mieux un fondu « au carré »)
            val volume = (normCurrent * fade * fade).coerceIn(0f, 1f)
            if (abs(p.volume - volume) > 0.003f) p.volume = volume

            handler.postDelayed(this, if (p.isPlaying) 50L else 400L)
        }
    }
}
