package com.taper.app

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/** All persisted state. SharedPreferences is plenty for v1. */
object Prefs {
    private const val FILE = "taper"

    private fun sp(c: Context): SharedPreferences =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Daily minutes measured during the baseline week. 0 = not measured yet. */
    var Context.baselineMinutes: Int
        get() = sp(this).getInt("baseline", 0)
        set(v) = sp(this).edit().putInt("baseline", v).apply()

    /** Epoch day the taper started. -1 = not started. */
    var Context.startEpochDay: Long
        get() = sp(this).getLong("start", -1L)
        set(v) = sp(this).edit().putLong("start", v).apply()

    /**
     * When measuring a fresh baseline instead of using history: the epoch day the
     * measurement completes. -1 = not measuring.
     */
    var Context.baselinePendingUntil: Long
        get() = sp(this).getLong("basePending", -1L)
        set(v) = sp(this).edit().putLong("basePending", v).apply()

    var Context.onboarded: Boolean
        get() = sp(this).getBoolean("onboarded", false)
        set(v) = sp(this).edit().putBoolean("onboarded", v).apply()

    /** Weekly reduction, e.g. 0.08 = 8% a week. */
    var Context.pace: Float
        get() = sp(this).getFloat("pace", 0.08f)
        set(v) = sp(this).edit().putFloat("pace", v).apply()

    /** The target never drops below this. */
    var Context.floorMinutes: Int
        get() = sp(this).getInt("floor", 90)
        set(v) = sp(this).edit().putInt("floor", v).apply()

    var Context.hardBlock: Boolean
        get() = sp(this).getBoolean("hardBlock", false)
        set(v) = sp(this).edit().putBoolean("hardBlock", v).apply()

    /** Package of the tracked app currently in a run, or "" between runs. */
    var Context.sessionPkg: String
        get() = sp(this).getString("sessPkg", "") ?: ""
        set(v) = sp(this).edit().putString("sessPkg", v).apply()

    /** When the current run started, in millis. 0 = no run. */
    var Context.sessionStartedAt: Long
        get() = sp(this).getLong("sessStart", 0L)
        set(v) = sp(this).edit().putLong("sessStart", v).apply()

    /** When the last cooldown was served, so one run triggers one break. */
    var Context.lastCooldownAt: Long
        get() = sp(this).getLong("coolAt", 0L)
        set(v) = sp(this).edit().putLong("coolAt", v).apply()

    /** Let Taper move the wind-down and windows to match observed usage. */
    var Context.autoAdjust: Boolean
        get() = sp(this).getBoolean("autoAdjust", true)
        set(v) = sp(this).edit().putBoolean("autoAdjust", v).apply()

    /** Minute of day after which tracked apps are closed. -1 = none set. */
    var Context.windDownMinute: Int
        get() = sp(this).getInt("windDown", -1)
        set(v) = sp(this).edit().putInt("windDown", v).apply()

    var Context.remindersOn: Boolean
        get() = sp(this).getBoolean("reminders", true)
        set(v) = sp(this).edit().putBoolean("reminders", v).apply()

    /** Minutes of day. Two windows by default: lunch and evening. */
    var Context.windows: List<IntRange>
        get() {
            val raw = sp(this).getString("windows", "760-780,1230-1260")!!
            return raw.split(",").mapNotNull {
                val p = it.split("-")
                if (p.size == 2) p[0].toInt()..p[1].toInt() else null
            }
        }
        set(v) = sp(this).edit()
            .putString("windows", v.joinToString(",") { "${it.first}-${it.last}" })
            .apply()

    var Context.tracked: Set<String>
        get() = sp(this).getStringSet("tracked", Catalog.defaults)!!
        set(v) = sp(this).edit().putStringSet("tracked", v).apply()

    /** Records the last day a "you're near your target" reminder fired. */
    var Context.lastReminderDay: Long
        get() = sp(this).getLong("remDay", -1L)
        set(v) = sp(this).edit().putLong("remDay", v).apply()

    fun Context.ensureStarted() {
        if (startEpochDay < 0) startEpochDay = LocalDate.now().toEpochDay()
    }
}

/** Apps Taper knows how to track. */
object Catalog {
    val all: List<Pair<String, String>> = listOf(
        "com.instagram.android" to "Instagram",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.twitter.android" to "X",
        "com.reddit.frontpage" to "Reddit",
        "com.google.android.youtube" to "YouTube",
        "com.facebook.katana" to "Facebook",
        "com.snapchat.android" to "Snapchat",
    )

    val defaults: MutableSet<String> = mutableSetOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.twitter.android",
    )

    fun label(pkg: String): String = all.firstOrNull { it.first == pkg }?.second ?: pkg
}
