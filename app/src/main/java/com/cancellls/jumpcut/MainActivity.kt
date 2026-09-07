package com.cancellls.jumpcut

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.cancellls.jumpcut.model.ProcessingState
import com.cancellls.jumpcut.theme.BgDark
import com.cancellls.jumpcut.theme.JumpCutTheme
import com.cancellls.jumpcut.theme.PrimaryCyan
import com.cancellls.jumpcut.theme.TextPrimary
import com.cancellls.jumpcut.theme.TextSecondary
import com.cancellls.jumpcut.ui.EditorScreen
import com.cancellls.jumpcut.ui.ExportScreen
import com.cancellls.jumpcut.ui.HomeScreen
import com.cancellls.jumpcut.ui.ProPaywallSheet

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Handle incoming shared media or links
        if (intent?.action == Intent.ACTION_SEND) {
            val uri = androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            if (uri != null) {
                viewModel.selectMedia(uri)
            } else {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                val url = sharedText?.split("\\s+".toRegex())?.firstOrNull {
                    it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
                }
                if (url != null) {
                    viewModel.downloadFromUrl(url)
                }
            }
        }

        setContent {
            JumpCutTheme {
                val selectedMedia by viewModel.selectedMedia.collectAsState()
                val processingState by viewModel.processingState.collectAsState()
                val cutSettings by viewModel.cutSettings.collectAsState()
                val exportConfig by viewModel.exportConfig.collectAsState()
                val isProUser by viewModel.isProUser.collectAsState()
                val skipSilencePreview by viewModel.skipSilencePreview.collectAsState()
                val savedProjects by viewModel.savedProjects.collectAsState()
                val cacheSize by viewModel.cacheSize.collectAsState()

                var showProPaywall by remember { mutableStateOf(false) }

                // Keep screen on during heavy operations (analyzing and export) so OS doesn't sleep and abort
                DisposableEffect(processingState) {
                    if (processingState is ProcessingState.Analyzing || processingState is ProcessingState.Exporting) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                    color = BgDark
                ) {
                    when (val state = processingState) {
                        is ProcessingState.Idle -> {
                            HomeScreen(
                                savedProjects = savedProjects,
                                cacheSize = cacheSize,
                                onMediaSelected = { uri -> viewModel.selectMedia(uri) },
                                onDownloadUrl = { url -> viewModel.downloadFromUrl(url) },
                                onApplyPreset = { preset -> viewModel.updateSettings(preset) },
                                onDeleteProject = { id -> viewModel.deleteProject(id) },
                                onClearCache = { viewModel.clearCache() },
                                onOpenPro = { showProPaywall = true },
                                isProUser = isProUser
                            )
                        }

                        is ProcessingState.Analyzing -> {
                            Box(
                                modifier = Modifier.fillMaxSize().background(BgDark),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = PrimaryCyan,
                                        modifier = Modifier.size(54.dp),
                                        strokeWidth = 5.dp
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = state.status,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${(state.progress * 100).toInt()}%",
                                        fontSize = 13.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        is ProcessingState.Ready -> {
                            val media = selectedMedia
                            if (media != null) {
                                EditorScreen(
                                    media = media,
                                    segments = state.segments,
                                    waveformAmplitudes = state.waveformAmplitudes,
                                    originalDurationMs = state.originalDurationMs,
                                    cutDurationMs = state.cutDurationMs,
                                    savedPercent = state.savedPercent,
                                    cutSettings = cutSettings,
                                    skipSilencePreview = skipSilencePreview,
                                    exportConfig = exportConfig,
                                    onSettingsChanged = { viewModel.updateSettings(it) },
                                    onToggleSkipSilence = { viewModel.toggleSkipSilencePreview(it) },
                                    onToggleSegment = { segId -> viewModel.toggleSegment(segId) },
                                    onExportConfirm = { config ->
                                        viewModel.updateExportConfig(config)
                                        viewModel.exportSplicedMedia(config)
                                    },
                                    onBackClick = { viewModel.reset() }
                                )
                            } else {
                                viewModel.reset()
                            }
                        }

                        is ProcessingState.Exporting,
                        is ProcessingState.Exported,
                        is ProcessingState.Error -> {
                            ExportScreen(
                                state = state,
                                onDoneClick = { viewModel.reset() },
                                onCancelClick = { viewModel.reset() }
                            )
                        }
                    }

                    if (showProPaywall) {
                        ProPaywallSheet(
                            onDismiss = { showProPaywall = false },
                            onUnlock = { viewModel.unlockPro() }
                        )
                    }
                }
            }
        }
    }
}
