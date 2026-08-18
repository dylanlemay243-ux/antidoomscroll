package com.taper.app

import android.content.Context
import com.taper.app.Prefs.baselineMinutes
import com.taper.app.Prefs.floorMinutes
import com.taper.app.Prefs.hardBlock
import com.taper.app.Prefs.pace
import com.taper.app.Prefs.startEpochDay
import com.taper.app.Prefs.windows
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

enum class Stage { REMINDERS, PAUSE, SOFT_WINDOWS, WINDOWS_ONLY }

/** The taper: a baseline, a weekly reduction, and an intervention that escalates with it. */
object TaperPlan {

    fun weekNumber(c: Context): Int {
        val start = c.startEpochDay
        if (start < 0) return 1
        val days = LocalDate.now().toEpochDay() - start
        return (days / 7 + 1).toInt().coerceAtLeast(1)
    }

    fun targetMinutes(c: Context, week: Int = weekNumber(c)): Int {
        val base = c.baselineMinutes
        if (base <= 0) return 0
        val reduced = base * (1f - c.pace).toDouble().pow((week - 1).toDouble())
        return max(c.floorMinutes, reduced.roundToInt())
    }

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

    /** How the pause screen should behave for the current app launch. */
    data class Verdict(val show: Boolean, val delaySeconds: Int, val allowContinue: Boolean, val reason: String)

    fun verdict(c: Context, usedToday: Int): Verdict {
        val target = targetMinutes(c)
        val stage = stage(weekNumber(c))
        val inWindow = insideWindow(c)
        val over = target > 0 && usedToday >= target

        return when (stage) {
            Stage.REMINDERS ->
                if (over) Verdict(true, 5, true, "You're past today's target of $target minutes.")
                else Verdict(false, 0, true, "")

            Stage.PAUSE ->
                Verdict(true, if (over) 20 else 15, true,
                    if (over) "Past today's target of $target minutes." else "A short pause before you go in.")

            Stage.SOFT_WINDOWS ->
                if (inWindow && !over) Verdict(true, 5, true, "You're inside a scroll window.")
                else Verdict(true, 20, true,
                    if (over) "Past today's target of $target minutes." else "Outside your scroll windows.")

            Stage.WINDOWS_ONLY ->
                if (inWindow && !over) Verdict(false, 0, true, "")
                else Verdict(true, 20, !c.hardBlock,
                    if (over) "Past today's target of $target minutes." else "Outside your scroll windows.")
        }
    }

    /** Week-by-week plan, for the Ladder screen. */
    fun ladder(c: Context, weeks: Int = 12): List<Triple<Int, Int, Stage>> =
        (1..weeks).map { w -> Triple(w, targetMinutes(c, w), stage(w)) }
}
