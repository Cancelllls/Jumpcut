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
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

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

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdManager.TEST_BANNER_AD_UNIT_ID
                try {
                    loadAd(AdRequest.Builder().build())
                } catch (e: Exception) {
                    Log.w("BannerAdComposable", "Ad load error", e)
                }
            }
        }
    )
}
