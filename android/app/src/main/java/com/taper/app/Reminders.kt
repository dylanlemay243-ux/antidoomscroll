package com.taper.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.taper.app.Prefs.lastReminderDay
import com.taper.app.Prefs.remindersOn
import java.time.LocalDate

/** Hourly check: if you're near today's target, say so once. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Reminders.schedule(context)
        if (!context.remindersOn) return

        val target = TaperPlan.targetMinutes(context)
        if (target <= 0) return
        val used = UsageRepo.totalMinutes(context)
        val today = LocalDate.now().toEpochDay()

        if (used >= target * 0.75 && context.lastReminderDay != today) {
            context.lastReminderDay = today
            Reminders.notify(
                context,
                if (used >= target) "You're past today's target" else "You're near today's target",
                "$used of $target minutes. ${if (used >= target) "Tomorrow resets." else "About ${target - used} left."}"
            )
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = Reminders.schedule(context)
}

object Reminders {
    private const val CHANNEL = "taper.targets"
    private const val REQUEST = 4201

    fun schedule(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            c, REQUEST, Intent(c, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + 30 * 60_000L,
            AlarmManager.INTERVAL_HOUR,
            pi
        )
    }

    fun notify(c: Context, title: String, body: String) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Target reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(1, n)
        } catch (e: SecurityException) {
            // Notification permission not granted yet.
        }
    }
}
