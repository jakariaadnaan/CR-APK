package com.example.callrec

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView

/** চেহারা ও ভাষা: language, theme, accent colour, digits and text size. Every change applies to the whole app. */
class AppearanceActivity : GuardedActivity() {

    private lateinit var box: LinearLayout
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)
        setScreen(tr("চেহারা ও ভাষা"), scroll)
        render()
    }

    private fun changed() {
        Ui.stamp++
        recreate()
    }

    private fun section(title: String) {
        box.addView(label(title, 13f, Ui.MUTED, true), lp(l = 4, t = 10, b = 6))
    }

    /** A card of single-choice rows; the chosen one shows a tick. */
    private fun choices(names: List<String>, selected: Int, onPick: (Int) -> Unit): LinearLayout {
        val rows = names.mapIndexed { i, n ->
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(18), dp(16), dp(18), dp(16))
            r.addView(label(n, 16f, Ui.TEXT, i == selected), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (i == selected) r.addView(label("✓", 18f, Ui.PRIMARY, true))
            r.setOnClickListener { if (i != selected) onPick(i) }
            r as View
        }
        return groupCard(rows)
    }

    private fun render() {
        box.removeAllViews()

        section(tr("ভাষা"))
        box.addView(
            choices(listOf("বাংলা", "English"), if (Lang.en) 1 else 0) { i ->
                Lang.set(this, i == 1)
                changed()
            },
            lp(b = 8)
        )

        section(tr("থিম"))
        box.addView(
            choices(listOf(tr("ফোনের সেটিং অনুযায়ী"), tr("হালকা"), tr("গাঢ় (ডার্ক)")), prefs.getInt("theme", 0).coerceIn(0, 2)) { i ->
                prefs.edit().putInt("theme", i).apply()
                changed()
            },
            lp(b = 8)
        )

        section(tr("থিমের রঙ"))
        val sw = card(14)
        val line = LinearLayout(this)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER
        val cur = prefs.getInt("accent", 0).coerceIn(0, Ui.ACCENTS.size - 1)
        for ((i, a) in Ui.ACCENTS.withIndex()) {
            val col = android.graphics.Color.parseColor(if (Ui.dark) a.dark else a.light)
            val dot = LinearLayout(this)
            dot.gravity = Gravity.CENTER
            dot.background = bg(col, 22)
            if (i == cur) dot.addView(label("✓", 18f, android.graphics.Color.WHITE, true))
            dot.setOnClickListener { if (i != cur) { prefs.edit().putInt("accent", i).apply(); changed() } }
            line.addView(dot, LinearLayout.LayoutParams(dp(44), dp(44), 1f).also { it.setMargins(dp(4), 0, dp(4), 0) })
        }
        sw.addView(line)
        sw.addView(label(tr(Ui.ACCENTS[cur].name), 13f, Ui.MUTED), lp(t = 10))
        box.addView(sw, lp(b = 8))

        section(tr("সংখ্যার ধরন"))
        box.addView(
            choices(listOf(tr("বাংলা (১২৩)"), tr("ইংরেজি (123)")), if (prefs.getBoolean("digitsBn", true)) 0 else 1) { i ->
                prefs.edit().putBoolean("digitsBn", i == 0).apply()
                changed()
            },
            lp(b = 8)
        )

        section(tr("লেখার আকার"))
        val sizeVals = floatArrayOf(0.9f, 1f, 1.15f, 1.3f)
        val curSize = prefs.getFloat("fontScale", 1f)
        var curIdx = 1
        for (i in sizeVals.indices) if (Math.abs(sizeVals[i] - curSize) < 0.01f) curIdx = i
        box.addView(
            choices(listOf(tr("ছোট"), tr("মাঝারি"), tr("বড়"), tr("অনেক বড়")), curIdx) { i ->
                prefs.edit().putFloat("fontScale", sizeVals[i]).apply()
                changed()
            },
            lp(b = 8)
        )
    }
}
