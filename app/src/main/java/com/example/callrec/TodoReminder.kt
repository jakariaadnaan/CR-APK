package com.example.callrec

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Notification at the exact reminder time chosen for a to-do. */
object TodoReminder {
    private fun code(id: Long): Int = ((id % 1_000_000_000L).toInt()) xor 0x2A2A

    private fun pending(c: Context, id: Long, flags: Int): PendingIntent? {
        val i = Intent(c, TodoReminderReceiver::class.java)
        i.putExtra("id", id)
        return PendingIntent.getBroadcast(c, code(id), i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    fun cancel(c: Context, id: Long) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pending(c, id, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
    }

    fun schedule(c: Context, t: Todo) {
        cancel(c, t.id)
        if (t.remind == 0L || t.done || t.remind <= System.currentTimeMillis()) return
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(c, t.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.remind, pi)
    }

    fun rescheduleAll(c: Context) {
        for (t in TodoStore.all(c)) if (t.remind != 0L) schedule(c, t)
    }
}

class TodoReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (!NotifPrefs.on(ctx, NotifPrefs.TODO)) return
        val id = intent.getLongExtra("id", 0L)
        val t = TodoStore.all(ctx).firstOrNull { it.id == id } ?: return
        if (t.done) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("todo_reminder", tr("করণীয়ের রিমাইন্ডার"), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            ctx, 301, Intent(ctx, TodoActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, "todo_reminder")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (t.urgent) tr("জরুরি কাজ") else tr("করণীয়"))
            .setContentText(t.title)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(3000 + (id % 100000L).toInt(), n)
    }
}
