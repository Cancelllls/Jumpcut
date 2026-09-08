package com.cancellls.jumpcut.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cancellls.jumpcut.theme.*
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.cancellls.jumpcut.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Enterprise Google Mobile Ads (AdMob) integration manager for JumpCut.
 * Uses official Google sample/test unit IDs in development to prevent policy violations.
 */
object AdManager {
    private const val TAG = "AdManager"

    // Official Google AdMob Test Ad Unit IDs
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    private var interstitialAd: InterstitialAd? = null
    private var isAdLoading = false
    private var isInitialized = false

    private val _isAdBlockerDetected = MutableStateFlow(false)
    val isAdBlockerDetected: StateFlow<Boolean> = _isAdBlockerDetected.asStateFlow()

    fun dismissAdBlockerNotice() {
        _isAdBlockerDetected.value = false
    }

    fun checkAdBlocker(context: Context, error: LoadAdError) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val network = cm.activeNetwork ?: return
            val caps = cm.getNetworkCapabilities(network) ?: return
            val isOnline = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

            // If online but ad requests fail with network/internal error, it's indicative of ad-blocking DNS
            if (isOnline && (error.code == AdRequest.ERROR_CODE_NETWORK_ERROR || error.code == AdRequest.ERROR_CODE_INTERNAL_ERROR)) {
                Log.w(TAG, "Ad request dropped with active internet: Ad-blocker detected (${error.message})")
                _isAdBlockerDetected.value = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking ad-blocker condition", e)
        }
    }

    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            MobileAds.initialize(context) { status ->
                isInitialized = true
                Log.d(TAG, "Google Mobile Ads initialized successfully: $status")
                // Preload an interstitial for free users
                preloadInterstitial(context)
            }
        } catch (e: Exception) {
            Log.w(TAG, "AdMob initialization skipped or failed (offline/non-GMS device)", e)
        }
    }

    fun preloadInterstitial(context: Context) {
        if (interstitialAd != null || isAdLoading) return
        isAdLoading = true

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            TEST_INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isAdLoading = false
                    Log.d(TAG, "Interstitial ad loaded and ready for free tier export")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isAdLoading = false
                    Log.d(TAG, "Interstitial ad failed to load: ${error.message}")
                    checkAdBlocker(context, error)
                }
            }
        )
    }

    fun showPostExportInterstitial(
        activity: Activity,
        isProUser: Boolean,
        onComplete: () -> Unit
    ) {
        // Pro users have 100% ad-free experience
        if (isProUser) {
            onComplete()
            return
        }

        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    preloadInterstitial(activity)
                    onComplete()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.w(TAG, "Interstitial ad failed to show: ${error.message}")
                    interstitialAd = null
                    preloadInterstitial(activity)
                    onComplete()
                }
            }
            ad.show(activity)
        } else {
            // If ad not loaded or offline, proceed immediately so user workflow is never blocked
            preloadInterstitial(activity)
            onComplete()
        }
    }
}

/**
 * Adaptive Banner Ad composable for the Free Tier.
 * Completely hidden when the user is JumpCut PRO.
 */
@Composable
fun BannerAdComposable(
    isProUser: Boolean,
    modifier: Modifier = Modifier
) {
    if (isProUser) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(top = 4.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SPONSOR",
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextMuted,
                letterSpacing = 1.2.sp
            )
            Text(
                text = "FREE TIER SUPPORT",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 0.6.sp
            )
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CardDarkElevated)
                .border(1.dp, CardBorderSubtle, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                factory = { context ->
                    AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = AdManager.TEST_BANNER_AD_UNIT_ID
                        adListener = object : com.google.android.gms.ads.AdListener() {
                            override fun onAdFailedToLoad(error: LoadAdError) {
                                AdManager.checkAdBlocker(context, error)
                            }
                        }
                        try {
                            loadAd(AdRequest.Builder().build())
                        } catch (e: Exception) {
                            Log.w("BannerAdComposable", "Ad load error", e)
                        }
                    }
                }
            )
        }
    }
}

/**
 * Strategy D: Gentle creator-friendly Ad-Blocker awareness notice.
 */
@Composable
fun AdBlockerNoticeCard(
    onUpgradePro: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, SilenceRed.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Shield",
                    tint = SilenceRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ad-Blocker Active",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "JumpCut is a 100% free tool supported by lightweight ads. Please whitelist JumpCut or upgrade to Pro for an ad-free experience with unlimited exports.",
                fontSize = 11.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onUpgradePro,
                modifier = Modifier.fillMaxWidth().height(36.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "Upgrade to Pro (Ad-Free & Unlimited)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BgDark
                )
            }
        }
    }
}
