package com.memoredia.app

import android.app.AlertDialog
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/** Appearance: theme mode, accent colour, app icon, font size and a few interface switches. */
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
        setScreen(tl("Appearance"), scroll)
        render()
    }

    private fun changed() {
        Ui.stamp++
        recreate()
    }

    private fun section(title: String) {
        box.addView(label(title, 13f, Ui.PRIMARY, true), lp(l = 6, t = 14, b = 8))
    }

    private fun check(): TextView {
        val c = label("✓", 12f, Color.WHITE, true)
        c.gravity = Gravity.CENTER
        c.background = bg(Ui.PRIMARY, 11)
        return c
    }

    private fun themeCard(icon: Int, name: String, on: Boolean, onPick: () -> Unit): View {
        val f = FrameLayout(this)
        val inner = LinearLayout(this)
        inner.orientation = LinearLayout.VERTICAL
        inner.gravity = Gravity.CENTER
        inner.setPadding(0, dp(16), 0, dp(14))
        inner.background = if (on) bg(Ui.KEY_OP, 16, Ui.PRIMARY, 2) else bg(Ui.CARD, 16, Ui.LINE, 1)
        inner.addView(iconView(icon, 30, if (on) Ui.PRIMARY else Ui.ICON))
        inner.addView(label(name, 14f, Ui.TEXT, true), lp(t = 8, width = ViewGroup.LayoutParams.WRAP_CONTENT))
        f.addView(inner, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (on) {
            val lpb = FrameLayout.LayoutParams(dp(22), dp(22))
            lpb.gravity = Gravity.BOTTOM or Gravity.END
            lpb.setMargins(0, 0, dp(6), dp(6))
            f.addView(check(), lpb)
        }
        f.setOnClickListener { it.tapFx(); if (!on) onPick() }
        return f
    }

    private fun accentDot(fill: Int?, on: Boolean, onPick: () -> Unit): View {
        val f = FrameLayout(this)
        val d = View(this)
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        if (fill != null) {
            g.setColor(fill)
        } else {
            g.gradientType = GradientDrawable.SWEEP_GRADIENT
            g.colors = intArrayOf(
                Color.parseColor("#E5484D"), Color.parseColor("#F5A524"), Color.parseColor("#2FB872"),
                Color.parseColor("#2D8CF0"), Color.parseColor("#8E4EC6"), Color.parseColor("#E5484D")
            )
        }
        d.background = g
        f.addView(d, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER))
        if (on) {
            val ring = View(this)
            val rg = GradientDrawable()
            rg.shape = GradientDrawable.OVAL
            rg.setColor(Color.TRANSPARENT)
            rg.setStroke(dp(2), fill ?: Ui.PRIMARY)
            ring.background = rg
            f.addView(ring, FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER))
        }
        f.setOnClickListener { it.tapFx(); onPick() }
        return f
    }

    private fun customAccent() {
        val e = EditText(this)
        e.hint = "#2D5BE3"
        e.setText(prefs.getString("accentHex", "") ?: "")
        e.inputType = InputType.TYPE_CLASS_TEXT
        val w = LinearLayout(this)
        w.setPadding(dp(20), dp(8), dp(20), 0)
        w.orientation = LinearLayout.VERTICAL
        w.addView(label(tl("Type a colour code like #2D5BE3. Pick a fairly dark colour so white text stays readable."), 12f, Ui.MUTED), lp(b = 8))
        w.addView(e)
        AlertDialog.Builder(this).setTitle(tl("Custom colour")).setView(w)
            .setPositiveButton(tl("Apply")) { _, _ ->
                var s = e.text.toString().trim()
                if (!s.startsWith("#")) s = "#$s"
                try {
                    Color.parseColor(s)
                    prefs.edit().putString("accentHex", s).putInt("accent", Ui.ACCENTS.size).apply()
                    changed()
                } catch (x: Throwable) {
                    Toast.makeText(this, tl("That colour code is not valid"), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(tl("Cancel"), null).show()
    }

    private fun iconTile(res: Int, alias: String, on: Boolean): View {
        val f = FrameLayout(this)
        val frame = LinearLayout(this)
        frame.gravity = Gravity.CENTER
        frame.setPadding(dp(8), dp(8), dp(8), dp(8))
        frame.background = if (on) bg(Ui.KEY_OP, 18, Ui.PRIMARY, 2) else bg(Ui.CARD, 18, Ui.LINE, 1)
        val iv = ImageView(this)
        iv.setImageResource(res)
        iv.clipToOutline = true
        iv.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, v.width * 0.24f) }
        }
        frame.addView(iv, LinearLayout.LayoutParams(dp(60), dp(60)))
        f.addView(frame, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (on) {
            val lpb = FrameLayout.LayoutParams(dp(22), dp(22))
            lpb.gravity = Gravity.BOTTOM or Gravity.END
            f.addView(check(), lpb)
        }
        f.setOnClickListener {
            it.tapFx()
            if (!on) {
                val pm = packageManager
                for (a in listOf("IconA", "IconB", "IconC")) {
                    pm.setComponentEnabledSetting(
                        ComponentName(this, "com.memoredia.app.$a"),
                        if (a == alias) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
                prefs.edit().putString("appIcon", alias).apply()
                Toast.makeText(this, tl("Icon changed. The home screen may take a few seconds to update."), Toast.LENGTH_LONG).show()
                render()
            }
        }
        return f
    }

    private fun toggleRow(icon: Int, title: String, sub: String, key: String, def: Boolean): LinearLayout {
        val on = prefs.getBoolean(key, def)
        val r = listRow(icon, title, sub, Ui.TEXT, styledSwitch(on), false)
        r.setOnClickListener {
            it.tapFx()
            prefs.edit().putBoolean(key, !on).apply()
            if (key == "anim") Ui.stamp++
            render()
        }
        return r
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

        // Theme Mode: Light, Dark, System
        section(tl("Theme Mode"))
        val theme = prefs.getInt("theme", 0).coerceIn(0, 2)
        val trow = LinearLayout(this)
        trow.orientation = LinearLayout.HORIZONTAL
        val tdefs = listOf(Triple(R.drawable.ic_sun, tl("Light"), 1), Triple(R.drawable.ic_moon, tl("Dark"), 2), Triple(R.drawable.ic_phone, tl("System"), 0))
        for ((k, d) in tdefs.withIndex()) {
            val c = themeCard(d.first, d.second, theme == d.third) {
                prefs.edit().putInt("theme", d.third).apply()
                changed()
            }
            val p = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            p.setMargins(if (k == 0) 0 else dp(5), 0, if (k == 2) 0 else dp(5), 0)
            trow.addView(c, p)
        }
        box.addView(trow, lp())

        // Accent Color
        section(tl("Accent Color"))
        val cur = prefs.getInt("accent", 0).coerceIn(0, Ui.ACCENTS.size)
        val arow = card(12)
        val line = LinearLayout(this)
        line.orientation = LinearLayout.HORIZONTAL
        line.gravity = Gravity.CENTER
        for ((i, a) in Ui.ACCENTS.withIndex()) {
            val col = Color.parseColor(if (Ui.dark) a.dark else a.light)
            line.addView(accentDot(col, i == cur) { prefs.edit().putInt("accent", i).apply(); changed() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        }
        line.addView(accentDot(null, cur == Ui.ACCENTS.size) { customAccent() }, LinearLayout.LayoutParams(0, dp(46), 1f))
        arow.addView(line)
        box.addView(arow, lp())

        // App Icon
        section(tl("App Icon"))
        val ic = prefs.getString("appIcon", "IconA") ?: "IconA"
        val irow = LinearLayout(this)
        irow.orientation = LinearLayout.HORIZONTAL
        irow.addView(iconTile(R.mipmap.ic_launcher_a, "IconA", ic == "IconA"), lp(width = ViewGroup.LayoutParams.WRAP_CONTENT, r = 12))
        irow.addView(iconTile(R.mipmap.ic_launcher_b, "IconB", ic == "IconB"), lp(width = ViewGroup.LayoutParams.WRAP_CONTENT, r = 12))
        irow.addView(iconTile(R.mipmap.ic_launcher_c, "IconC", ic == "IconC"), lp(width = ViewGroup.LayoutParams.WRAP_CONTENT))
        box.addView(irow, lp())

        // Font Size
        section(tl("Font Size"))
        val sizeVals = floatArrayOf(0.9f, 1f, 1.15f, 1.3f)
        val curSize = prefs.getFloat("fontScale", 1f)
        var curIdx = 1
        for (i in sizeVals.indices) if (Math.abs(sizeVals[i] - curSize) < 0.01f) curIdx = i
        val frow = card(14)
        frow.orientation = LinearLayout.HORIZONTAL
        frow.gravity = Gravity.CENTER_VERTICAL
        frow.addView(label("A", 14f, Ui.TEXT, true))
        val sb = SeekBar(this)
        sb.max = 3
        sb.progress = curIdx
        sb.progressTintList = android.content.res.ColorStateList.valueOf(Ui.PRIMARY)
        sb.thumbTintList = android.content.res.ColorStateList.valueOf(Ui.PRIMARY)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                val v = sizeVals[s?.progress ?: 1]
                if (Math.abs(v - prefs.getFloat("fontScale", 1f)) > 0.01f) {
                    prefs.edit().putFloat("fontScale", v).apply()
                    changed()
                }
            }
        })
        frow.addView(sb, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        frow.addView(label("A", 24f, Ui.TEXT, true))
        box.addView(frow, lp())

        // Other
        section(tl("Other"))
        box.addView(groupCard(listOf(
            toggleRow(R.drawable.ic_text, tl("Show Tool Descriptions"), tl("Show helpful description for each tool"), "toolDesc", true),
            toggleRow(R.drawable.ic_vibrate, tl("Vibration on Action"), "", "vibAction", true),
            toggleRow(R.drawable.ic_exit, tl("Confirm Before Exit"), "", "confirmExit", false),
            toggleRow(R.drawable.ic_sparkle, tl("Animations"), "", "anim", true)
        )), lp())
    }
}
