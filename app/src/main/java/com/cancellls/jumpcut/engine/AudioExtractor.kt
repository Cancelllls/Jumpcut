package com.cancellls.jumpcut.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

data class AudioAnalysisResult(
    val durationMs: Long,
    val sampleRate: Int,
    val channelCount: Int,
    val timePointsMs: LongArray,
    val decibels: FloatArray,
    val waveformNormalized: FloatArray
)

object AudioExtractor {
    private const val TAG = "AudioExtractor"
    private const val TIMEOUT_US = 5000L

    suspend fun analyzeAudio(
        context: Context,
        mediaUri: Uri,
        onProgress: (Float) -> Unit
    ): AudioAnalysisResult = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(context, mediaUri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                throw IllegalStateException("No audio track found in selected media")
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            val durationMs = durationUs / 1000L

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val sampleRate = if (audioFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100

            val channelCount = if (audioFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            // Read PCM buffers and accumulate 30ms windows
            val windowMs = 30L
            val samplesPerWindow = (sampleRate * channelCount * (windowMs / 1000.0)).toInt().coerceAtLeast(1)

            val timeList = mutableListOf<Long>()
            val dbList = mutableListOf<Float>()
            val ampList = mutableListOf<Float>()

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false

            var windowSumSquares = 0.0
            var windowSampleCount = 0
            var currentTimeMs = 0L

            while (!isOutputEOS) {
                if (!isInputEOS) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isInputEOS = true
                        } else {
                            val pts = extractor.sampleTime
                            codec.queueInputBuffer(inIndex, 0, sampleSize, pts, 0)
                            extractor.advance()
                            if (durationUs > 0) {
                                onProgress(0.1f + (pts.toFloat() / durationUs) * 0.7f)
                            }
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                        val shortBuffer = outputBuffer.asShortBuffer()
                        while (shortBuffer.hasRemaining()) {
                            val sample = shortBuffer.get()
                            windowSumSquares += (sample * sample)
                            windowSampleCount++

                            if (windowSampleCount >= samplesPerWindow) {
                                val rms = sqrt(windowSumSquares / windowSampleCount)
                                val normalizedAmp = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
                                val db = if (rms > 1e-4) {
                                    (20.0 * log10(rms / 32768.0)).toFloat().coerceIn(-100f, 0f)
                                } else {
                                    -100f
                                }

                                timeList.add(currentTimeMs)
                                dbList.add(db)
                                ampList.add(normalizedAmp)

                                currentTimeMs += windowMs
                                windowSumSquares = 0.0
                                windowSampleCount = 0
                            }
                        }
                    }

                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }
                }
            }

            // Downsample waveform for UI rendering (target 200 - 300 bars)
            val targetBars = 250
            val waveform = downsampleWaveform(ampList, targetBars)

            AudioAnalysisResult(
                durationMs = max(durationMs, currentTimeMs),
                sampleRate = sampleRate,
                channelCount = channelCount,
                timePointsMs = timeList.toLongArray(),
                decibels = dbList.toFloatArray(),
                waveformNormalized = waveform
            )
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing codec", e)
            }
            extractor.release()
        }
    }

    private fun downsampleWaveform(raw: List<Float>, targetSize: Int): FloatArray {
        if (raw.isEmpty()) return FloatArray(targetSize) { 0.1f }
        if (raw.size <= targetSize) return raw.toFloatArray()

        val result = FloatArray(targetSize)
        val bucketSize = raw.size.toFloat() / targetSize

        for (i in 0 until targetSize) {
            val start = (i * bucketSize).toInt()
            val end = ((i + 1) * bucketSize).toInt().coerceAtMost(raw.size)
            var maxVal = 0f
            for (j in start until end) {
                if (raw[j] > maxVal) maxVal = raw[j]
            }
            result[i] = maxVal
        }

        // Normalize so peaks reach ~0.95
        val globalMax = result.maxOrNull() ?: 1f
        if (globalMax > 0.05f) {
            val scale = 0.95f / globalMax
            for (i in result.indices) {
                result[i] = (result[i] * scale).coerceIn(0.05f, 1f)
            }
        }
        return result
    }
}
