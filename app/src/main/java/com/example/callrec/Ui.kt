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
    /** True while a tool screen is showing: it uses the calm teal look from the design mockups. */
    var tool = false; private set
    /** Corner size of cards: tool screens use the tighter mockup corners. */
    val cardR: Int get() = if (tool) 14 else 20

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
        val ai = p.getInt("accent", 0).coerceIn(0, ACCENTS.size)
        PRIMARY = if (ai >= ACCENTS.size) {
            try { Color.parseColor(p.getString("accentHex", "#2D5BE3")) } catch (e: Throwable) { Color.parseColor("#2D5BE3") }
        } else Color.parseColor(if (dark) ACCENTS[ai].dark else ACCENTS[ai].light)
        KEY_OP = if (dark) mix(PRIMARY, CARD, 0.75f) else mix(PRIMARY, Color.WHITE, 0.87f)
    }

    /** Switches every colour to the tool-screen look (white cards, slate text, one teal accent) when [on]. */
    fun applyFor(ctx: Context, on: Boolean) {
        apply(ctx)
        tool = on
        if (!on) return
        val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val custom = p.getInt("accent", 0) != 0
        if (dark) {
            BG = Color.parseColor("#0E1417"); CARD = Color.parseColor("#161E22"); TEXT = Color.parseColor("#E8EEF0")
            MUTED = Color.parseColor("#8C99A1"); LINE = Color.parseColor("#26323A"); ICON = Color.parseColor("#C5D0D5")
            NAV = Color.parseColor("#121A1E"); GREY = Color.parseColor("#33424B"); CHEV = Color.parseColor("#55656F")
            PREVIEW = Color.parseColor("#1D282D"); GREEN = Color.parseColor("#3BA597"); RED = Color.parseColor("#E5624F")
            if (!custom) PRIMARY = Color.parseColor("#3BA597")
            KEY_OP = mix(PRIMARY, CARD, 0.8f); KEY_FN = Color.parseColor("#1D282D")
        } else {
            BG = Color.parseColor("#F4F6F7"); CARD = Color.WHITE; TEXT = Color.parseColor("#16212B")
            MUTED = Color.parseColor("#77838D"); LINE = Color.parseColor("#E3E8EB"); ICON = Color.parseColor("#34414C")
            NAV = Color.WHITE; GREY = Color.parseColor("#C9D1D6"); CHEV = Color.parseColor("#A9B4BB")
            PREVIEW = Color.parseColor("#E8EEF0"); GREEN = Color.parseColor("#287A70"); RED = Color.parseColor("#B5473A")
            if (!custom) PRIMARY = Color.parseColor("#287A70")
            KEY_OP = if (!custom) Color.parseColor("#E7F3F0") else mix(PRIMARY, Color.WHITE, 0.9f); KEY_FN = Color.parseColor("#F4F6F7")
        }
    }

    class Accent(val name: String, val light: String, val dark: String)

    val ACCENTS = listOf(
        Accent("নীল", "#2D5BE3", "#5B84F2"),
        Accent("সবুজ", "#1E8E5A", "#3FB27F"),
        Accent("বেগুনি", "#7A45D6", "#9E78EA"),
        Accent("কমলা", "#E0701A", "#F0903F"),
        Accent("লাল", "#D23B3B", "#EA6666"),
        Accent("টিল", "#0E8A94", "#35B0BA")
    )

    /** Blends [a] towards [b]; [t] is how much of [b] (0..1). */
    fun mix(a: Int, b: Int, t: Float): Int {
        val r = (Color.red(a) * (1 - t) + Color.red(b) * t).toInt()
        val g = (Color.green(a) * (1 - t) + Color.green(b) * t).toInt()
        val bl = (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
        return Color.rgb(r, g, bl)
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
    c.background = bg(Ui.CARD, Ui.cardR, Ui.LINE, 1)
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

/** A styled on/off switch that follows the accent colour. */
fun Context.styledSwitch(on: Boolean): android.widget.Switch {
    val s = android.widget.Switch(this)
    s.isChecked = on
    s.isClickable = false
    styleSwitch(s)
    return s
}

/** A solid accent track when on and a clearly visible grey one when off, with a round white thumb. */
fun Context.styleSwitch(s: android.widget.Switch) {
    fun track(color: Int): android.graphics.drawable.GradientDrawable {
        val g = android.graphics.drawable.GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(13).toFloat()
        g.setSize(dp(48), dp(26))
        return g
    }
    val offColor = if (Ui.dark) Color.parseColor("#5A6070") else Color.parseColor("#B8BDC7")
    val tl = android.graphics.drawable.StateListDrawable()
    tl.addState(intArrayOf(android.R.attr.state_checked), track(Ui.PRIMARY))
    tl.addState(intArrayOf(), track(offColor))
    val th = android.graphics.drawable.GradientDrawable()
    th.shape = android.graphics.drawable.GradientDrawable.OVAL
    th.setColor(Color.WHITE)
    th.setSize(dp(20), dp(20))
    s.thumbTintList = null
    s.trackTintList = null
    s.trackDrawable = tl
    s.thumbDrawable = android.graphics.drawable.InsetDrawable(th, dp(3))
}

/** One short buzz when a job finishes, if "Vibration on Action" is on. */
fun Context.actionBuzz() {
    try {
        if (!getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("vibAction", true)) return
        val v = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        v.vibrate(android.os.VibrationEffect.createOneShot(45, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (e: Throwable) { }
}

/** Small vibration and click sound on a tap, each controlled from Settings. */
fun View.tapFx() {
    try {
        val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (p.getBoolean("haptic", true)) performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        if (p.getBoolean("sound", true)) playSoundEffect(android.view.SoundEffectConstants.CLICK)
    } catch (e: Throwable) { }
}

fun Context.pill(text: String, color: Int = Ui.PRIMARY, fg: Int = Color.WHITE, onClick: () -> Unit): TextView {
    val t = label(text, 15f, fg, true)
    t.gravity = Gravity.CENTER
    t.background = bg(color, if (Ui.tool) 10 else 16)
    t.setPadding(dp(18), dp(11), dp(18), dp(11))
    t.setOnClickListener { t.tapFx(); onClick() }
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
    val tv = label(Modes.title(this, title), if (back) 22f else 32f, Ui.TEXT, true)
    tv.maxLines = 1
    tv.ellipsize = android.text.TextUtils.TruncateAt.END
    val modeChip = Modes.chip(this)
    if (modeChip != null) {
        bar.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(modeChip)
    } else bar.addView(tv)
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
    return bn((if (neg) "-" else "") + tr("৳") + s)
}

val BN_MONTHS: List<String> get() = listOf(
    tr("জানুয়ারি"), tr("ফেব্রুয়ারি"), tr("মার্চ"), tr("এপ্রিল"), tr("মে"), tr("জুন"),
    tr("জুলাই"), tr("আগস্ট"), tr("সেপ্টেম্বর"), tr("অক্টোবর"), tr("নভেম্বর"), tr("ডিসেম্বর")
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
    android.widget.Toast.makeText(this, tr("কপি হয়েছে"), android.widget.Toast.LENGTH_SHORT).show()
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
    c.addView(pill(tr("কপি"), Ui.BG, Ui.PRIMARY) { copyText(v.text.toString()) })
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
    bar.setPadding(dp(8), dp(8), dp(8), dp(10))
    wrap.addView(bar, lp())
    val names = listOf(tr("হোম"), tr("ক্যাটাগরি"), tr("প্রিয়"), tr("সেটিংস"))
    val icons = listOf(R.drawable.ic_home, R.drawable.ic_grid, R.drawable.ic_star, R.drawable.ic_settings)
    for (i in 0..3) {
        val item = LinearLayout(this)
        item.orientation = LinearLayout.VERTICAL
        item.gravity = Gravity.CENTER_HORIZONTAL
        val on = i == selected
        val color = if (on) Ui.PRIMARY else Ui.MUTED
        val pillBox = LinearLayout(this)
        pillBox.gravity = Gravity.CENTER
        if (on) pillBox.background = bg(Ui.KEY_OP, 16)
        pillBox.addView(iconView(icons[i], 24, color))
        item.addView(pillBox, LinearLayout.LayoutParams(dp(60), dp(32)))
        val tv = label(names[i], 12f, color)
        tv.gravity = Gravity.CENTER
        tv.maxLines = 1
        tv.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        item.addView(tv, lp(width = ViewGroup.LayoutParams.WRAP_CONTENT, t = 4))
        item.setOnClickListener {
            item.tapFx()
            if (!on) {
                val intent = when (i) {
                    0 -> Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    1 -> Intent(this, ToolListActivity::class.java).putExtra("cat", "all")
                    2 -> Intent(this, ToolListActivity::class.java).putExtra("cat", "fav")
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
    c.background = bg(Ui.CARD, Ui.cardR, Ui.LINE, 1)
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
