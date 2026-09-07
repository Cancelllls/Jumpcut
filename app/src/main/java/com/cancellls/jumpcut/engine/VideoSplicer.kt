package com.cancellls.jumpcut.engine

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.cancellls.jumpcut.model.CutSegment
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File

sealed class SplicerProgress {
    data class Progress(val percentage: Float) : SplicerProgress()
    data class Success(val outputFile: File) : SplicerProgress()
    data class Error(val throwable: Throwable) : SplicerProgress()
}

object VideoSplicer {
    private const val TAG = "VideoSplicer"

    @OptIn(UnstableApi::class)
    fun splice(
        context: Context,
        inputUri: Uri,
        speechSegments: List<CutSegment>,
        outputFile: File
    ): Flow<SplicerProgress> = callbackFlow {
        val validSegments = speechSegments.filter { (it.endMs - it.startMs) >= 80L }
        if (validSegments.isEmpty()) {
            trySend(SplicerProgress.Error(IllegalArgumentException("No valid speech segments to export")))
            close()
            return@callbackFlow
        }

        val handler = Handler(Looper.getMainLooper())
        val progressHolder = ProgressHolder()

        val editedMediaItems = validSegments.map { seg ->
            val clipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(seg.startMs)
                .setEndPositionMs(seg.endMs)
                .setStartsAtKeyFrame(false)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(inputUri)
                .setClippingConfiguration(clipping)
                .build()

            EditedMediaItem.Builder(mediaItem)
                .setFlattenForSlowMotion(false)
                .build()
        }

        val composition = Composition.Builder(
            EditedMediaItemSequence(editedMediaItems)
        ).build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                Log.d(TAG, "Export completed successfully: ${outputFile.absolutePath}")
                trySend(SplicerProgress.Success(outputFile))
                close()
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException
            ) {
                Log.e(TAG, "Export failed", exportException)
                trySend(SplicerProgress.Error(exportException))
                close()
            }
        }

        val encoderFactory = androidx.media3.transformer.DefaultEncoderFactory.Builder(context)
            .setEnableFallback(true)
            .build()

        val decoderFactory = androidx.media3.transformer.DefaultDecoderFactory.Builder(context)
            .build()

        val transformer = Transformer.Builder(context)
            .setEncoderFactory(encoderFactory)
            .setDecoderFactory(decoderFactory)
            .setMaxDelayBetweenMuxerSamplesMs(10_000L)
            .addListener(listener)
            .build()

        transformer.start(composition, outputFile.absolutePath)

        // Poll progress every 150ms
        val progressRunnable = object : Runnable {
            override fun run() {
                val state = transformer.getProgress(progressHolder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val p = (progressHolder.progress / 100f).coerceIn(0f, 1f)
                    trySend(SplicerProgress.Progress(p))
                }
                handler.postDelayed(this, 150)
            }
        }
        handler.post(progressRunnable)

        awaitClose {
            handler.removeCallbacks(progressRunnable)
            try {
                transformer.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Transformer cancel exception", e)
            }
        }
    }
}
