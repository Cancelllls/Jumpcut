package com.cancellls.jumpcut.billing

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Manages daily export quotas for Free Tier users with multi-layer anti-tamper online time validation.
 *
 * Anti-Tamper Security Architecture:
 * 1. Monotonic Hardware Clock Anchoring:
 *    Android's SystemClock.elapsedRealtime() is driven by the CPU hardware timer and ticks
 *    continuously from boot through sleep. Changing system date/time in Android Settings has
 *    ZERO effect on elapsedRealtime().
 * 2. Secure Online Time Synchronization:
 *    Fetches UTC epoch milliseconds from reliable edge endpoints (Google 204, Cloudflare) and
 *    anchors to SystemClock.elapsedRealtime():
 *    trustedEpochMs = anchorNetworkTimeMs + (SystemClock.elapsedRealtime() - anchorElapsedRealtimeMs).
 * 3. Time Rollback Detection:
 *    Maintains a strictly non-decreasing high-water mark timestamp. If system time is wound backwards,
 *    the backwards jump is clamped and flagged as a tamper attempt.
 * 4. Reboot & Offline Resistance:
 *    If the device reboots while quota was exhausted, resetting the quota requires online network
 *    verification, preventing offline date-advance exploits.
 * 5. Universal UTC Date Boundaries:
 *    Calendar days are computed in UTC (yyyy-MM-dd), eliminating timezone switching exploits.
 */
object UsageQuotaManager {
    private const val TAG = "UsageQuotaManager"
    private const val PREFS_NAME = "jumpcut_usage_prefs"

    // Storage Keys
    private const val KEY_LAST_EXPORT_DATE = "last_export_date"
    private const val KEY_DAILY_EXPORT_COUNT = "daily_export_count"
    private const val KEY_ANCHOR_NETWORK_TIME = "anchor_network_time"
    private const val KEY_ANCHOR_ELAPSED_REALTIME = "anchor_elapsed_realtime"
    private const val KEY_LAST_MAX_RECORDED_TIME = "last_max_recorded_time"
    private const val KEY_LAST_SYNC_SUCCESS = "last_sync_success"

    const val FREE_DAILY_EXPORT_LIMIT = 2

    private val _remainingExports = MutableStateFlow(FREE_DAILY_EXPORT_LIMIT)
    val remainingExports: StateFlow<Int> = _remainingExports.asStateFlow()

    private val _isTamperDetected = MutableStateFlow(false)
    val isTamperDetected: StateFlow<Boolean> = _isTamperDetected.asStateFlow()

    @Volatile
    private var isSyncing = false

    /**
     * Asynchronously synchronizes time with trusted network servers in the background.
     */
    fun syncOnlineTimeAsync(context: Context, isPro: Boolean = false, onComplete: (() -> Unit)? = null) {
        if (isPro || isSyncing) return
        isSyncing = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val serverTime = fetchTrustedNetworkEpochMillis()
                if (serverTime != null && serverTime > 1700000000000L) {
                    val currentElapsed = SystemClock.elapsedRealtime()
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putLong(KEY_ANCHOR_NETWORK_TIME, serverTime)
                        .putLong(KEY_ANCHOR_ELAPSED_REALTIME, currentElapsed)
                        .putLong(KEY_LAST_SYNC_SUCCESS, System.currentTimeMillis())
                        .apply()
                    Log.d(TAG, "Successfully anchored online time: $serverTime at elapsed $currentElapsed")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Online time sync skipped/failed: ${e.message}")
            } finally {
                isSyncing = false
                withContext(Dispatchers.Main) {
                    refreshQuota(context, isPro)
                    onComplete?.invoke()
                }
            }
        }
    }

    /**
     * Queries high-availability endpoints for the HTTP Date header.
     */
    private fun fetchTrustedNetworkEpochMillis(): Long? {
        val endpoints = listOf(
            "https://www.google.com/generate_204",
            "https://www.cloudflare.com",
            "https://clients3.google.com/generate_204"
        )

        for (urlStr in endpoints) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlStr)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                val serverDate = connection.date
                if (serverDate > 1700000000000L) {
                    return serverDate
                }
            } catch (e: Exception) {
                // Try next endpoint
            } finally {
                connection?.disconnect()
            }
        }
        return null
    }

    /**
     * Computes the current trusted UTC epoch milliseconds.
     * Uses monotonic elapsedRealtime relative to our last verified network anchor.
     * Falls back to System.currentTimeMillis() with strict anti-rollback clamping.
     */
    fun getTrustedEpochMillis(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val anchorNetTime = prefs.getLong(KEY_ANCHOR_NETWORK_TIME, 0L)
        val anchorElapsed = prefs.getLong(KEY_ANCHOR_ELAPSED_REALTIME, 0L)
        val lastMaxRecorded = prefs.getLong(KEY_LAST_MAX_RECORDED_TIME, 0L)
        val currentElapsed = SystemClock.elapsedRealtime()

        val computedTime: Long

        if (anchorNetTime > 0L && anchorElapsed > 0L && currentElapsed >= anchorElapsed) {
            // Monotonic hardware clock anchor is active (cannot be altered by changing phone date)
            val elapsedDelta = currentElapsed - anchorElapsed
            computedTime = anchorNetTime + elapsedDelta
        } else {
            // Device rebooted (currentElapsed < anchorElapsed) or no anchor established yet
            val sysTime = System.currentTimeMillis()
            if (lastMaxRecorded > 0L && sysTime < lastMaxRecorded - 60_000L) {
                // Clock rollback detected! (Phone time was set backwards)
                _isTamperDetected.value = true
                Log.w(TAG, "Clock rollback detected! SysTime=$sysTime, LastMax=$lastMaxRecorded")
                computedTime = lastMaxRecorded
            } else {
                computedTime = sysTime.coerceAtLeast(lastMaxRecorded)
            }
        }

        // Update high-water mark strictly monotonically
        val finalTime = computedTime.coerceAtLeast(lastMaxRecorded)
        if (finalTime > lastMaxRecorded) {
            prefs.edit().putLong(KEY_LAST_MAX_RECORDED_TIME, finalTime).apply()
        }
        return finalTime
    }

    /**
     * Returns today's UTC calendar date (yyyy-MM-dd) based on trusted epoch time.
     */
    fun getTodayDateString(context: Context): String {
        val trustedMillis = getTrustedEpochMillis(context)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(trustedMillis))
    }

    /**
     * Refreshes the remaining daily quota.
     */
    fun refreshQuota(context: Context, isPro: Boolean) {
        if (isPro) {
            _remainingExports.value = Int.MAX_VALUE
            return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Trigger an online sync in the background if network anchor is older than 1 hour or uninitialized
        val lastSync = prefs.getLong(KEY_LAST_SYNC_SUCCESS, 0L)
        if (System.currentTimeMillis() - lastSync > 3600_000L) {
            syncOnlineTimeAsync(context, isPro)
        }

        val today = getTodayDateString(context)
        val lastDate = prefs.getString(KEY_LAST_EXPORT_DATE, "")

        val anchorElapsed = prefs.getLong(KEY_ANCHOR_ELAPSED_REALTIME, 0L)
        val currentElapsed = SystemClock.elapsedRealtime()
        val isAnchorValid = anchorElapsed > 0L && currentElapsed >= anchorElapsed

        val count = if (lastDate == today) {
            prefs.getInt(KEY_DAILY_EXPORT_COUNT, 0)
        } else {
            // Date changed. If device was offline without valid anchor and quota was exhausted,
            // verify with network before granting new quota
            val previousCount = prefs.getInt(KEY_DAILY_EXPORT_COUNT, 0)
            if (!isAnchorValid && previousCount >= FREE_DAILY_EXPORT_LIMIT) {
                syncOnlineTimeAsync(context, isPro)
                previousCount
            } else {
                0
            }
        }

        _remainingExports.value = (FREE_DAILY_EXPORT_LIMIT - count).coerceAtLeast(0)
    }

    /**
     * Checks whether the user can export.
     */
    fun canExport(context: Context, isPro: Boolean): Boolean {
        if (isPro) return true
        refreshQuota(context, isPro)
        return _remainingExports.value > 0
    }

    /**
     * Records an export event.
     */
    fun recordExport(context: Context, isPro: Boolean) {
        if (isPro) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayDateString(context)
        val lastDate = prefs.getString(KEY_LAST_EXPORT_DATE, "")
        val count = if (lastDate == today) {
            prefs.getInt(KEY_DAILY_EXPORT_COUNT, 0)
        } else {
            0
        }
        val newCount = count + 1
        prefs.edit()
            .putString(KEY_LAST_EXPORT_DATE, today)
            .putInt(KEY_DAILY_EXPORT_COUNT, newCount)
            .apply()
        _remainingExports.value = (FREE_DAILY_EXPORT_LIMIT - newCount).coerceAtLeast(0)

        // Ensure network anchor is refreshed
        syncOnlineTimeAsync(context, isPro)
    }
}
