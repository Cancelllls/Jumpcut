package com.cancellls.jumpcut.engine

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.sin

/**
 * Applies a smooth 15ms half-cosine micro-crossfade to speech cut seams.
 * Eliminates digital clicks, pops, and room tone hiss dropouts at boundary points.
 */
@OptIn(UnstableApi::class)
class MicroCrossfadeAudioProcessor(
    private val fadeDurationMs: Long = 15L
) : BaseAudioProcessor() {

    private var samplesProcessed = 0L
    private var fadeSamples = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        fadeSamples = (inputAudioFormat.sampleRate * fadeDurationMs / 1000L) * inputAudioFormat.channelCount
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        val shortBuffer = inputBuffer.asShortBuffer()
        val totalShorts = remaining / 2

        for (i in 0 until totalShorts) {
            val sample = shortBuffer.get()
            val gain = if (fadeSamples > 0 && samplesProcessed < fadeSamples) {
                // Smooth half-cosine fade-in
                val progress = samplesProcessed.toFloat() / fadeSamples.toFloat()
                sin(progress * (PI.toFloat() / 2f))
            } else {
                1.0f
            }
            samplesProcessed++
            val fadedSample = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            outputBuffer.putShort(fadedSample)
        }

        inputBuffer.position(inputBuffer.limit())
    }

    override fun onFlush() {
        samplesProcessed = 0L
    }

    override fun onReset() {
        samplesProcessed = 0L
    }
}
