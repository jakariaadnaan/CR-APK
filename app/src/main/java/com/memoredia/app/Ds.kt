package com.memoredia.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Decides which screens use the calm tool look from the design mockups (home, list and settings keep the app look). */
object ToolLook {
    private val extra = setOf("BudgetActivity", "ActivityChart")

    fun isTool(a: Activity): Boolean {
        val c = a.javaClass
        if (extra.contains(c.simpleName)) return true
        if (Modes.groups.any { g -> g.modes.any { it.second == c } }) return true
        return Registry.tools.any { it.target == c }
    }
}

/** Small grey section title above a block, like "Output options" in the mockups. */
fun Context.sectionLabel(text: String, topDp: Int = 14): TextView {
    val t = label(text, 13f, Ui.TEXT, true)
    t.layoutParams = lp(t = topDp, b = 8)
    return t
}

/** A tiny muted caption, e.g. the "AMOUNT" above an input. */
fun Context.captionLabel(text: String): TextView {
    val t = label(text, 11f, Ui.MUTED, false)
    t.letterSpacing = 0.04f
    t.layoutParams = lp(b = 4)
    return t
}

/** Rounded choice chip: the picked one has the accent tint and outline, the others are white with a thin line. */
fun Context.choiceChip(text: String, selected: Boolean, onClick: () -> Unit): TextView {
    val t = label(text, 13f, if (selected) Ui.PRIMARY else Ui.MUTED, selected)
    t.gravity = Gravity.CENTER
    t.setPadding(dp(16), dp(8), dp(16), dp(8))
    t.background = if (selected) bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.88f), 20, Ui.mix(Ui.PRIMARY, Ui.CARD, 0.5f), 1)
    else bg(Ui.CARD, 20, Ui.LINE, 1)
    t.setOnClickListener { it.tapFx(); onClick() }
    return t
}

/** A row of chips that wraps; [onPick] gets the tapped index. Rebuilds itself to show the new choice. */
fun Context.chipRow(items: List<String>, selected: Int, onPick: (Int) -> Unit): FlowLayout {
    val f = FlowLayout(this)
    fun build(sel: Int) {
        f.removeAllViews()
        for ((i, s) in items.withIndex()) f.addView(choiceChip(s, i == sel) { build(i); onPick(i) })
    }
    build(selected)
    return f
}

/** The highlighted result card: small accent label, big value, optional muted line below. */
fun Context.resultBox(caption: String, value: String, note: String = "", valueSp: Float = 24f): LinearLayout {
    val c = LinearLayout(this)
    c.orientation = LinearLayout.VERTICAL
    c.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.9f), Ui.cardR, Ui.mix(Ui.PRIMARY, Ui.CARD, 0.6f), 1)
    c.setPadding(dp(16), dp(14), dp(16), dp(14))
    c.addView(label(caption.uppercase(), 11f, Ui.PRIMARY, true).also { it.letterSpacing = 0.04f })
    c.addView(label(value, valueSp, Ui.TEXT, true), lp(t = 6))
    if (note.isNotEmpty()) c.addView(label(note, 12f, Ui.MUTED), lp(t = 10))
    return c
}

/** A small white label+value card, used for pairs such as income and expenses. */
fun Context.statTile(caption: String, value: String, valueColor: Int = Ui.TEXT): LinearLayout {
    val c = LinearLayout(this)
    c.orientation = LinearLayout.VERTICAL
    c.background = bg(Ui.CARD, Ui.cardR, Ui.LINE, 1)
    c.setPadding(dp(14), dp(12), dp(14), dp(12))
    c.addView(label(caption.uppercase(), 11f, Ui.MUTED, false).also { it.letterSpacing = 0.04f })
    c.addView(label(value, 18f, valueColor, true), lp(t = 4))
    return c
}

/** Label above a white bordered input. */
fun Context.fieldBox(caption: String, input: EditText): LinearLayout {
    val c = LinearLayout(this)
    c.orientation = LinearLayout.VERTICAL
    c.addView(captionLabel(caption))
    input.setTextColor(Ui.TEXT)
    input.setHintTextColor(Ui.MUTED)
    input.textSize = 15f
    input.background = bg(Ui.CARD, 10, Ui.LINE, 1)
    input.setPadding(dp(14), dp(13), dp(14), dp(13))
    c.addView(input, lp())
    return c
}

/** Two-column list line (name left, value right) with a thin line under it. */
fun Context.kvRow(name: String, value: String, last: Boolean = false): LinearLayout {
    val r = LinearLayout(this)
    r.orientation = LinearLayout.VERTICAL
    val row = LinearLayout(this)
    row.gravity = Gravity.CENTER_VERTICAL
    row.setPadding(dp(4), dp(12), dp(4), dp(12))
    row.addView(label(name, 14f, Ui.MUTED), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    row.addView(label(value, 14f, Ui.TEXT, true))
    r.addView(row)
    if (!last) r.addView(View(this).also { it.setBackgroundColor(Ui.LINE) }, lp(height = 1))
    return r
}

/** The wide accent button the mockups put at the bottom of every tool. */
fun Context.primaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit): TextView {
    val b = label(text, 16f, Color.WHITE, true)
    b.gravity = Gravity.CENTER
    b.background = bg(Ui.PRIMARY, 10)
    b.alpha = if (enabled) 1f else 0.4f
    b.setOnClickListener { if (enabled) { it.tapFx(); onClick() } }
    return b
}
