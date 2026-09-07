package com.cancellls.jumpcut.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cancellls.jumpcut.model.CutSettings
import com.cancellls.jumpcut.model.SavedProject
import com.cancellls.jumpcut.theme.*

@Composable
fun HomeScreen(
    savedProjects: List<SavedProject>,
    cacheSize: String,
    onMediaSelected: (Uri) -> Unit,
    onDownloadUrl: (String) -> Unit,
    onApplyPreset: (CutSettings) -> Unit,
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
                    label = { Text("Cutter", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
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
                                        Text("${savedProjects.size}", color = BgDark, fontSize = 10.sp)
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
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> CutterStudioContent(
                    onMediaSelected = onMediaSelected,
                    onDownloadUrl = onDownloadUrl,
                    onApplyPreset = onApplyPreset,
                    onOpenPro = onOpenPro,
                    isProUser = isProUser
                )
                1 -> ProjectsScreen(
                    projects = savedProjects,
                    onDeleteProject = onDeleteProject,
                    onStartNewProject = { selectedTab = 0 }
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
    onMediaSelected: (Uri) -> Unit,
    onDownloadUrl: (String) -> Unit,
    onApplyPreset: (CutSettings) -> Unit,
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
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(listOf(PrimaryCyan, NeonViolet))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = "Logo",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "JumpCut AI",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Lossless Silence Cutter",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }

            // Pro Badge / Button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isProUser) GoldPro else CardDark)
                    .border(
                        1.dp,
                        if (isProUser) GoldPro else CardBorder,
                        CircleShape
                    )
                    .clickable { onOpenPro() }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Pro",
                        tint = if (isProUser) BgDark else GoldPro,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isProUser) "PRO ACTIVE" else "GO PRO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isProUser) BgDark else GoldPro
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Hero Title
        Text(
            text = "Make Your Content\nTight & Engaging",
            fontSize = 28.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Automatically remove pauses, filler gaps, and dead air without re-encoding quality loss.",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Big Main Pick Video Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceDark)
                .border(2.dp, Brush.linearGradient(listOf(PrimaryCyan, NeonViolet)), RoundedCornerShape(24.dp))
                .clickable {
                    singleMediaPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                }
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(CardDark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoCall,
                        contentDescription = "Pick Video",
                        tint = PrimaryCyan,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Select Video to Cut",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Supports MP4, MOV, 4K, 60FPS",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Browse Files & Downloads Button
            OutlinedButton(
                onClick = { anyFilePicker.launch("video/*") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CardDark,
                    contentColor = TextPrimary
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(CardBorder, CardBorder))),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Files", tint = PrimaryCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Browse Files", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            // Download from Link / URL Button
            OutlinedButton(
                onClick = { showUrlDialog = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CardDark,
                    contentColor = TextPrimary
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(CardBorder, CardBorder))),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Link, contentDescription = "Link", tint = NeonViolet, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("From Link", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Secondary Button: Pick Audio File (e.g. Podcasts, Voice Notes)
        OutlinedButton(
            onClick = { anyFilePicker.launch("audio/*") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = CardDark,
                contentColor = TextPrimary
            ),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(CardBorder, CardBorder)))
        ) {
            Icon(Icons.Default.Mic, contentDescription = "Audio", tint = SpeechCyan)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select Audio or Voice Memo (MP3, M4A, WAV)")
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Quick Presets Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Preset Sensitivity",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Pre-calibrated for speech rhythm",
                fontSize = 12.sp,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PresetCard(
                title = "Shorts / TikTok",
                subtitle = "Snappy (-30dB)",
                icon = Icons.Default.FlashOn,
                accentColor = PrimaryCyan,
                modifier = Modifier.weight(1f)
            ) {
                onApplyPreset(CutSettings(silenceThresholdDb = -30f, minSilenceDurationMs = 250L, paddingMs = 40L))
            }

            PresetCard(
                title = "Podcast",
                subtitle = "Natural (-34dB)",
                icon = Icons.Default.Podcasts,
                accentColor = NeonViolet,
                modifier = Modifier.weight(1f)
            ) {
                onApplyPreset(CutSettings(silenceThresholdDb = -34f, minSilenceDurationMs = 450L, paddingMs = 70L))
            }

            PresetCard(
                title = "Lecture",
                subtitle = "Aggressive (-28dB)",
                icon = Icons.Default.Speed,
                accentColor = GreenSuccess,
                modifier = Modifier.weight(1f)
            ) {
                onApplyPreset(CutSettings(silenceThresholdDb = -28f, minSilenceDurationMs = 200L, paddingMs = 30L))
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Feature Highlights
        FeatureItem(
            icon = Icons.Default.Bolt,
            title = "Hardware Lossless Splice",
            description = "10-minute 4K videos cut in seconds with zero re-encoding loss."
        )

        Spacer(modifier = Modifier.height(10.dp))

        FeatureItem(
            icon = Icons.Default.Security,
            title = "100% On-Device & Private",
            description = "Zero cloud uploads. Your videos never leave your hardware."
        )

        Spacer(modifier = Modifier.height(10.dp))

        FeatureItem(
            icon = Icons.Default.VolumeOff,
            title = "Speech Padding Armor",
            description = "Natural word endings and consonants are preserved smoothly."
        )

        Spacer(modifier = Modifier.height(24.dp))
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
            .padding(12.dp)
    ) {
        Column {
            Icon(imageVector = icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
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
