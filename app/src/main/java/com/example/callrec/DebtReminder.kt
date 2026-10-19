package com.example.callrec

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Notification on the reminder day of a debt, at 9 in the morning. */
object DebtReminder {
    private fun code(id: Long): Int = (id % 1_000_000_000L).toInt()

    private fun pending(c: Context, d: Debt?, id: Long, flags: Int, lead: Int = 0): PendingIntent? {
        val i = Intent(c, DebtReminderReceiver::class.java)
        i.putExtra("id", id)
        if (d != null) {
            i.putExtra("name", d.name)
            i.putExtra("owe", d.owe)
            i.putExtra("lead", lead)
        }
        return PendingIntent.getBroadcast(c, code(id), i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    fun cancel(c: Context, id: Long) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pending(c, null, id, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
    }

    fun schedule(c: Context, d: Debt) {
        cancel(c, d.id)
        if (d.due == 0L) return
        var lead = NotifPrefs.debtLead(c)
        val cal = Calendar.getInstance()
        cal.timeInMillis = d.due
        cal.set(Calendar.HOUR_OF_DAY, 9)
        cal.set(Calendar.MINUTE, 0)
        cal.add(Calendar.DAY_OF_YEAR, -lead)
        if (cal.timeInMillis <= System.currentTimeMillis() && lead > 0) {
            // the early reminder time has passed, so keep the one on the due date itself
            lead = 0
            cal.timeInMillis = d.due
            cal.set(Calendar.HOUR_OF_DAY, 9)
            cal.set(Calendar.MINUTE, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) return
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(c, d, d.id, PendingIntent.FLAG_UPDATE_CURRENT, lead) ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }

    /** Alarms do not survive a restart, so the app sets them again each time it opens. */
    fun rescheduleAll(c: Context) {
        for (d in DebtStore.all(c)) if (d.due != 0L) schedule(c, d)
    }
}

class DebtReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!NotifPrefs.shouldShow(ctx, NotifPrefs.DEBT)) return
        val id = intent.getLongExtra("id", 0L)
        val d = DebtStore.all(ctx).firstOrNull { it.id == id } ?: return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("debt_reminder", tr("বাকির রিমাইন্ডার"), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            ctx, 300, Intent(ctx, DebtActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val lead = intent.getIntExtra("lead", 0)
        val day = if (lead > 0) bn(lead.toString()) + tr(" দিন পরে ") else tr("আজ ")
        val text = if (d.owe) day + d.name + tr(" কে দেওয়ার দিন: ") + taka(d.amount)
        else day + d.name + tr(" এর কাছ থেকে নেওয়ার দিন: ") + taka(d.amount)
        val n = Notification.Builder(ctx, "debt_reminder")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(tr("বাকির রিমাইন্ডার"))
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(2000 + (id % 100000L).toInt(), n)
        NotifLog.add(ctx, tr("বাকির রিমাইন্ডার"), text)
    }
}
