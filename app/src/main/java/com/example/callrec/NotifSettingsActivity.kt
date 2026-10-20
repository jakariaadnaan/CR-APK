package com.example.callrec

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** Which reminders are allowed to speak up. Read by the alarm receivers before they show anything. */
object NotifPrefs {
    const val ALL = "notifAll"
    const val DEBT = "notifDebt"
    const val TODO = "notifTodo"
    const val PRAYER = "notifPrayer"
    const val BACKUP = "notifBackup"

    fun on(c: Context, key: String): Boolean {
        val p = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return p.getBoolean(ALL, true) && p.getBoolean(key, true)
    }

    private fun minutesNow(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    }

    /** True while the "quiet hours" window is on and the clock is inside it. */
    fun quietNow(c: Context): Boolean {
        val p = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!p.getBoolean("quietOn", false)) return false
        val from = p.getInt("quietFrom", 23 * 60)
        val to = p.getInt("quietTo", 5 * 60)
        val now = minutesNow()
        return if (from <= to) now in from until to else now >= from || now < to
    }

    /** Switch on, and not silenced by quiet hours (prayers can be excused from quiet hours). */
    fun shouldShow(c: Context, key: String): Boolean {
        if (!on(c, key)) return false
        if (!quietNow(c)) return true
        val p = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return key == PRAYER && p.getBoolean("quietPrayerOk", true)
    }

    fun prayerLead(c: Context): Int = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("prayerLead", 0)
    fun debtLead(c: Context): Int = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getInt("debtLead", 0)
}

/** Notifications: one place to switch reminders on or off and to check that they can reach the phone. */
class NotifSettingsActivity : GuardedActivity() {

    private lateinit var box: LinearLayout
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)
        setScreen("Notifications", scroll)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::box.isInitialized) render()
    }

    private fun section(s: String) {
        box.addView(label(s, 13f, Ui.PRIMARY, true), lp(l = 6, t = 14, b = 8))
    }

    private fun granted(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()
    }

    private fun openPhoneSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } catch (e: Throwable) {
            Toast.makeText(this, "Could not open the phone settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggle(icon: Int, title: String, sub: String, key: String, enabled: Boolean = true): LinearLayout {
        val on = prefs.getBoolean(key, true)
        val r = listRow(icon, title, sub, if (enabled) Ui.TEXT else Ui.MUTED, styledSwitch(on && enabled), false)
        r.setOnClickListener {
            it.tapFx()
            prefs.edit().putBoolean(key, !on).apply()
            render()
        }
        return r
    }

    private fun choose(title: String, items: Array<String>, cur: Int, onPick: (Int) -> Unit) {
        android.app.AlertDialog.Builder(this).setTitle(title)
            .setSingleChoiceItems(items, cur) { d, i -> d.dismiss(); onPick(i) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun clock(min: Int): String {
        val h = min / 60
        val m = min % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        return h12.toString() + ":" + (if (m < 10) "0$m" else "$m") + (if (h < 12) " AM" else " PM")
    }

    private fun timeRow(icon: Int, title: String, key: String, def: Int): LinearLayout {
        val v = prefs.getInt(key, def)
        val r = listRow(icon, title, "", Ui.TEXT, label(clock(v), 13f, Ui.MUTED), true)
        r.setOnClickListener {
            it.tapFx()
            android.app.TimePickerDialog(this, { _, h, m ->
                prefs.edit().putInt(key, h * 60 + m).apply()
                render()
            }, v / 60, v % 60, false).show()
        }
        return r
    }

    private fun sendTest() {
        if (!granted()) {
            Toast.makeText(this, "Notifications are not allowed yet", Toast.LENGTH_SHORT).show()
            return
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("test", "Test", NotificationManager.IMPORTANCE_HIGH))
        val n = Notification.Builder(this, "test")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(tr("আমার খাতা"))
            .setContentText("This is a test notification. Reminders will look like this.")
            .setAutoCancel(true)
            .build()
        nm.notify(9001, n)
    }

    private fun render() {
        box.removeAllViews()
        val ok = granted()
        val master = prefs.getBoolean(NotifPrefs.ALL, true)

        // permission status
        val st = card(14)
        st.orientation = LinearLayout.HORIZONTAL
        st.gravity = Gravity.CENTER_VERTICAL
        st.addView(iconBox(R.drawable.ic_bell, if (ok) Ui.PRIMARY else Ui.RED, android.graphics.Color.WHITE, 46, 24, 23, 14))
        val mid = LinearLayout(this)
        mid.orientation = LinearLayout.VERTICAL
        mid.addView(label(if (ok) "Notifications are allowed" else "Notifications are blocked", 16f, Ui.TEXT, true))
        mid.addView(
            label(if (ok) "Reminders can reach this phone." else "Allow them, or no reminder will show.", 12f, Ui.MUTED),
            lp(t = 2)
        )
        st.addView(mid, LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (!ok) {
            st.addView(pill("Allow") {
                if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 11)
                } else openPhoneSettings()
            }, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, l = 8))
        }
        box.addView(st, lp())

        section("General")
        box.addView(groupCard(listOf(
            toggle(R.drawable.ic_bell, "All notifications", "Switch every reminder on or off", NotifPrefs.ALL),
            listRow(R.drawable.ic_sparkle, "Send test notification", "Check that reminders work").also {
                it.setOnClickListener { v -> v.tapFx(); sendTest() }
            }
        )), lp())

        section("Reminders")
        box.addView(groupCard(listOf(
            toggle(R.drawable.ic_wallet, "Debt reminders", "On the due date you set", NotifPrefs.DEBT, master),
            toggle(R.drawable.ic_text, "To-do reminders", "At the time you set for a task", NotifPrefs.TODO, master),
            toggle(R.drawable.ic_clock, "Prayer reminders", "For the prayers you turned on", NotifPrefs.PRAYER, master),
            toggle(R.drawable.ic_backup, "Backup reminder", "An alert in the bell when a backup is due", NotifPrefs.BACKUP, master)
        )), lp())

        section("Prayer alerts")
        val pn = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
        val prows = ArrayList<View>()
        for (k in pn.indices) {
            val idx = PrayerReminder.IDX[k]
            val on = PrayerCalc.notify(this, idx)
            val r = listRow(R.drawable.ic_clock, pn[k], "", if (master) Ui.TEXT else Ui.MUTED, styledSwitch(on && master), false)
            r.setOnClickListener {
                it.tapFx()
                PrayerCalc.setNotify(this, idx, !on)
                PrayerReminder.scheduleAll(this)
                render()
            }
            prows.add(r)
        }
        val leads = intArrayOf(0, 5, 10, 15, 30)
        val leadNames = arrayOf("On time", "5 minutes before", "10 minutes before", "15 minutes before", "30 minutes before")
        val pl = NotifPrefs.prayerLead(this)
        val plIdx = leads.indexOf(pl).let { if (it < 0) 0 else it }
        val lr = listRow(R.drawable.ic_bell, "Alert before time", "", Ui.TEXT, label(leadNames[plIdx], 13f, Ui.MUTED), true)
        lr.setOnClickListener {
            it.tapFx()
            choose("Alert before time", leadNames, plIdx) { i ->
                prefs.edit().putInt("prayerLead", leads[i]).apply()
                PrayerReminder.scheduleAll(this)
                render()
            }
        }
        prows.add(lr)
        box.addView(groupCard(prows), lp())

        section("Debt reminders")
        val dl = intArrayOf(0, 1, 3)
        val dlNames = arrayOf("On the due date", "1 day before", "3 days before")
        val dlIdx = dl.indexOf(NotifPrefs.debtLead(this)).let { if (it < 0) 0 else it }
        val dr = listRow(R.drawable.ic_wallet, "Remind me", "", Ui.TEXT, label(dlNames[dlIdx], 13f, Ui.MUTED), true)
        dr.setOnClickListener {
            it.tapFx()
            choose("Debt reminder", dlNames, dlIdx) { i ->
                prefs.edit().putInt("debtLead", dl[i]).apply()
                DebtReminder.rescheduleAll(this)
                render()
            }
        }
        box.addView(groupCard(listOf(dr)), lp())

        section("Quiet hours")
        val qOn = prefs.getBoolean("quietOn", false)
        val qrows = ArrayList<View>()
        val qr = listRow(R.drawable.ic_moon, "Quiet hours", "Reminders in this time are skipped", Ui.TEXT, styledSwitch(qOn), false)
        qr.setOnClickListener {
            it.tapFx()
            prefs.edit().putBoolean("quietOn", !qOn).apply()
            render()
        }
        qrows.add(qr)
        if (qOn) {
            qrows.add(timeRow(R.drawable.ic_clock, "From", "quietFrom", 23 * 60))
            qrows.add(timeRow(R.drawable.ic_clock, "To", "quietTo", 5 * 60))
            val pOk = prefs.getBoolean("quietPrayerOk", true)
            val pr = listRow(R.drawable.ic_shield, "Prayer alerts still ring", "Prayers ignore quiet hours", Ui.TEXT, styledSwitch(pOk), false)
            pr.setOnClickListener {
                it.tapFx()
                prefs.edit().putBoolean("quietPrayerOk", !pOk).apply()
                render()
            }
            qrows.add(pr)
        }
        box.addView(groupCard(qrows), lp())

        section("Daily summary")
        val sOn = prefs.getBoolean("sumOn", false)
        val srows = ArrayList<View>()
        val sr = listRow(R.drawable.ic_sparkle, "Daily summary", "One notification each morning with today's tasks and due debts", Ui.TEXT, styledSwitch(sOn && master), false)
        sr.setOnClickListener {
            it.tapFx()
            prefs.edit().putBoolean("sumOn", !sOn).apply()
            DailySummary.schedule(this)
            render()
        }
        srows.add(sr)
        if (sOn) {
            val v = prefs.getInt("sumTime", 8 * 60)
            val tr0 = listRow(R.drawable.ic_clock, "Send at", "", Ui.TEXT, label(clock(v), 13f, Ui.MUTED), true)
            tr0.setOnClickListener {
                it.tapFx()
                android.app.TimePickerDialog(this, { _, h, m ->
                    prefs.edit().putInt("sumTime", h * 60 + m).apply()
                    DailySummary.schedule(this)
                    render()
                }, v / 60, v % 60, false).show()
            }
            srows.add(tr0)
        }
        box.addView(groupCard(srows), lp())

        section("Keep reminders reliable")
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        val unrestricted = pm.isIgnoringBatteryOptimizations(packageName)
        val rel = ArrayList<View>()
        val br = listRow(R.drawable.ic_backup, "Battery", "Let alarms ring on time", Ui.TEXT, label(if (unrestricted) "Unrestricted" else "Optimised", 13f, if (unrestricted) Ui.GREEN else Ui.MUTED), true)
        br.setOnClickListener {
            it.tapFx()
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e: Throwable) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:$packageName")))
            }
        }
        rel.add(br)
        val brand = android.os.Build.MANUFACTURER.lowercase()
        if (brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")) {
            val ar = listRow(R.drawable.ic_bolt, "Autostart (Xiaomi)", "Needed to restore reminders after a restart")
            ar.setOnClickListener {
                it.tapFx()
                try {
                    startActivity(Intent().setComponent(android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")))
                } catch (e: Throwable) {
                    try {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(android.net.Uri.parse("package:$packageName")))
                    } catch (x: Throwable) { }
                }
            }
            rel.add(ar)
        }
        box.addView(groupCard(rel), lp())

        section("Recent notifications")
        val log = NotifLog.list(this)
        if (log.isEmpty()) {
            val e = label("Nothing yet. Reminders you receive will be listed here.", 13f, Ui.MUTED)
            e.setPadding(dp(6), 0, dp(6), 0)
            box.addView(e, lp())
        } else {
            val lrows = ArrayList<View>()
            for (en in log) {
                val when0 = java.text.SimpleDateFormat("dd MMM, h:mm a", java.util.Locale.US).format(java.util.Date(en.first))
                lrows.add(listRow(R.drawable.ic_bell, en.second, en.third + "  ·  " + when0, Ui.TEXT, null, false))
            }
            val clr = listRow(R.drawable.ic_trash, "Clear history", "", Ui.RED, null, false)
            clr.setOnClickListener { it.tapFx(); NotifLog.clear(this); render() }
            lrows.add(clr)
            box.addView(groupCard(lrows), lp())
        }

        section("Sound & vibration")
        box.addView(groupCard(listOf(
            listRow(R.drawable.ic_volume, "Phone notification settings", "Choose sound and vibration for each type").also {
                it.setOnClickListener { v -> v.tapFx(); openPhoneSettings() }
            }
        )), lp())
        box.addView(
            label("Reminders are set again automatically after a restart. On Xiaomi, also allow Autostart and set this app's battery to “No restrictions”, or alarms may come late.", 12f, Ui.MUTED),
            lp(l = 6, t = 12)
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::box.isInitialized) render()
    }
}
