package com.cancellls.jumpcut.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(listOf(PrimaryCyan, ElectricBlue))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = "Logo",
                        tint = BgDark,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "JumpCut",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PrimaryCyan.copy(alpha = 0.15f))
                                .border(1.dp, PrimaryCyan.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PRO STUDIO",
                                fontSize = 9.sp,
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

            // Top Actions: Quota Pill + Pro Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isProUser) {
                    val hasRemaining = remainingExports > 0
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (hasRemaining) CardDark else SilenceRed.copy(alpha = 0.18f))
                            .border(
                                1.dp,
                                if (hasRemaining) CardBorder else SilenceRed.copy(alpha = 0.6f),
                                CircleShape
                            )
                            .clickable { if (!hasRemaining) onOpenPro() }
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
                                text = if (hasRemaining) "$remainingExports left" else "0 left",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasRemaining) TextPrimary else SilenceRed
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isProUser) GoldPro else CardDark)
                        .border(
                            1.dp,
                            if (isProUser) GoldPro else GoldPro.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .clickable { onOpenPro() }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Pro",
                            tint = if (isProUser) BgDark else GoldPro,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isProUser) "PRO ACTIVE" else "PRO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isProUser) BgDark else GoldPro
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

        // Master Creation Card: CapCut-Style "Start New Project"
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                Brush.verticalGradient(
                    listOf(PrimaryCyan.copy(alpha = 0.7f), ElectricBlue.copy(alpha = 0.25f), CardBorder)
                )
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Primary Start New Project Tap Target
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            singleMediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(PrimaryCyan, ElectricBlue)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Project",
                            tint = BgDark,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Start New Project",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(PrimaryCyan.copy(alpha = 0.15f))
                                    .border(0.5.dp, PrimaryCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "4K 60FPS",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = "Lossless hardware cut • Zero quality loss",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                HorizontalDivider(
                    color = CardBorder.copy(alpha = 0.5f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )

                // Quick Import Ribbon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioActionChip(
                        icon = Icons.Default.FolderOpen,
                        label = "Browse Files",
                        tint = PrimaryCyan,
                        modifier = Modifier.weight(1f),
                        onClick = { anyFilePicker.launch("video/*") }
                    )
                    StudioActionChip(
                        icon = Icons.Default.Link,
                        label = "Web Stream",
                        tint = ElectricBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { showUrlDialog = true }
                    )
                    StudioActionChip(
                        icon = Icons.Default.Mic,
                        label = "Audio Memo",
                        tint = SpeechCyan,
                        modifier = Modifier.weight(1f),
                        onClick = { anyFilePicker.launch("audio/*") }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Creator Studio Impact HUD
        StudioImpactBar(savedProjects = savedProjects)

        Spacer(modifier = Modifier.height(18.dp))

        // Studio Quick Toolkit (2x2 Grid)
        StudioQuickToolsGrid(
            onInstantCut = {
                singleMediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
            onVoiceArmor = {
                selectedPresetId = "preset_podcast"
                onApplyPreset(CutSettings(-34f, 450L, 70L))
            },
            onEdlExport = {
                onViewAllProjects()
            },
            onBatchQueue = {
                onOpenPro()
            }
        )

        // JumpCut PRO Showcase Banner (if free tier)
        if (!isProUser) {
            Spacer(modifier = Modifier.height(18.dp))
            StudioProCard(onUpgrade = onOpenPro)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Sensitivity Presets Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Rhythm Presets",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Calibrated speech pacing",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val displayPresets = if (creatorPresets.isNotEmpty()) creatorPresets else listOf(
                CreatorPreset("preset_shorts", "Viral Shorts", CutSettings(-30f, 250L, 40L), isBuiltIn = true),
                CreatorPreset("preset_podcast", "Podcast Studio", CutSettings(-34f, 450L, 70L), isBuiltIn = true),
                CreatorPreset("preset_lecture", "Fast Lecture", CutSettings(-28f, 200L, 30L), isBuiltIn = true),
                CreatorPreset("preset_vlog", "Vlog & Story", CutSettings(-36f, 600L, 80L), isBuiltIn = true)
            )

            displayPresets.forEach { preset ->
                val isSelected = selectedPresetId == preset.id
                val icon = when {
                    preset.name.contains("Short", true) || preset.name.contains("TikTok", true) -> Icons.Default.FlashOn
                    preset.name.contains("Pod", true) -> Icons.Default.Podcasts
                    preset.name.contains("Lect", true) -> Icons.Default.Speed
                    preset.name.contains("Vlog", true) || preset.name.contains("Story", true) -> Icons.Default.Videocam
                    else -> Icons.Default.Tune
                }
                val accent = when {
                    preset.name.contains("Short", true) -> PrimaryCyan
                    preset.name.contains("Pod", true) -> ElectricBlue
                    preset.name.contains("Lect", true) -> GreenSuccess
                    else -> PrimaryCyan
                }

                Box(modifier = Modifier.width(142.dp)) {
                    PresetCard(
                        title = preset.name,
                        subtitle = "${preset.settings.silenceThresholdDb.toInt()}dB • ${preset.settings.paddingMs}ms",
                        icon = icon,
                        accentColor = accent,
                        isSelected = isSelected,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        selectedPresetId = preset.id
                        onApplyPreset(preset.settings)
                    }

                    if (!preset.isBuiltIn && onDeleteCustomPreset != null) {
                        IconButton(
                            onClick = { onDeleteCustomPreset(preset.id) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Delete",
                                tint = TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Recent Projects Showcase
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
                    text = "View Library (${savedProjects.size}) ›",
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                savedProjects.take(3).forEach { proj ->
                    ProjectCard(
                        project = proj,
                        onPlay = { playingProject = proj },
                        onShare = {
                            MediaSaver.shareMedia(
                                context = context,
                                file = File(proj.filePath),
                                isVideo = proj.isVideo
                            )
                        },
                        onExportEdl = if (onExportEdl != null) { { onExportEdl(proj, false) } } else null,
                        onDelete = { projectToDelete = proj }
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(CardDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = PrimaryCyan.copy(alpha = 0.8f),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Studio Workspace Ready",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Import a video or audio clip above to automatically detect and cut pauses with zero quality loss.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
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
fun StudioActionChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SpecPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(CardDark)
            .border(1.dp, CardBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryCyan,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun PresetCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) CardDark else SurfaceDark)
            .border(
                1.5.dp,
                if (isSelected) accentColor else CardBorder,
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = if (isSelected) 0.25f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 10.sp, color = if (isSelected) TextPrimary else TextSecondary)
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
            // Stat 1: Time Saved
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

            // Stat 2: Dead Pauses Eliminated
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

            // Stat 3: Lossless Direct
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
                Text(text = "Direct Passthrough", fontSize = 10.sp, color = TextSecondary)
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
fun StudioQuickToolsGrid(
    onInstantCut: () -> Unit,
    onVoiceArmor: () -> Unit,
    onEdlExport: () -> Unit,
    onBatchQueue: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Creator Toolkit",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Studio utilities",
                fontSize = 12.sp,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StudioToolCard(
                icon = Icons.Default.AutoAwesome,
                iconTint = PrimaryCyan,
                title = "Magic Auto-Cut",
                subtitle = "1-tap silence trim",
                modifier = Modifier.weight(1f),
                onClick = onInstantCut
            )
            StudioToolCard(
                icon = Icons.Default.GraphicEq,
                iconTint = ElectricBlue,
                title = "Voice Floor",
                subtitle = "Speech edge armor",
                modifier = Modifier.weight(1f),
                onClick = onVoiceArmor
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StudioToolCard(
                icon = Icons.Default.DesktopWindows,
                iconTint = GreenSuccess,
                title = "EDL / XML Studio",
                subtitle = "Send to Premiere & DaVinci",
                modifier = Modifier.weight(1f),
                onClick = onEdlExport
            )
            StudioToolCard(
                icon = Icons.Default.Layers,
                iconTint = GoldPro,
                title = "Batch Splicer",
                subtitle = "Multi-clip queue",
                isProBadge = true,
                modifier = Modifier.weight(1f),
                onClick = onBatchQueue
            )
        }
    }
}

@Composable
fun StudioToolCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    isProBadge: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
        modifier = modifier
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                }

                if (isProBadge) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(GoldPro.copy(alpha = 0.18f))
                            .border(0.5.dp, GoldPro.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text("PRO", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = GoldPro)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
        }
    }
}

@Composable
fun StudioProCard(
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            Brush.horizontalGradient(listOf(GoldPro.copy(alpha = 0.8f), PrimaryCyan.copy(alpha = 0.6f)))
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GoldPro.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = GoldPro, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = "JumpCut Studio PRO", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                        Text(text = "For creators & professional editors", fontSize = 11.sp, color = TextSecondary)
                    }
                }

                Button(
                    onClick = onUpgrade,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPro),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Upgrade", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BgDark)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Unlimited 4K Exports",
                    "Premiere/DaVinci EDL",
                    "Zero Ads"
                ).forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GoldPro, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = feature, fontSize = 10.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}
