package com.example.callrec

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Shared pieces for the step-by-step PDF screens (header, big button, thumbnails, progress ring). */

/** Back arrow, title and a small grey line under it. [right] is an optional view placed at the far end. */
fun Activity.pdfHeader(title: String, sub: String, onBack: (() -> Unit)?, right: View? = null): View {
    val bar = LinearLayout(this)
    bar.orientation = LinearLayout.HORIZONTAL
    bar.gravity = Gravity.CENTER_VERTICAL
    bar.setPadding(dp(8), dp(10), dp(16), dp(8))
    if (onBack != null) {
        val b = LinearLayout(this)
        b.gravity = Gravity.CENTER
        b.addView(iconView(R.drawable.ic_back, 24, Ui.TEXT))
        b.setOnClickListener { onBack() }
        bar.addView(b, LinearLayout.LayoutParams(dp(44), dp(44)))
    } else {
        bar.setPadding(dp(20), dp(10), dp(16), dp(8))
    }
    val col = LinearLayout(this)
    col.orientation = LinearLayout.VERTICAL
    val t = label(title, 19f, Ui.TEXT, true)
    t.maxLines = 1
    col.addView(t)
    if (sub.isNotEmpty()) {
        val s = label(sub, 12f, Ui.MUTED)
        s.maxLines = 1
        col.addView(s)
    }
    bar.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    if (right != null) bar.addView(right)
    return bar
}

/** The wide accent button used at the bottom of every step. */
fun Activity.bigButton(text: String, enabled: Boolean = true, onClick: () -> Unit): TextView {
    val b = label(text, 16f, Color.WHITE, true)
    b.gravity = Gravity.CENTER
    b.background = bg(Ui.PRIMARY, 14)
    b.alpha = if (enabled) 1f else 0.4f
    b.setOnClickListener { if (enabled) { it.tapFx(); onClick() } }
    return b
}

/** A quiet button with an accent outline. */
fun Activity.outlineButton(text: String, onClick: () -> Unit): TextView {
    val b = label(text, 15f, Ui.PRIMARY, true)
    b.gravity = Gravity.CENTER
    b.background = bg(Ui.CARD, 14, Ui.PRIMARY, 1)
    b.setOnClickListener { it.tapFx(); onClick() }
    return b
}

/** The bar that holds the bottom buttons, with a thin line above it. */
fun Activity.bottomBar(content: View): View {
    val w = LinearLayout(this)
    w.orientation = LinearLayout.VERTICAL
    w.setBackgroundColor(Ui.BG)
    val line = View(this)
    line.setBackgroundColor(Ui.LINE)
    w.addView(line, lp(height = 1))
    content.setPadding(dp(16), dp(10), dp(16), dp(12))
    w.addView(content, lp())
    return w
}

/** Renders the first page of [uri] at roughly [wpx] pixels wide, plus the page count. Calls [cb] on the main thread. */
fun Activity.pdfThumb(uri: Uri, wpx: Int, cb: (Bitmap?, Int) -> Unit) {
    Thread {
        var bmp: Bitmap? = null
        var pages = 0
        try {
            val pfd: ParcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r") ?: throw IllegalStateException()
            pfd.use {
                val r = PdfRenderer(it)
                pages = r.pageCount
                if (pages > 0) {
                    val p = r.openPage(0)
                    val h = Math.max(1, (wpx.toFloat() * p.height / p.width).toInt())
                    val b = Bitmap.createBitmap(wpx, h, Bitmap.Config.ARGB_8888)
                    b.eraseColor(Color.WHITE)
                    p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    p.close()
                    bmp = b
                }
                r.close()
            }
        } catch (e: Throwable) { }
        val fb = bmp
        val fp = pages
        runOnUiThread { cb(fb, fp) }
    }.start()
}

/** A small framed thumbnail of a PDF's first page. */
fun Activity.thumbView(w: Int, h: Int): ImageView {
    val iv = ImageView(this)
    iv.scaleType = ImageView.ScaleType.FIT_CENTER
    iv.background = bg(Color.WHITE, 6, Ui.LINE, 1)
    iv.setPadding(dp(2), dp(2), dp(2), dp(2))
    iv.layoutParams = LinearLayout.LayoutParams(dp(w), dp(h))
    return iv
}

/** A circle with the percent in the middle. */
class PercentRing(ctx: Context) : View(ctx) {
    var percent = 0
        set(v) { field = v.coerceIn(0, 100); invalidate() }
    var color = Ui.PRIMARY
    var textColor = Ui.TEXT
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val sw = 9f * d
        val box = RectF(sw, sw, width - sw, height - sw)
        p.style = Paint.Style.STROKE
        p.strokeWidth = sw
        p.strokeCap = Paint.Cap.ROUND
        p.color = Ui.mix(color, Ui.BG, 0.15f)
        c.drawArc(box, 0f, 360f, false, p)
        p.color = color
        c.drawArc(box, -90f, 360f * percent / 100f, false, p)
        p.style = Paint.Style.FILL
        p.color = textColor
        p.textSize = 30f * d
        p.isFakeBoldText = true
        val s = bn(percent.toString()) + "%"
        c.drawText(s, (width - p.measureText(s)) / 2f, height / 2f + 10f * d, p)
    }
}
