package com.cancellls.jumpcut

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cancellls.jumpcut.engine.AudioAnalysisResult
import com.cancellls.jumpcut.engine.AudioExtractor
import com.cancellls.jumpcut.engine.SilenceDetector
import com.cancellls.jumpcut.engine.SplicerProgress
import com.cancellls.jumpcut.engine.VideoSplicer
import com.cancellls.jumpcut.model.CutSettings
import com.cancellls.jumpcut.model.MediaItem
import com.cancellls.jumpcut.model.ProcessingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Context get() = getApplication()
    private val TAG = "MainViewModel"

    private val _selectedMedia = MutableStateFlow<MediaItem?>(null)
    val selectedMedia: StateFlow<MediaItem?> = _selectedMedia.asStateFlow()

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    private val _cutSettings = MutableStateFlow(CutSettings())
    val cutSettings: StateFlow<CutSettings> = _cutSettings.asStateFlow()

    private val _audioAnalysis = MutableStateFlow<AudioAnalysisResult?>(null)
    val audioAnalysis: StateFlow<AudioAnalysisResult?> = _audioAnalysis.asStateFlow()

    private val _skipSilencePreview = MutableStateFlow(true)
    val skipSilencePreview: StateFlow<Boolean> = _skipSilencePreview.asStateFlow()

    private val _isProUser = MutableStateFlow(false)
    val isProUser: StateFlow<Boolean> = _isProUser.asStateFlow()

    fun selectMedia(uri: Uri) {
        viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.Analyzing(0.05f, "Reading media metadata...")
                val mediaItem = inspectMedia(uri)
                _selectedMedia.value = mediaItem

                _processingState.value = ProcessingState.Analyzing(0.15f, "Extracting audio waveform...")
                val analysis = AudioExtractor.analyzeAudio(context, uri) { prog ->
                    _processingState.value = ProcessingState.Analyzing(prog, "Analyzing voice energy...")
                }
                _audioAnalysis.value = analysis

                _processingState.value = ProcessingState.Analyzing(0.95f, "Detecting silence...")
                applySilenceDetection(analysis, _cutSettings.value)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing media", e)
                _processingState.value = ProcessingState.Error(e.message ?: "Failed to analyze media")
            }
        }
    }

    fun updateSettings(newSettings: CutSettings) {
        _cutSettings.value = newSettings
        val analysis = _audioAnalysis.value ?: return
        applySilenceDetection(analysis, newSettings)
    }

    private fun applySilenceDetection(analysis: AudioAnalysisResult, settings: CutSettings) {
        val result = SilenceDetector.detect(analysis, settings)
        _processingState.value = ProcessingState.Ready(
            originalDurationMs = result.originalDurationMs,
            cutDurationMs = result.cutDurationMs,
            segments = result.segments,
            waveformAmplitudes = analysis.waveformNormalized.toList()
        )
    }

    fun toggleSkipSilencePreview(skip: Boolean) {
        _skipSilencePreview.value = skip
    }

    fun unlockPro() {
        _isProUser.value = true
    }

    fun exportSplicedMedia() {
        val media = _selectedMedia.value ?: return
        val state = _processingState.value as? ProcessingState.Ready ?: return
        val speechSegments = state.segments.filter { !it.isSilence }

        if (speechSegments.isEmpty()) {
            _processingState.value = ProcessingState.Error("No speech detected to export")
            return
        }

        viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.Exporting(0f, "Starting video splicing...")

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val extension = if (media.isVideo) "mp4" else "m4a"
                val outputDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val outputFile = File(outputDir, "JumpCut_$timestamp.$extension")

                VideoSplicer.splice(
                    context = context,
                    inputUri = media.uri,
                    speechSegments = speechSegments,
                    outputFile = outputFile
                ).collect { progress ->
                    when (progress) {
                        is SplicerProgress.Progress -> {
                            val percentText = (progress.percentage * 100).toInt()
                            _processingState.value = ProcessingState.Exporting(
                                progress.percentage,
                                "Splicing video: $percentText%"
                            )
                        }
                        is SplicerProgress.Success -> {
                            _processingState.value = ProcessingState.Exported(
                                outputUri = Uri.fromFile(progress.outputFile),
                                outputPath = progress.outputFile.absolutePath,
                                originalDurationMs = state.originalDurationMs,
                                cutDurationMs = state.cutDurationMs,
                                savedPercent = state.savedPercent
                            )
                        }
                        is SplicerProgress.Error -> {
                            Log.e(TAG, "Export error", progress.throwable)
                            _processingState.value = ProcessingState.Error(
                                progress.throwable.message ?: "Export failed"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Export launch error", e)
                _processingState.value = ProcessingState.Error(e.message ?: "Failed to export video")
            }
        }
    }

    fun reset() {
        _selectedMedia.value = null
        _audioAnalysis.value = null
        _processingState.value = ProcessingState.Idle
    }

    private suspend fun inspectMedia(uri: Uri): MediaItem = withContext(Dispatchers.IO) {
        var name = "Selected Media"
        var size = 0L

        if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            if (file.exists()) {
                name = file.name
                size = file.length()
            }
        } else {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
        }

        val retriever = MediaMetadataRetriever()
        try {
            if (uri.scheme == "file") {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920

            MediaItem(
                uri = uri,
                name = name,
                durationMs = durationMs,
                sizeBytes = size,
                isVideo = hasVideo,
                width = width,
                height = height
            )
        } finally {
            retriever.release()
        }
    }
}
