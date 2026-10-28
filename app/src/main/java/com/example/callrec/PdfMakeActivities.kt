package com.example.callrec

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** ওয়েবপেজ থেকে PDF: open a web address and save it as a PDF through the system print screen. */
class WebToPdfActivity : GuardedActivity() {
    private lateinit var url: EditText
    private lateinit var web: WebView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scroll.addView(box)
        url = EditText(this)
        url.hint = "https://example.com"
        url.setSingleLine(true)
        url.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        url.imeOptions = EditorInfo.IME_ACTION_GO
        url.setOnEditorActionListener { _, _, _ -> load(); true }
        box.addView(fieldBox(tr("ওয়েব ঠিকানা"), url), lp(b = 8))
        box.addView(outlineButton(tr("পেজ খুলুন")) { load() }, lp(height = dp(44), b = 12))
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.webViewClient = WebViewClient()
        box.addView(captionLabel(tr("পেজ প্রিভিউ")))
        val wc = card(6)
        wc.addView(web, lp(height = dp(380)))
        box.addView(wc, lp(b = 12))
        status = label(tr("পেজ খুলে নিচের বোতাম চাপুন। খোলা পর্দায় \"PDF হিসেবে সংরক্ষণ\" বাছুন।"), 13f, Ui.MUTED)
        box.addView(statusCard(status))
        val bar = LinearLayout(this)
        bar.addView(primaryButton(tr("PDF করুন")) { print() }, lp(height = dp(48)))
        setScreen(tr("ওয়েবপেজ থেকে PDF"), scroll, null, true, bottomBar(bar))
    }

    private fun load() {
        var u = url.text.toString().trim()
        if (u.isEmpty()) { status.text = tr("আগে ওয়েব ঠিকানা লিখুন"); return }
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        status.text = tr("খোলা হচ্ছে…")
        web.loadUrl(u)
    }

    private fun print() {
        if (web.url == null) { status.text = tr("আগে একটি পেজ খুলুন"); return }
        val pm = getSystemService(PRINT_SERVICE) as PrintManager
        val name = (web.title ?: "webpage").replace(Regex("[^A-Za-z0-9_-]"), "_").take(40).ifEmpty { "webpage" }
        pm.print(name, web.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
    }

    override fun onDestroy() {
        try { web.destroy() } catch (e: Throwable) { }
        super.onDestroy()
    }
}

private fun fitPage(pdf: PdfDocument, n: Int, bmp: Bitmap) {
    val pw = 595
    val ph = 842
    val page = pdf.startPage(PdfDocument.PageInfo.Builder(pw, ph, n).create())
    val c = page.canvas
    c.drawColor(Color.WHITE)
    val m = 16f
    val s = Math.min((pw - 2 * m) / bmp.width, (ph - 2 * m) / bmp.height)
    val w = bmp.width * s
    val h = bmp.height * s
    val x = (pw - w) / 2f
    val y = (ph - h) / 2f
    c.drawBitmap(bmp, null, android.graphics.RectF(x, y, x + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG))
    pdf.finishPage(page)
}

/** ইনভয়েস: type the items and make a neat A4 invoice PDF. */
class InvoicePdfActivity : GuardedActivity() {
    private class Row(val name: EditText, val qty: EditText, val price: EditText)

    private val rows = ArrayList<Row>()
    private lateinit var rowBox: LinearLayout
    private lateinit var from: EditText
    private lateinit var to: EditText
    private lateinit var no: EditText
    private lateinit var note: EditText
    private lateinit var status: TextView

    private fun edit(hint: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
        val e = EditText(this)
        e.hint = hint
        e.inputType = type
        styleInput(e)
        return e
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scroll.addView(box)
        from = edit(tr("আপনার নাম / দোকানের নাম"))
        to = edit(tr("ক্রেতার নাম"))
        no = edit(tr("ইনভয়েস নম্বর (ঐচ্ছিক)"))
        box.addView(captionLabel(tr("ইনভয়েসের তথ্য")))
        box.addView(from, lp(b = 8)); box.addView(to, lp(b = 8)); box.addView(no, lp(b = 12))
        box.addView(captionLabel(tr("পণ্য / সেবা")))
        rowBox = LinearLayout(this)
        rowBox.orientation = LinearLayout.VERTICAL
        box.addView(rowBox, lp())
        val btns = LinearLayout(this)
        btns.addView(choiceChip(tr("+ সারি যোগ"), false) { addRow() }, lp(r = 8))
        btns.addView(choiceChip(tr("শেষ সারি বাদ"), false) {
            if (rows.size > 1) { val r = rows.removeAt(rows.size - 1); rowBox.removeView(r.name.parent as android.view.View) }
        })
        box.addView(btns, lp(t = 4, b = 12))
        note = edit(tr("মন্তব্য (ঐচ্ছিক)"))
        box.addView(captionLabel(tr("মন্তব্য")))
        box.addView(note, lp(b = 12))
        status = label("", 14f, Ui.TEXT)
        box.addView(statusCard(status))
        val bar = LinearLayout(this)
        bar.addView(primaryButton(tr("ইনভয়েস PDF বানান")) { go() }, lp(height = dp(48)))
        setScreen(tr("ইনভয়েস বানান"), scroll, null, true, bottomBar(bar))
        addRow(); addRow()
    }

    private fun addRow() {
        val line = LinearLayout(this)
        line.orientation = LinearLayout.HORIZONTAL
        val n = edit(tr("বিবরণ"))
        val q = edit(tr("পরিমাণ"), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val p = edit(tr("দাম"), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        q.setText("1")
        line.addView(n, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.2f).also { it.rightMargin = dp(6) })
        line.addView(q, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.rightMargin = dp(6) })
        line.addView(p, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f))
        rowBox.addView(line, lp(b = 8))
        rows.add(Row(n, q, p))
    }

    private fun num(e: EditText): Double = asciiDigits(e.text.toString()).trim().toDoubleOrNull() ?: 0.0

    private fun go() {
        val any = rows.any { it.name.text.isNotBlank() && num(it.price) > 0 }
        if (!any) { status.text = tr("অন্তত একটি পণ্যের বিবরণ ও দাম দিন"); return }
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        i.putExtra(Intent.EXTRA_TITLE, "invoice.pdf")
        saveOrAsk(i, 96)
    }

    private class Line(val name: String, val qty: Double, val price: Double)

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 96 || resultCode != Activity.RESULT_OK) return
        val out = data?.data ?: return
        val lines = rows.filter { it.name.text.isNotBlank() && num(it.price) > 0 }
            .map { Line(it.name.text.toString().trim(), if (num(it.qty) > 0) num(it.qty) else 1.0, num(it.price)) }
        val f = from.text.toString().trim()
        val t = to.text.toString().trim()
        val nn = no.text.toString().trim()
        val nt = note.text.toString().trim()
        val title = tr("ইনভয়েস")
        val hdrNo = tr("নম্বর: ")
        val hdrDate = tr("তারিখ: ")
        val hdrFrom = tr("প্রেরক")
        val hdrTo = tr("ক্রেতা")
        val cDesc = tr("বিবরণ")
        val cQty = tr("পরিমাণ")
        val cPrice = tr("দাম")
        val cAmt = tr("মোট")
        val cTotal = tr("সর্বমোট")
        val cNote = tr("মন্তব্য")
        val date = dateText(System.currentTimeMillis())
        status.text = tr("তৈরি হচ্ছে…")
        Thread {
            val msg = try {
                val pw = 595
                val ph = 842
                val pdf = PdfDocument()
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                val ink = Color.parseColor("#1B1F24")
                val soft = Color.parseColor("#6B7280")
                val line = Color.parseColor("#D5D9DF")
                fun ell(s: String, w: Float): String {
                    val k = p.breakText(s, true, w, null)
                    return if (k >= s.length) s else s.substring(0, Math.max(0, k - 1)) + "…"
                }
                var pageNo = 1
                var page = pdf.startPage(PdfDocument.PageInfo.Builder(pw, ph, pageNo).create())
                var c = page.canvas
                fun header(first: Boolean) {
                    if (!first) return
                    p.typeface = Typeface.DEFAULT_BOLD
                    p.textSize = 26f
                    p.color = Ui.PRIMARY
                    c.drawText(title, 40f, 62f, p)
                    p.typeface = Typeface.DEFAULT
                    p.textSize = 11f
                    p.color = soft
                    p.textAlign = Paint.Align.RIGHT
                    if (nn.isNotEmpty()) c.drawText(hdrNo + nn, pw - 40f, 50f, p)
                    c.drawText(hdrDate + date, pw - 40f, 66f, p)
                    p.textAlign = Paint.Align.LEFT
                    var y = 110f
                    p.textSize = 10f; p.color = soft
                    c.drawText(hdrFrom, 40f, y, p)
                    c.drawText(hdrTo, 320f, y, p)
                    p.textSize = 14f; p.color = ink; p.typeface = Typeface.DEFAULT_BOLD
                    c.drawText(ell(f.ifEmpty { "-" }, 250f), 40f, y + 20f, p)
                    c.drawText(ell(t.ifEmpty { "-" }, 235f), 320f, y + 20f, p)
                }
                header(true)
                var y = 175f
                fun colHeads() {
                    p.typeface = Typeface.DEFAULT_BOLD
                    p.textSize = 11f
                    p.color = soft
                    p.textAlign = Paint.Align.LEFT
                    c.drawText(cDesc, 40f, y, p)
                    p.textAlign = Paint.Align.RIGHT
                    c.drawText(cQty, 360f, y, p)
                    c.drawText(cPrice, 450f, y, p)
                    c.drawText(cAmt, pw - 40f, y, p)
                    p.textAlign = Paint.Align.LEFT
                    p.color = line
                    p.strokeWidth = 1f
                    c.drawLine(40f, y + 8f, pw - 40f, y + 8f, p)
                    y += 28f
                }
                colHeads()
                var total = 0.0
                for (l in lines) {
                    if (y > ph - 90f) {
                        pdf.finishPage(page)
                        pageNo++
                        page = pdf.startPage(PdfDocument.PageInfo.Builder(pw, ph, pageNo).create())
                        c = page.canvas
                        y = 60f
                        colHeads()
                    }
                    val amt = l.qty * l.price
                    total += amt
                    p.typeface = Typeface.DEFAULT
                    p.textSize = 12f
                    p.color = ink
                    p.textAlign = Paint.Align.LEFT
                    c.drawText(ell(l.name, 270f), 40f, y, p)
                    p.textAlign = Paint.Align.RIGHT
                    c.drawText(bn(fmtNum(l.qty, 2)), 360f, y, p)
                    c.drawText(taka(l.price), 450f, y, p)
                    c.drawText(taka(amt), pw - 40f, y, p)
                    p.color = line
                    c.drawLine(40f, y + 9f, pw - 40f, y + 9f, p)
                    y += 26f
                }
                if (y > ph - 120f) {
                    pdf.finishPage(page)
                    pageNo++
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(pw, ph, pageNo).create())
                    c = page.canvas
                    y = 70f
                }
                y += 10f
                p.typeface = Typeface.DEFAULT_BOLD
                p.textSize = 15f
                p.color = ink
                p.textAlign = Paint.Align.LEFT
                c.drawText(cTotal, 330f, y, p)
                p.textAlign = Paint.Align.RIGHT
                p.color = Ui.PRIMARY
                c.drawText(taka(total), pw - 40f, y, p)
                if (nt.isNotEmpty()) {
                    y += 40f
                    p.textAlign = Paint.Align.LEFT
                    p.typeface = Typeface.DEFAULT_BOLD
                    p.textSize = 10f
                    p.color = soft
                    c.drawText(cNote, 40f, y, p)
                    p.typeface = Typeface.DEFAULT
                    p.textSize = 12f
                    p.color = ink
                    c.drawText(ell(nt, (pw - 80).toFloat()), 40f, y + 18f, p)
                }
                pdf.finishPage(page)
                contentResolver.openOutputStream(out)?.use { pdf.writeTo(it) }
                pdf.close()
                tr("সংরক্ষিত হয়েছে")
            } catch (e: Throwable) { tr("কাজটি করা যায়নি") }
            runOnUiThread { status.text = msg }
        }.start()
    }
}
