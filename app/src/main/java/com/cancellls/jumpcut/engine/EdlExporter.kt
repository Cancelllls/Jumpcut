package com.cancellls.jumpcut.engine

import android.content.Context
import com.cancellls.jumpcut.model.CutSegment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EdlExporter {

    /**
     * Generates standard CMX 3600 Edit Decision List (EDL) text.
     * Fully compatible with DaVinci Resolve, Adobe Premiere Pro, and Final Cut Pro.
     */
    fun generateEdl(
        projectName: String,
        sourceClipName: String,
        speechSegments: List<CutSegment>,
        fps: Int = 30
    ): String {
        val sb = StringBuilder()
        val sanitizedTitle = projectName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        sb.append("TITLE: $sanitizedTitle\n")
        sb.append("FCM: NON-DROP FRAME\n\n")

        var recordTimelineMs = 0L

        speechSegments.forEachIndexed { index, seg ->
            val eventNum = String.format(Locale.US, "%03d", index + 1)
            val srcIn = msToTimecode(seg.startMs, fps)
            val srcOut = msToTimecode(seg.endMs, fps)
            val recIn = msToTimecode(recordTimelineMs, fps)
            val segDuration = seg.durationMs
            val recOut = msToTimecode(recordTimelineMs + segDuration, fps)
            recordTimelineMs += segDuration

            // CMX 3600 standard event line for video and audio (AA/V = Audio 1/2 + Video)
            sb.append(String.format(Locale.US, "%s  AX       AA/V  C        %s %s %s %s\n", eventNum, srcIn, srcOut, recIn, recOut))
            sb.append("* FROM CLIP NAME: $sourceClipName\n\n")
        }

        return sb.toString()
    }

    /**
     * Generates Final Cut Pro 7 / Premiere Pro XML format for timeline import.
     */
    fun generateFcpXml(
        projectName: String,
        sourceClipName: String,
        sourceDurationMs: Long,
        speechSegments: List<CutSegment>,
        fps: Int = 30
    ): String {
        val totalCutMs = speechSegments.sumOf { it.durationMs }
        val totalTimelineFrames = (totalCutMs * fps / 1000L).coerceAtLeast(1L)
        val sourceTotalFrames = (sourceDurationMs * fps / 1000L).coerceAtLeast(1L)

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<!DOCTYPE xmeml>\n")
        sb.append("<xmeml version=\"5\">\n")
        sb.append("  <sequence>\n")
        sb.append("    <name>").append(escapeXml(projectName)).append("</name>\n")
        sb.append("    <duration>").append(totalTimelineFrames).append("</duration>\n")
        sb.append("    <rate>\n")
        sb.append("      <timebase>").append(fps).append("</timebase>\n")
        sb.append("      <ntsc>FALSE</ntsc>\n")
        sb.append("    </rate>\n")
        sb.append("    <media>\n")
        sb.append("      <video>\n")
        sb.append("        <format>\n")
        sb.append("          <samplecharacteristics>\n")
        sb.append("            <width>1920</width>\n")
        sb.append("            <height>1080</height>\n")
        sb.append("          </samplecharacteristics>\n")
        sb.append("        </format>\n")
        sb.append("        <track>\n")

        var recordFrame = 0L
        speechSegments.forEachIndexed { idx, seg ->
            val srcInFrame = (seg.startMs * fps / 1000L)
            val srcOutFrame = (seg.endMs * fps / 1000L)
            val clipDurationFrames = (srcOutFrame - srcInFrame).coerceAtLeast(1L)
            val recInFrame = recordFrame
            val recOutFrame = recordFrame + clipDurationFrames
            recordFrame = recOutFrame

            sb.append("          <clipitem id=\"clipitem-").append(idx + 1).append("\">\n")
            sb.append("            <name>").append(escapeXml(sourceClipName)).append("</name>\n")
            sb.append("            <duration>").append(clipDurationFrames).append("</duration>\n")
            sb.append("            <rate><timebase>").append(fps).append("</timebase><ntsc>FALSE</ntsc></rate>\n")
            sb.append("            <start>").append(recInFrame).append("</start>\n")
            sb.append("            <end>").append(recOutFrame).append("</end>\n")
            sb.append("            <in>").append(srcInFrame).append("</in>\n")
            sb.append("            <out>").append(srcOutFrame).append("</out>\n")
            sb.append("            <file id=\"file-1\">\n")
            sb.append("              <name>").append(escapeXml(sourceClipName)).append("</name>\n")
            sb.append("              <pathurl>").append(escapeXml(sourceClipName)).append("</pathurl>\n")
            sb.append("              <rate><timebase>").append(fps).append("</timebase><ntsc>FALSE</ntsc></rate>\n")
            sb.append("              <duration>").append(sourceTotalFrames).append("</duration>\n")
            sb.append("            </file>\n")
            sb.append("          </clipitem>\n")
        }

        sb.append("        </track>\n")
        sb.append("      </video>\n")
        sb.append("    </media>\n")
        sb.append("  </sequence>\n")
        sb.append("</xmeml>\n")

        return sb.toString()
    }

    fun exportEdlToFile(
        context: Context,
        projectName: String,
        sourceClipName: String,
        speechSegments: List<CutSegment>,
        fps: Int = 30
    ): File {
        val edlContent = generateEdl(projectName, sourceClipName, speechSegments, fps)
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val sanitized = projectName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val file = File(exportDir, "${sanitized}_$timeStamp.edl")
        file.writeText(edlContent)
        return file
    }

    fun exportFcpXmlToFile(
        context: Context,
        projectName: String,
        sourceClipName: String,
        sourceDurationMs: Long,
        speechSegments: List<CutSegment>,
        fps: Int = 30
    ): File {
        val xmlContent = generateFcpXml(projectName, sourceClipName, sourceDurationMs, speechSegments, fps)
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val sanitized = projectName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val file = File(exportDir, "${sanitized}_$timeStamp.xml")
        file.writeText(xmlContent)
        return file
    }

    /**
     * Generates standard SubRip (.SRT) timed subtitle format for CapCut, Premiere, and social video tools.
     */
    fun generateSrt(
        speechSegments: List<CutSegment>
    ): String {
        val sb = StringBuilder()
        speechSegments.forEachIndexed { index, seg ->
            val cueNumber = index + 1
            val start = msToSrtTimecode(seg.startMs)
            val end = msToSrtTimecode(seg.endMs)
            sb.append(cueNumber).append("\n")
            sb.append(start).append(" --> ").append(end).append("\n")
            sb.append("[Speech ").append(cueNumber).append("]\n\n")
        }
        return sb.toString()
    }

    /**
     * Generates WebVTT (.VTT) subtitle format for web players and video editors.
     */
    fun generateVtt(
        projectName: String,
        speechSegments: List<CutSegment>
    ): String {
        val sb = StringBuilder()
        val sanitized = projectName.replace("[^a-zA-Z0-9 _-]".toRegex(), " ")
        sb.append("WEBVTT - ").append(sanitized).append("\n\n")
        speechSegments.forEachIndexed { index, seg ->
            val cueNumber = index + 1
            val start = msToVttTimecode(seg.startMs)
            val end = msToVttTimecode(seg.endMs)
            sb.append(cueNumber).append("\n")
            sb.append(start).append(" --> ").append(end).append("\n")
            sb.append("[Speech ").append(cueNumber).append("]\n\n")
        }
        return sb.toString()
    }

    fun exportSrtToFile(
        context: Context,
        projectName: String,
        speechSegments: List<CutSegment>
    ): File {
        val srtContent = generateSrt(speechSegments)
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val sanitized = projectName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val file = File(exportDir, "${sanitized}_$timeStamp.srt")
        file.writeText(srtContent)
        return file
    }

    fun exportVttToFile(
        context: Context,
        projectName: String,
        speechSegments: List<CutSegment>
    ): File {
        val vttContent = generateVtt(projectName, speechSegments)
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val sanitized = projectName.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val file = File(exportDir, "${sanitized}_$timeStamp.vtt")
        file.writeText(vttContent)
        return file
    }

    fun msToSrtTimecode(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val millis = safeMs % 1000
        val totalSeconds = safeMs / 1000
        val seconds = totalSeconds % 60
        val totalMinutes = totalSeconds / 60
        val minutes = totalMinutes % 60
        val hours = totalMinutes / 60
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    fun msToVttTimecode(ms: Long): String {
        val safeMs = ms.coerceAtLeast(0L)
        val millis = safeMs % 1000
        val totalSeconds = safeMs / 1000
        val seconds = totalSeconds % 60
        val totalMinutes = totalSeconds / 60
        val minutes = totalMinutes % 60
        val hours = totalMinutes / 60
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, millis)
    }

    fun msToTimecode(ms: Long, fps: Int): String {
        val safeFps = fps.coerceAtLeast(1)
        val totalFrames = (ms * safeFps) / 1000L
        val frames = (totalFrames % safeFps).toInt()
        val totalSeconds = totalFrames / safeFps
        val seconds = (totalSeconds % safeFps).toInt()
        val totalMinutes = totalSeconds / 60
        val minutes = (totalMinutes % 60).toInt()
        val hours = (totalMinutes / 60).toInt()
        return String.format(Locale.US, "%02d:%02d:%02d:%02d", hours, minutes, seconds, frames)
    }

    fun segmentsToJson(segments: List<CutSegment>): String {
        val array = JSONArray()
        for (seg in segments) {
            val obj = JSONObject()
            obj.put("id", seg.id)
            obj.put("startMs", seg.startMs)
            obj.put("endMs", seg.endMs)
            obj.put("isSilence", seg.isSilence)
            obj.put("isExcluded", seg.isExcluded)
            array.put(obj)
        }
        return array.toString()
    }

    fun jsonToSegments(json: String): List<CutSegment> {
        val list = mutableListOf<CutSegment>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    CutSegment(
                        id = obj.getInt("id"),
                        startMs = obj.getLong("startMs"),
                        endMs = obj.getLong("endMs"),
                        isSilence = obj.getBoolean("isSilence"),
                        isExcluded = obj.optBoolean("isExcluded", false)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
