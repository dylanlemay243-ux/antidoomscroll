package com.taper.app

import android.content.Context
import com.taper.app.Prefs.baselineMinutes
import com.taper.app.Prefs.baselinePendingUntil
import com.taper.app.Prefs.floorMinutes
import com.taper.app.Prefs.hardBlock
import com.taper.app.Prefs.pace
import com.taper.app.Prefs.startEpochDay
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/** How firmly the pause screen behaves this week. */
enum class Stage { OPEN, SHORT_WAIT, WAIT, FIRM, CLOSED }

/**
 * What it costs to go in anyway. The point of the taper is that this erodes: at first
 * you can wave the screen away instantly, and by the end you can't get past it at all.
 */
enum class Gate { FREE, WAIT, LOCKED }

/**
 * The taper: a baseline, a weekly reduction, and a pause screen that hardens with it.
 *
 * One axis only — minutes used today against this week's target. No time-of-day
 * windows, no scheduling to keep straight.
 */
object TaperPlan {

    /** Below this the geometric decay hands over to a linear walk down to zero. */
    private const val TAIL = 20

    /** Returned by [targetMinutes] when there is no baseline yet. */
    const val NO_TARGET = -1

    fun baseline(c: Context): Int = c.baselineMinutes

    fun measuring(c: Context): Boolean =
        c.baselineMinutes <= 0 && c.baselinePendingUntil >= LocalDate.now().toEpochDay()

    /** Days left in a running baseline measurement. */
    fun measureDaysLeft(c: Context): Int =
        (c.baselinePendingUntil - LocalDate.now().toEpochDay()).toInt().coerceAtLeast(0)

    /** True once there is a real target to compare against. */
    fun hasTarget(c: Context): Boolean = c.baselineMinutes > 0 && !measuring(c)

    /**
     * Closes out a finished measurement: averages what was recorded and starts
     * the taper. Safe to call on every resume.
     */
    fun finishMeasurementIfDue(c: Context) {
        val until = c.baselinePendingUntil
        if (c.baselineMinutes > 0 || until < 0) return
        if (LocalDate.now().toEpochDay() < until) return
        val avg = historyAverage(c, 3)
        if (avg <= 0) return
        c.baselineMinutes = avg
        c.baselinePendingUntil = -1L
        c.startEpochDay = LocalDate.now().toEpochDay()
    }

    /**
     * The average of complete recorded days. Today is never in it — a day still
     * running would drag the mean down in the morning and up at night. One canonical
     * number: onboarding, the Settings readout, Insights and the auto-seed all call
     * this, so they can't disagree.
     *
     * Returns 0 until there are at least [minDays] complete days.
     */
    fun historyAverage(c: Context, minDays: Int = 3, window: Int = 7): Int {
        val days = History.completeDays(c, window)
        if (days.size < minDays) return 0
        return days.average().roundToInt()
    }

    fun historyDayCount(c: Context, window: Int = 7): Int = History.completeDays(c, window).size

    /**
     * Sets the baseline from history when a measurement isn't already running and the
     * user hasn't asked to measure. Deliberately conservative: it will not overwrite a
     * baseline, and it will not fire while a measurement window is open.
     */
    fun seedBaselineFromHistory(c: Context, minDays: Int = 7): Boolean {
        if (c.baselineMinutes > 0) return false
        if (c.baselinePendingUntil >= LocalDate.now().toEpochDay()) return false
        val avg = historyAverage(c, minDays)
        if (avg <= 0) return false
        c.baselineMinutes = avg
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

    /**
     * This week's ceiling, in minutes. [NO_TARGET] when there's no baseline yet — a
     * target of 0 is a real, reachable value, so it can't double as "unknown".
     *
     * The decay is a fixed percentage a week until it reaches [TAIL], then a straight
     * five minutes a week down to zero, because a percentage of a small number stops
     * meaning anything. A floor above zero stops the walk early.
     */
    fun targetMinutes(c: Context, week: Int = weekNumber(c)): Int {
        val base = c.baselineMinutes
        if (base <= 0) return NO_TARGET
        val floor = c.floorMinutes
        val geo = geometric(base, c.pace, week)
        if (floor > 0) return max(floor, geo)
        if (geo >= TAIL) return geo
        val steps = (week - tailStartWeek(base, c.pace)).coerceAtLeast(0)
        return (TAIL - steps * 5).coerceAtLeast(0)
    }

    private fun geometric(base: Int, pace: Float, week: Int): Int =
        (base * (1f - pace).toDouble().pow((week - 1).toDouble())).roundToInt()

    /** The first week the geometric decay drops under [TAIL]. */
    private fun tailStartWeek(base: Int, pace: Float): Int {
        var w = 1
        while (w < 400) {
            if (geometric(base, pace, w) < TAIL) return w
            w++
        }
        return w
    }

    /** The week the target first reaches zero, or null if a floor stops it. */
    fun weeksToZero(c: Context): Int? {
        if (c.baselineMinutes <= 0 || c.floorMinutes > 0) return null
        return tailStartWeek(c.baselineMinutes, c.pace) + TAIL / 5
    }

    /** The target that applied on a past day — needed for streaks. */
    fun targetForDay(c: Context, epochDay: Long): Int =
        targetMinutes(c, weekNumberForDay(c, epochDay))

    fun stage(week: Int): Stage = when {
        week <= 2 -> Stage.OPEN
        week <= 4 -> Stage.SHORT_WAIT
        week <= 6 -> Stage.WAIT
        week <= 8 -> Stage.FIRM
        else -> Stage.CLOSED
    }

    fun stageLabel(s: Stage): String = when (s) {
        Stage.OPEN -> "Skip straight through"
        Stage.SHORT_WAIT -> "Five-second wait"
        Stage.WAIT -> "Ten-second wait"
        Stage.FIRM -> "Locked past target"
        Stage.CLOSED -> "Locked past target"
    }

    /** What the stage costs, spelled out for the Ladder. */
    fun stageDetail(s: Stage): String = when (s) {
        Stage.OPEN -> "Screen appears, skip button live immediately"
        Stage.SHORT_WAIT -> "5s under target · 15s once you're past it"
        Stage.WAIT -> "10s under target · 30s once you're past it"
        Stage.FIRM -> "15s under target · no way in once you're past it"
        Stage.CLOSED -> "20s under target · no way in once you're past it"
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
        week <= 4 -> 22
        week <= 6 -> 15
        else -> 10
    }

    /** Seconds the cooldown gap holds you out for. Grows as the limit shrinks. */
    fun cooldownSeconds(week: Int): Int = when {
        week <= 2 -> 20
        week <= 4 -> 45
        week <= 6 -> 90
        else -> 150
    }

    /** Seconds before the skip button lights up. */
    fun waitSeconds(week: Int, over: Boolean): Int = when (stage(week)) {
        Stage.OPEN -> if (over) 5 else 0
        Stage.SHORT_WAIT -> if (over) 15 else 5
        Stage.WAIT -> if (over) 30 else 10
        Stage.FIRM -> if (over) 0 else 15
        Stage.CLOSED -> if (over) 0 else 20
    }

    /** The cost of continuing. Weeks 1-2 never hold you: the screen only has to be seen. */
    private fun gateFor(c: Context, week: Int, over: Boolean): Gate {
        if (!over) return if (stage(week) == Stage.OPEN) Gate.FREE else Gate.WAIT
        if (c.hardBlock) return Gate.LOCKED
        return when (stage(week)) {
            Stage.OPEN, Stage.SHORT_WAIT, Stage.WAIT -> Gate.WAIT
            Stage.FIRM, Stage.CLOSED -> Gate.LOCKED
        }
    }

    fun verdict(c: Context, usedToday: Int): Verdict {
        // While measuring the baseline, Taper watches and says nothing.
        if (measuring(c)) return Verdict(false, 0, Gate.FREE, "")

        val target = targetMinutes(c)
        if (target == NO_TARGET) return Verdict(false, 0, Gate.FREE, "")

        val week = weekNumber(c)
        val over = usedToday >= target
        val gate = gateFor(c, week, over)

        val reason = when {
            gate == Gate.LOCKED && target == 0 ->
                "Your target is zero minutes a day now. This one is closed."
            gate == Gate.LOCKED ->
                "$usedToday minutes against a target of $target. That's the day — " +
                    "there's no way through this one."
            over ->
                "Past today's target of $target minutes."
            week <= 2 ->
                "$target minutes is today's ceiling. Skip this if you want — for now the " +
                    "screen only has to be seen."
            else ->
                "${max(0, target - usedToday)} minutes left of today's $target."
        }

        return Verdict(true, waitSeconds(week, over), gate, reason, over)
    }

    /** The verdict for a cooldown gap — triggered by run length, not by daily total. */
    fun cooldownVerdict(c: Context, minutesInRun: Int): Verdict {
        val week = weekNumber(c)
        return Verdict(
            show = true,
            delaySeconds = cooldownSeconds(week),
            gate = if (stage(week) == Stage.OPEN) Gate.WAIT else Gate.LOCKED,
            reason = "$minutesInRun minutes without stopping. Take the gap — it's the run " +
                "length that does the damage, not the day's total.",
            cooldown = true
        )
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
    fun ladder(c: Context, weeks: Int = 16): List<Triple<Int, Int, Stage>> =
        (1..weeks).map { w -> Triple(w, targetMinutes(c, w), stage(w)) }
}
