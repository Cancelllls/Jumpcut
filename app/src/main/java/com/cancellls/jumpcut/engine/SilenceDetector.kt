package com.cancellls.jumpcut.engine

import com.cancellls.jumpcut.model.CutSegment
import com.cancellls.jumpcut.model.CutSettings
import kotlin.math.max
import kotlin.math.min

data class SilenceDetectionResult(
    val segments: List<CutSegment>,
    val speechSegments: List<CutSegment>,
    val originalDurationMs: Long,
    val cutDurationMs: Long
) {
    val savedMs: Long get() = (originalDurationMs - cutDurationMs).coerceAtLeast(0)
    val savedPercent: Int get() = if (originalDurationMs > 0) {
        ((savedMs.toFloat() / originalDurationMs) * 100).toInt()
    } else 0
}

object SilenceDetector {

    fun detect(
        analysis: AudioAnalysisResult,
        settings: CutSettings
    ): SilenceDetectionResult {
        val timePoints = analysis.timePointsMs
        val dbs = analysis.decibels
        val voiceDbs = analysis.voiceBandDecibels
        val voiceConf = analysis.voiceConfidence
        val count = min(timePoints.size, dbs.size)

        if (count == 0) {
            val emptyList = listOf(CutSegment(0, 0, analysis.durationMs, false))
            return SilenceDetectionResult(emptyList, emptyList, analysis.durationMs, analysis.durationMs)
        }

        val hasRichVoiceFeatures = voiceConf.isNotEmpty() && voiceDbs.isNotEmpty()

        // Effective ambient noise floor:
        // When autoNoiseFloor is enabled, dynamically adapt to the recording's true room noise floor
        val noiseFloor = if (settings.autoNoiseFloor && analysis.estimatedNoiseFloorDb > -65f) {
            analysis.estimatedNoiseFloorDb
        } else {
            settings.silenceThresholdDb
        }

        // Noise rejection factor (0.10 to 0.95, default 0.65)
        // High rejection -> strictly requires high vocal confidence and clear SNR above ambient noise
        // Low rejection -> permissive, accepts softer speech
        val rejection = settings.voiceNoiseRejection.coerceIn(0.10f, 0.95f)
        val onsetSnrDb = 2.0f + (rejection * 6.5f) // 2.5dB to 8.2dB above noise floor
        val exitSnrDb = (onsetSnrDb - 2.8f).coerceAtLeast(0.8f) // Hysteresis floor for trailing syllables

        val onsetConfidence = 0.20f + (rejection * 0.40f) // 0.24 to 0.58
        val exitConfidence = (onsetConfidence - 0.12f).coerceAtLeast(0.18f)

        // 1. Dual-Threshold Schmitt-Trigger Hysteresis (Voice vs Background Noise)
        var currentlySpeaking = false
        val isSpeechArray = BooleanArray(count) { i ->
            val rawDb = dbs[i]
            if (rawDb < (settings.silenceThresholdDb - 10f)) {
                // Hard floor: digital silence
                currentlySpeaking = false
                false
            } else if (!hasRichVoiceFeatures) {
                val thresh = if (currentlySpeaking) settings.silenceThresholdDb - 3.0f else settings.silenceThresholdDb
                val isSpeech = rawDb >= thresh
                currentlySpeaking = isSpeech
                isSpeech
            } else {
                val vDb = if (i < voiceDbs.size) voiceDbs[i] else rawDb
                val conf = if (i < voiceConf.size) voiceConf[i] else 0.5f

                val activeSnr = if (currentlySpeaking) exitSnrDb else onsetSnrDb
                val activeConf = if (currentlySpeaking) exitConfidence else onsetConfidence

                val isAboveFloor = if (settings.autoNoiseFloor) {
                    (vDb >= (noiseFloor + activeSnr)) || (rawDb >= (settings.silenceThresholdDb + (if (currentlySpeaking) 3f else 6f)))
                } else {
                    rawDb >= (settings.silenceThresholdDb - (if (currentlySpeaking) 3.5f else 0f))
                }

                val hasVoiceAcoustics = conf >= activeConf
                val isSpeech = isAboveFloor && hasVoiceAcoustics
                currentlySpeaking = isSpeech
                isSpeech
            }
        }

        // 2. Preset-Adaptive Consonant Lookahead & Hangover Smoothing
        // Lookahead: dynamically scales with padding to preserve unvoiced onset consonants ("s", "t", "p", "f", "k")
        // Hangover: dynamically scales with rhythm pacing (shorter for snappy shorts, longer for relaxed podcasts)
        val lookaheadFrames = (settings.paddingMs / 25L).toInt().coerceIn(3, 6)
        val hangoverFrames = when {
            settings.minSilenceDurationMs <= 250L -> 3 // 90ms for punchy shorts
            settings.minSilenceDurationMs >= 500L -> 7 // 210ms for relaxed podcast cadence
            else -> 5 // 150ms default
        }

        val smoothedSpeech = BooleanArray(count)
        for (i in 0 until count) {
            if (isSpeechArray[i]) {
                val lookaheadStart = max(0, i - lookaheadFrames)
                for (k in lookaheadStart..i) {
                    if (dbs[k] > -55f) smoothedSpeech[k] = true
                }
                val hangoverEnd = min(count - 1, i + hangoverFrames)
                for (k in i..hangoverEnd) {
                    if (dbs[k] > -55f) smoothedSpeech[k] = true
                }
            }
        }

        val isSilentArray = BooleanArray(count) { i -> !smoothedSpeech[i] }

        // 2. Identify contiguous candidate silence intervals
        val candidateSilences = mutableListOf<Pair<Long, Long>>()
        var inSilence = false
        var silenceStartMs = 0L

        for (i in 0 until count) {
            val t = timePoints[i]
            val isSilent = isSilentArray[i]

            if (isSilent && !inSilence) {
                inSilence = true
                silenceStartMs = t
            } else if (!isSilent && inSilence) {
                inSilence = false
                val silenceDuration = t - silenceStartMs
                if (silenceDuration >= settings.minSilenceDurationMs) {
                    candidateSilences.add(Pair(silenceStartMs, t))
                }
            }
        }

        if (inSilence) {
            val endT = timePoints.lastOrNull() ?: analysis.durationMs
            if (endT - silenceStartMs >= settings.minSilenceDurationMs) {
                candidateSilences.add(Pair(silenceStartMs, endT))
            }
        }

        // 3. Apply padding around speech (shrink silence interval by paddingMs on each end)
        val pad = settings.paddingMs
        val rawAdjustedSilences = mutableListOf<Pair<Long, Long>>()

        for ((start, end) in candidateSilences) {
            val paddedStart = start + pad
            val paddedEnd = end - pad
            // Only keep if the silence is at least 120ms after padding
            if (paddedEnd - paddedStart >= 120L) {
                rawAdjustedSilences.add(Pair(paddedStart, paddedEnd))
            }
        }

        // 4. Merge silences separated by micro-sounds (<150ms) to avoid microscopic clips
        val sortedSilences = rawAdjustedSilences.sortedBy { it.first }
        val mergedSilences = mutableListOf<Pair<Long, Long>>()
        for (silence in sortedSilences) {
            if (mergedSilences.isEmpty()) {
                mergedSilences.add(silence)
            } else {
                val last = mergedSilences.last()
                if (silence.first <= last.second + 150L) {
                    // Merge adjacent silences
                    mergedSilences[mergedSilences.size - 1] = Pair(last.first, max(last.second, silence.second))
                } else {
                    mergedSilences.add(silence)
                }
            }
        }

        // 5. Construct complete interleaved timeline of speech and silence
        val allSegments = mutableListOf<CutSegment>()
        val speechSegments = mutableListOf<CutSegment>()
        var cursorMs = 0L
        var segId = 0

        for ((silenceStart, silenceEnd) in mergedSilences) {
            if (silenceStart > cursorMs) {
                val speechDur = silenceStart - cursorMs
                if (speechDur >= 100L) {
                    val speech = CutSegment(
                        id = segId++,
                        startMs = cursorMs,
                        endMs = silenceStart,
                        isSilence = false
                    )
                    allSegments.add(speech)
                    speechSegments.add(speech)
                }
            }

            val silence = CutSegment(
                id = segId++,
                startMs = silenceStart,
                endMs = silenceEnd,
                isSilence = true
            )
            allSegments.add(silence)
            cursorMs = silenceEnd
        }

        val totalDuration = max(analysis.durationMs, timePoints.lastOrNull() ?: 0L)
        if (totalDuration - cursorMs >= 100L) {
            val finalSpeech = CutSegment(
                id = segId++,
                startMs = cursorMs,
                endMs = totalDuration,
                isSilence = false
            )
            allSegments.add(finalSpeech)
            speechSegments.add(finalSpeech)
        }

        // If no speech segments remained, retain whole video
        if (speechSegments.isEmpty()) {
            val whole = CutSegment(0, 0, totalDuration, false)
            allSegments.clear()
            allSegments.add(whole)
            speechSegments.add(whole)
        }

        val cutDuration = speechSegments.sumOf { it.durationMs }

        return SilenceDetectionResult(
            segments = allSegments,
            speechSegments = speechSegments,
            originalDurationMs = totalDuration,
            cutDurationMs = cutDuration
        )
    }
}
