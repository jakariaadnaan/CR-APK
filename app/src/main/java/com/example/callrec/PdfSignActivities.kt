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
