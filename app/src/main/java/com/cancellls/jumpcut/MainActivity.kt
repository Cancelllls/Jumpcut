package com.cancellls.jumpcut

import android.content.Context
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
import com.cancellls.jumpcut.ads.AdManager
import com.cancellls.jumpcut.billing.BillingManager
import com.cancellls.jumpcut.model.ProcessingState
import com.cancellls.jumpcut.theme.BgDark
import com.cancellls.jumpcut.theme.JumpCutTheme
import com.cancellls.jumpcut.theme.PrimaryCyan
import com.cancellls.jumpcut.theme.TextPrimary
import com.cancellls.jumpcut.theme.TextSecondary
import com.cancellls.jumpcut.ui.EditorScreen
import com.cancellls.jumpcut.ui.ExportScreen
import com.cancellls.jumpcut.ui.HomeScreen
import com.cancellls.jumpcut.ui.OnboardingScreen
import com.cancellls.jumpcut.ui.ProPaywallSheet

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        AdManager.initialize(this)
        billingManager = BillingManager(
            context = this,
            onProUnlocked = { viewModel.unlockPro() },
            onProRevoked = { viewModel.revokePro() }
        )

        // Handle incoming media or links
        handleIncomingIntent(intent)

        setContent {
            JumpCutTheme {
                val prefs = remember { getSharedPreferences("jumpcut_prefs", Context.MODE_PRIVATE) }
                var hasSeenIntro by remember { mutableStateOf(prefs.getBoolean("has_seen_intro", false)) }
                var showIntroModal by remember { mutableStateOf(false) }

                val selectedMedia by viewModel.selectedMedia.collectAsState()
                val processingState by viewModel.processingState.collectAsState()
                val cutSettings by viewModel.cutSettings.collectAsState()
                val exportConfig by viewModel.exportConfig.collectAsState()
                val isProUser by viewModel.isProUser.collectAsState()
                val skipSilencePreview by viewModel.skipSilencePreview.collectAsState()
                val savedProjects by viewModel.savedProjects.collectAsState()
                val creatorPresets by viewModel.creatorPresets.collectAsState()
                val cacheSize by viewModel.cacheSize.collectAsState()
                val remainingExports by viewModel.remainingExports.collectAsState()
                val pricing by billingManager.pricing.collectAsState()
                val isAdBlockerDetected by AdManager.isAdBlockerDetected.collectAsState()

                var showProPaywall by remember { mutableStateOf(false) }
                var proPaywallReason by remember { mutableStateOf<String?>(null) }

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
                    if (!hasSeenIntro || showIntroModal) {
                        OnboardingScreen(
                            onFinishOnboarding = {
                                prefs.edit().putBoolean("has_seen_intro", true).apply()
                                hasSeenIntro = true
                                showIntroModal = false
                            }
                        )
                    } else {
                        when (val state = processingState) {
                            is ProcessingState.Idle -> {
                                HomeScreen(
                                    savedProjects = savedProjects,
                                    cacheSize = cacheSize,
                                    creatorPresets = creatorPresets,
                                    remainingExports = remainingExports,
                                    isAdBlockerDetected = isAdBlockerDetected,
                                    onDismissAdBlockerNotice = { AdManager.dismissAdBlockerNotice() },
                                    onMediaSelected = { uri -> viewModel.selectMedia(uri) },
                                    onDownloadUrl = { url -> viewModel.downloadFromUrl(url) },
                                    onApplyPreset = { preset -> viewModel.updateSettings(preset) },
                                    onDeleteCustomPreset = { id -> viewModel.deleteCustomPreset(id) },
                                    onExportProjectEdl = { project, asXml -> viewModel.exportProjectEdl(this@MainActivity, project, asXml) },
                                    onDeleteProject = { id -> viewModel.deleteProject(id) },
                                    onClearCache = { viewModel.clearCache() },
                                    onOpenPro = {
                                        proPaywallReason = null
                                        showProPaywall = true
                                    },
                                    onReplayIntro = { showIntroModal = true },
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
                                    creatorPresets = creatorPresets,
                                    isProUser = isProUser,
                                    remainingExports = remainingExports,
                                    onOpenPro = {
                                        proPaywallReason = if (remainingExports <= 0) "Daily Free Export Limit Reached (2/2 used today). Upgrade to JumpCut Pro for unlimited 4K 60FPS exports!" else null
                                        showProPaywall = true
                                    },
                                    onSettingsChanged = { viewModel.updateSettings(it) },
                                    onToggleSkipSilence = { viewModel.toggleSkipSilencePreview(it) },
                                    onToggleSegment = { segId -> viewModel.toggleSegment(segId) },
                                    onExportConfirm = { config ->
                                        viewModel.updateExportConfig(config)
                                        viewModel.exportSplicedMedia(config)
                                    },
                                    onSavePreset = { name, settings ->
                                        viewModel.saveCustomPreset(name, settings)
                                    },
                                    onExportEdl = { asXml ->
                                        viewModel.exportCurrentEdl(this@MainActivity, asXml)
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
                                onDoneClick = {
                                    if (state is ProcessingState.Exported) {
                                        AdManager.showPostExportInterstitial(this@MainActivity, isProUser) {
                                            viewModel.reset()
                                        }
                                    } else {
                                        viewModel.reset()
                                    }
                                },
                                onCancelClick = { viewModel.reset() }
                            )
                        }
                    }
                }

                if (showProPaywall) {
                    ProPaywallSheet(
                        lifetimePrice = pricing.lifetimePrice,
                        monthlyPrice = pricing.monthlyPrice,
                        quotaReason = proPaywallReason,
                        onDismiss = {
                            showProPaywall = false
                            proPaywallReason = null
                        },
                        onPurchasePlan = { plan, onError ->
                            billingManager.launchPurchaseFlow(this@MainActivity, plan, onError)
                        },
                        onRestorePurchases = { onResult ->
                            billingManager.restorePurchases(onResult)
                        }
                    )
                }
            }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
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
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null) {
                    viewModel.selectMedia(uri)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.destroy()
    }
}
