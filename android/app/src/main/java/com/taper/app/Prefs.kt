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

    var Context.remindersOn: Boolean
        get() = sp(this).getBoolean("reminders", true)
        set(v) = sp(this).edit().putBoolean("reminders", v).apply()

    /** Minutes of day. Two windows, each start/end. */
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
