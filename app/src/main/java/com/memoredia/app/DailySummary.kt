package com.memoredia.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** A short list of the last notifications the app showed, kept so a missed one can be read later. */
object NotifLog {
    private const val KEY = "notifLog"

    private fun sp(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun add(c: Context, title: String, text: String) {
        try {
            val old = JSONArray(sp(c).getString(KEY, "[]"))
            val out = JSONArray()
            out.put(JSONObject().put("t", System.currentTimeMillis()).put("title", title).put("text", text))
            for (i in 0 until Math.min(old.length(), 19)) out.put(old.getJSONObject(i))
            sp(c).edit().putString(KEY, out.toString()).apply()
        } catch (e: Throwable) { }
    }

    /** Newest first: (time, title, text). */
    fun list(c: Context): List<Triple<Long, String, String>> {
        val r = ArrayList<Triple<Long, String, String>>()
        try {
            val a = JSONArray(sp(c).getString(KEY, "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                r.add(Triple(o.optLong("t"), o.optString("title"), o.optString("text")))
            }
        } catch (e: Throwable) { }
        return r
    }

    fun clear(c: Context) { sp(c).edit().remove(KEY).apply() }
}

/** One notification each morning that sums up what is waiting today. */
object DailySummary {
    private fun pending(c: Context, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(c, 5000, Intent(c, DailySummaryReceiver::class.java), flags or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pending(c, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
        val p = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!p.getBoolean("sumOn", false)) return
        val min = p.getInt("sumTime", 8 * 60)
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, min / 60)
        cal.set(Calendar.MINUTE, min % 60)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        val pi = pending(c, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }
}

class DailySummaryReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        try {
            if (NotifPrefs.shouldShow(ctx, "sumOn")) {
                val start = todayStart()
                val end = start + 86_400_000L
                val todos = TodoStore.all(ctx).count { !it.done && ((it.due in start until end) || (it.remind in start until end)) }
                val debts = DebtStore.all(ctx).count { it.due in start until end }
                if (todos + debts > 0) {
                    val title = tr("আজকের সারাংশ")
                    val parts = ArrayList<String>()
                    if (todos > 0) parts.add(bn(todos.toString()) + tr("টি করণীয়"))
                    if (debts > 0) parts.add(bn(debts.toString()) + tr("টি বাকির তারিখ"))
                    val text = parts.joinToString(", ")
                    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.createNotificationChannel(NotificationChannel("summary", tr("দৈনিক সারাংশ"), NotificationManager.IMPORTANCE_DEFAULT))
                    val open = PendingIntent.getActivity(
                        ctx, 303, Intent(ctx, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    nm.notify(5001, Notification.Builder(ctx, "summary")
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .build())
                    NotifLog.add(ctx, title, text)
                }
            }
        } catch (e: Throwable) { }
        DailySummary.schedule(ctx)
    }
}
