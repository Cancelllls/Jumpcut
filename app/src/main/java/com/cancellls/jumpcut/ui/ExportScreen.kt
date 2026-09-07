package com.cancellls.jumpcut.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.cancellls.jumpcut.model.ProcessingState
import com.cancellls.jumpcut.theme.*
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun ExportScreen(
    state: ProcessingState,
    onDoneClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (state) {
            is ProcessingState.Exporting -> {
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxSize(),
                        color = PrimaryCyan,
                        strokeWidth = 10.dp,
                        trackColor = CardDark
                    )
                    Text(
                        text = "${(state.progress * 100).toInt()}%",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "Splicing Video...",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = state.status,
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(40.dp))

                OutlinedButton(
                    onClick = onCancelClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SilenceRed),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(SilenceRed, SilenceRed)))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel Export")
                }
            }

            is ProcessingState.Exported -> {
                val outputFile = File(state.outputPath)
                val fileSizeMb = String.format(java.util.Locale.US, "%.1f MB", outputFile.length() / (1024.0 * 1024.0))

                // Success Icon
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(GreenSuccess),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Done", tint = BgDark, modifier = Modifier.size(40.dp))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Export Complete!",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Your video is now tight, clean, and ready to share.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Stats Row Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatBox(
                        title = "Saved Time",
                        value = formatTime((state.originalDurationMs - state.cutDurationMs).coerceAtLeast(0)),
                        accent = GreenSuccess,
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        title = "Snappier By",
                        value = "${state.savedPercent}%",
                        accent = PrimaryCyan,
                        modifier = Modifier.weight(1f)
                    )
                    StatBox(
                        title = "File Size",
                        value = fileSizeMb,
                        accent = NeonViolet,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Mini Video Preview of Result
                val exportedPlayer = remember {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(ExoMediaItem.fromUri(state.outputUri))
                        prepare()
                    }
                }

                DisposableEffect(exportedPlayer) {
                    onDispose { exportedPlayer.release() }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exportedPlayer
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Gallery Saved Badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardDark)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Saved to Gallery & Projects Library",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GreenSuccess
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action: Share Video
                Button(
                    onClick = {
                        val isVideo = outputFile.name.endsWith(".mp4", true) || outputFile.name.endsWith(".mov", true)
                        com.cancellls.jumpcut.storage.MediaSaver.shareMedia(context, outputFile, isVideo)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.horizontalGradient(listOf(PrimaryCyan, NeonViolet))),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = TextPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share to CapCut / TikTok / Gallery", fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action: Return to Studio & Projects
                TextButton(
                    onClick = onDoneClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done • Return to Studio", color = TextSecondary, fontSize = 14.sp)
                }
            }

            is ProcessingState.Error -> {
                Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = SilenceRed, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Export Failed", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = state.message, fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onDoneClick, colors = ButtonDefaults.buttonColors(containerColor = CardDark)) {
                    Text("Try Again")
                }
            }

            else -> {}
        }
    }
}

@Composable
fun StatBox(
    title: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, fontSize = 11.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = accent)
        }
    }
}

private fun shareVideo(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Clean Video via"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
