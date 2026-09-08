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
    onDeleteProject: (String) -> Unit,
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
                NavigationBar(
                    containerColor = SurfaceDark,
                    contentColor = TextPrimary,
                    tonalElevation = 8.dp
                ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = "Cutter"
                        )
                    },
                    label = { Text("Studio", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryCyan,
                        selectedTextColor = PrimaryCyan,
                        indicatorColor = CardDark,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (savedProjects.isNotEmpty()) {
                                    Badge(containerColor = PrimaryCyan) {
                                        Text("${savedProjects.size}", color = BgDark, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = "Projects"
                            )
                        }
                    },
                    label = { Text("Projects", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryCyan,
                        selectedTextColor = PrimaryCyan,
                        indicatorColor = CardDark,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryCyan,
                        selectedTextColor = PrimaryCyan,
                        indicatorColor = CardDark,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
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
                    onOpenPro = onOpenPro,
                    isProUser = isProUser
                )
                1 -> ProjectsScreen(
                    projects = savedProjects,
                    onDeleteProject = onDeleteProject,
                    onStartNewProject = { selectedTab = 0 },
                    onExportEdl = onExportProjectEdl
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
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            Brush.linearGradient(listOf(PrimaryCyan, ElectricBlue))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = "Logo",
                        tint = BgDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "JumpCut",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PrimaryCyan.copy(alpha = 0.12f))
                                .border(0.5.dp, PrimaryCyan.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "STUDIO",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PrimaryCyan,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                    Text(
                        text = "Lossless Silence Cutter",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            // Single unified Pro / Quota Badge (No duplicate buttons)
            if (isProUser) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(GoldPro)
                        .clickable { onOpenPro() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = "Pro", tint = BgDark, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PRO ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = BgDark)
                    }
                }
            } else {
                val hasRemaining = remainingExports > 0
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (hasRemaining) CardDark else SilenceRed.copy(alpha = 0.18f))
                        .border(
                            1.dp,
                            if (hasRemaining) CardBorder else SilenceRed.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .clickable { onOpenPro() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (hasRemaining) Icons.Default.Bolt else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (hasRemaining) PrimaryCyan else SilenceRed,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (hasRemaining) "$remainingExports free left" else "0 left • Upgrade",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hasRemaining) TextPrimary else SilenceRed
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

        // Unified Master Creation Hero Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    listOf(PrimaryCyan.copy(alpha = 0.5f), ElectricBlue.copy(alpha = 0.2f), CardBorder)
                )
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(PrimaryCyan, ElectricBlue)))
                        .clickable {
                            singleMediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Project",
                        tint = BgDark,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "New Project",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Auto-detect and cut pauses • Native 4K 60FPS",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Primary full-width Gallery CTA
                Button(
                    onClick = {
                        singleMediaPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = BgDark, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select from Gallery", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BgDark)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary actions: All Files & Web Stream
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { anyFilePicker.launch("*/*") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("All Files", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedButton(
                        onClick = { showUrlDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Web Link", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
    onShare: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
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
                Text(
                    text = project.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(GreenSuccess.copy(alpha = 0.15f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${project.savedPercent}% trimmed",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = project.formattedSize,
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            IconButton(
                onClick = onShare,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (totalSavedMs > 0) formatTimeSavedShort(totalSavedMs) else "0s",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "Time Saved", fontSize = 10.sp, color = TextSecondary)
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(26.dp)
                    .background(CardBorder)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ContentCut, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (cutsCount > 0) "$cutsCount+" else "0",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "Pauses Cut", fontSize = 10.sp, color = TextSecondary)
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(26.dp)
                    .background(CardBorder)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(GreenSuccess)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Lossless",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = GreenSuccess
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "Native 4K 60FPS", fontSize = 10.sp, color = TextSecondary)
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
