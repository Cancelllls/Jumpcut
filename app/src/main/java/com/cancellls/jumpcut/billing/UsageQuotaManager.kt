package com.cancellls.jumpcut.billing

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages daily export quotas for Free Tier users.
 * Limits free exports to 2 per calendar day. Pro users have unlimited exports.
 */
object UsageQuotaManager {
    private const val PREFS_NAME = "jumpcut_usage_prefs"
    private const val KEY_LAST_EXPORT_DATE = "last_export_date"
    private const val KEY_DAILY_EXPORT_COUNT = "daily_export_count"
    const val FREE_DAILY_EXPORT_LIMIT = 2

    private val _remainingExports = MutableStateFlow(FREE_DAILY_EXPORT_LIMIT)
    val remainingExports: StateFlow<Int> = _remainingExports.asStateFlow()

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    fun refreshQuota(context: Context, isPro: Boolean) {
        if (isPro) {
            _remainingExports.value = Int.MAX_VALUE
            return
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayDateString()
        val lastDate = prefs.getString(KEY_LAST_EXPORT_DATE, "")
        val count = if (lastDate == today) {
            prefs.getInt(KEY_DAILY_EXPORT_COUNT, 0)
        } else {
            0
        }
        _remainingExports.value = (FREE_DAILY_EXPORT_LIMIT - count).coerceAtLeast(0)
    }

    fun canExport(context: Context, isPro: Boolean): Boolean {
        if (isPro) return true
        refreshQuota(context, isPro)
        return _remainingExports.value > 0
    }

    fun recordExport(context: Context, isPro: Boolean) {
        if (isPro) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayDateString()
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
    }
}
