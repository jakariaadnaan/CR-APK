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

/** Alarms for the prayers the user switched on (Fajr, Dhuhr, Asr, Maghrib, Isha). */
object PrayerReminder {
    /** Table index of the five prayers in [PrayerCalc.Times.at]. */
    val IDX = listOf(0, 2, 3, 4, 5)

    private fun pending(c: Context, k: Int, flags: Int): PendingIntent? {
        val i = Intent(c, PrayerReminderReceiver::class.java)
        i.putExtra("k", k)
        return PendingIntent.getBroadcast(c, 4000 + k, i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    fun scheduleAll(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (k in IDX.indices) {
            pending(c, k, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
            if (!PrayerCalc.notify(c, IDX[k])) continue
            val now = System.currentTimeMillis()
            var when0 = 0L
            for (add in 0..1) {
                val day = Calendar.getInstance()
                day.add(Calendar.DAY_OF_YEAR, add)
                val ms = PrayerCalc.toMillis(day, PrayerCalc.forDay(c, day).at(IDX[k])) - NotifPrefs.prayerLead(c) * 60_000L
                if (ms > now) { when0 = ms; break }
            }
            if (when0 == 0L) continue
            val pi = pending(c, k, PendingIntent.FLAG_UPDATE_CURRENT) ?: continue
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when0, pi)
        }
    }
}

val PRAYER_NAMES = listOf("ফজর", "সূর্যোদয়", "যোহর", "আসর", "মাগরিব", "এশা")

class PrayerReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val k = intent.getIntExtra("k", 0)
        val idx = PrayerReminder.IDX.getOrNull(k) ?: return
        if (!NotifPrefs.shouldShow(ctx, NotifPrefs.PRAYER)) {
            PrayerReminder.scheduleAll(ctx)
            return
        }
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("prayer", tr("নামাজের সময়"), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            ctx, 302, Intent(ctx, PrayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val ttl = if (NotifPrefs.prayerLead(ctx) > 0) tr(PRAYER_NAMES[idx]) + tr(" এর সময় হতে ") + bn(NotifPrefs.prayerLead(ctx).toString()) + tr(" মিনিট বাকি")
        else tr(PRAYER_NAMES[idx]) + tr(" এর সময় হয়েছে")
        val place = PrayerCalc.location(ctx).first.let { tr(it) }
        val n = Notification.Builder(ctx, "prayer")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(ttl)
            .setContentText(place)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(4100 + k, n)
        NotifLog.add(ctx, ttl, place)
        PrayerReminder.scheduleAll(ctx)
    }
}
