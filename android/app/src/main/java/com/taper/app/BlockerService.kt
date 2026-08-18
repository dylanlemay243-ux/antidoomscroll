package com.taper.app

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import com.taper.app.Prefs.lastCooldownAt
import com.taper.app.Prefs.sessionPkg
import com.taper.app.Prefs.sessionStartedAt
import com.taper.app.Prefs.tracked

/**
 * Notices when a tracked app is in front and, if the plan says so, puts the pause
 * screen over it. Reads package names only.
 *
 * Two different interruptions come from here. The launch pause asks about the day's
 * total. The cooldown gap asks about this run: unbroken scrolling is what actually
 * eats an evening, and a daily total can't see it. Content-changed events act as a
 * heartbeat so a long run inside one screen still gets noticed.
 */
class BlockerService : AccessibilityService() {

    private var lastPkg: String? = null
    private var lastShownAt = 0L
    private var lastHeartbeat = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val now = System.currentTimeMillis()
            if (now - lastHeartbeat < HEARTBEAT_MS) return
            lastHeartbeat = now
            if (pkg in tracked) checkRunLength(pkg, now)
            return
        }
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        if (pkg !in tracked) {
            // Left for something else — the run is over.
            if (sessionPkg.isNotEmpty()) endRun()
            lastPkg = pkg
            return
        }

        val now = System.currentTimeMillis()
        if (sessionPkg != pkg) startRun(pkg, now)

        // Same app still in front, or we just showed the screen — leave it alone.
        if (pkg == lastPkg && now - lastShownAt < COOLDOWN_MS) return
        lastPkg = pkg

        val used = UsageRepo.totalMinutes(this)
        val v = TaperPlan.verdict(this, used)
        if (v.gate == Gate.AD) Ads.preload(this)
        if (!v.show) return

        lastShownAt = now
        show(pkg, v, used)
    }

    private fun startRun(pkg: String, now: Long) {
        sessionPkg = pkg
        sessionStartedAt = now
        lastCooldownAt = 0L
    }

    private fun endRun() {
        sessionPkg = ""
        sessionStartedAt = 0L
        lastCooldownAt = 0L
    }

    /** Imposes the gap once a run passes the week's limit, then again each limit after. */
    private fun checkRunLength(pkg: String, now: Long) {
        val started = sessionStartedAt
        if (started <= 0L || sessionPkg != pkg) {
            startRun(pkg, now)
            return
        }
        if (TaperPlan.measuring(this)) return
        val limit = TaperPlan.sessionLimitMinutes(this)
        val since = ((now - maxOf(started, lastCooldownAt)) / 60_000L).toInt()
        if (since < limit) return

        lastCooldownAt = now
        lastShownAt = now
        val runMinutes = ((now - started) / 60_000L).toInt()
        val v = TaperPlan.cooldownVerdict(this, runMinutes)
        if (v.gate == Gate.AD) Ads.preload(this)
        show(pkg, v, UsageRepo.totalMinutes(this))
    }

    private fun show(pkg: String, v: TaperPlan.Verdict, used: Int) {
        startActivity(
            Intent(this, PauseActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(PauseActivity.EXTRA_PKG, pkg)
                putExtra(PauseActivity.EXTRA_DELAY, v.delaySeconds)
                putExtra(PauseActivity.EXTRA_GATE, v.gate.name)
                putExtra(PauseActivity.EXTRA_REASON, v.reason)
                putExtra(PauseActivity.EXTRA_USED, used)
                putExtra(PauseActivity.EXTRA_COOLDOWN, v.cooldown)
            }
        )
    }

    override fun onInterrupt() {}

    companion object {
        private const val COOLDOWN_MS = 45_000L
        private const val HEARTBEAT_MS = 15_000L

        fun isEnabled(c: Context): Boolean {
            val expected = ComponentName(c, BlockerService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(
                c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return TextUtils.SimpleStringSplitter(':').let { s ->
                s.setString(enabled)
                s.any { it.equals(expected, ignoreCase = true) }
            }
        }
    }
}
