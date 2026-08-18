package com.taper.app

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate

/**
 * Local daily history. Android's own usage stats expire (~28 days) and can't
 * answer "how many weeks did I meet target", so Taper keeps its own record.
 * On-device only, no network.
 */
object History {
    private const val FILE = "taper_history"
    private const val KEY = "days"
    private const val KEEP_DAYS = 400

    private fun read(c: Context): JSONObject =
        JSONObject(
            c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "{}") ?: "{}"
        )

    private fun write(c: Context, o: JSONObject) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, o.toString()).apply()

    /** Records (or overwrites) one day's total. Called hourly and on app resume. */
    fun record(c: Context, epochDay: Long, totalMinutes: Int) {
        val o = read(c)
        o.put(epochDay.toString(), totalMinutes)
        val cutoff = LocalDate.now().toEpochDay() - KEEP_DAYS
        val stale = o.keys().asSequence().filter { (it.toLongOrNull() ?: 0L) < cutoff }.toList()
        stale.forEach { o.remove(it) }
        write(c, o)
    }

    /**
     * Copies the days Android still remembers into local history, so Insights,
     * streaks and minutes-saved have real content from the first launch instead of
     * filling in over a week. Never overwrites a day already recorded.
     */
    fun backfill(c: Context, days: Long = 27) {
        if (!UsageRepo.hasPermission(c)) return
        val today = LocalDate.now().toEpochDay()
        val o = read(c)
        var wrote = false
        for (back in 1L..days) {
            val key = (today - back).toString()
            if (o.has(key)) continue
            val mins = UsageRepo.totalMinutes(c, back)
            if (mins <= 0) continue
            o.put(key, mins)
            wrote = true
        }
        if (wrote) write(c, o)
    }

    /**
     * Complete days only, newest last. Today is deliberately excluded: it's a partial
     * day, so averaging it in makes the baseline drift down in the morning and up at
     * night. Every baseline calculation goes through here so they all agree.
     */
    fun completeDays(c: Context, limit: Int = 28): List<Int> {
        val today = LocalDate.now().toEpochDay()
        return all(c)
            .filter { it.first != today && it.second > 0 }
            .sortedBy { it.first }
            .map { it.second }
            .takeLast(limit)
    }

    fun minutesOn(c: Context, epochDay: Long): Int? {
        val o = read(c)
        val k = epochDay.toString()
        return if (o.has(k)) o.optInt(k) else null
    }

    /** Every recorded day, newest first. */
    fun all(c: Context): List<Pair<Long, Int>> {
        val o = read(c)
        return o.keys().asSequence()
            .mapNotNull { k -> k.toLongOrNull()?.let { it to o.optInt(k) } }
            .sortedByDescending { it.first }
            .toList()
    }

    /** Consecutive days up to yesterday that came in at or under that day's target. */
    fun streak(c: Context): Int {
        var day = LocalDate.now().toEpochDay() - 1
        var n = 0
        while (true) {
            val mins = minutesOn(c, day) ?: break
            val target = TaperPlan.targetForDay(c, day)
            if (target <= 0 || mins > target) break
            n++
            day--
        }
        return n
    }

    /** Days recorded in the current calendar week that went over target. */
    fun overDaysThisWeek(c: Context): Int {
        val today = LocalDate.now().toEpochDay()
        return (0L..6L).count { back ->
            val day = today - back
            val mins = minutesOn(c, day) ?: return@count false
            val target = TaperPlan.targetForDay(c, day)
            target > 0 && mins > target
        }
    }

    /** Minutes saved against baseline across everything recorded. */
    fun minutesSaved(c: Context): Int {
        val base = TaperPlan.baseline(c)
        if (base <= 0) return 0
        return all(c).sumOf { (_, mins) -> (base - mins).coerceAtLeast(0) }
    }
}
