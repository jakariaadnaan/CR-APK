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
    private var current = Color.parseColor("#2D5BE3")

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
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)

        box.addView(pickCard(300), lp(b = 12))
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
        box.addView(label(tr("ছবির উপর আঙুল রাখুন বা টেনে নিন"), 12f, Ui.MUTED), lp(l = 4, b = 10))

        val c = card(14)
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        swatch = View(this)
        top.addView(swatch, lp(width = dp(72), height = dp(72), r = 14))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        hexV = label("", 20f, Ui.TEXT, true)
        rgbV = label("", 14f, Ui.MUTED)
        hslV = label("", 14f, Ui.MUTED)
        col.addView(hexV)
        col.addView(rgbV)
        col.addView(hslV)
        top.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(top, lp(b = 12))
        val btns = LinearLayout(this)
        btns.orientation = LinearLayout.HORIZONTAL
        btns.addView(pill(tr("HEX কপি")) { copyText(hex(current)) }, lp(r = 8))
        btns.addView(pill(tr("RGB কপি"), Ui.BG, Ui.PRIMARY) { copyText(rgbText(current)) }, lp(r = 8))
        btns.addView(pill(tr("HSL কপি"), Ui.BG, Ui.PRIMARY) { copyText(hslText(current)) })
        c.addView(btns, lp())
        box.addView(c, lp(b = 12))

        val h = card(14)
        h.addView(label(tr("বাছা রঙ"), 12f, Ui.MUTED, true), lp(b = 8))
        histBox = FlowLayout(this)
        h.addView(histBox, lp())
        box.addView(h, lp())

        setScreen(tr("কালার পিকার"), scroll)
        show(current)
    }

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

    private fun show(c: Int) {
        swatch.background = bg(c, 14, Ui.LINE, 1)
        hexV.text = hex(c)
        rgbV.text = rgbText(c)
        hslV.text = hslText(c)
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
