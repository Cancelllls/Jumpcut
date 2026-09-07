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
        val count = min(timePoints.size, dbs.size)

        if (count == 0) {
            val emptyList = listOf(CutSegment(0, 0, analysis.durationMs, false))
            return SilenceDetectionResult(emptyList, emptyList, analysis.durationMs, analysis.durationMs)
        }

        // 1. Mark each window as silent or sound
        val isSilentArray = BooleanArray(count) { i ->
            dbs[i] < settings.silenceThresholdDb
        }

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
