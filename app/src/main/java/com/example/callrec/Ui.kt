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
    var PRIMARY: Int = Color.parseColor("#2D5BE3"); private set
    var BG: Int = Color.parseColor("#F1F2F4"); private set
    var CARD: Int = Color.WHITE; private set
    var TEXT: Int = Color.parseColor("#14161A"); private set
    var MUTED: Int = Color.parseColor("#6E737A"); private set
    var GREEN: Int = Color.parseColor("#2E8B57"); private set
    var RED: Int = Color.parseColor("#C0392B"); private set
    var LINE: Int = Color.parseColor("#E4E6EA"); private set
    var ICON: Int = Color.parseColor("#3A3F46"); private set
    var NAV: Int = Color.WHITE; private set
    var GREY: Int = Color.parseColor("#B9BEC6"); private set
    var CHEV: Int = Color.parseColor("#B3B8C0"); private set
    var KEY_OP: Int = Color.parseColor("#DDE6FB"); private set
    var KEY_FN: Int = Color.parseColor("#E8EAEE"); private set
    var PREVIEW: Int = Color.parseColor("#ECEEF1"); private set
    var dark = false; private set
    var bangla = true; private set

    /** Bumped when the look changes, so screens already open rebuild themselves. */
    var stamp = 0

    /** Reads the saved look (theme, digits) and sets every colour. Call before building a screen. */
    fun apply(ctx: Context) {
        val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val theme = p.getInt("theme", 0) // 0 follow phone, 1 light, 2 dark
        val night = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        dark = theme == 2 || (theme == 0 && night)
        bangla = p.getBoolean("digitsBn", true)
        if (dark) {
            PRIMARY = Color.parseColor("#5B84F2"); BG = Color.parseColor("#0F1115"); CARD = Color.parseColor("#1A1D23")
            TEXT = Color.parseColor("#ECEFF3"); MUTED = Color.parseColor("#9AA1AB"); LINE = Color.parseColor("#2A2E36")
            ICON = Color.parseColor("#C9CFD8"); NAV = Color.parseColor("#14171C"); GREY = Color.parseColor("#3A3F4A")
            CHEV = Color.parseColor("#5A6070"); KEY_OP = Color.parseColor("#26324F"); KEY_FN = Color.parseColor("#262A32")
            PREVIEW = Color.parseColor("#22262D"); GREEN = Color.parseColor("#3FAE76"); RED = Color.parseColor("#E5624F")
        } else {
            PRIMARY = Color.parseColor("#2D5BE3"); BG = Color.parseColor("#F1F2F4"); CARD = Color.WHITE
            TEXT = Color.parseColor("#14161A"); MUTED = Color.parseColor("#6E737A"); LINE = Color.parseColor("#E4E6EA")
            ICON = Color.parseColor("#3A3F46"); NAV = Color.WHITE; GREY = Color.parseColor("#B9BEC6")
            CHEV = Color.parseColor("#B3B8C0"); KEY_OP = Color.parseColor("#DDE6FB"); KEY_FN = Color.parseColor("#E8EAEE")
            PREVIEW = Color.parseColor("#ECEEF1"); GREEN = Color.parseColor("#2E8B57"); RED = Color.parseColor("#C0392B")
        }
    }
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
    c.background = bg(Ui.CARD, 20, Ui.LINE, 1)
    c.setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
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
    t.background = bg(color, 16)
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

/** Standard screen: plain large title on the grey background, optional back arrow, content below, optional floating button. */
@Suppress("DEPRECATION")
fun Activity.setScreen(title: String, content: View, fab: View? = null, back: Boolean = true, bottom: View? = null) {
    window.statusBarColor = Ui.BG
    window.navigationBarColor = Ui.NAV
    window.decorView.systemUiVisibility = if (Ui.dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    val root = FrameLayout(this)
    root.setBackgroundColor(Ui.BG)

    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL

    val bar = LinearLayout(this)
    bar.orientation = LinearLayout.HORIZONTAL
    bar.gravity = Gravity.CENTER_VERTICAL
    bar.setPadding(dp(if (back) 8 else 20), dp(if (back) 10 else 22), dp(16), dp(if (back) 6 else 10))
    if (back) {
        val box = LinearLayout(this)
        box.gravity = Gravity.CENTER
        box.addView(iconView(R.drawable.ic_back, 24, Ui.TEXT))
        box.setOnClickListener { finish() }
        bar.addView(box, LinearLayout.LayoutParams(dp(44), dp(44)))
    }
    val tv = label(title, if (back) 22f else 32f, Ui.TEXT, true)
    tv.maxLines = 1
    tv.ellipsize = android.text.TextUtils.TruncateAt.END
    bar.addView(tv)
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
    if (!Ui.bangla) return s
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
    val grey = Ui.GREY
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
    val wrap = LinearLayout(this)
    wrap.orientation = LinearLayout.VERTICAL
    wrap.setBackgroundColor(Ui.NAV)
    val line = View(this)
    line.setBackgroundColor(Ui.LINE)
    wrap.addView(line, lp(height = 1))
    val bar = LinearLayout(this)
    bar.orientation = LinearLayout.HORIZONTAL
    bar.setPadding(dp(8), dp(8), dp(8), dp(8))
    wrap.addView(bar, lp())
    val names = listOf("হোম", "সব টুল", "সেটিংস")
    val icons = listOf(R.drawable.ic_home, R.drawable.ic_grid, R.drawable.ic_lock)
    for (i in 0..2) {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.gravity = Gravity.CENTER
        item.minimumHeight = dp(52)
        val on = i == selected
        val color = if (on) Ui.PRIMARY else Ui.MUTED
        item.addView(iconView(icons[i], 26, color))
        val tv = label(names[i], 13f, color)
        if (on) tv.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        item.addView(tv, lp(t = 2))
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
    return wrap
}

/** One tappable row: icon on the left, title with a grey line below it, optional extra view, chevron on the right. */
fun Context.listRow(
    icon: Int,
    title: String,
    sub: String = "",
    titleColor: Int = Ui.TEXT,
    extra: View? = null,
    chevron: Boolean = true
): LinearLayout {
    val r = LinearLayout(this)
    r.orientation = LinearLayout.HORIZONTAL
    r.gravity = Gravity.CENTER_VERTICAL
    r.setPadding(dp(18), dp(15), dp(16), dp(15))
    r.addView(iconView(icon, 26, Ui.ICON), lp(width = dp(26), height = dp(26), r = 16))
    val mid = LinearLayout(this)
    mid.orientation = LinearLayout.VERTICAL
    val t = label(title, 17f, titleColor, true)
    t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    mid.addView(t)
    if (sub.isNotEmpty()) mid.addView(label(sub, 14f, Ui.MUTED), lp(t = 2))
    r.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    if (extra != null) r.addView(extra, lp(width = ViewGroup.LayoutParams.WRAP_CONTENT, l = 8))
    if (chevron) r.addView(iconView(R.drawable.ic_chevron, 20, Ui.CHEV), lp(width = dp(20), height = dp(20), l = 8))
    return r
}

/** A white rounded card holding several rows, with a thin line between them. */
fun Context.groupCard(rows: List<View>): LinearLayout {
    val c = LinearLayout(this)
    c.orientation = LinearLayout.VERTICAL
    c.background = bg(Ui.CARD, 20, Ui.LINE, 1)
    for ((i, v) in rows.withIndex()) {
        if (i > 0) {
            val d = View(this)
            d.setBackgroundColor(Ui.LINE)
            val p = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
            p.setMargins(dp(60), 0, 0, 0)
            c.addView(d, p)
        }
        c.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    return c
}
