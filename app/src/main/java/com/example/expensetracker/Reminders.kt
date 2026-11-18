package com.example.expensetracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Daily or weekly "N unsorted payments" reminder. Needs no special alarm permission. */
object Reminders {
    const val ACTION = "com.example.expensetracker.REMINDER"

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 1001, Intent(ctx, ReminderReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun nextTrigger(ctx: Context, now: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        c.set(Calendar.HOUR_OF_DAY, Prefs.reminderHour(ctx))
        c.set(Calendar.MINUTE, Prefs.reminderMinute(ctx))
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (Prefs.reminderWeekly(ctx)) {
            c.set(Calendar.DAY_OF_WEEK, Prefs.reminderDay(ctx))
            if (c.timeInMillis <= now) c.add(Calendar.WEEK_OF_YEAR, 1)
        } else if (c.timeInMillis <= now) {
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }

    /** Sets (or clears) the next reminder from the saved settings. Safe to call at any time. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val p = pending(ctx)
        am.cancel(p)
        if (!Prefs.reminderOn(ctx)) return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger(ctx, System.currentTimeMillis()), p)
    }

    /** Unsorted payments from the same last-30-days window the bubble queue uses. */
    fun unsortedCount(ctx: Context): Int =
        Db.get(ctx).unsortedIds(System.currentTimeMillis() - 30L * 86_400_000L).size
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Reminders.ACTION && Prefs.reminderOn(context)) {
            val n = Reminders.unsortedCount(context)
            if (n > 0) Notifier.showReminder(context, n) // nothing to sort: stay quiet
        }
        Reminders.schedule(context) // plan the next one; this also runs after a reboot
    }
}