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
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
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

                val uri = context.contentResolver.insert(collection, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outStream ->
                        FileInputStream(sourceFile).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }

                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
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
}
