package com.cancellls.jumpcut.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object MediaSaver {
    private const val TAG = "MediaSaver"

    suspend fun saveToGallery(
        context: Context,
        sourceFile: File,
        isVideo: Boolean
    ): Uri? = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) return@withContext null

        try {
            val mimeType = if (isVideo) "video/mp4" else "audio/mp4"
            val folderName = if (isVideo) "JumpCut" else "JumpCut_Audio"
            val displayName = sourceFile.name

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

                val relativePath = if (isVideo) {
                    "${Environment.DIRECTORY_MOVIES}/$folderName"
                } else {
                    "${Environment.DIRECTORY_MUSIC}/$folderName"
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                var uri = try {
                    context.contentResolver.insert(collection, values)
                } catch (e: Exception) {
                    Log.w(TAG, "Primary collection insertion failed, attempting fallback", e)
                    null
                }

                // Fallback to Downloads folder if Movies/Music was blocked by manufacturer ROM
                if (uri == null) {
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$folderName")
                    uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                }

                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outStream ->
                        FileInputStream(sourceFile).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }

                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)

                    // Notify media scanner for instant indexing across Xiaomi/MIUI/Samsung galleries
                    try {
                        android.media.MediaScannerConnection.scanFile(
                            context,
                            arrayOf(sourceFile.absolutePath),
                            arrayOf(mimeType),
                            null
                        )
                    } catch (_: Exception) {}

                    Log.d(TAG, "Saved media to gallery URI: $uri")
                    return@withContext uri
                }
            } else {
                // Pre-Android 10
                val publicDir = Environment.getExternalStoragePublicDirectory(
                    if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
                )
                val targetDir = File(publicDir, folderName).apply { mkdirs() }
                val targetFile = File(targetDir, displayName)

                FileInputStream(sourceFile).use { inStream ->
                    FileOutputStream(targetFile).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }

                val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                val contentUri = Uri.fromFile(targetFile)
                mediaScanIntent.data = contentUri
                context.sendBroadcast(mediaScanIntent)
                return@withContext contentUri
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save media to gallery", e)
        }
        null
    }

    suspend fun saveToCustomUri(
        context: Context,
        sourceFile: File,
        targetUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) return@withContext false
        try {
            context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                FileInputStream(sourceFile).use { inStream ->
                    inStream.copyTo(outStream)
                }
            }
            Log.d(TAG, "Successfully copied export to custom URI: $targetUri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy export to custom URI $targetUri", e)
            false
        }
    }

    fun openMediaInExternalApp(context: Context, file: File, isVideo: Boolean) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mimeType = if (isVideo) "video/*" else "audio/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Play Exported Media")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open media in player", e)
        }
    }

    fun shareMedia(context: Context, file: File, isVideo: Boolean) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (isVideo) "video/*" else "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Share Clean Video")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing media", e)
        }
    }

    fun shareDocument(
        context: Context,
        file: File,
        mimeType: String = "text/plain",
        title: String = "Share Timeline"
    ) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, title)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing document", e)
        }
    }
}
