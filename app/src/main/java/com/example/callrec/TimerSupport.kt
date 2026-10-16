package com.example.callrec

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager

/** True while the timer screen is on top, so the receiver does not double-notify. */
object TimerState {
    @Volatile
    var visible = false
}

/**
 * A countdown that survives screen changes: it stores an absolute end time (wall clock)
 * while running and the remaining milliseconds while paused.
 */
class Countdown(ctx: Context, private val key: String, defaultMs: Long) {
    private val sp = ctx.getSharedPreferences("timers", Context.MODE_PRIVATE)

    var durationMs: Long = sp.getLong("$key.dur", defaultMs)
        private set
    var remainingMs: Long = sp.getLong("$key.rem", durationMs)
        private set
    var endAt: Long = sp.getLong("$key.end", 0L)
        private set

    val running: Boolean get() = endAt > 0L

    fun remaining(now: Long = System.currentTimeMillis()): Long =
        if (running) maxOf(0L, endAt - now) else remainingMs

    fun start() {
        if (running) return
        if (remainingMs <= 0L) remainingMs = durationMs
        endAt = System.currentTimeMillis() + remainingMs
        save()
    }

    fun pause() {
        if (!running) return
        remainingMs = remaining()
        endAt = 0L
        save()
    }

    fun reset(newDurationMs: Long = durationMs) {
        durationMs = newDurationMs
        remainingMs = newDurationMs
        endAt = 0L
        save()
    }

    private fun save() {
        sp.edit()
            .putLong("$key.dur", durationMs)
            .putLong("$key.rem", remainingMs)
            .putLong("$key.end", endAt)
            .apply()
    }
}

object Alarms {
    const val CODE_COUNTDOWN = 1
    const val CODE_POMODORO = 2

    private fun alarmIntent(ctx: Context, code: Int, title: String?, text: String?): PendingIntent {
        val i = Intent(ctx, TimerReceiver::class.java)
        i.putExtra("code", code)
        i.putExtra("title", title)
        i.putExtra("text", text)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Uses an alarm-clock alarm: exact, needs no special permission. */
    fun schedule(ctx: Context, code: Int, endAt: Long, title: String, text: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(
            ctx, 100 + code, Intent(ctx, TimerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(endAt, show), alarmIntent(ctx, code, title, text))
    }

    fun cancel(ctx: Context, code: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(alarmIntent(ctx, code, null, null))
    }
}

class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (TimerState.visible) return
        val code = intent.getIntExtra("code", 0)
        val title = intent.getStringExtra("title") ?: tr("সময় শেষ")
        val text = intent.getStringExtra("text") ?: ""

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel("timer_alarm", tr("টাইমার"), NotificationManager.IMPORTANCE_HIGH)
        ch.setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        )
        ch.enableVibration(true)
        nm.createNotificationChannel(ch)

        val open = PendingIntent.getActivity(
            ctx, 200 + code, Intent(ctx, TimerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, "timer_alarm")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .build()
        nm.notify(1000 + code, n)
    }
}
