package com.cancellls.jumpcut.engine

import com.cancellls.jumpcut.model.CutSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdlExporterTest {

    @Test
    fun testMsToSrtTimecode() {
        val ms = (1 * 3600 + 2 * 60 + 3) * 1000L + 456L
        val result = EdlExporter.msToSrtTimecode(ms)
        assertEquals("01:02:03,456", result)
    }

    @Test
    fun testMsToVttTimecode() {
        val ms = (1 * 3600 + 2 * 60 + 3) * 1000L + 456L
        val result = EdlExporter.msToVttTimecode(ms)
        assertEquals("01:02:03.456", result)
    }

    @Test
    fun testMsToTimecode() {
        val result = EdlExporter.msToTimecode(1000L, 30)
        assertEquals("00:00:01:00", result)
    }

    @Test
    fun testGenerateSrt() {
        val segments = listOf(
            CutSegment(id = 1, startMs = 1000L, endMs = 3500L, isSilence = false),
            CutSegment(id = 2, startMs = 4000L, endMs = 8000L, isSilence = false)
        )

        val srt = EdlExporter.generateSrt(segments)
        assertTrue(srt.contains("1\n00:00:01,000 --> 00:00:03,500\n[Speech 1]"))
        assertTrue(srt.contains("2\n00:00:04,000 --> 00:00:08,000\n[Speech 2]"))
    }

    @Test
    fun testGenerateVtt() {
        val segments = listOf(
            CutSegment(id = 1, startMs = 1000L, endMs = 3500L, isSilence = false)
        )

        val vtt = EdlExporter.generateVtt("Test Clip", segments)
        assertTrue(vtt.startsWith("WEBVTT - Test Clip"))
        assertTrue(vtt.contains("1\n00:00:01.000 --> 00:00:03.500\n[Speech 1]"))
    }

    @Test
    fun testGenerateEdl() {
        val segments = listOf(
            CutSegment(id = 1, startMs = 0L, endMs = 2000L, isSilence = false)
        )

        val edl = EdlExporter.generateEdl("MyProject", "source.mp4", segments, fps = 30)
        assertTrue(edl.contains("TITLE: MyProject"))
        assertTrue(edl.contains("FCM: NON-DROP FRAME"))
        assertTrue(edl.contains("001  AX       AA/V  C"))
        assertTrue(edl.contains("* FROM CLIP NAME: source.mp4"))
    }

    @Test
    fun testGenerateFcpXml() {
        val segments = listOf(
            CutSegment(id = 1, startMs = 0L, endMs = 2000L, isSilence = false)
        )

        val xml = EdlExporter.generateFcpXml("MyProject", "source.mp4", 5000L, segments, fps = 30)
        assertTrue(xml.contains("<xmeml version=\"5\">"))
        assertTrue(xml.contains("<sequence>"))
        assertTrue(xml.contains("<name>MyProject</name>"))
        assertTrue(xml.contains("<clipitem id=\"clipitem-1\">"))
    }
}
