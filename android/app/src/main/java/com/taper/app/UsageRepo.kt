package com.taper.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.taper.app.Prefs.tracked
import java.time.LocalDate
import java.time.ZoneId

/** Reads real per-app foreground time from UsageStatsManager. */
object UsageRepo {

    // MOVE_TO_FOREGROUND / MOVE_TO_BACKGROUND — stable since API 21, unlike the
    // ACTIVITY_RESUMED constants which only exist from API 29.
    private const val EV_FOREGROUND = 1
    private const val EV_BACKGROUND = 2

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

    /**
     * Minutes spent in tracked apps by hour of day, averaged over [days] of history.
     * Built from foreground/background events so a run that straddles midnight lands
     * in the right hours. Index 0..23; hours 0-5 read as the night tail of the day
     * before, which is what makes late-night scrolling visible.
     */
    fun hourlyProfile(c: Context, days: Long = 14): FloatArray {
        val out = FloatArray(24)
        if (!hasPermission(c)) return out
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = startOfDayMillis(days)
        val now = System.currentTimeMillis()
        val trackedSet = c.tracked
        val events = usm.queryEvents(start, now) ?: return out
        val openedAt = HashMap<String, Long>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            if (pkg !in trackedSet) continue
            when (e.eventType) {
                EV_FOREGROUND -> openedAt[pkg] = e.timeStamp
                EV_BACKGROUND -> {
                    val from = openedAt.remove(pkg) ?: continue
                    spread(out, from, e.timeStamp)
                }
            }
        }
        val observed = ((now - start) / 86_400_000L).coerceAtLeast(1L).toFloat()
        for (i in out.indices) out[i] = out[i] / observed
        return out
    }

    /** Adds a run's minutes into the hours it actually covered. */
    private fun spread(into: FloatArray, fromMs: Long, toMs: Long) {
        if (toMs <= fromMs) return
        var cursor = fromMs
        while (cursor < toMs) {
            val zoned = java.time.Instant.ofEpochMilli(cursor).atZone(ZoneId.systemDefault())
            val hourEnd = zoned.truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                .plusHours(1).toInstant().toEpochMilli()
            val slice = minOf(hourEnd, toMs) - cursor
            into[zoned.hour] += slice / 60_000f
            cursor = minOf(hourEnd, toMs)
        }
    }

    fun isInstalled(c: Context, pkg: String): Boolean =
        try {
            c.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: Exception) {
            false
        }
}
