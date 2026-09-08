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
import kotlin.math.min
import kotlin.math.sqrt

data class AudioAnalysisResult(
    val durationMs: Long,
    val sampleRate: Int,
    val channelCount: Int,
    val timePointsMs: LongArray,
    val decibels: FloatArray,
    val waveformNormalized: FloatArray,
    val voiceBandDecibels: FloatArray = FloatArray(0),
    val voiceConfidence: FloatArray = FloatArray(0),
    val estimatedNoiseFloorDb: Float = -40f
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
            if (mediaUri.scheme == "file") {
                extractor.setDataSource(mediaUri.path ?: "")
            } else {
                extractor.setDataSource(context, mediaUri, null)
            }
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

            // 2nd-order bandpass filter centered at 1500Hz, isolating human voice formants (300-3400Hz)
            val voiceFilter = VoiceBandpassFilter(sampleRate)

            // Read PCM buffers and accumulate 30ms windows
            val windowMs = 30L
            val samplesPerWindow = (sampleRate * channelCount * (windowMs / 1000.0)).toInt().coerceAtLeast(1)

            val timeList = mutableListOf<Long>()
            val dbList = mutableListOf<Float>()
            val voiceDbList = mutableListOf<Float>()
            val voiceRatioList = mutableListOf<Float>()
            val zcrList = mutableListOf<Float>()
            val pitchList = mutableListOf<Float>()
            val ampList = mutableListOf<Float>()

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false

            var windowSumSquares = 0.0
            var windowVoiceSumSquares = 0.0
            var windowZcrCount = 0
            var prevSign = 0
            var windowSampleCount = 0
            var currentTimeMs = 0L

            // Pitch autocorrelation buffer (downsampled to ~11kHz)
            val pitchDownsampleFactor = (channelCount * 4).coerceAtLeast(1)
            val effectivePitchRate = sampleRate / 4
            val pitchBufferSize = 350
            val pitchBuffer = FloatArray(pitchBufferSize)
            var pitchBufferCount = 0

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
                            val normSample = sample / 32768.0f
                            windowSumSquares += (sample * sample)

                            // Filter sample through vocal formant bandpass filter
                            val voiceSample = voiceFilter.process(normSample)
                            windowVoiceSumSquares += (voiceSample * voiceSample * 32768.0f * 32768.0f)

                            // Zero-crossing tracking
                            val currentSign = if (normSample > 0.001f) 1 else if (normSample < -0.001f) -1 else 0
                            if (prevSign != 0 && currentSign != 0 && currentSign != prevSign) {
                                windowZcrCount++
                            }
                            if (currentSign != 0) prevSign = currentSign

                            // Collect downsampled audio for pitch autocorrelation
                            if (windowSampleCount % pitchDownsampleFactor == 0 && pitchBufferCount < pitchBufferSize) {
                                pitchBuffer[pitchBufferCount++] = normSample
                            }

                            windowSampleCount++

                            if (windowSampleCount >= samplesPerWindow) {
                                val rms = sqrt(windowSumSquares / windowSampleCount)
                                val voiceRms = sqrt(windowVoiceSumSquares / windowSampleCount)
                                val normalizedAmp = (rms / 32768.0).toFloat().coerceIn(0f, 1f)

                                val db = if (rms > 1e-4) {
                                    (20.0 * log10(rms / 32768.0)).toFloat().coerceIn(-100f, 0f)
                                } else {
                                    -100f
                                }

                                val voiceDb = if (voiceRms > 1e-4) {
                                    (20.0 * log10(voiceRms / 32768.0)).toFloat().coerceIn(-100f, 0f)
                                } else {
                                    -100f
                                }

                                val voiceRatio = if (windowSumSquares > 0.0) {
                                    (windowVoiceSumSquares / windowSumSquares).toFloat().coerceIn(0f, 1f)
                                } else 0f

                                val zcrRate = windowZcrCount.toFloat() / windowSampleCount

                                // Pitch autocorrelation in vocal range (80Hz - 350Hz)
                                var maxPitchPeak = 0f
                                if (pitchBufferCount >= 120 && effectivePitchRate > 0) {
                                    val minLag = (effectivePitchRate / 350).coerceAtLeast(10)
                                    val maxLag = (effectivePitchRate / 80).coerceAtMost(pitchBufferCount - 35)

                                    if (maxLag > minLag) {
                                        val corrLen = 35.coerceAtMost(pitchBufferCount - maxLag)
                                        var norm0 = 0f
                                        for (k in 0 until corrLen) norm0 += pitchBuffer[k] * pitchBuffer[k]

                                        if (norm0 > 1e-4f) {
                                            val step = if (maxLag - minLag > 50) 2 else 1
                                            var lag = minLag
                                            while (lag <= maxLag) {
                                                var dot = 0f
                                                var normLag = 0f
                                                for (k in 0 until corrLen) {
                                                    val a = pitchBuffer[k]
                                                    val b = pitchBuffer[k + lag]
                                                    dot += a * b
                                                    normLag += b * b
                                                }
                                                val denom = sqrt(norm0 * normLag)
                                                if (denom > 1e-4f) {
                                                    val r = dot / denom
                                                    if (r > maxPitchPeak) maxPitchPeak = r
                                                }
                                                lag += step
                                            }
                                        }
                                    }
                                }

                                timeList.add(currentTimeMs)
                                dbList.add(db)
                                voiceDbList.add(voiceDb)
                                voiceRatioList.add(voiceRatio)
                                zcrList.add(zcrRate)
                                pitchList.add(maxPitchPeak.coerceIn(0f, 1f))
                                ampList.add(normalizedAmp)

                                currentTimeMs += windowMs
                                windowSumSquares = 0.0
                                windowVoiceSumSquares = 0.0
                                windowZcrCount = 0
                                windowSampleCount = 0
                                pitchBufferCount = 0
                            }
                        }
                    }

                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }
                }
            }

            // 1. Ambient noise floor estimation (20th percentile of voice-band dB)
            val validDbs = voiceDbList.filter { it > -65f }.sorted()
            val estimatedNoiseFloorDb = if (validDbs.isNotEmpty()) {
                val idx = (validDbs.size * 0.20f).toInt().coerceIn(0, validDbs.size - 1)
                validDbs[idx]
            } else {
                -45f
            }

            // 2. Syllabic modulation (local standard deviation of voice dB across +/- 150ms)
            val windowCount = voiceDbList.size
            val varianceList = FloatArray(windowCount)
            for (i in 0 until windowCount) {
                val start = max(0, i - 4)
                val end = min(windowCount - 1, i + 4)
                var sum = 0f
                val count = end - start + 1
                for (j in start..end) sum += voiceDbList[j]
                val mean = sum / count
                var sumSqDiff = 0f
                for (j in start..end) {
                    val diff = voiceDbList[j] - mean
                    sumSqDiff += diff * diff
                }
                varianceList[i] = sqrt((sumSqDiff / count).toDouble()).toFloat()
            }

            // 3. Multi-feature Voice Activity Confidence (0.0 to 1.0)
            val voiceConfidence = FloatArray(windowCount)
            for (i in 0 until windowCount) {
                val vDb = voiceDbList[i]
                val rDb = dbList[i]
                val ratio = voiceRatioList[i]
                val pitch = pitchList[i]
                val zcr = zcrList[i]
                val stdDev = varianceList[i]

                val snr = vDb - estimatedNoiseFloorDb
                val snrScore = ((snr - 2.5f) / 10.0f).coerceIn(0f, 1f)
                val bandScore = ((ratio - 0.25f) / 0.45f).coerceIn(0f, 1f)
                val pitchScore = ((pitch - 0.25f) / 0.35f).coerceIn(0f, 1f)
                val modScore = ((stdDev - 1.2f) / 3.0f).coerceIn(0f, 1f)
                val zcrScore = if (zcr in 0.015f..0.22f) 1.0f else if (zcr < 0.35f) 0.5f else 0.1f

                val fused = (0.35f * snrScore) +
                            (0.25f * bandScore) +
                            (0.20f * pitchScore) +
                            (0.10f * modScore) +
                            (0.10f * zcrScore)

                voiceConfidence[i] = if (rDb < -55f) (fused * 0.2f).coerceIn(0f, 1f) else fused.coerceIn(0f, 1f)
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
                waveformNormalized = waveform,
                voiceBandDecibels = voiceDbList.toFloatArray(),
                voiceConfidence = voiceConfidence,
                estimatedNoiseFloorDb = estimatedNoiseFloorDb
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

/**
 * 2nd-order Biquad Bandpass filter isolating human voice formant range (300 Hz - 3400 Hz)
 */
private class VoiceBandpassFilter(sampleRate: Int, centerFreq: Float = 1500f, q: Float = 0.707f) {
    private val b0: Float
    private val b2: Float
    private val a1: Float
    private val a2: Float

    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    init {
        val safeRate = sampleRate.coerceAtLeast(8000)
        val w0 = (2.0 * Math.PI * centerFreq / safeRate).toFloat()
        val alpha = (kotlin.math.sin(w0) / (2.0 * q)).toFloat()
        val cosW0 = kotlin.math.cos(w0).toFloat()

        val a0 = 1.0f + alpha
        b0 = alpha / a0
        b2 = -alpha / a0
        a1 = (-2.0f * cosW0) / a0
        a2 = (1.0f - alpha) / a0
    }

    fun process(x: Float): Float {
        val y = b0 * x + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }
}

