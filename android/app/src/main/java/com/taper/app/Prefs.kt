package com.taper.app

import android.content.Context
import android.content.Intent
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

    /** The target never drops below this. 0 means the taper runs all the way down. */
    var Context.floorMinutes: Int
        get() = sp(this).getInt("floor", 0)
        set(v) = sp(this).edit().putInt("floor", v).apply()

    /** Skip the gentle weeks: lock out at the target from now on. */
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

    var Context.remindersOn: Boolean
        get() = sp(this).getBoolean("reminders", true)
        set(v) = sp(this).edit().putBoolean("reminders", v).apply()

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

/**
 * App names. The seven usual suspects are named by hand so they read correctly
 * before anything is installed; anything else on the phone resolves through the
 * package manager, so any app can be tracked.
 */
object Catalog {
    val suggested: List<Pair<String, String>> = listOf(
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

    private val labels = HashMap<String, String>()

    fun label(c: Context, pkg: String): String = labels.getOrPut(pkg) {
        suggested.firstOrNull { it.first == pkg }?.second ?: try {
            val pm = c.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    /**
     * Everything on the phone with a launcher entry, alphabetically. Taper itself and
     * apps with no way to open them are left out.
     */
    fun installed(c: Context): List<Pair<String, String>> {
        val pm = c.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return try {
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .distinct()
                .filter { it != c.packageName }
                .map { it to label(c, it) }
                .sortedBy { it.second.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
