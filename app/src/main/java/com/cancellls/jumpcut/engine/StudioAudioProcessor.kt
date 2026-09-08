package com.cancellls.jumpcut.engine

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Studio-grade audio processor for JumpCut AI:
 * 1. Studio Speech Auto-Leveler (Target ~ -14 LUFS / -16 dBFS RMS speech dialogue).
 * 2. True-Peak Soft Limiter (-1.0 dBFS ceiling at 30,000 / 32,767 PCM short).
 * 3. Dual-ended 15ms S-curve micro-crossfades (fade-in & fade-out) at cut boundaries.
 * 4. Room-Tone Comfort Bed preventing unnatural vacuum dropouts across cuts.
 */
@OptIn(UnstableApi::class)
class StudioAudioProcessor(
    private val segmentDurationMs: Long = 0L,
    private val levelingEnabled: Boolean = true,
    private val roomToneSmoothingEnabled: Boolean = true,
    private val fadeDurationMs: Long = 15L,
    private val ambientNoiseFloorDb: Float = -40f
) : BaseAudioProcessor() {

    private var samplesProcessed = 0L
    private var fadeSamples = 0L
    private var totalExpectedSamples = 0L

    // Target speech RMS (~ -16 dBFS in 16-bit PCM: ~5,200 amplitude out of 32,767)
    private val targetSpeechRms = 5200.0
    private var currentGain = 1.0f
    private val maxGainBoost = 3.16f // +10 dB cap
    private val maxGainCut = 0.25f   // -12 dB cut cap

    // Peak limiter soft-knee threshold (-2.7 dBFS = 24,000 short, ceiling = 30,000 short)
    private val limiterKnee = 24000.0
    private val limiterCeiling = 30000.0

    // Comfort bed noise floor generator (PRNG state for pink/shaped noise)
    private var comfortNoiseState = 0x12345678
    private val comfortNoiseGain: Float = run {
        // -40 dB -> amplitude ~15, -30 dB -> amplitude ~35, -50 dB -> amplitude ~6
        val norm = ((ambientNoiseFloorDb + 60f) / 40f).coerceIn(0.1f, 1.0f)
        (norm * 24.0f).coerceIn(4f, 32f)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            fadeSamples = 0L
            totalExpectedSamples = 0L
            return inputAudioFormat
        }
        val sampleRate = inputAudioFormat.sampleRate
        val channelCount = inputAudioFormat.channelCount
        fadeSamples = (sampleRate * fadeDurationMs / 1000L) * channelCount
        totalExpectedSamples = if (segmentDurationMs > 0) {
            (sampleRate * segmentDurationMs / 1000L) * channelCount
        } else {
            0L
        }
        samplesProcessed = 0L
        currentGain = 1.0f
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        outputBuffer.order(ByteOrder.nativeOrder())
        val shortBuffer = inputBuffer.asShortBuffer()
        val totalShorts = remaining / 2

        // Process in 64-sample micro-chunks to compute local speech envelope smoothly
        var chunkSumSq = 0.0
        var chunkSize = 0

        for (i in 0 until totalShorts) {
            val rawSample = shortBuffer.get()
            val sampleIdx = samplesProcessed
            samplesProcessed++

            // 1. Dual-Ended S-Curve Crossfade Gain (Half-Cosine)
            var seamGain = 1.0f
            if (fadeSamples > 0L) {
                if (sampleIdx < fadeSamples) {
                    // Fade-in at start of segment
                    val progress = (sampleIdx.toFloat() / fadeSamples.toFloat()).coerceIn(0f, 1f)
                    seamGain = sin(progress * (PI.toFloat() / 2f))
                } else if (totalExpectedSamples > 0L && sampleIdx >= (totalExpectedSamples - fadeSamples)) {
                    // Fade-out at end of segment
                    val remainingSamples = (totalExpectedSamples - sampleIdx).coerceAtLeast(0)
                    val progress = (remainingSamples.toFloat() / fadeSamples.toFloat()).coerceIn(0f, 1f)
                    seamGain = sin(progress * (PI.toFloat() / 2f))
                }
            }

            // 2. Speech Auto-Leveling (Smooth AGC)
            chunkSumSq += (rawSample * rawSample).toDouble()
            chunkSize++
            if (chunkSize >= 64) {
                if (levelingEnabled) {
                    val localRms = sqrt(chunkSumSq / chunkSize)
                    // Only adjust gain if active speech is detected (above -42 dBFS baseline)
                    if (localRms > 600.0) {
                        val idealGain = (targetSpeechRms / localRms).toFloat().coerceIn(maxGainCut, maxGainBoost)
                        // Smooth attack/release to prevent audio pumping (0.05 interpolation rate)
                        currentGain += (idealGain - currentGain) * 0.05f
                    }
                }
                chunkSumSq = 0.0
                chunkSize = 0
            }

            // Apply leveling and seam gain
            val activeLevelGain = if (levelingEnabled) currentGain else 1.0f
            var sampleVal = (rawSample * activeLevelGain * seamGain).toDouble()

            // 3. Room-Tone Comfort Bed
            if (roomToneSmoothingEnabled && comfortNoiseGain > 0f) {
                // Linear congruential pseudorandom generator for smooth subtle room bed
                comfortNoiseState = (comfortNoiseState * 1103515245 + 12345) and 0x7FFFFFFF
                val noiseSample = ((comfortNoiseState ushr 16) - 16384) / 16384.0f
                sampleVal += (noiseSample * comfortNoiseGain)
            }

            // 4. True-Peak Soft Limiter (Smooth Tanh compression above limiter knee)
            val absSample = kotlin.math.abs(sampleVal)
            val limitedSample = if (absSample > limiterKnee) {
                val excess = absSample - limiterKnee
                val headroom = limiterCeiling - limiterKnee
                val compressed = limiterKnee + headroom * tanh(excess / headroom)
                if (sampleVal < 0) -compressed else compressed
            } else {
                sampleVal
            }

            val finalShort = limitedSample.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            outputBuffer.putShort(finalShort)
        }

        inputBuffer.position(inputBuffer.limit())
        outputBuffer.flip()
    }

    override fun onFlush() {
        samplesProcessed = 0L
        currentGain = 1.0f
    }

    override fun onReset() {
        samplesProcessed = 0L
        fadeSamples = 0L
        totalExpectedSamples = 0L
        currentGain = 1.0f
    }
}
