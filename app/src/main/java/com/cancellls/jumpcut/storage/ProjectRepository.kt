package com.cancellls.jumpcut.storage

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import com.cancellls.jumpcut.model.SavedProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class ProjectRepository(private val context: Context) {
    private val TAG = "ProjectRepository"
    private val projectsFile = File(context.filesDir, "jumpcut_projects.json")
    private val thumbnailsDir = File(context.filesDir, "thumbnails").apply { mkdirs() }

    private val _projects = MutableStateFlow<List<SavedProject>>(emptyList())
    val projects: StateFlow<List<SavedProject>> = _projects.asStateFlow()

    init {
        loadProjects()
    }

    fun loadProjects() {
        try {
            if (!projectsFile.exists()) {
                _projects.value = emptyList()
                return
            }

            val jsonStr = projectsFile.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<SavedProject>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val project = SavedProject(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    originalDurationMs = obj.getLong("originalDurationMs"),
                    cutDurationMs = obj.getLong("cutDurationMs"),
                    savedPercent = obj.getInt("savedPercent"),
                    filePath = obj.getString("filePath"),
                    fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                    isVideo = obj.optBoolean("isVideo", true),
                    createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis()),
                    thumbnailPath = obj.optString("thumbnailPath").takeIf { it.isNotBlank() },
                    segmentsJson = obj.optString("segmentsJson").takeIf { it.isNotBlank() }
                )
                // Only keep if the exported file actually exists on disk
                if (File(project.filePath).exists()) {
                    list.add(project)
                }
            }

            // Sort by most recent first
            _projects.value = list.sortedByDescending { it.createdAtMs }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading projects", e)
            _projects.value = emptyList()
        }
    }

    suspend fun saveProject(
        id: String,
        title: String,
        originalDurationMs: Long,
        cutDurationMs: Long,
        savedPercent: Int,
        file: File,
        isVideo: Boolean,
        segmentsJson: String? = null
    ): SavedProject = withContext(Dispatchers.IO) {
        var thumbnailPath: String? = null
        if (isVideo && file.exists()) {
            thumbnailPath = generateThumbnail(file, id)
        }

        val newProject = SavedProject(
            id = id,
            title = title,
            originalDurationMs = originalDurationMs,
            cutDurationMs = cutDurationMs,
            savedPercent = savedPercent,
            filePath = file.absolutePath,
            fileSizeBytes = file.length(),
            isVideo = isVideo,
            createdAtMs = System.currentTimeMillis(),
            thumbnailPath = thumbnailPath,
            segmentsJson = segmentsJson
        )

        val currentList = _projects.value.toMutableList()
        currentList.removeAll { it.id == id }
        currentList.add(0, newProject)
        _projects.value = currentList

        persistProjects(currentList)
        newProject
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        val currentList = _projects.value.toMutableList()
        val toRemove = currentList.firstOrNull { it.id == projectId }

        if (toRemove != null) {
            currentList.remove(toRemove)
            _projects.value = currentList

            // Delete exported media file
            try {
                val file = File(toRemove.filePath)
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete media file", e)
            }

            // Delete thumbnail file
            try {
                toRemove.thumbnailPath?.let { path ->
                    val thumbFile = File(path)
                    if (thumbFile.exists()) thumbFile.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete thumbnail file", e)
            }

            persistProjects(currentList)
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val currentList = _projects.value
        for (project in currentList) {
            try {
                File(project.filePath).delete()
                project.thumbnailPath?.let { File(it).delete() }
            } catch (e: Exception) {
                Log.w(TAG, "Failed deleting project file", e)
            }
        }
        _projects.value = emptyList()
        projectsFile.delete()
    }

    private fun persistProjects(projects: List<SavedProject>) {
        try {
            val array = JSONArray()
            for (p in projects) {
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("title", p.title)
                    put("originalDurationMs", p.originalDurationMs)
                    put("cutDurationMs", p.cutDurationMs)
                    put("savedPercent", p.savedPercent)
                    put("filePath", p.filePath)
                    put("fileSizeBytes", p.fileSizeBytes)
                    put("isVideo", p.isVideo)
                    put("createdAtMs", p.createdAtMs)
                    put("thumbnailPath", p.thumbnailPath ?: "")
                    put("segmentsJson", p.segmentsJson ?: "")
                }
                array.put(obj)
            }
            projectsFile.writeText(array.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting projects", e)
        }
    }

    private fun generateThumbnail(videoFile: File, projectId: String): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val bitmap = retriever.getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) {
                val thumbFile = File(thumbnailsDir, "thumb_$projectId.jpg")
                FileOutputStream(thumbFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                thumbFile.absolutePath
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to generate thumbnail for ${videoFile.name}", e)
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }
}
