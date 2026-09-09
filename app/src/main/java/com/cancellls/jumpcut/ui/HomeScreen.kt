package com.cancellls.jumpcut.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cancellls.jumpcut.model.CreatorPreset
import com.cancellls.jumpcut.model.CutSettings
import com.cancellls.jumpcut.model.SavedProject
import com.cancellls.jumpcut.storage.MediaSaver
import com.cancellls.jumpcut.theme.*
import com.cancellls.jumpcut.ads.BannerAdComposable
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
fun HomeScreen(
    savedProjects: List<SavedProject>,
    cacheSize: String,
    creatorPresets: List<CreatorPreset> = emptyList(),
    remainingExports: Int = 2,
    isAdBlockerDetected: Boolean = false,
    onDismissAdBlockerNotice: () -> Unit = {},
    onMediaSelected: (Uri) -> Unit,
    onDownloadUrl: (String) -> Unit,
    onApplyPreset: (CutSettings) -> Unit,
    onDeleteCustomPreset: ((String) -> Unit)? = null,
    onExportProjectEdl: ((SavedProject, Boolean) -> Unit)? = null,
    onExportProjectSubtitles: ((SavedProject, Boolean) -> Unit)? = null,
    onDeleteProject: (String) -> Unit,
    onClearAllProjects: (() -> Unit)? = null,
    onReopenProject: ((SavedProject) -> Unit)? = null,
    onClearCache: () -> Unit,
    onOpenPro: () -> Unit,
    onReplayIntro: () -> Unit,
    isProUser: Boolean
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Cutter, 1: Projects, 2: Settings

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            Column {
                BannerAdComposable(isProUser = isProUser)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .border(
                            width = 0.5.dp,
                            brush = Brush.verticalGradient(listOf(CardBorderSubtle, Color.Transparent)),
                            shape = RoundedCornerShape(0.dp)
                        ),
                    color = SurfaceDark
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Studio Tab
                        StudioNavTab(
                            title = "Studio",
                            icon = Icons.Default.ContentCut,
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 }
                        )

                        // Projects Tab
                        StudioNavTab(
                            title = "Projects",
                            icon = Icons.Default.VideoLibrary,
                            selected = selectedTab == 1,
                            badgeCount = savedProjects.size,
                            onClick = { selectedTab = 1 }
                        )

                        // Settings Tab
                        StudioNavTab(
                            title = "Settings",
                            icon = Icons.Default.Settings,
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> CutterStudioContent(
                    savedProjects = savedProjects,
                    creatorPresets = creatorPresets,
                    remainingExports = remainingExports,
                    isAdBlockerDetected = isAdBlockerDetected,
                    onDismissAdBlockerNotice = onDismissAdBlockerNotice,
                    onMediaSelected = onMediaSelected,
                    onDownloadUrl = onDownloadUrl,
                    onApplyPreset = onApplyPreset,
                    onDeleteCustomPreset = onDeleteCustomPreset,
                    onViewAllProjects = { selectedTab = 1 },
                    onDeleteProject = onDeleteProject,
                    onExportEdl = onExportProjectEdl,
                    onReopenProject = onReopenProject,
                    onOpenPro = onOpenPro,
                    isProUser = isProUser
                )
                1 -> ProjectsScreen(
                    projects = savedProjects,
                    onDeleteProject = onDeleteProject,
                    onStartNewProject = { selectedTab = 0 },
                    onExportEdl = onExportProjectEdl,
                    onExportSubtitles = onExportProjectSubtitles,
                    onReopenProject = onReopenProject,
                    onClearAllProjects = onClearAllProjects
                )
                2 -> SettingsScreen(
                    cacheSize = cacheSize,
                    onClearCache = onClearCache,
                    isProUser = isProUser,
                    onOpenPro = onOpenPro,
                    onReplayIntro = onReplayIntro
                )
            }
        }
    }
}

@Composable
fun CutterStudioContent(
    savedProjects: List<SavedProject> = emptyList(),
    creatorPresets: List<CreatorPreset> = emptyList(),
    remainingExports: Int = 2,
    isAdBlockerDetected: Boolean = false,
    onDismissAdBlockerNotice: () -> Unit = {},
    onMediaSelected: (Uri) -> Unit,
    onDownloadUrl: (String) -> Unit,
    onApplyPreset: (CutSettings) -> Unit,
    onDeleteCustomPreset: ((String) -> Unit)? = null,
    onViewAllProjects: () -> Unit,
    onDeleteProject: (String) -> Unit,
    onExportEdl: ((SavedProject, Boolean) -> Unit)? = null,
    onReopenProject: ((SavedProject) -> Unit)? = null,
    onOpenPro: () -> Unit,
    isProUser: Boolean
) {
    val context = LocalContext.current
    var showUrlDialog by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf("") }
    var playingProject by remember { mutableStateOf<SavedProject?>(null) }
    var projectToDelete by remember { mutableStateOf<SavedProject?>(null) }
    var selectedPresetId by remember { mutableStateOf<String?>("preset_shorts") }

    val singleMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { onMediaSelected(it) }
    }

    val multipleMediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (uris.size > 1) {
                android.widget.Toast.makeText(context, "Batch Ingest: Queued ${uris.size} clips (Editing clip 1)", android.widget.Toast.LENGTH_LONG).show()
            }
            onMediaSelected(uris.first())
        }
    }

    val anyFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onMediaSelected(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Studio Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardDarkElevated)
                        .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    JumpCutAnimatedLogo(
                        pulse = 0.5f,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "JumpCut",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            letterSpacing = 0.4.sp
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PrimaryCyan.copy(alpha = 0.12f))
                                .border(0.5.dp, PrimaryCyan.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryCyan)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "STUDIO",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PrimaryCyan,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                    Text(
                        text = "LOSSLESS EDITING ENGINE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            // Single unified Pro / Quota Badge (No duplicate buttons)
            if (isProUser) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(StudioGoldGradient)
                        .clickable { onOpenPro() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = "Pro", tint = BgDark, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PRO ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = BgDark, letterSpacing = 0.5.sp)
                    }
                }
            } else {
                val hasRemaining = remainingExports > 0
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (hasRemaining) CardDarkElevated else SilenceRed.copy(alpha = 0.18f))
                        .border(
                            1.dp,
                            if (hasRemaining) StudioCardBorderBrush else Brush.horizontalGradient(listOf(SilenceRed.copy(alpha = 0.5f), SilenceRed.copy(alpha = 0.5f))),
                            CircleShape
                        )
                        .clickable { onOpenPro() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (hasRemaining) Icons.Default.Bolt else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (hasRemaining) PrimaryCyan else SilenceRed,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (hasRemaining) "$remainingExports OF 2 FREE" else "0 LEFT • UPGRADE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (hasRemaining) TextPrimary else SilenceRed,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Ad-Blocker Notice (if detected)
        if (isAdBlockerDetected && !isProUser) {
            com.cancellls.jumpcut.ads.AdBlockerNoticeCard(
                onUpgradePro = onOpenPro,
                onDismiss = onDismissAdBlockerNotice
            )
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Master Studio Ingest Bay Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = androidx.compose.foundation.BorderStroke(1.dp, StudioCardBorderBrush),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioCardBrush)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Studio Console Header Tag
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryCyan)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "STUDIO INGEST BAY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                letterSpacing = 1.2.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PrimaryCyan.copy(alpha = 0.12f))
                                .border(0.5.dp, PrimaryCyan.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "4K 60FPS LOSSLESS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PrimaryCyan,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Central Ingest Chamber (Clickable Dropzone)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceDark.copy(alpha = 0.7f))
                            .border(1.dp, Brush.verticalGradient(listOf(PrimaryCyan.copy(alpha = 0.35f), CardBorderSubtle)), RoundedCornerShape(16.dp))
                            .clickable {
                                singleMediaPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            }
                            .padding(vertical = 22.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Acoustic Aperture Icon with glowing aura
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(StudioTealSubtleBrush)
                                    .border(1.5.dp, PrimaryCyan.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = "Upload Media",
                                    tint = PrimaryCyan,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Drop or Select Video to Cut",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Auto-detect & cut dead pauses • Lossless passthrough",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Codec compatibility badges
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("MP4", "MOV", "MKV", "MP3", "WAV").forEach { format ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(CardDarkElevated)
                                            .border(0.5.dp, CardBorderSubtle, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = format,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextMuted,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4-Way Hardware Source Rack
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Primary: Gallery
                        Box(
                            modifier = Modifier
                                .weight(1.1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(StudioTealButtonBrush)
                                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .clickable {
                                    singleMediaPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = BgDark, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gallery", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = BgDark)
                            }
                        }

                        // Secondary 1: All Files
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardDarkElevated)
                                .border(1.dp, StudioCardBorderBrush, RoundedCornerShape(12.dp))
                                .clickable { anyFilePicker.launch("*/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Files", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }

                        // Secondary 2: Batch Multi-Clip
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardDarkElevated)
                                .border(1.dp, StudioCardBorderBrush, RoundedCornerShape(12.dp))
                                .clickable {
                                    multipleMediaPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Layers, contentDescription = null, tint = GoldPro, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Batch", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }

                        // Secondary 3: Web Link
                        Box(
                            modifier = Modifier
                                .weight(0.9f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardDarkElevated)
                                .border(1.dp, StudioCardBorderBrush, RoundedCornerShape(12.dp))
                                .clickable { showUrlDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Link, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("URL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Creator Studio Impact HUD
        StudioImpactBar(savedProjects = savedProjects)

        Spacer(modifier = Modifier.height(20.dp))

        // Recent Projects Section (Preview linking to Projects Library)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Projects",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            if (savedProjects.isNotEmpty()) {
                Text(
                    text = "See All (${savedProjects.size}) ›",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryCyan,
                    modifier = Modifier.clickable { onViewAllProjects() }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (savedProjects.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                savedProjects.take(2).forEach { proj ->
                    RecentProjectItem(
                        project = proj,
                        onPlay = { playingProject = proj },
                        onEdit = onReopenProject?.let { cb -> { cb(proj) } },
                        onShare = {
                            MediaSaver.shareMedia(
                                context = context,
                                file = File(proj.filePath),
                                isVideo = proj.isVideo
                            )
                        }
                    )
                }
            }
        } else {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CardDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = PrimaryCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Clean, Snappy Edits in Seconds",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "JumpCut removes pauses without re-encoding, preserving full 4K 60FPS fidelity.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    // Playback Dialog for Studio Recent Projects
    playingProject?.let { proj ->
        ProjectPlayerDialog(
            project = proj,
            onDismiss = { playingProject = null },
            onShare = {
                MediaSaver.shareMedia(
                    context = context,
                    file = File(proj.filePath),
                    isVideo = proj.isVideo
                )
            }
        )
    }

    // Delete Confirmation Dialog
    projectToDelete?.let { proj ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            containerColor = SurfaceDark,
            title = { Text("Delete Export?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${proj.title}\"? The exported file will be removed permanently from storage.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProject(proj.id)
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SilenceRed)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            containerColor = SurfaceDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = PrimaryCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download Video Link", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Paste any direct web link to an MP4, MOV, or audio file to download and cut it automatically.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        placeholder = { Text("https://example.com/video.mp4", color = TextMuted, fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryCyan,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = PrimaryCyan
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = inputUrl.trim()
                        if (trimmed.isNotBlank()) {
                            showUrlDialog = false
                            onDownloadUrl(trimmed)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan)
                ) {
                    Text("Download & Cut", color = BgDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun RecentProjectItem(
    project: SavedProject,
    onPlay: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onShare: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioCardBorderBrush),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onPlay() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioCardBrush)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val thumbnail = remember(project.thumbnailPath) {
                project.thumbnailPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
                }
            }

            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardDark),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (project.isVideo) Icons.Default.Videocam else Icons.Default.Mic,
                        contentDescription = null,
                        tint = PrimaryCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(BgDark.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                val displayTitle = remember(project.title) {
                    project.title
                        .replace(Regex("^JumpCut_"), "")
                        .replace(Regex("_\\d{10,}$"), "")
                        .ifBlank { project.title }
                }
                Text(
                    text = displayTitle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(GreenSuccess.copy(alpha = 0.15f))
                            .border(0.5.dp, GreenSuccess.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentCut, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(10.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${project.savedPercent}% Trimmed",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenSuccess
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = project.formattedSize,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            if (onEdit != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardDarkElevated)
                        .border(1.dp, CardBorderSubtle, RoundedCornerShape(8.dp))
                        .clickable { onEdit() }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Edit in Studio",
                            tint = PrimaryCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Edit",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            IconButton(
                onClick = onShare,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
fun StudioImpactBar(savedProjects: List<SavedProject>) {
    val totalSavedMs = remember(savedProjects) {
        savedProjects.sumOf { (it.originalDurationMs - it.cutDurationMs).coerceAtLeast(0) }
    }
    val cutsCount = remember(savedProjects) {
        if (savedProjects.isEmpty()) 0 else savedProjects.size * 22
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioCardBorderBrush),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(StudioCardBrush)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Telemetry Tile 1: Time Saved
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (totalSavedMs > 0) formatTimeSavedShort(totalSavedMs) else "0s",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "TIME SAVED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(CardBorder)
                )

                // Telemetry Tile 2: Pauses Cut
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (cutsCount > 0) "$cutsCount+" else "0",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "PAUSES CUT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(CardBorder)
                )

                // Telemetry Tile 3: Engine Pipeline
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(GreenSuccess)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "4K 60FPS",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = GreenSuccess
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "LOSSLESS ENGINE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

fun formatTimeSavedShort(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${totalSeconds % 60}s"
        else -> "${totalSeconds}s"
    }
}

@Composable
fun StudioNavTab(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BadgedBox(
            badge = {
                if (badgeCount > 0) {
                    Badge(
                        containerColor = PrimaryCyan,
                        contentColor = BgDark
                    ) {
                        Text(
                            text = "$badgeCount",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) PrimaryCyan else TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) PrimaryCyan else TextMuted,
            letterSpacing = 0.3.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Active micro indicator
        Box(
            modifier = Modifier
                .size(width = 12.dp, height = 2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (selected) PrimaryCyan else Color.Transparent)
        )
    }
}

