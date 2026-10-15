package com.example.callrec

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Colours shared by every screen. */
object Ui {
    val PRIMARY: Int = Color.parseColor("#1E6B52")
    val BG: Int = Color.parseColor("#F3F5F4")
    val CARD: Int = Color.WHITE
    val TEXT: Int = Color.parseColor("#1B1F1D")
    val MUTED: Int = Color.parseColor("#6B7570")
    val GREEN: Int = Color.parseColor("#2E8B57")
    val RED: Int = Color.parseColor("#C0392B")
    val LINE: Int = Color.parseColor("#E1E6E3")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun Context.bg(color: Int, radiusDp: Int = 14, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable {
    val d = GradientDrawable()
    d.setColor(color)
    d.cornerRadius = dp(radiusDp).toFloat()
    if (strokeDp > 0) d.setStroke(dp(strokeDp), strokeColor)
    return d
}

fun Context.label(text: String, sp: Float = 15f, color: Int = Ui.TEXT, bold: Boolean = false): TextView {
    val t = TextView(this)
    t.text = text
    t.textSize = sp
    t.setTextColor(color)
    if (bold) t.typeface = Typeface.DEFAULT_BOLD
    return t
}

fun Context.card(pad: Int = 16): LinearLayout {
    val c = LinearLayout(this)
    c.orientation = LinearLayout.VERTICAL
    c.background = bg(Ui.CARD)
    c.setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
    c.elevation = dp(1).toFloat()
    return c
}

/** LinearLayout params with margins given in dp. */
fun Context.lp(
    width: Int = ViewGroup.LayoutParams.MATCH_PARENT,
    height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
    l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0
): LinearLayout.LayoutParams {
    val p = LinearLayout.LayoutParams(width, height)
    p.setMargins(dp(l), dp(t), dp(r), dp(b))
    return p
}

fun Context.pill(text: String, color: Int = Ui.PRIMARY, fg: Int = Color.WHITE, onClick: () -> Unit): TextView {
    val t = label(text, 15f, fg, true)
    t.gravity = Gravity.CENTER
    t.background = bg(color, 22)
    t.setPadding(dp(18), dp(11), dp(18), dp(11))
    t.setOnClickListener { onClick() }
    return t
}

fun Context.divider(): View {
    val v = View(this)
    v.setBackgroundColor(Ui.LINE)
    v.layoutParams = lp(height = 1)
    return v
}

/** Standard screen: green title bar, optional back arrow, content below, optional floating button. */
fun Activity.setScreen(title: String, content: View, fab: View? = null, back: Boolean = true, bottom: View? = null) {
    window.statusBarColor = Ui.PRIMARY
    val root = FrameLayout(this)
    root.setBackgroundColor(Ui.BG)

    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL

    val bar = LinearLayout(this)
    bar.orientation = LinearLayout.HORIZONTAL
    bar.gravity = Gravity.CENTER_VERTICAL
    bar.setBackgroundColor(Ui.PRIMARY)
    bar.setPadding(dp(if (back) 4 else 20), dp(8), dp(16), dp(8))
    bar.elevation = dp(3).toFloat()
    if (back) {
        val b = label("←", 24f, Color.WHITE, true)
        b.setPadding(dp(14), dp(6), dp(14), dp(6))
        b.setOnClickListener { finish() }
        bar.addView(b)
    }
    bar.addView(label(title, 19f, Color.WHITE, true))
    col.addView(bar, lp())

    content.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
    col.addView(content)
    if (bottom != null) col.addView(bottom, lp())
    root.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

    if (fab != null) {
        val fp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        fp.gravity = Gravity.BOTTOM or Gravity.END
        fp.setMargins(0, 0, dp(18), dp(22))
        fab.elevation = dp(6).toFloat()
        root.addView(fab, fp)
    }
    setContentView(root)
}

// ---------- Bangla number helpers ----------

fun bn(s: String): String {
    val sb = StringBuilder()
    for (ch in s) sb.append(if (ch in '0'..'9') ('০' + (ch - '0')) else ch)
    return sb.toString()
}

/** Turns Bangla digits typed on a Bangla keyboard back into ASCII digits for parsing. */
fun asciiDigits(s: String): String {
    val sb = StringBuilder()
    for (ch in s) sb.append(if (ch in '০'..'৯') ('0' + (ch - '০')) else ch)
    return sb.toString()
}

/** 1234567 -> 12,34,567 (lakh / crore grouping). */
fun groupIndian(n: Long): String {
    val neg = n < 0
    val s = Math.abs(n).toString()
    val out: String
    if (s.length <= 3) {
        out = s
    } else {
        val last3 = s.substring(s.length - 3)
        var rest = s.substring(0, s.length - 3)
        val parts = ArrayList<String>()
        while (rest.length > 2) {
            parts.add(0, rest.substring(rest.length - 2))
            rest = rest.substring(0, rest.length - 2)
        }
        if (rest.isNotEmpty()) parts.add(0, rest)
        out = parts.joinToString(",") + "," + last3
    }
    return (if (neg) "-" else "") + out
}

/** 12500.5 -> ৳১২,৫০০.৫০ */
fun taka(d: Double): String {
    val neg = d < 0
    val a = Math.abs(d)
    var whole = a.toLong()
    var paisa = Math.round((a - whole) * 100).toInt()
    if (paisa >= 100) {
        whole += 1
        paisa = 0
    }
    var s = groupIndian(whole)
    if (paisa != 0) s += "." + String.format(Locale.US, "%02d", paisa)
    return bn((if (neg) "-" else "") + "৳" + s)
}

val BN_MONTHS = listOf(
    "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
    "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
)

// ---------- Date helpers and small shared widgets ----------

/** Local midnight of today, in epoch millis. */
fun todayStart(): Long {
    val c = java.util.Calendar.getInstance()
    c.set(java.util.Calendar.HOUR_OF_DAY, 0)
    c.set(java.util.Calendar.MINUTE, 0)
    c.set(java.util.Calendar.SECOND, 0)
    c.set(java.util.Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** 1697000000000 -> ১৪ অক্টোবর ২০২৬ */
fun dateText(ms: Long): String {
    val c = java.util.Calendar.getInstance()
    c.timeInMillis = ms
    return bn(c.get(java.util.Calendar.DAY_OF_MONTH).toString()) + " " +
        BN_MONTHS[c.get(java.util.Calendar.MONTH)] + " " + bn(c.get(java.util.Calendar.YEAR).toString())
}

/** Two-way coloured switch used inside dialogs. onChange(true) means the second tab was picked. */
fun Context.dialogTabs(
    a: String, b: String, colorA: Int, colorB: Int, startB: Boolean, onChange: (Boolean) -> Unit
): LinearLayout {
    val row = LinearLayout(this)
    row.orientation = LinearLayout.HORIZONTAL
    val ta = label(a, 15f, Color.WHITE, true)
    val tb = label(b, 15f, Color.WHITE, true)
    val grey = Color.parseColor("#B8C0BC")
    fun style(isB: Boolean) {
        ta.background = bg(if (!isB) colorA else grey, 10)
        tb.background = bg(if (isB) colorB else grey, 10)
    }
    for (t in listOf(ta, tb)) {
        t.gravity = Gravity.CENTER
        t.setPadding(0, dp(10), 0, dp(10))
        row.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }
    style(startB)
    ta.setOnClickListener { style(false); onChange(false) }
    tb.setOnClickListener { style(true); onChange(true) }
    return row
}

// ---------- More shared helpers ----------

fun android.widget.EditText.onChange(f: () -> Unit) {
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            f()
        }
    })
}

/** Plain decimal text with trailing zeros removed, e.g. 2.50000 -> 2.5 */
fun fmtNum(d: Double, scale: Int): String {
    if (d.isNaN() || d.isInfinite()) return "—"
    return java.math.BigDecimal.valueOf(d).setScale(scale, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros().toPlainString()
}

fun Context.copyText(text: String) {
    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("text", text))
    android.widget.Toast.makeText(this, "কপি হয়েছে", android.widget.Toast.LENGTH_SHORT).show()
}

// ---------- Icons, wrapping layout, result card, bottom navigation ----------

fun Context.iconView(res: Int, sizeDp: Int, tint: Int): android.widget.ImageView {
    val v = android.widget.ImageView(this)
    v.setImageResource(res)
    v.imageTintList = android.content.res.ColorStateList.valueOf(tint)
    v.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    return v
}

/** A rounded tinted square holding one icon. */
fun Context.iconBox(res: Int, tint: Int, fg: Int, boxDp: Int, iconDp: Int, radiusDp: Int = 12, endMarginDp: Int = 0): LinearLayout {
    val b = LinearLayout(this)
    b.gravity = Gravity.CENTER
    b.background = bg(tint, radiusDp)
    b.addView(iconView(res, iconDp, fg))
    val p = LinearLayout.LayoutParams(dp(boxDp), dp(boxDp))
    p.setMargins(0, 0, dp(endMarginDp), 0)
    b.layoutParams = p
    return b
}

/** Children flow left to right and wrap onto new lines. */
class FlowLayout(ctx: Context) : ViewGroup(ctx) {
    private val gap = ctx.dp(8)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            c.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val w = c.measuredWidth
            val h = c.measuredHeight
            if (x > 0 && x + w > maxW) {
                x = 0
                y += rowH + gap
                rowH = 0
            }
            x += w + gap
            rowH = maxOf(rowH, h)
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), y + rowH + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            val w = c.measuredWidth
            val h = c.measuredHeight
            if (x > 0 && x + w > maxW) {
                x = 0
                y += rowH + gap
                rowH = 0
            }
            c.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + w, paddingTop + y + h)
            x += w + gap
            rowH = maxOf(rowH, h)
        }
    }
}

/** A card with a small title, a selectable value and a copy button. Returns the card and the value view. */
fun Context.resultCard(title: String): Pair<LinearLayout, TextView> {
    val c = card(14)
    c.addView(label(title, 12f, Ui.MUTED, true))
    val v = label("", 15f, Ui.TEXT)
    v.setTextIsSelectable(true)
    c.addView(v, lp(t = 4, b = 8))
    c.addView(pill("কপি", Ui.BG, Ui.PRIMARY) { copyText(v.text.toString()) })
    return Pair(c, v)
}

/** Bottom menu: 0 = হোম, 1 = সব টুল, 2 = সেটিংস. */
fun Activity.navBar(selected: Int): View {
    val bar = LinearLayout(this)
    bar.orientation = LinearLayout.HORIZONTAL
    bar.setBackgroundColor(Color.WHITE)
    bar.elevation = dp(8).toFloat()
    bar.setPadding(dp(8), dp(6), dp(8), dp(8))
    val names = listOf("হোম", "সব টুল", "সেটিংস")
    val icons = listOf(R.drawable.ic_home, R.drawable.ic_grid, R.drawable.ic_lock)
    for (i in 0..2) {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.gravity = Gravity.CENTER
        item.minimumHeight = dp(48)
        val on = i == selected
        val color = if (on) Ui.PRIMARY else Ui.MUTED
        item.addView(iconView(icons[i], 24, color))
        item.addView(label(names[i], 12f, color, on))
        item.setOnClickListener {
            if (!on) {
                val intent = when (i) {
                    0 -> Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    1 -> Intent(this, ToolListActivity::class.java).putExtra("cat", "all")
                    else -> Intent(this, SettingsActivity::class.java)
                }
                startActivity(intent)
            }
        }
        bar.addView(item, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }
    return bar
}
