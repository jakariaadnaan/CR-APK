package com.example.callrec

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File

/** পেজ নম্বর, ওয়াটারমার্ক ও তথ্য: stamp page numbers or a watermark, or edit the PDF's info. */
class PdfStampActivity : PdfBase() {
    private var mode = 0 // 0 numbers, 1 watermark, 2 info
    private var pos = 1 // 0 left, 1 centre, 2 right
    private lateinit var modeRow: LinearLayout
    private lateinit var opt: LinearLayout
    private lateinit var f1: EditText
    private lateinit var f2: EditText
    private lateinit var f3: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        modeRow = LinearLayout(this)
        val mc = card(14)
        val hs = android.widget.HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(modeRow)
        mc.addView(hs)
        opt = LinearLayout(this)
        opt.orientation = LinearLayout.VERTICAL
        mc.addView(opt, lp(t = 12))
        val act = actionCard(tr("নতুন PDF বানান"), "") { go() }
        screen(tr("PDF স্ট্যাম্প ও তথ্য"), pickCard(), mc, act)
        draw()
    }

    private fun draw() {
        modeRow.removeAllViews()
        val names = listOf(tr("পেজ নম্বর"), tr("ওয়াটারমার্ক"), tr("PDF-এর তথ্য"))
        for (i in names.indices) modeRow.addView(chip(names[i], mode == i) { mode = i; draw() }, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, r = 8))
        opt.removeAllViews()
        f1 = field("", InputType.TYPE_CLASS_TEXT)
        f2 = field("", InputType.TYPE_CLASS_TEXT)
        f3 = field("", InputType.TYPE_CLASS_TEXT)
        when (mode) {
            0 -> {
                opt.addView(label(tr("নম্বর কোথায় বসবে"), 12f, Ui.MUTED, true))
                val r = LinearLayout(this)
                val names2 = listOf(tr("বাঁয়ে"), tr("মাঝে"), tr("ডানে"))
                for (i in names2.indices) r.addView(chip(names2[i], pos == i) { pos = i; draw() }, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, r = 8, t = 8))
                opt.addView(r)
                f1.hint = tr("কত নম্বর থেকে শুরু (ঐচ্ছিক)")
                f1.inputType = InputType.TYPE_CLASS_NUMBER
                opt.addView(f1, lp(t = 8))
                opt.addView(label(tr("নম্বর নিচের মাঝে বা কোণে \"১ / ১০\" ধাঁচে বসবে (সংখ্যা ইংরেজিতে)।"), 12f, Ui.MUTED), lp(t = 6))
            }
            1 -> {
                opt.addView(label(tr("ওয়াটারমার্কের লেখা"), 12f, Ui.MUTED, true))
                f1.hint = tr("যেমন: CONFIDENTIAL")
                opt.addView(f1)
                opt.addView(label(tr("লেখা ফিকে হয়ে কোনাকুনি সব পেজে বসবে। শুধু ইংরেজি অক্ষর ও সংখ্যা চলবে।"), 12f, Ui.MUTED), lp(t = 6))
            }
            else -> {
                opt.addView(label(tr("শিরোনাম, লেখক ও বিষয় বদলান। সব খালি রেখে সংরক্ষণ করলে এই তথ্য মুছে যাবে।"), 12f, Ui.MUTED))
                f1.hint = tr("শিরোনাম")
                f2.hint = tr("লেখক")
                f3.hint = tr("বিষয়")
                opt.addView(f1, lp(t = 6)); opt.addView(f2); opt.addView(f3)
            }
        }
        prefill()
    }

    override fun onLoaded() { prefill() }

    private fun prefill() {
        if (mode != 2) return
        val f = src ?: return
        try {
            PDDocument.load(f).use { d ->
                f1.setText(d.documentInformation.title ?: "")
                f2.setText(d.documentInformation.author ?: "")
                f3.setText(d.documentInformation.subject ?: "")
            }
        } catch (e: Throwable) { }
    }

    private fun latinOnly(s: String): Boolean = s.all { it.code in 32..126 }

    private fun go() {
        if (!requireFile()) return
        if (encrypted) { status.text = tr("আগে PDF আনলক করুন (PDF লক / আনলক টুল)"); return }
        val m = mode
        val p = pos
        val t1 = f1.text.toString().trim()
        val t2 = f2.text.toString().trim()
        val t3 = f3.text.toString().trim()
        if (m == 1) {
            if (t1.isEmpty()) { status.text = tr("ওয়াটারমার্কের লেখা দিন"); return }
            if (!latinOnly(t1)) { status.text = tr("ওয়াটারমার্কে শুধু ইংরেজি অক্ষর ও সংখ্যা চলবে"); return }
        }
        val startNo = (asciiDigits(t1).toIntOrNull() ?: 1).coerceAtLeast(0)
        askCreate(baseOut() + (if (m == 0) "_numbered.pdf" else if (m == 1) "_marked.pdf" else "_info.pdf")) { out ->
            val f = src!!
            work(tr("তৈরি হচ্ছে…")) {
                PDDocument.load(f).use { doc ->
                    val n = doc.numberOfPages
                    when (m) {
                        0 -> {
                            val font = PDType1Font.HELVETICA
                            for (i in 0 until n) {
                                val page = doc.getPage(i)
                                val box = page.mediaBox
                                val text = (startNo + i).toString() + " / " + (startNo + n - 1).toString()
                                val tw = font.getStringWidth(text) / 1000f * 11f
                                val x = when (p) { 0 -> 28f; 1 -> (box.width - tw) / 2f; else -> box.width - 28f - tw }
                                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                                    cs.beginText()
                                    cs.setFont(font, 11f)
                                    cs.setNonStrokingColor(70, 70, 70)
                                    cs.newLineAtOffset(box.lowerLeftX + x, box.lowerLeftY + 20f)
                                    cs.showText(text)
                                    cs.endText()
                                }
                            }
                        }
                        1 -> {
                            val font = PDType1Font.HELVETICA_BOLD
                            val gs = PDExtendedGraphicsState()
                            gs.nonStrokingAlphaConstant = 0.22f
                            for (i in 0 until n) {
                                val page = doc.getPage(i)
                                val box = page.mediaBox
                                val size = Math.min(80f, Math.max(24f, box.width * 1.1f / Math.max(4, t1.length) * 1.4f))
                                val tw = font.getStringWidth(t1) / 1000f * size
                                val ang = Math.toRadians(45.0)
                                val cx = box.lowerLeftX + box.width / 2f
                                val cy = box.lowerLeftY + box.height / 2f
                                val ox = (cx - (tw / 2f) * Math.cos(ang) + (size / 3f) * Math.sin(ang)).toFloat()
                                val oy = (cy - (tw / 2f) * Math.sin(ang) - (size / 3f) * Math.cos(ang)).toFloat()
                                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                                    cs.setGraphicsStateParameters(gs)
                                    cs.beginText()
                                    cs.setFont(font, size)
                                    cs.setNonStrokingColor(90, 90, 90)
                                    cs.setTextMatrix(Matrix.getRotateInstance(ang, ox, oy))
                                    cs.showText(t1)
                                    cs.endText()
                                }
                            }
                        }
                        else -> {
                            val info = doc.documentInformation
                            info.title = if (t1.isEmpty()) null else t1
                            info.author = if (t2.isEmpty()) null else t2
                            info.subject = if (t3.isEmpty()) null else t3
                            info.keywords = null
                            info.creator = null
                            info.producer = null
                            doc.documentInformation = info
                        }
                    }
                    saveDoc(doc, out)
                }
                tr("সংরক্ষিত হয়েছে")
            }
        }
    }
}
