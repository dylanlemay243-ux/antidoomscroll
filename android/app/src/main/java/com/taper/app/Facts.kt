package com.taper.app

import kotlin.math.roundToInt

/**
 * The case for the app, and the arithmetic behind it.
 *
 * Every claim here is attributed. Nothing is rounded up for effect, and nothing is
 * invented — if a number can't be sourced it isn't in this file.
 */
object Facts {

    data class Study(val claim: String, val source: String)

    /** Shown on first run, and again under Insights. */
    val studies: List<Study> = listOf(
        Study(
            "The average internet user spends about two hours and twenty minutes a day " +
                "on social media alone — roughly a full waking day every week.",
            "DataReportal, Digital 2024 Global Overview Report"
        ),
        Study(
            "143 undergraduates were capped at ten minutes per platform per day for three " +
                "weeks. Compared with a control group they reported significant reductions " +
                "in loneliness and depression.",
            "Hunt, Marx, Lipson & Young (2018), Journal of Social and Clinical Psychology"
        ),
        Study(
            "Among 6,595 US adolescents, those using social media more than three hours a " +
                "day had a higher risk of internalising mental health problems — anxiety, " +
                "low mood, withdrawal — even after adjusting for baseline symptoms.",
            "Riehm et al. (2019), JAMA Psychiatry"
        ),
        Study(
            "Having your own phone merely within reach measurably reduced available " +
                "cognitive capacity on attention tasks, even when it was face down and " +
                "switched off, and even when participants believed it made no difference.",
            "Ward, Duke, Gneezy & Bos (2017), Journal of the Association for Consumer Research"
        ),
        Study(
            "After an interruption, people took an average of about 23 minutes to return " +
                "to the task they had left. The scroll is rarely the only cost.",
            "Mark, Gudith & Klocke (2008), CHI '08"
        ),
    )

    /** The one-line framing above the studies. */
    const val PITCH =
        "Doom scrolling isn't a willpower problem. These apps are tuned by teams of " +
            "engineers to hold your attention, and they are extremely good at it. Taper's " +
            "only job is to put a small, growing cost in front of the reflex — and to show " +
            "you the real number, which is usually larger than anyone guesses."

    // ---- Projection ----

    data class Projection(
        val minutesPerDay: Int,
        val hoursPerWeek: Int,
        val daysPerYear: Int,
        val weeksPerYear: Int,
        val monthsPerDecade: Int,
    )

    /** What a daily average costs over a year and a decade. Waking-hours free. */
    fun project(minutesPerDay: Int): Projection {
        val yearMinutes = minutesPerDay.toLong() * 365L
        return Projection(
            minutesPerDay = minutesPerDay,
            hoursPerWeek = (minutesPerDay * 7 / 60.0).roundToInt(),
            daysPerYear = (yearMinutes / 1440.0).roundToInt(),
            weeksPerYear = (yearMinutes / 10080.0).roundToInt(),
            monthsPerDecade = (yearMinutes * 10 / 43_800.0).roundToInt(),
        )
    }

    /** Whole months of waking time, per decade, at this daily average. */
    fun wakingMonthsPerDecade(minutesPerDay: Int): Int =
        (minutesPerDay.toLong() * 3650L / 29_200.0).roundToInt()

    /** Same arithmetic counted in waking hours (16 a day), which is the fairer read. */
    fun wakingDaysPerYear(minutesPerDay: Int): Int =
        (minutesPerDay.toLong() * 365L / 960.0).roundToInt()

    fun hoursSpan(minutes: Long): String {
        val h = minutes / 60
        return if (h < 1000) "${h}h" else "${(h / 1000.0 * 10).roundToInt() / 10.0}k hours"
    }
}
