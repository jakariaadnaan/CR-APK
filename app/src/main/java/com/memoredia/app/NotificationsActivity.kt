package com.memoredia.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** The bell page: live warnings first, then the history of reminders that were sent. */
class NotificationsActivity : GuardedActivity() {

    private lateinit var box: LinearLayout
    private var prevSeen = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("settings", MODE_PRIVATE)
        prevSeen = sp.getLong("notifSeen", 0L)
        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(8), dp(16), dp(24))
        scroll.addView(box)
        setScreen(tr("বিজ্ঞপ্তি"), scroll)
        render()
        sp.edit().putLong("notifSeen", System.currentTimeMillis()).apply()
    }

    override fun onResume() {
        super.onResume()
        if (::box.isInitialized) render()
    }

    private fun section(t: String, action: String? = null, onAction: (() -> Unit)? = null): View {
        val r = LinearLayout(this)
        r.gravity = Gravity.CENTER_VERTICAL
        r.addView(label(t, 13f, Ui.MUTED, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (action != null) {
            val a = label(action, 13f, Ui.PRIMARY, true)
            a.setPadding(dp(10), dp(6), dp(4), dp(6))
            a.setOnClickListener { it.tapFx(); onAction?.invoke() }
            r.addView(a)
        }
        return r
    }

    private fun item(color: Int, icon: Int, title: String, text: String, meta: String, unread: Boolean, onClick: (() -> Unit)?): View {
        val r = LinearLayout(this)
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(14), dp(13), dp(12), dp(13))
        val ib = LinearLayout(this)
        ib.gravity = Gravity.CENTER
        ib.background = bg(Ui.mix(color, Ui.CARD, 0.82f), 14)
        ib.addView(iconView(icon, 22, color))
        r.addView(ib, lp(width = dp(44), height = dp(44), r = 12))
        val mid = LinearLayout(this)
        mid.orientation = LinearLayout.VERTICAL
        mid.addView(label(title, 14f, Ui.TEXT, true))
        if (text.isNotBlank()) mid.addView(label(text, 13f, Ui.MUTED), lp(t = 2))
        if (meta.isNotBlank()) mid.addView(label(meta, 11f, Ui.MUTED), lp(t = 3))
        r.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (unread) {
            val dot = View(this)
            dot.background = bg(Ui.PRIMARY, 5)
            r.addView(dot, lp(width = dp(10), height = dp(10), l = 8))
        }
        if (onClick != null) r.setOnClickListener { it.tapFx(); onClick() }
        return r
    }

    private fun iconFor(title: String): Pair<Int, Int> = when {
        title.contains("বাকি") || title.contains("Debt", true) -> Pair(R.drawable.ic_user, Color.parseColor("#F29900"))
        title.contains("করণীয়") || title.contains("কাজ") || title.contains("Task", true) || title.contains("To-do", true) -> Pair(R.drawable.ic_clock, Color.parseColor("#3F6FE0"))
        title.contains("সারাংশ") || title.contains("Summary", true) -> Pair(R.drawable.ic_doc, Color.parseColor("#7E57C2"))
        else -> Pair(R.drawable.ic_bell, Color.parseColor("#2E9E6B"))
    }

    private fun render() {
        box.removeAllViews()
        val alerts = NotifCenter.alerts(this)
        val log = NotifLog.list(this)

        if (alerts.isEmpty() && log.isEmpty()) {
            val e = LinearLayout(this)
            e.orientation = LinearLayout.VERTICAL
            e.gravity = Gravity.CENTER
            e.setPadding(dp(20), dp(70), dp(20), dp(20))
            val ib = LinearLayout(this)
            ib.gravity = Gravity.CENTER
            ib.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.85f), 36)
            ib.addView(iconView(R.drawable.ic_bell, 32, Ui.PRIMARY))
            e.addView(ib, lp(width = dp(72), height = dp(72)))
            val t = label(tr("সব ঠিক আছে"), 17f, Ui.TEXT, true)
            t.gravity = Gravity.CENTER
            e.addView(t, lp(t = 14))
            val s = label(tr("কোনো সতর্কতা বা রিমাইন্ডার নেই। কিছু হলে এখানে দেখা যাবে।"), 13f, Ui.MUTED)
            s.gravity = Gravity.CENTER
            e.addView(s, lp(t = 4))
            box.addView(e, lp())
            return
        }

        if (alerts.isNotEmpty()) {
            box.addView(section(tr("সতর্কতা") + " (" + bn(alerts.size.toString()) + ")"), lp(l = 4, b = 8))
            box.addView(groupCard(alerts.map { a ->
                item(a.color, a.icon, a.title, a.text, "", false, if (a.open == null) null else ({
                    try { startActivity(a.open.invoke(this)) } catch (e: Throwable) { }
                }))
            }), lp(b = 16))
        }

        if (log.isNotEmpty()) {
            box.addView(section(tr("সাম্প্রতিক রিমাইন্ডার"), tr("লগ মুছে ফেলুন")) { NotifLog.clear(this); render() }, lp(l = 4, b = 8))
            val today = todayStart()
            val fmtT = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val fmtD = java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale.getDefault())
            val rows = log.take(30).map { e ->
                val (ic, col) = iconFor(e.second)
                val whenTxt = when {
                    e.first >= today -> tr("আজ") + ", " + bn(fmtT.format(java.util.Date(e.first)))
                    e.first >= today - 86_400_000L -> tr("গতকাল") + ", " + bn(fmtT.format(java.util.Date(e.first)))
                    else -> bn(fmtD.format(java.util.Date(e.first)))
                }
                item(col, ic, e.second, e.third, whenTxt, e.first > prevSeen, null)
            }
            box.addView(groupCard(rows), lp())
        }
    }
}
