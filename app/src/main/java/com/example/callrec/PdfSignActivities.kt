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

/** লেখা থেকে PDF: turn typed text (Bangla or English) into an A4 PDF. */
class TextToPdfActivity : GuardedActivity() {
    private lateinit var input: EditText
    private lateinit var status: TextView
    private var sizeLevel = 1
    private lateinit var row: LinearLayout
    private var paper = 0 // 0 A4, 1 Letter, 2 Legal
    private var landscape = false
    private lateinit var paperRow: LinearLayout
    private lateinit var pv: PagePreview
    private lateinit var pageNote: TextView

    private fun pageW(): Int = (intArrayOf(595, 612, 612)[paper]).let { if (landscape) pageH0() else it }
    private fun pageH0(): Int = intArrayOf(842, 792, 1008)[paper]
    private fun pageH(): Int = if (landscape) intArrayOf(595, 612, 612)[paper] else pageH0()

    /** A small white page showing the start of the text, so page shape and font size can be judged before saving. */
    private inner class PagePreview : View(this@TextToPdfActivity) {
        override fun onDraw(c: Canvas) {
            val pw = pageW()
            val ph = pageH()
            val s = Math.min(width * 0.8f / pw, height * 0.92f / ph)
            val w = pw * s
            val h = ph * s
            val l = (width - w) / 2f
            val t = (height - h) / 2f
            val bgp = Paint()
            bgp.color = Color.WHITE
            c.drawRect(l, t, l + w, t + h, bgp)
            bgp.style = Paint.Style.STROKE
            bgp.color = Ui.LINE
            c.drawRect(l, t, l + w, t + h, bgp)
            val txt = input.text.toString().ifBlank { tr("এখানে লিখুন বা পেস্ট করুন") }
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
            tp.textSize = floatArrayOf(11f, 14f, 18f)[sizeLevel]
            tp.color = if (input.text.isBlank()) Color.LTGRAY else Color.BLACK
            val m = 44
            val lay = StaticLayout.Builder.obtain(txt, 0, txt.length, tp, pw - 2 * m).setLineSpacing(2f, 1f).build()
            c.save()
            c.clipRect(l, t, l + w, t + h)
            c.translate(l + m * s, t + m * s)
            c.scale(s, s)
            lay.draw(c)
            c.restore()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scroll.addView(box)
        box.addView(sectionLabel(tr("ডকুমেন্টের লেখা"), 0))
        input = EditText(this)
        input.hint = tr("এখানে লিখুন বা পেস্ট করুন")
        styleInput(input)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.minLines = 7
        input.gravity = Gravity.TOP
        input.onChange { pv.invalidate(); updateNote() }
        box.addView(input, lp(b = 4))
        box.addView(sectionLabel(tr("লেখার আকার")))
        row = LinearLayout(this)
        box.addView(row, lp(b = 4))
        box.addView(sectionLabel(tr("পেজ সেটিংস")))
        paperRow = LinearLayout(this)
        box.addView(paperRow, lp(b = 6))
        box.addView(captionLabel(tr("লাইভ PDF প্রিভিউ")).also { it.layoutParams = lp(t = 8, b = 6) })
        val pc = LinearLayout(this)
        pc.background = bg(Ui.PREVIEW, Ui.cardR, Ui.LINE, 1)
        pv = PagePreview()
        pc.addView(pv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(190)))
        box.addView(pc)
        pageNote = label("", 11f, Ui.MUTED)
        pageNote.gravity = Gravity.CENTER
        box.addView(pageNote, lp(t = 6, b = 10))
        status = label("", 14f, Ui.TEXT)
        box.addView(statusCard(status))
        setPdfScreen(tr("লেখা থেকে PDF"), scroll, tr("PDF বানান")) { go() }
        chips()
    }

    private fun updateNote() {
        val text = input.text.toString()
        if (text.isBlank()) { pageNote.text = ""; return }
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tp.textSize = floatArrayOf(11f, 14f, 18f)[sizeLevel]
        val lay = StaticLayout.Builder.obtain(text, 0, text.length, tp, pageW() - 88).setLineSpacing(2f, 1f).build()
        val n = Math.max(1, Math.ceil(lay.height.toDouble() / (pageH() - 88)).toInt())
        pageNote.text = bn(n.toString()) + tr(" পেজ হবে")
    }

    private fun chips() {
        row.removeAllViews()
        val names = listOf(tr("ছোট"), tr("মাঝারি"), tr("বড়"))
        for (i in names.indices) row.addView(choiceChip(names[i], sizeLevel == i) { sizeLevel = i; chips() }, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, r = 8))
        paperRow.removeAllViews()
        val pn = listOf("A4", "Letter", "Legal")
        for (i in pn.indices) paperRow.addView(choiceChip(pn[i], paper == i) { paper = i; chips() }, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, r = 8))
        paperRow.addView(choiceChip(tr("পোর্ট্রেট"), !landscape) { landscape = false; chips() }, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, l = 8, r = 8))
        paperRow.addView(choiceChip(tr("ল্যান্ডস্কেপ"), landscape) { landscape = true; chips() }, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, r = 8))
        if (::pv.isInitialized) { pv.invalidate(); updateNote() }
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
        val pw0 = pageW()
        val ph0 = pageH()
        status.text = tr("তৈরি হচ্ছে…")
        Thread {
            val msg = try {
                val pw = pw0
                val ph = ph0
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
