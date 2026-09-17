package com.roxi.player.playback

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Mesure la « force » moyenne d'une chanson (en dB par rapport au maximum).
 * On décode environ 45 secondes à partir de 20 % du morceau, par blocs de 400 ms,
 * en ignorant les silences.
 */
object LoudnessAnalyzer {

    fun measure(context: Context, uri: Uri): Float? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    track = i
                    format = f
                    break
                }
            }
            if (track < 0 || format == null) return null
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            if (durationUs > 60_000_000L) {
                extractor.seekTo((durationUs * 0.2).toLong(), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(format, null, null, 0)
            c.start()

            val info = MediaCodec.BufferInfo()
            val startWall = SystemClock.elapsedRealtime()
            var firstUs = -1L
            var inputDone = false
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false

            val blockPowers = ArrayList<Double>()
            var blockSum = 0.0
            var blockCount = 0
            var blockSize = sampleRate * channels * 2 / 5 // 400 ms

            loop@ while (true) {
                if (SystemClock.elapsedRealtime() - startWall > 10_000L) break
                if (!inputDone) {
                    val inIndex = c.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buf = c.getInputBuffer(inIndex) ?: break
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            c.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = c.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = c.outputFormat
                        sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        blockSize = sampleRate * channels * 2 / 5
                    }
                    outIndex >= 0 -> {
                        val out = c.getOutputBuffer(outIndex)
                        if (out != null && info.size > 0) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            out.order(ByteOrder.LITTLE_ENDIAN)
                            if (floatPcm) {
                                val fb = out.asFloatBuffer()
                                while (fb.hasRemaining()) {
                                    val v = fb.get().toDouble()
                                    blockSum += v * v
                                    if (++blockCount >= blockSize) {
                                        blockPowers += blockSum / blockCount
                                        blockSum = 0.0
                                        blockCount = 0
                                    }
                                }
                            } else {
                                val sb = out.asShortBuffer()
                                while (sb.hasRemaining()) {
                                    val v = sb.get() / 32768.0
                                    blockSum += v * v
                                    if (++blockCount >= blockSize) {
                                        blockPowers += blockSum / blockCount
                                        blockSum = 0.0
                                        blockCount = 0
                                    }
                                }
                            }
                        }
                        c.releaseOutputBuffer(outIndex, false)
                        if (firstUs < 0) firstUs = info.presentationTimeUs
                        if (info.presentationTimeUs - firstUs > 45_000_000L) break@loop
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break@loop
                    }
                }
            }

            // Ignore les silences (< -50 dB), moyenne de la puissance restante
            val gate = Math.pow(10.0, -50.0 / 10.0)
            val loud = blockPowers.filter { it > gate }
            if (loud.isEmpty()) return null
            val mean = loud.average()
            return (10.0 * log10(mean)).toFloat()
        } catch (e: Exception) {
            Log.w("RoxiLoudness", "Mesure impossible", e)
            return null
        } finally {
            try {
                codec?.stop()
            } catch (e: Exception) {
            }
            codec?.release()
            extractor.release()
        }
    }

    @Suppress("unused")
    private fun rms(power: Double) = sqrt(power)
}
