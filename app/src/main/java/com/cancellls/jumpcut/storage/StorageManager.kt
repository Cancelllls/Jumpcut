package com.cancellls.jumpcut.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object StorageManager {

    suspend fun getCacheSizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
        calculateDirSize(context.cacheDir)
    }

    suspend fun clearTempCache(context: Context): Long = withContext(Dispatchers.IO) {
        val before = calculateDirSize(context.cacheDir)
        // Clear input_cache and downloads dirs
        val inputCache = File(context.cacheDir, "input_cache")
        val downloads = File(context.cacheDir, "downloads")
        deleteRecursive(inputCache)
        deleteRecursive(downloads)

        // Clear loose temp files in cacheDir that aren't exports
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.endsWith(".pcm") || file.name.endsWith(".wav") || file.name.startsWith("jumpcut_"))) {
                file.delete()
            }
        }

        val after = calculateDirSize(context.cacheDir)
        (before - after).coerceAtLeast(0L)
    }

    private fun calculateDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }

    private fun deleteRecursive(fileOrDir: File) {
        if (fileOrDir.isDirectory) {
            fileOrDir.listFiles()?.forEach { deleteRecursive(it) }
        }
        fileOrDir.delete()
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) {
            String.format(Locale.US, "%.2f GB", mb / 1024.0)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }
}
