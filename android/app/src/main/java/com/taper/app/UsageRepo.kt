package com.taper.app

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.taper.app.Prefs.tracked
import java.time.LocalDate
import java.time.ZoneId

/** Reads real per-app foreground time from UsageStatsManager. */
object UsageRepo {

    fun hasPermission(c: Context): Boolean {
        val aom = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = aom.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfDayMillis(daysAgo: Long = 0): Long =
        LocalDate.now().minusDays(daysAgo).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()

    /** Minutes per tracked package for a given day (0 = today). */
    fun minutesByApp(c: Context, daysAgo: Long = 0): Map<String, Int> {
        if (!hasPermission(c)) return emptyMap()
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = startOfDayMillis(daysAgo)
        val end = if (daysAgo == 0L) System.currentTimeMillis() else start + 86_400_000L
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        val trackedSet = c.tracked
        val out = HashMap<String, Int>()
        stats?.forEach { s ->
            if (s.packageName in trackedSet && s.totalTimeInForeground > 0) {
                val mins = (s.totalTimeInForeground / 60_000L).toInt()
                out[s.packageName] = (out[s.packageName] ?: 0) + mins
            }
        }
        return out
    }

    fun totalMinutes(c: Context, daysAgo: Long = 0): Int =
        minutesByApp(c, daysAgo).values.sum()

    /** Today first, then the six days before it. */
    fun lastSevenDays(c: Context): List<Int> = (0L..6L).map { totalMinutes(c, it) }

    fun isInstalled(c: Context, pkg: String): Boolean =
        try {
            c.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: Exception) {
            false
        }
}
