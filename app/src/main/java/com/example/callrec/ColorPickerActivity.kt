package com.example.callrec

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/** কালার পিকার: touch a picture to read the colour under the finger. */
class ColorPickerActivity : ImageToolBase() {

    private val history = ArrayList<Int>()
    private var current = Color.parseColor("#287A70")

    private lateinit var swatch: View
    private lateinit var hexV: TextView
    private lateinit var rgbV: TextView
    private lateinit var hslV: TextView
    private lateinit var histBox: FlowLayout

    init {
        maxSide = 2048
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scroll.addView(box)

        box.addView(pickCard(240), lp(b = 12))
        preview.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    sample(e.x, e.y)
                }
                MotionEvent.ACTION_UP -> {
                    addHistory(current)
                }
            }
            bmp != null
        }
        box.addView(label(tr("ছবি বাছলে তার উপর আঙুল রাখুন বা টেনে রঙ নিন; অথবা সরাসরি HEX লিখুন"), 12f, Ui.MUTED), lp(l = 4, b = 10))

        val sw = LinearLayout(this)
        sw.orientation = LinearLayout.VERTICAL
        sw.setPadding(dp(16), dp(14), dp(16), dp(16))
        capV = label(tr("নির্বাচিত রঙ").uppercase(), 11f, Color.WHITE, true)
        hexV = label("", 28f, Color.WHITE, true)
        rgbV = label("", 12f, Color.WHITE)
        sw.addView(capV)
        sw.addView(hexV, lp(t = 22))
        sw.addView(rgbV, lp(t = 2))
        swatch = sw
        box.addView(sw, lp(b = 12))

        hexIn = EditText(this)
        hexIn.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        hexIn.isSingleLine = true
        hexIn.onChange {
            if (settingHex) return@onChange
            var t = hexIn.text.toString().trim().removePrefix("#")
            if (t.length == 3) t = t.map { "$it$it" }.joinToString("")
            if (t.length == 6) try {
                current = Color.parseColor("#$t")
                settingHex = true
                show(current, false)
                settingHex = false
            } catch (e: Throwable) { }
        }
        box.addView(fieldBox(tr("HEX মান"), hexIn), lp(b = 12))

        box.addView(sectionLabel(tr("রঙের ফরম্যাট"), 2))
        val fm = card(8)
        rgbF = label("", 14f, Ui.TEXT, true)
        hslV = label("", 14f, Ui.TEXT, true)
        cmykV = label("", 14f, Ui.TEXT, true)
        for ((i, p) in listOf(Pair("RGB", rgbF), Pair("HSL", hslV), Pair("CMYK", cmykV)).withIndex()) {
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(4), dp(12), dp(4), dp(12))
            r.addView(label(p.first, 13f, Ui.PRIMARY, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(p.second)
            r.setOnClickListener { copyText(p.second.text.toString()) }
            fm.addView(r)
            if (i < 2) fm.addView(View(this).also { it.setBackgroundColor(Ui.LINE) }, lp(height = 1))
        }
        box.addView(fm, lp(b = 12))

        box.addView(sectionLabel(tr("সাম্প্রতিক প্যালেট"), 2))
        histBox = FlowLayout(this)
        box.addView(histBox, lp())

        screenWithBottom(tr("কালার পিকার"), scroll, primaryButton(tr("HEX কপি")) { copyText(hex(current)) })
        show(current)
    }

    private lateinit var capV: TextView
    private lateinit var hexIn: EditText
    private lateinit var rgbF: TextView
    private lateinit var cmykV: TextView
    private var settingHex = false

    override fun onImageLoaded() {
        info.text = srcName + " · " + bn(bmp!!.width.toString()) + "×" + bn(bmp!!.height.toString())
    }

    private fun sample(x: Float, y: Float) {
        val b = bmp ?: return
        val d = preview.drawable ?: return
        val inv = Matrix()
        if (!preview.imageMatrix.invert(inv)) return
        val p = floatArrayOf(x, y)
        inv.mapPoints(p)
        val ix = p[0].toInt()
        val iy = p[1].toInt()
        if (ix < 0 || iy < 0 || ix >= b.width || iy >= b.height || d.intrinsicWidth <= 0) return
        val px = b.getPixel(ix, iy)
        current = Color.rgb(Color.red(px), Color.green(px), Color.blue(px))
        show(current)
    }

    private fun addHistory(c: Int) {
        if (bmp == null) return
        history.remove(c)
        history.add(0, c)
        while (history.size > 12) history.removeAt(history.size - 1)
        buildHistory()
    }

    private fun buildHistory() {
        histBox.removeAllViews()
        for (col in history) {
            val v = View(this)
            v.background = bg(col, 12, Ui.LINE, 1)
            v.setOnClickListener {
                current = col
                show(col)
            }
            val p = LinearLayout.LayoutParams(dp(44), dp(44))
            p.setMargins(0, 0, dp(8), dp(8))
            v.layoutParams = p
            histBox.addView(v)
        }
    }

    private fun show(c: Int, setField: Boolean = true) {
        swatch.background = bg(c, 14, Ui.LINE, 1)
        val lum = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0
        val fg = if (lum > 0.65) Color.parseColor("#16212B") else Color.WHITE
        capV.setTextColor(fg); hexV.setTextColor(fg); rgbV.setTextColor(fg)
        hexV.text = hex(c)
        rgbV.text = "RGB " + Color.red(c) + " · " + Color.green(c) + " · " + Color.blue(c)
        rgbF.text = Color.red(c).toString() + ", " + Color.green(c) + ", " + Color.blue(c)
        hslV.text = hslText(c).removePrefix("hsl(").removeSuffix(")")
        cmykV.text = cmykText(c)
        if (setField) { settingHex = true; hexIn.setText(hex(c)); settingHex = false }
    }

    private fun cmykText(c: Int): String {
        val r = Color.red(c) / 255f
        val g = Color.green(c) / 255f
        val b = Color.blue(c) / 255f
        val k = 1f - Math.max(r, Math.max(g, b))
        if (k >= 1f) return "0, 0, 0, 100"
        val cy = (1f - r - k) / (1f - k)
        val m = (1f - g - k) / (1f - k)
        val y = (1f - b - k) / (1f - k)
        return Math.round(cy * 100).toString() + ", " + Math.round(m * 100) + ", " + Math.round(y * 100) + ", " + Math.round(k * 100)
    }

    private fun hex(c: Int) = String.format(Locale.US, "#%02X%02X%02X", Color.red(c), Color.green(c), Color.blue(c))
    private fun rgbText(c: Int) = "rgb(" + Color.red(c) + ", " + Color.green(c) + ", " + Color.blue(c) + ")"
    private fun hslText(c: Int): String {
        val hsv = FloatArray(3)
        val r = Color.red(c) / 255f
        val g = Color.green(c) / 255f
        val b = Color.blue(c) / 255f
        val mx = Math.max(r, Math.max(g, b))
        val mn = Math.min(r, Math.min(g, b))
        val l = (mx + mn) / 2f
        val d = mx - mn
        var s = 0f
        var h = 0f
        if (d != 0f) {
            s = d / (1f - Math.abs(2f * l - 1f))
            h = (when (mx) {
                r -> ((g - b) / d) % 6f
                g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            }) * 60f
            if (h < 0) h += 360f
        }
        hsv[0] = h
        return "hsl(" + Math.round(h) + ", " + Math.round(s * 100) + "%, " + Math.round(l * 100) + "%)"
    }
}
