package com.taper.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import com.taper.app.Prefs.tracked

/**
 * Notices when a tracked app comes to the front and, if the plan says so,
 * puts the pause screen in front of it. Reads package names only.
 */
class BlockerService : AccessibilityService() {

    private var lastPkg: String? = null
    private var lastShownAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        if (pkg !in tracked) {
            lastPkg = pkg
            return
        }
        // Same app still in front, or we just showed the screen — leave it alone.
        val now = System.currentTimeMillis()
        if (pkg == lastPkg && now - lastShownAt < COOLDOWN_MS) return
        lastPkg = pkg

        val used = UsageRepo.totalMinutes(this)
        val v = TaperPlan.verdict(this, used)
        if (!v.show) return

        lastShownAt = now
        startActivity(
            Intent(this, PauseActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(PauseActivity.EXTRA_PKG, pkg)
                putExtra(PauseActivity.EXTRA_DELAY, v.delaySeconds)
                putExtra(PauseActivity.EXTRA_ALLOW, v.allowContinue)
                putExtra(PauseActivity.EXTRA_REASON, v.reason)
                putExtra(PauseActivity.EXTRA_USED, used)
            }
        )
    }

    override fun onInterrupt() {}

    companion object {
        private const val COOLDOWN_MS = 45_000L

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
