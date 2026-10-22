package com.example.callrec

import android.app.AlertDialog
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.File

/** A freehand pad used to draw a signature. */
class SigPad(ctx: Context) : View(ctx) {
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var any = false

    init {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.parseColor("#12306B")
        setBackgroundColor(Color.WHITE)
    }

    fun clear() { path.reset(); any = false; invalidate() }
    fun isEmpty() = !any

    override fun onDraw(c: Canvas) { c.drawPath(path, paint) }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { parent.requestDisallowInterceptTouchEvent(true); path.moveTo(e.x, e.y); path.lineTo(e.x + 0.1f, e.y + 0.1f); any = true }
            MotionEvent.ACTION_MOVE -> path.lineTo(e.x, e.y)
        }
        invalidate()
        return true
    }

    /** The drawing cropped to its own bounds on a transparent background. */
    fun toBitmap(): Bitmap? {
        if (!any) return null
        val b = RectF()
        path.computeBounds(b, true)
        val pad = 8f
        val w = Math.max(2, (b.width() + pad * 2).toInt())
        val h = Math.max(2, (b.height() + pad * 2).toInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(pad - b.left, pad - b.top)
        c.drawPath(path, paint)
        return bmp
    }
}

/** Shows one PDF page and lets the person tap where a picture (signature / text) should go. */
class PagePreview(ctx: Context) : View(ctx) {
    class Stamp(val page: Int, val bmp: Bitmap, val cx: Float, val cy: Float, val wPts: Float)

    var page: Bitmap? = null
    var pageWPts = 595f
    var cur = 0
    var stamps: List<Stamp> = emptyList()
    var ghost: Bitmap? = null
    var ghostWPts = 100f
    var mx = -1f
    var my = -1f
    private val p = Paint(Paint.FILTER_BITMAP_FLAG)
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG)

    init { mark.color = Color.parseColor("#D9534F"); mark.style = Paint.Style.STROKE; mark.strokeWidth = 3f }

    override fun onMeasure(w: Int, h: Int) {
        val ww = MeasureSpec.getSize(w)
        val b = page
        val hh = if (b == null) ww else (ww.toFloat() * b.height / b.width).toInt()
        setMeasuredDimension(ww, hh)
    }

    private fun drawBmp(c: Canvas, b: Bitmap, cx: Float, cy: Float, wPts: Float, a: Int) {
        val s = width / pageWPts
        val w = wPts * s
        val h = w * b.height / b.width
        p.alpha = a
        val x = cx * width - w / 2f
        val y = cy * height - h / 2f
        c.drawBitmap(b, null, RectF(x, y, x + w, y + h), p)
    }

    override fun onDraw(c: Canvas) {
        val b = page ?: return
        c.drawBitmap(b, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), p)
        for (s in stamps) if (s.page == cur) drawBmp(c, s.bmp, s.cx, s.cy, s.wPts, 255)
        val g = ghost
        if (g != null && mx >= 0f) {
            drawBmp(c, g, mx, my, ghostWPts, 150)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mark)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) parent.requestDisallowInterceptTouchEvent(true)
        if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
            mx = (e.x / width).coerceIn(0f, 1f)
            my = (e.y / height).coerceIn(0f, 1f)
            invalidate()
        }
        return true
    }
}

/** PDF-এ সই ও লেখা: put a drawn signature or typed text onto pages of a PDF. */
class PdfSignActivity : PdfBase() {
    private val stamps = ArrayList<PagePreview.Stamp>()
    private lateinit var preview: PagePreview
    private lateinit var pageInfo: TextView
    private lateinit var sizeRow: LinearLayout
    private lateinit var count: TextView
    private var pageIdx = 0
    private var sigBmp: Bitmap? = null
    private var kind = 0 // 0 signature, 1 text
    private var sizeLevel = 1
    private var textBmp: Bitmap? = null
    private lateinit var textField: EditText
    private lateinit var kindRow: LinearLayout
    private lateinit var kindBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preview = PagePreview(this)
        pageInfo = label("", 13f, Ui.MUTED, true)
        pageInfo.gravity = Gravity.CENTER

        val nav = card(12)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER_VERTICAL
        val prev = label("◀", 20f, Ui.PRIMARY, true)
        prev.gravity = Gravity.CENTER
        prev.setOnClickListener { if (pageIdx > 0) { pageIdx--; showPage() } }
        val next = label("▶", 20f, Ui.PRIMARY, true)
        next.gravity = Gravity.CENTER
        next.setOnClickListener { if (pageIdx < pages - 1) { pageIdx++; showPage() } }
        nav.addView(prev, lp(width = dp(48), height = dp(40)))
        nav.addView(pageInfo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(next, lp(width = dp(48), height = dp(40)))

        val pv = card(8)
        pv.addView(preview, lp())
        pv.addView(label(tr("পেজে ছুঁয়ে জায়গা ঠিক করুন, তারপর \"বসান\" চাপুন।"), 12f, Ui.MUTED), lp(t = 6))

        kindRow = LinearLayout(this)
        kindBox = LinearLayout(this)
        kindBox.orientation = LinearLayout.VERTICAL
        sizeRow = LinearLayout(this)
        val tools = card(14)
        tools.addView(kindRow)
        tools.addView(kindBox, lp(t = 10))
        tools.addView(sizeRow, lp(t = 10))
        val place = pill(tr("এই জায়গায় বসান")) { place() }
        tools.addView(place, lp(t = 12))
        count = label("", 13f, Ui.MUTED)
        tools.addView(count, lp(t = 8))
        tools.addView(pill(tr("শেষটি বাতিল"), Ui.GREY, Ui.TEXT) {
            if (stamps.isNotEmpty()) { stamps.removeAt(stamps.size - 1); refresh() }
        }, lp(t = 8))

        val act = actionCard(tr("নতুন PDF বানান"), "") { go() }
        screen(tr("PDF-এ সই ও লেখা"), pickCard(), nav, pv, tools, act)
        drawTools()
        refresh()
    }

    private fun drawTools() {
        kindRow.removeAllViews()
        val names = listOf(tr("সই"), tr("লেখা"))
        for (i in names.indices) kindRow.addView(chip(names[i], kind == i) { kind = i; drawTools() }, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, r = 8))
        kindBox.removeAllViews()
        if (kind == 0) {
            kindBox.addView(pill(tr("সই আঁকুন")) { drawSig() })
            kindBox.addView(label(if (sigBmp == null) tr("এখনো সই আঁকা হয়নি") else tr("সই তৈরি আছে"), 12f, Ui.MUTED), lp(t = 6))
        } else {
            textField = EditText(this)
            textField.hint = tr("যা লিখতে চান")
            textField.setTextColor(Ui.TEXT)
            textField.inputType = InputType.TYPE_CLASS_TEXT
            kindBox.addView(textField)
        }
        sizeRow.removeAllViews()
        val sz = listOf(tr("ছোট"), tr("মাঝারি"), tr("বড়"))
        for (i in sz.indices) sizeRow.addView(chip(sz[i], sizeLevel == i) { sizeLevel = i; drawTools() }, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, r = 8))
        updateGhost()
    }

    private fun makeText(s: String): Bitmap {
        val pt = floatArrayOf(11f, 16f, 24f)[sizeLevel]
        val scale = 3f
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tp.textSize = pt * scale
        tp.color = Color.parseColor("#111111")
        val w = Math.max(1, Math.ceil(tp.measureText(s).toDouble()).toInt() + 8)
        val lay = StaticLayout.Builder.obtain(s, 0, s.length, tp, w).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        val bmp = Bitmap.createBitmap(w, Math.max(1, lay.height + 4), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(4f, 2f)
        lay.draw(c)
        return bmp
    }

    private fun currentBmp(): Pair<Bitmap, Float>? {
        if (kind == 0) {
            val b = sigBmp ?: return null
            val frac = floatArrayOf(0.2f, 0.3f, 0.45f)[sizeLevel]
            return Pair(b, preview.pageWPts * frac)
        }
        val s = if (::textField.isInitialized) textField.text.toString().trim() else ""
        if (s.isEmpty()) return null
        val b = makeText(s)
        return Pair(b, b.width / 3f)
    }

    private fun updateGhost() {
        val cb = currentBmp()
        preview.ghost = cb?.first
        preview.ghostWPts = cb?.second ?: 100f
        preview.invalidate()
    }

    private fun drawSig() {
        val pad = SigPad(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(8), dp(16), 0)
        box.addView(pad, lp(height = dp(200)))
        val d = AlertDialog.Builder(this)
            .setTitle(tr("এখানে সই করুন"))
            .setView(box)
            .setPositiveButton(tr("ঠিক আছে")) { _, _ ->
                val b = pad.toBitmap()
                if (b != null) { sigBmp = b; drawTools() }
            }
            .setNeutralButton(tr("আবার আঁকুন"), null)
            .setNegativeButton(tr("বাতিল"), null)
            .create()
        d.show()
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { pad.clear() }
    }

    private fun place() {
        if (!requireFile()) return
        // text may have changed since the ghost was last built
        updateGhost()
        val cb = currentBmp()
        if (cb == null) {
            status.text = if (kind == 0) tr("আগে সই আঁকুন") else tr("আগে লেখাটি লিখুন")
            return
        }
        if (preview.mx < 0f) { status.text = tr("পেজে ছুঁয়ে জায়গা বাছুন"); return }
        stamps.add(PagePreview.Stamp(pageIdx, cb.first, preview.mx, preview.my, cb.second))
        status.text = ""
        refresh()
    }

    private fun refresh() {
        preview.stamps = stamps
        preview.invalidate()
        count.text = tr("বসানো হয়েছে: ") + bn(stamps.size.toString()) + tr("টি")
    }

    override fun onLoaded() {
        stamps.clear()
        pageIdx = 0
        refresh()
        showPage()
    }

    private fun showPage() {
        val f = src ?: return
        if (encrypted) return
        pageInfo.text = bn((pageIdx + 1).toString()) + " / " + bn(pages.toString())
        val idx = pageIdx
        Thread {
            var bmp: Bitmap? = null
            var wp = 595f
            try {
                val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                val r = PdfRenderer(pfd)
                val pg = r.openPage(idx)
                wp = pg.width.toFloat()
                val w = 1000
                val h = Math.max(1, Math.round(pg.height * w / wp))
                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pg.close(); r.close(); pfd.close()
            } catch (e: Throwable) { bmp = null }
            runOnUiThread {
                if (idx == pageIdx) {
                    preview.page = bmp
                    preview.pageWPts = wp
                    preview.cur = idx
                    preview.mx = -1f
                    preview.requestLayout()
                    updateGhost()
                }
            }
        }.start()
    }

    private fun go() {
        if (!requireFile()) return
        if (encrypted) { status.text = tr("আগে PDF আনলক করুন (PDF লক / আনলক টুল)"); return }
        if (stamps.isEmpty()) { status.text = tr("আগে অন্তত একটি সই বা লেখা বসান"); return }
        val list = stamps.toList()
        askCreate(baseOut() + "_signed.pdf") { out ->
            val f = src!!
            work(tr("তৈরি হচ্ছে…")) {
                PDDocument.load(f).use { doc ->
                    for (s in list) {
                        val page = doc.getPage(s.page)
                        val box = page.mediaBox
                        val sc = box.width / preview.pageWPts
                        val w = s.wPts * sc
                        val h = w * s.bmp.height / s.bmp.width
                        val img = LosslessFactory.createFromImage(doc, s.bmp)
                        val x = box.lowerLeftX + s.cx * box.width - w / 2f
                        val y = box.lowerLeftY + (1f - s.cy) * box.height - h / 2f
                        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                            cs.drawImage(img, x, y, w, h)
                        }
                    }
                    saveDoc(doc, out)
                }
                tr("সংরক্ষিত হয়েছে")
            }
        }
    }
}

/** লেখা থেকে PDF: turn typed text (Bangla or English) into an A4 PDF. */
class TextToPdfActivity : GuardedActivity() {
    private lateinit var input: EditText
    private lateinit var status: TextView
    private var sizeLevel = 1
    private lateinit var row: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)
        val c = card(14)
        input = EditText(this)
        input.hint = tr("এখানে লিখুন বা পেস্ট করুন")
        styleInput(input)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.minLines = 8
        input.gravity = Gravity.TOP
        c.addView(input, lp())
        c.addView(label(tr("লেখার আকার"), 12f, Ui.MUTED, true), lp(t = 10))
        row = LinearLayout(this)
        c.addView(row, lp(t = 6))
        box.addView(c, lp(b = 12))
        status = label("", 14f, Ui.TEXT)
        box.addView(statusCard(status))
        setPdfScreen(tr("লেখা থেকে PDF"), scroll, tr("PDF বানান")) { go() }
        chips()
    }

    private fun chips() {
        row.removeAllViews()
        val names = listOf(tr("ছোট"), tr("মাঝারি"), tr("বড়"))
        for (i in names.indices) {
            val on = sizeLevel == i
            val t = label(names[i], 14f, if (on) Color.WHITE else Ui.PRIMARY, true)
            t.gravity = Gravity.CENTER
            t.background = bg(if (on) Ui.PRIMARY else Ui.CARD, 20, Ui.PRIMARY, 1)
            t.setPadding(dp(16), dp(8), dp(16), dp(8))
            t.setOnClickListener { sizeLevel = i; chips() }
            row.addView(t, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, r = 8))
        }
    }

    private fun go() {
        if (input.text.toString().isBlank()) { status.text = tr("আগে কিছু লিখুন"); return }
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        i.putExtra(Intent.EXTRA_TITLE, "text.pdf")
        saveOrAsk(i, 93)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 93 || resultCode != Activity.RESULT_OK) return
        val out = data?.data ?: return
        val text = input.text.toString()
        val size = floatArrayOf(11f, 14f, 18f)[sizeLevel]
        status.text = tr("তৈরি হচ্ছে…")
        Thread {
            val msg = try {
                val pw = 595
                val ph = 842
                val m = 44
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
                tp.textSize = size
                tp.color = Color.BLACK
                val lay = StaticLayout.Builder.obtain(text, 0, text.length, tp, pw - 2 * m).setLineSpacing(2f, 1f).build()
                val avail = ph - 2 * m
                val pdf = PdfDocument()
                var line = 0
                var n = 0
                while (line < lay.lineCount) {
                    val top = lay.getLineTop(line)
                    var end = line
                    while (end + 1 < lay.lineCount && lay.getLineBottom(end + 1) - top <= avail) end++
                    val bottom = lay.getLineBottom(end)
                    n++
                    val pg = pdf.startPage(PdfDocument.PageInfo.Builder(pw, ph, n).create())
                    val c = pg.canvas
                    c.translate(m.toFloat(), m.toFloat() - top)
                    c.clipRect(0f, top.toFloat(), (pw - 2 * m).toFloat(), bottom.toFloat())
                    lay.draw(c)
                    pdf.finishPage(pg)
                    line = end + 1
                }
                if (n == 0) { val pg = pdf.startPage(PdfDocument.PageInfo.Builder(pw, ph, 1).create()); pdf.finishPage(pg); n = 1 }
                contentResolver.openOutputStream(out)?.use { pdf.writeTo(it) }
                pdf.close()
                tr("সংরক্ষিত হয়েছে: ") + bn(n.toString()) + tr(" পেজের PDF")
            } catch (e: Throwable) {
                tr("কাজটি করা যায়নি")
            }
            runOnUiThread { status.text = msg }
        }.start()
    }
}
