package com.taper.app

import android.content.Context
import com.taper.app.Prefs.baselineMinutes
import com.taper.app.Prefs.baselinePendingUntil
import com.taper.app.Prefs.floorMinutes
import com.taper.app.Prefs.hardBlock
import com.taper.app.Prefs.autoAdjust
import com.taper.app.Prefs.windDownMinute
import com.taper.app.Prefs.pace
import com.taper.app.Prefs.startEpochDay
import com.taper.app.Prefs.windows
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

enum class Stage { REMINDERS, PAUSE, SOFT_WINDOWS, WINDOWS_ONLY }

/**
 * What it costs to go in anyway. The point of the taper is that this erodes: at first
 * you can always wave the screen away, and by the end you can't.
 */
enum class Gate { FREE, WAIT, AD, LOCKED }

/** The taper: a baseline, a weekly reduction, and an intervention that escalates with it. */
object TaperPlan {

    fun baseline(c: Context): Int = c.baselineMinutes

    fun measuring(c: Context): Boolean =
        c.baselineMinutes <= 0 && c.baselinePendingUntil >= LocalDate.now().toEpochDay()

    /** Days left in a running baseline measurement. */
    fun measureDaysLeft(c: Context): Int =
        (c.baselinePendingUntil - LocalDate.now().toEpochDay()).toInt().coerceAtLeast(0)

    /**
     * Closes out a finished measurement: averages what was recorded and starts
     * the taper. Safe to call on every resume.
     */
    fun finishMeasurementIfDue(c: Context) {
        val until = c.baselinePendingUntil
        if (c.baselineMinutes > 0 || until < 0) return
        if (LocalDate.now().toEpochDay() < until) return
        val days = History.all(c).map { it.second }.filter { it > 0 }
        if (days.isEmpty()) return
        c.baselineMinutes = days.average().roundToInt()
        c.baselinePendingUntil = -1L
        c.startEpochDay = LocalDate.now().toEpochDay()
    }

    /**
     * Sets the baseline straight from history Android already had, so the taper starts
     * on day one instead of after a week of watching. Needs at least three recorded
     * days to be worth trusting. Does nothing once a baseline exists.
     *
     * This is why there's no "ease in" setting: week 1 is already gentle — every gate
     * is skippable — and the ramp does the rest on its own.
     */
    fun seedBaselineFromHistory(c: Context): Boolean {
        if (c.baselineMinutes > 0) return false
        val days = History.all(c).map { it.second }.filter { it > 0 }
        if (days.size < 3) return false
        c.baselineMinutes = days.takeLast(14).average().roundToInt()
        c.baselinePendingUntil = -1L
        c.startEpochDay = LocalDate.now().toEpochDay()
        return true
    }

    fun weekNumber(c: Context): Int = weekNumberForDay(c, LocalDate.now().toEpochDay())

    fun weekNumberForDay(c: Context, epochDay: Long): Int {
        val start = c.startEpochDay
        if (start < 0) return 1
        return ((epochDay - start) / 7 + 1).toInt().coerceAtLeast(1)
    }

    fun targetMinutes(c: Context, week: Int = weekNumber(c)): Int {
        val base = c.baselineMinutes
        if (base <= 0) return 0
        val reduced = base * (1f - c.pace).toDouble().pow((week - 1).toDouble())
        return max(c.floorMinutes, reduced.roundToInt())
    }

    /** The target that applied on a past day — needed for streaks. */
    fun targetForDay(c: Context, epochDay: Long): Int =
        targetMinutes(c, weekNumberForDay(c, epochDay))

    fun stage(week: Int): Stage = when {
        week <= 2 -> Stage.REMINDERS
        week <= 5 -> Stage.PAUSE
        week <= 7 -> Stage.SOFT_WINDOWS
        else -> Stage.WINDOWS_ONLY
    }

    fun stageLabel(s: Stage): String = when (s) {
        Stage.REMINDERS -> "Reminders only"
        Stage.PAUSE -> "15-second pause"
        Stage.SOFT_WINDOWS -> "Soft windows"
        Stage.WINDOWS_ONLY -> "Windows only"
    }

    fun insideWindow(c: Context, now: LocalTime = LocalTime.now()): Boolean {
        val m = now.hour * 60 + now.minute
        return c.windows.any { m in it }
    }

    /** Minutes until the next window opens, or null if one is open now. */
    fun minutesToNextWindow(c: Context, now: LocalTime = LocalTime.now()): Int? {
        val m = now.hour * 60 + now.minute
        if (insideWindow(c, now)) return null
        val starts = c.windows.map { it.first }.sorted()
        val next = starts.firstOrNull { it > m } ?: (starts.firstOrNull()?.plus(1440))
        return next?.minus(m)
    }

    /** How the pause screen should behave for the current app launch. */
    data class Verdict(
        val show: Boolean,
        val delaySeconds: Int,
        val gate: Gate,
        val reason: String,
        val overTarget: Boolean = false,
        val cooldown: Boolean = false,
    )

    /**
     * Minutes of unbroken scrolling before a cooldown gap is imposed. Tapers with the
     * plan: half an hour at the start, ten minutes once the ladder is up.
     */
    fun sessionLimitMinutes(c: Context, week: Int = weekNumber(c)): Int = when {
        week <= 2 -> 30
        week <= 5 -> 22
        week <= 7 -> 15
        else -> 10
    }

    /** Seconds the cooldown gap holds you out for. Grows as the limit shrinks. */
    fun cooldownSeconds(week: Int): Int = when {
        week <= 2 -> 20
        week <= 5 -> 45
        week <= 7 -> 90
        else -> 150
    }

    /**
     * The cost of continuing, given the week and how far past the line you are.
     * Weeks 1-2 never lock — the early weeks are for noticing, not for fighting.
     */
    private fun gateFor(c: Context, week: Int, over: Boolean, outsideWindow: Boolean): Gate = when {
        week <= 2 -> if (over) Gate.WAIT else Gate.FREE
        week <= 5 -> if (over) Gate.AD else Gate.WAIT
        week <= 7 -> if (over || outsideWindow) Gate.AD else Gate.WAIT
        else -> if (over || outsideWindow) {
            if (c.hardBlock) Gate.LOCKED else Gate.AD
        } else Gate.WAIT
    }

    fun verdict(c: Context, usedToday: Int): Verdict {
        // While measuring the baseline, Taper watches and says nothing.
        if (measuring(c)) return Verdict(false, 0, Gate.FREE, "")

        val target = targetMinutes(c)
        val week = weekNumber(c)
        val stage = stage(week)
        val inWindow = insideWindow(c)
        val over = target > 0 && usedToday >= target
        val pastWindDown = pastWindDown(c)

        // A learned wind-down outranks the stage: if this is the hour you always
        // lose, the screen shows up from week one.
        if (pastWindDown) {
            return Verdict(
                true, 20,
                gateFor(c, max(week, 3), true, true),
                "Past your wind-down at ${hhmm(c.windDownMinute)}. This is the hour that " +
                    "usually runs long.",
                true
            )
        }

        return when (stage) {
            Stage.REMINDERS ->
                if (over) Verdict(true, 5, gateFor(c, week, true, false),
                    "You're past today's target of $target minutes.", true)
                else Verdict(false, 0, Gate.FREE, "")

            Stage.PAUSE ->
                Verdict(true, if (over) 20 else 15, gateFor(c, week, over, false),
                    if (over) "Past today's target of $target minutes." else "A short pause before you go in.",
                    over)

            Stage.SOFT_WINDOWS ->
                if (inWindow && !over) Verdict(true, 5, gateFor(c, week, false, false),
                    "You're inside a scroll window.")
                else Verdict(true, 20, gateFor(c, week, over, !inWindow),
                    if (over) "Past today's target of $target minutes." else "Outside your scroll windows.",
                    over)

            Stage.WINDOWS_ONLY ->
                if (inWindow && !over) Verdict(false, 0, Gate.FREE, "")
                else Verdict(true, 20, gateFor(c, week, over, !inWindow),
                    if (over) "Past today's target of $target minutes." else "Outside your scroll windows.",
                    over)
        }
    }

    /** The verdict for a cooldown gap — triggered by run length, not by daily total. */
    fun cooldownVerdict(c: Context, minutesInRun: Int): Verdict {
        val week = weekNumber(c)
        return Verdict(
            show = true,
            delaySeconds = cooldownSeconds(week),
            gate = if (week <= 2) Gate.WAIT else if (week <= 7) Gate.AD
            else if (c.hardBlock) Gate.LOCKED else Gate.AD,
            reason = "$minutesInRun minutes without stopping. Take the gap — it's the run " +
                "length that does the damage, not the day's total.",
            cooldown = true
        )
    }

    // ---- Learned cut-off ----

    /**
     * The hour your scrolling reliably runs into. Reads the evening/night tail of the
     * hourly profile and returns the minute of day where it starts climbing, rounded
     * to the half hour. Null when there isn't a clear late pattern.
     */
    fun suggestedWindDown(c: Context): Int? {
        val profile = UsageRepo.hourlyProfile(c)
        val total = profile.sum()
        if (total < 20f) return null
        // Evening onward, wrapping past midnight: 18:00 .. 03:00.
        val lateHours = (18..26).map { it % 24 }
        val lateTotal = lateHours.sumOf { profile[it].toDouble() }
        if (lateTotal < total * 0.25) return null
        // The first late hour that carries a real share, and holds it.
        val threshold = total / 24f * 1.4f
        val startHour = lateHours.firstOrNull { h ->
            val i = lateHours.indexOf(h)
            profile[h] >= threshold &&
                (i + 1 >= lateHours.size || profile[lateHours[i + 1]] >= threshold * 0.6f)
        } ?: return null
        // Give back half an hour so it lands before the peak, not on it.
        val minute = startHour * 60 + 30
        return if (minute >= 1440) minute - 1440 else minute
    }

    fun pastWindDown(c: Context, now: LocalTime = LocalTime.now()): Boolean {
        val cut = c.windDownMinute
        if (cut < 0) return false
        val m = now.hour * 60 + now.minute
        // A cut-off in the evening runs until 05:00, so 00:30 still counts as "late".
        return if (cut >= 18 * 60) m >= cut || m < 5 * 60 else m >= cut
    }

    /** Adopts the learned cut-off when auto-adjust is on. Safe to call on resume. */
    fun applyAutoAdjust(c: Context) {
        if (!c.autoAdjust) return
        val s = suggestedWindDown(c) ?: return
        if (c.windDownMinute != s) c.windDownMinute = s
    }

    fun hhmm(minute: Int): String {
        if (minute < 0) return "—"
        val h = (minute / 60) % 24
        val m = minute % 60
        val ampm = if (h < 12) "am" else "pm"
        val h12 = if (h % 12 == 0) 12 else h % 12
        return if (m == 0) "$h12$ampm" else String.format("%d:%02d%s", h12, m, ampm)
    }

    /** Week-by-week plan, for the Ladder screen. */
    fun ladder(c: Context, weeks: Int = 12): List<Triple<Int, Int, Stage>> =
        (1..weeks).map { w -> Triple(w, targetMinutes(c, w), stage(w)) }
}
