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
import com.cancellls.jumpcut.theme.*
import com.cancellls.jumpcut.ads.BannerAdComposable

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
                    creatorPresets = creatorPresets,
                    remainingExports = remainingExports,
                    isAdBlockerDetected = isAdBlockerDetected,
                    onDismissAdBlockerNotice = onDismissAdBlockerNotice,
                    onMediaSelected = onMediaSelected,
                    onDownloadUrl = onDownloadUrl,
                    onApplyPreset = onApplyPreset,
                    onDeleteCustomPreset = onDeleteCustomPreset,
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
    creatorPresets: List<CreatorPreset> = emptyList(),
    remainingExports: Int = 2,
    isAdBlockerDetected: Boolean = false,
    onDismissAdBlockerNotice: () -> Unit = {},
    onMediaSelected: (Uri) -> Unit,
    onDownloadUrl: (String) -> Unit,
    onApplyPreset: (CutSettings) -> Unit,
    onDeleteCustomPreset: ((String) -> Unit)? = null,
    onOpenPro: () -> Unit,
    isProUser: Boolean
) {
    var showUrlDialog by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf("") }

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
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "STUDIO",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryCyan,
                                letterSpacing = 1.sp
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

        // Master Studio Dropzone Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                Brush.verticalGradient(
                    listOf(PrimaryCyan.copy(alpha = 0.6f), ElectricBlue.copy(alpha = 0.25f), CardBorder)
                )
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Primary Clickable Dropzone Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            singleMediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                        .padding(top = 26.dp, bottom = 20.dp, start = 20.dp, end = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(PrimaryCyan.copy(alpha = 0.2f), CardDark)
                                    )
                                )
                                .border(1.5.dp, PrimaryCyan.copy(alpha = 0.8f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoCall,
                                contentDescription = "Select Video",
                                tint = PrimaryCyan,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Select Video to Cut",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Hardware accelerated • Zero quality loss",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Specs Pills
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SpecPill("4K UHD")
                            SpecPill("60 FPS")
                            SpecPill("LOSSLESS")
                        }
                    }
                }

                // Sleek Divider
                HorizontalDivider(
                    color = CardBorder.copy(alpha = 0.6f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )

                // Quick Action Action Row: 3 Equal Studio Action Tabs
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
                        label = "From Link",
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

        Spacer(modifier = Modifier.height(22.dp))

        // Sensitivity Presets Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Sensitivity Presets",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Calibrated for rhythm",
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
                CreatorPreset("preset_shorts", "Shorts / TikTok", CutSettings(-30f, 250L, 40L), isBuiltIn = true),
                CreatorPreset("preset_podcast", "Podcast Studio", CutSettings(-34f, 450L, 70L), isBuiltIn = true),
                CreatorPreset("preset_lecture", "Fast Lecture", CutSettings(-28f, 200L, 30L), isBuiltIn = true)
            )

            displayPresets.forEach { preset ->
                val icon = when {
                    preset.name.contains("Short", true) || preset.name.contains("TikTok", true) -> Icons.Default.FlashOn
                    preset.name.contains("Pod", true) -> Icons.Default.Podcasts
                    preset.name.contains("Lect", true) -> Icons.Default.Speed
                    preset.name.contains("Vlog", true) -> Icons.Default.Videocam
                    else -> Icons.Default.Tune
                }
                val accent = when {
                    preset.name.contains("Short", true) -> PrimaryCyan
                    preset.name.contains("Pod", true) -> ElectricBlue
                    preset.name.contains("Lect", true) -> GreenSuccess
                    else -> PrimaryCyan
                }

                Box(modifier = Modifier.width(140.dp)) {
                    PresetCard(
                        title = preset.name,
                        subtitle = "${preset.settings.silenceThresholdDb.toInt()}dB / ${preset.settings.paddingMs}ms",
                        icon = icon,
                        accentColor = accent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
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

        Spacer(modifier = Modifier.height(20.dp))

        // Feature Highlights
        FeatureItem(
            icon = Icons.Default.Bolt,
            title = "Hardware Lossless Splice",
            description = "Videos spliced in seconds with zero re-encoding loss."
        )

        Spacer(modifier = Modifier.height(8.dp))

        FeatureItem(
            icon = Icons.Default.Security,
            title = "100% On-Device & Private",
            description = "Zero cloud uploads. Your media never leaves device hardware."
        )

        Spacer(modifier = Modifier.height(8.dp))

        FeatureItem(
            icon = Icons.Default.VolumeOff,
            title = "Speech Padding Armor",
            description = "Natural sentence endings and room tone are preserved smoothly."
        )

        Spacer(modifier = Modifier.height(28.dp))
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 10.sp, color = TextSecondary)
        }
    }
}

@Composable
fun FeatureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(CardDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = description, fontSize = 12.sp, color = TextSecondary)
        }
    }
}
