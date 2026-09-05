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

        // Handle incoming shared media
        if (intent?.action == Intent.ACTION_SEND) {
            val uri = androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            uri?.let { viewModel.selectMedia(it) }
        }

        setContent {
            JumpCutTheme {
                val selectedMedia by viewModel.selectedMedia.collectAsState()
                val processingState by viewModel.processingState.collectAsState()
                val cutSettings by viewModel.cutSettings.collectAsState()
                val isProUser by viewModel.isProUser.collectAsState()
                val skipSilencePreview by viewModel.skipSilencePreview.collectAsState()

                var showProPaywall by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
                    color = BgDark
                ) {
                    when (val state = processingState) {
                        is ProcessingState.Idle -> {
                            HomeScreen(
                                onMediaSelected = { uri -> viewModel.selectMedia(uri) },
                                onApplyPreset = { preset -> viewModel.updateSettings(preset) },
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
                                    onSettingsChanged = { viewModel.updateSettings(it) },
                                    onToggleSkipSilence = { viewModel.toggleSkipSilencePreview(it) },
                                    onExportClick = {
                                        viewModel.exportSplicedMedia()
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
