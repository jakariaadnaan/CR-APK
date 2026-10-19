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
            toggle(R.drawable.ic_backup, "Backup reminder", "A banner on Home when a backup is due", NotifPrefs.BACKUP, master)
        )), lp())

        section("Sound & vibration")
        box.addView(groupCard(listOf(
            listRow(R.drawable.ic_volume, "Phone notification settings", "Choose sound and vibration for each type").also {
                it.setOnClickListener { v -> v.tapFx(); openPhoneSettings() }
            }
        )), lp())
        box.addView(
            label("If reminders come late on Xiaomi, set this app's battery to “No restrictions” and open the app once after a restart.", 12f, Ui.MUTED),
            lp(l = 6, t = 12)
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::box.isInitialized) render()
    }
}
