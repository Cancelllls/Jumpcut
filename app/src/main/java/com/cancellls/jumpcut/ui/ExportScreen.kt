package com.cancellls.jumpcut.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.cancellls.jumpcut.storage.MediaSaver
import com.cancellls.jumpcut.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun ExportScreen(
    state: ProcessingState,
    onDoneClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    val context = LocalContext.current
    val isExporting = state is ProcessingState.Exporting

    DisposableEffect(isExporting) {
        val activity = context as? android.app.Activity
        if (isExporting) {
            activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
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

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardDarkElevated)
                        .border(1.dp, CardBorderSubtle, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        tint = PrimaryCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Screen kept awake for encoding",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedButton(
                    onClick = onCancelClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SilenceRed),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(brush = Brush.horizontalGradient(listOf(SilenceRed, SilenceRed)))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cancel Export")
                }
            }

            is ProcessingState.Exported -> {
                val outputFile = File(state.outputPath)
                val isVideo = outputFile.name.endsWith(".mp4", true) || outputFile.name.endsWith(".mov", true)
                val fileSizeMb = String.format(java.util.Locale.US, "%.1f MB", outputFile.length() / (1024.0 * 1024.0))

                var showStorageInfoDialog by remember { mutableStateOf(false) }
                val coroutineScope = rememberCoroutineScope()
                var isSavingToFolder by remember { mutableStateOf(false) }
                var savedFolderName by remember { mutableStateOf<String?>(null) }

                val saveDocLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument(
                        if (isVideo) "video/mp4" else "audio/mp4"
                    )
                ) { targetUri: Uri? ->
                    if (targetUri != null) {
                        coroutineScope.launch {
                            isSavingToFolder = true
                            val ok = MediaSaver.saveToCustomUri(context, outputFile, targetUri)
                            isSavingToFolder = false
                            if (ok) {
                                savedFolderName = targetUri.lastPathSegment?.substringAfterLast(":") ?: "chosen folder"
                                Toast.makeText(context, "Export saved to files successfully!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Failed to save file to selected location", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

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
                        accent = ElectricBlue,
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

                Spacer(modifier = Modifier.height(14.dp))

                // Android 16 Scoped Storage Status Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showStorageInfoDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(CardBorder, CardBorder))
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(GreenSuccess.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = GreenSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Saved to Gallery (Movies/JumpCut)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Android 16 Scoped Storage • Zero permissions required",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Storage Info",
                            tint = PrimaryCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (savedFolderName != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(PrimaryCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Also saved to: $savedFolderName",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Action: Save to Files / Choose Folder (SAF)
                Button(
                    onClick = {
                        saveDocLauncher.launch(outputFile.name)
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
                            .background(Brush.horizontalGradient(listOf(PrimaryCyan, ElectricBlue))),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSavingToFolder) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = BgDark, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Saving to Folder...", fontWeight = FontWeight.Bold, color = BgDark)
                            } else {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = BgDark)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save to Files / Choose Folder (SAF)", fontWeight = FontWeight.Bold, color = BgDark)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary Row: Play in External Player & Share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            MediaSaver.openMediaInExternalApp(context, outputFile, isVideo)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = Brush.horizontalGradient(listOf(CardBorder, CardBorder))
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Player", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = {
                            MediaSaver.shareMedia(context, outputFile, isVideo)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                            brush = Brush.horizontalGradient(listOf(CardBorder, CardBorder))
                        )
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Video", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action: Return to Studio & Projects
                TextButton(
                    onClick = onDoneClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done • Return to Studio", color = TextSecondary, fontSize = 14.sp)
                }

                if (showStorageInfoDialog) {
                    AlertDialog(
                        onDismissRequest = { showStorageInfoDialog = false },
                        containerColor = SurfaceDark,
                        icon = {
                            Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(32.dp))
                        },
                        title = {
                            Text("Android 16 Storage & Permissions", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "On modern Android (13, 14, 15, and 16), Google permanently removed legacy 'Storage / Files' write permissions to protect your privacy.",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                                Text(
                                    "1. Automatic Gallery Save: JumpCut uses Android Scoped Storage to save directly to 'Movies/JumpCut' in Google Photos or your phone's Gallery without needing any system permission prompts.",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 18.sp
                                )
                                Text(
                                    "2. Custom Folder Save: Use 'Save to Files / Choose Folder' to save directly to Downloads, Documents, or an SD card using Android's native system file picker.",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 18.sp
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { showStorageInfoDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                            ) {
                                Text("Got It", color = BgDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
            }

            is ProcessingState.Error -> {
                val isMediaLoadError = state.message.contains("open", true) ||
                    state.message.contains("media", true) ||
                    state.message.contains("permission", true) ||
                    state.message.contains("analyz", true)

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(SilenceRed.copy(alpha = 0.15f))
                        .border(1.dp, SilenceRed.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Error",
                        tint = SilenceRed,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = if (isMediaLoadError) "Media Access Notice" else "Export Interrupted",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = state.message,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = onDoneClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CardDark),
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(48.dp)
                ) {
                    Text(if (isMediaLoadError) "Return to Studio" else "Return to Editor", fontWeight = FontWeight.SemiBold, color = TextPrimary)
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
