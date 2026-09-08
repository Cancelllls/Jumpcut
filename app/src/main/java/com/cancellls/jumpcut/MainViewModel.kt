package com.cancellls.jumpcut

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cancellls.jumpcut.billing.UsageQuotaManager
import com.cancellls.jumpcut.engine.AudioAnalysisResult
import com.cancellls.jumpcut.engine.AudioExtractor
import com.cancellls.jumpcut.engine.EdlExporter
import com.cancellls.jumpcut.engine.SilenceDetector
import com.cancellls.jumpcut.engine.SplicerProgress
import com.cancellls.jumpcut.engine.VideoSplicer
import com.cancellls.jumpcut.model.CreatorPreset
import com.cancellls.jumpcut.model.CutSegment
import com.cancellls.jumpcut.model.CutSettings
import com.cancellls.jumpcut.model.ExportConfig
import com.cancellls.jumpcut.model.MediaItem
import com.cancellls.jumpcut.model.ProcessingState
import com.cancellls.jumpcut.model.SavedProject
import com.cancellls.jumpcut.storage.MediaSaver
import com.cancellls.jumpcut.storage.PresetRepository
import com.cancellls.jumpcut.storage.ProjectRepository
import com.cancellls.jumpcut.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private var lastReadyState: ProcessingState.Ready? = null
    private var analysisJob: Job? = null
    private var exportJob: Job? = null

    private val projectRepository = ProjectRepository(context)
    val savedProjects: StateFlow<List<SavedProject>> = projectRepository.projects

    private val presetRepository = PresetRepository(context)
    val creatorPresets: StateFlow<List<CreatorPreset>> = presetRepository.presets

    private val _selectedMedia = MutableStateFlow<MediaItem?>(null)
    val selectedMedia: StateFlow<MediaItem?> = _selectedMedia.asStateFlow()

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    private val _cutSettings = MutableStateFlow(CutSettings())
    val cutSettings: StateFlow<CutSettings> = _cutSettings.asStateFlow()

    private val _exportConfig = MutableStateFlow(ExportConfig())
    val exportConfig: StateFlow<ExportConfig> = _exportConfig.asStateFlow()

    private val _audioAnalysis = MutableStateFlow<AudioAnalysisResult?>(null)
    val audioAnalysis: StateFlow<AudioAnalysisResult?> = _audioAnalysis.asStateFlow()

    private val _skipSilencePreview = MutableStateFlow(true)
    val skipSilencePreview: StateFlow<Boolean> = _skipSilencePreview.asStateFlow()

    private val _isProUser = MutableStateFlow(false)
    val isProUser: StateFlow<Boolean> = _isProUser.asStateFlow()

    val remainingExports: StateFlow<Int> = UsageQuotaManager.remainingExports

    private val _cacheSize = MutableStateFlow("0 MB")
    val cacheSize: StateFlow<String> = _cacheSize.asStateFlow()

    init {
        refreshCacheSize()
        UsageQuotaManager.refreshQuota(context, _isProUser.value)
        UsageQuotaManager.syncOnlineTimeAsync(context, _isProUser.value)
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            val bytes = StorageManager.getCacheSizeBytes(context)
            _cacheSize.value = StorageManager.formatBytes(bytes)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            StorageManager.clearTempCache(context)
            refreshCacheSize()
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            projectRepository.deleteProject(projectId)
            refreshCacheSize()
        }
    }

    fun downloadFromUrl(rawUrl: String) {
        val urlString = rawUrl.trim()
        if (!urlString.startsWith("http://", ignoreCase = true) && !urlString.startsWith("https://", ignoreCase = true)) {
            _processingState.value = ProcessingState.Error("Invalid URL. Must start with http:// or https://")
            return
        }

        val isSocialWebLink = urlString.contains("youtube.com", true) ||
                              urlString.contains("youtu.be", true) ||
                              urlString.contains("tiktok.com", true) ||
                              urlString.contains("instagram.com", true) ||
                              urlString.contains("twitter.com", true) ||
                              urlString.contains("x.com", true)

        if (isSocialWebLink) {
            _processingState.value = ProcessingState.Error(
                "Social platforms (YouTube, TikTok, Reels) protect their streams from direct web downloads. To cut this video: Download it via the free Seal app (powered by yt-dlp) and tap 'Share to JumpCut', or open it via 'Browse Files'."
            )
            return
        }

        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.Analyzing(0.02f, "Connecting to video URL...")
                val downloadedFile = withContext(Dispatchers.IO) {
                    val url = java.net.URL(urlString)
                    val connection = (url.openConnection() as java.net.HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 15000
                        readTimeout = 30000
                        instanceFollowRedirects = true
                    }
                    connection.connect()

                    val responseCode = connection.responseCode
                    if (responseCode !in 200..299) {
                        throw IllegalStateException("Server returned HTTP $responseCode")
                    }

                    val contentType = connection.contentType ?: ""
                    if (contentType.contains("text/html", true)) {
                        throw IllegalStateException("The URL returned a webpage instead of a direct video or audio stream. Please provide a direct video link (.mp4, .mov, .m4a) or cloud link.")
                    }

                    val totalBytes = connection.contentLengthLong
                    val downloadDir = File(context.cacheDir, "downloads").apply { mkdirs() }
                    val extension = if (urlString.contains(".mp3", true) || urlString.contains(".m4a", true) || urlString.contains(".wav", true)) "m4a" else "mp4"
                    val targetFile = File(downloadDir, "jumpcut_dl_${System.currentTimeMillis()}.$extension")

                    connection.inputStream.use { input ->
                        targetFile.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var read: Int
                            var downloaded = 0L

                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (totalBytes > 0) {
                                    val progress = (0.02f + (downloaded.toFloat() / totalBytes) * 0.40f).coerceIn(0.02f, 0.42f)
                                    val mbDownloaded = String.format(Locale.US, "%.1f", downloaded / (1024.0 * 1024.0))
                                    val mbTotal = String.format(Locale.US, "%.1f", totalBytes / (1024.0 * 1024.0))
                                    _processingState.value = ProcessingState.Analyzing(
                                        progress,
                                        "Downloading: ${mbDownloaded}MB / ${mbTotal}MB"
                                    )
                                } else {
                                    val mbDownloaded = String.format(Locale.US, "%.1f", downloaded / (1024.0 * 1024.0))
                                    _processingState.value = ProcessingState.Analyzing(
                                        0.20f,
                                        "Downloading: ${mbDownloaded}MB"
                                    )
                                }
                            }
                        }
                    }
                    targetFile
                }

                selectMedia(Uri.fromFile(downloadedFile))
            } catch (e: Exception) {
                Log.e(TAG, "Download error", e)
                _processingState.value = ProcessingState.Error(e.message ?: "Failed to download video")
            }
        }
    }

    fun selectMedia(uri: Uri) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.Analyzing(0.05f, "Preparing media file...")
                val safeUri = withContext(Dispatchers.IO) {
                    prepareLocalMediaUri(uri)
                }

                _processingState.value = ProcessingState.Analyzing(0.12f, "Reading media metadata...")
                val mediaItem = inspectMedia(safeUri)
                _selectedMedia.value = mediaItem

                _processingState.value = ProcessingState.Analyzing(0.20f, "Extracting audio waveform...")
                val analysis = AudioExtractor.analyzeAudio(context, safeUri) { prog ->
                    _processingState.value = ProcessingState.Analyzing(prog, "Analyzing voice energy...")
                }
                _audioAnalysis.value = analysis

                _processingState.value = ProcessingState.Analyzing(0.95f, "Detecting silence...")
                applySilenceDetection(analysis, _cutSettings.value)
                refreshCacheSize()
            } catch (e: Exception) {
                Log.e(TAG, "Error processing media", e)
                _processingState.value = ProcessingState.Error(e.message ?: "Failed to analyze media")
            }
        }
    }

    private fun prepareLocalMediaUri(uri: Uri): Uri {
        // If it's already an app-private file that exists and can be read, reuse it directly
        if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            val isAppPrivate = file.absolutePath.startsWith(context.filesDir.path) ||
                               file.absolutePath.startsWith(context.cacheDir.path)
            if (isAppPrivate && file.exists() && file.canRead() && file.length() > 0) {
                return uri
            }
        }

        try {
            val cacheDir = File(context.cacheDir, "input_cache").apply { mkdirs() }
            var extension = "mp4"
            var fileName = "input_${System.currentTimeMillis()}"

            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val displayName = cursor.getString(nameIndex)
                            if (!displayName.isNullOrBlank()) {
                                fileName = displayName.substringBeforeLast(".")
                                val ext = displayName.substringAfterLast(".", "")
                                if (ext.isNotBlank()) extension = ext
                            }
                        }
                    }
                }
            } else if (uri.scheme == "file") {
                val f = File(uri.path ?: "")
                if (f.name.isNotBlank()) {
                    fileName = f.name.substringBeforeLast(".")
                    val ext = f.name.substringAfterLast(".", "")
                    if (ext.isNotBlank()) extension = ext
                }
            }

            val safeFileName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_").take(50)
            val cachedFile = File(cacheDir, "${safeFileName}_${System.currentTimeMillis()}.$extension")

            val inputStream = if (uri.scheme == "content") {
                context.contentResolver.openInputStream(uri)
            } else {
                try {
                    val f = File(uri.path ?: "")
                    if (f.exists() && f.canRead()) f.inputStream() else context.contentResolver.openInputStream(uri)
                } catch (e: Exception) {
                    context.contentResolver.openInputStream(uri)
                }
            }

            inputStream?.use { input ->
                cachedFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (cachedFile.exists() && cachedFile.length() > 0) {
                Log.d(TAG, "Cached media URI successfully to local file: ${cachedFile.absolutePath} (${cachedFile.length()} bytes)")
                return Uri.fromFile(cachedFile)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache media URI locally: ${e.message}", e)
        }
        return uri
    }

    fun updateSettings(newSettings: CutSettings) {
        _cutSettings.value = newSettings
        val analysis = _audioAnalysis.value ?: return
        applySilenceDetection(analysis, newSettings)
    }

    fun toggleSegment(segmentId: Int) {
        val ready = _processingState.value as? ProcessingState.Ready ?: return
        val updated = ready.segments.map { seg ->
            if (seg.id == segmentId) seg.copy(isExcluded = !seg.isExcluded) else seg
        }
        val newCutDuration = updated.filter { it.shouldKeep }.sumOf { it.durationMs }
        val updatedReady = ready.copy(
            segments = updated,
            cutDurationMs = newCutDuration
        )
        lastReadyState = updatedReady
        _processingState.value = updatedReady
    }

    fun updateExportConfig(config: ExportConfig) {
        _exportConfig.value = config
    }

    private fun applySilenceDetection(analysis: AudioAnalysisResult, settings: CutSettings) {
        val result = SilenceDetector.detect(analysis, settings)
        val ready = ProcessingState.Ready(
            originalDurationMs = result.originalDurationMs,
            cutDurationMs = result.cutDurationMs,
            segments = result.segments,
            waveformAmplitudes = analysis.waveformNormalized.toList()
        )
        lastReadyState = ready
        _processingState.value = ready
    }

    fun toggleSkipSilencePreview(skip: Boolean) {
        _skipSilencePreview.value = skip
    }

    fun unlockPro() {
        _isProUser.value = true
        UsageQuotaManager.refreshQuota(context, true)
    }

    fun revokePro() {
        _isProUser.value = false
        UsageQuotaManager.refreshQuota(context, false)
    }

    fun exportSplicedMedia(config: ExportConfig = _exportConfig.value) {
        val media = _selectedMedia.value ?: return
        val state = _processingState.value as? ProcessingState.Ready ?: return
        lastReadyState = state
        val keptSegments = state.segments.filter { it.shouldKeep }

        if (keptSegments.isEmpty()) {
            _processingState.value = ProcessingState.Error("No content selected to export")
            return
        }

        // Check Free Tier Daily Quota (Limit: 2 exports per calendar day)
        if (!UsageQuotaManager.canExport(context, _isProUser.value)) {
            _processingState.value = ProcessingState.Error(
                "Daily Free Export Limit Reached (2/2 exports used today). Upgrade to JumpCut Pro for unlimited 4K 60FPS exports!"
            )
            return
        }

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            try {
                _processingState.value = ProcessingState.Exporting(0f, "Starting video splicing...")

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val isAudioOnly = config.extractAudioOnly || !media.isVideo
                val extension = if (isAudioOnly) "m4a" else "mp4"
                val outputDir = File(context.filesDir, "exports").apply { mkdirs() }
                val outputFile = File(outputDir, "JumpCut_${media.name.substringBeforeLast(".")}_$timestamp.$extension")

                VideoSplicer.splice(
                    context = context,
                    inputUri = media.uri,
                    speechSegments = keptSegments,
                    outputFile = outputFile,
                    totalDurationMs = media.durationMs,
                    extractAudioOnly = isAudioOnly,
                    autoZoomJumpcuts = config.autoZoomJumpcuts,
                    microCrossfade = config.microCrossfade
                ).collect { progress ->
                    when (progress) {
                        is SplicerProgress.Progress -> {
                            val percentText = (progress.percentage * 100).toInt()
                            _processingState.value = ProcessingState.Exporting(
                                progress.percentage,
                                "Splicing media: $percentText%"
                            )
                        }
                        is SplicerProgress.Success -> {
                            val isVideoOutput = !isAudioOnly

                            // Record daily quota consumption for free users
                            UsageQuotaManager.recordExport(context, _isProUser.value)

                            if (config.saveToGallery) {
                                MediaSaver.saveToGallery(context, progress.outputFile, isVideoOutput)
                            }

                            // Save to Project History with segments for desktop EDL export
                            val projectId = "proj_${System.currentTimeMillis()}"
                            val title = "JumpCut_${media.name.substringBeforeLast(".")}"
                            val segmentsJson = EdlExporter.segmentsToJson(keptSegments)

                            projectRepository.saveProject(
                                id = projectId,
                                title = title,
                                originalDurationMs = state.originalDurationMs,
                                cutDurationMs = state.cutDurationMs,
                                savedPercent = state.savedPercent,
                                file = progress.outputFile,
                                isVideo = isVideoOutput,
                                segmentsJson = segmentsJson
                            )

                            refreshCacheSize()

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

    fun saveCustomPreset(name: String, settings: CutSettings): CreatorPreset {
        return presetRepository.saveCustomPreset(name, settings)
    }

    fun deleteCustomPreset(id: String) {
        presetRepository.deletePreset(id)
    }

    fun exportCurrentEdl(context: Context, asXml: Boolean = false): File? {
        val media = _selectedMedia.value ?: return null
        val state = _processingState.value as? ProcessingState.Ready ?: return null
        val keptSegments = state.segments.filter { it.shouldKeep }
        if (keptSegments.isEmpty()) return null

        val projectName = media.name.substringBeforeLast(".")
        val file = if (asXml) {
            EdlExporter.exportFcpXmlToFile(
                context = context,
                projectName = projectName,
                sourceClipName = media.name,
                sourceDurationMs = state.originalDurationMs,
                speechSegments = keptSegments
            )
        } else {
            EdlExporter.exportEdlToFile(
                context = context,
                projectName = projectName,
                sourceClipName = media.name,
                speechSegments = keptSegments
            )
        }
        MediaSaver.shareDocument(
            context = context,
            file = file,
            mimeType = if (asXml) "application/xml" else "text/plain",
            title = "Share Timeline (${if (asXml) ".XML" else ".EDL"})"
        )
        return file
    }

    fun exportProjectEdl(context: Context, project: SavedProject, asXml: Boolean = false): File? {
        val segments = project.segmentsJson?.let { EdlExporter.jsonToSegments(it) } ?: emptyList()
        if (segments.isEmpty()) return null

        val file = if (asXml) {
            EdlExporter.exportFcpXmlToFile(
                context = context,
                projectName = project.title,
                sourceClipName = File(project.filePath).name,
                sourceDurationMs = project.originalDurationMs,
                speechSegments = segments
            )
        } else {
            EdlExporter.exportEdlToFile(
                context = context,
                projectName = project.title,
                sourceClipName = File(project.filePath).name,
                speechSegments = segments
            )
        }
        MediaSaver.shareDocument(
            context = context,
            file = file,
            mimeType = if (asXml) "application/xml" else "text/plain",
            title = "Share Timeline (${if (asXml) ".XML" else ".EDL"})"
        )
        return file
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
        reset()
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        val ready = lastReadyState
        if (ready != null && _selectedMedia.value != null) {
            _processingState.value = ready
        } else {
            reset()
        }
    }

    fun returnToEditor() {
        exportJob?.cancel()
        exportJob = null
        val ready = lastReadyState
        if (ready != null && _selectedMedia.value != null) {
            _processingState.value = ready
        } else {
            reset()
        }
    }

    fun reset() {
        analysisJob?.cancel()
        analysisJob = null
        exportJob?.cancel()
        exportJob = null
        lastReadyState = null
        _selectedMedia.value = null
        _audioAnalysis.value = null
        _processingState.value = ProcessingState.Idle
        refreshCacheSize()
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
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}
