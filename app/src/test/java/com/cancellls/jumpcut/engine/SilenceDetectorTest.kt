package com.cancellls.jumpcut.engine

import com.cancellls.jumpcut.model.CutSegment
import com.cancellls.jumpcut.model.CutSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceDetectorTest {

    @Test
    fun testCutSegment_durationAndKeeping() {
        val speechSeg = CutSegment(id = 1, startMs = 1000L, endMs = 3500L, isSilence = false)
        assertEquals(2500L, speechSeg.durationMs)
        assertTrue(speechSeg.shouldKeep)

        val excludedSpeech = speechSeg.copy(isExcluded = true)
        assertFalse(excludedSpeech.shouldKeep)

        val silenceSeg = CutSegment(id = 2, startMs = 3500L, endMs = 5000L, isSilence = true)
        assertEquals(1500L, silenceSeg.durationMs)
        assertFalse(silenceSeg.shouldKeep)

        // If user marks silence as excluded from cut, it should be kept
        val keptSilence = silenceSeg.copy(isExcluded = true)
        assertTrue(keptSilence.shouldKeep)
    }

    @Test
    fun testCutSettings_defaults() {
        val settings = CutSettings()
        assertTrue(settings.silenceThresholdDb in -45f..-20f)
        assertTrue(settings.minSilenceDurationMs >= 200L)
        assertTrue(settings.studioAudioLeveling)
        assertTrue(settings.roomToneSmoothing)
    }
}
