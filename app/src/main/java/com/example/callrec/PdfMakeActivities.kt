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
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)
        val c = card(14)
        url = EditText(this)
        url.hint = "https://example.com"
        styleInput(url)
        url.setSingleLine(true)
        url.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        url.imeOptions = EditorInfo.IME_ACTION_GO
        url.setOnEditorActionListener { _, _, _ -> load(); true }
        c.addView(url, lp())
        c.addView(pill(tr("পেজ খুলুন")) { load() }, lp(t = 8))
        box.addView(c, lp(b = 12))
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.webViewClient = WebViewClient()
        val wc = card(6)
        wc.addView(web, lp(height = dp(380)))
        box.addView(wc, lp(b = 12))
        status = label(tr("পেজ খুলে নিচের বোতাম চাপুন। খোলা পর্দায় \"PDF হিসেবে সংরক্ষণ\" বাছুন।"), 13f, Ui.MUTED)
        box.addView(statusCard(status))
        setPdfScreen(tr("ওয়েবপেজ থেকে PDF"), scroll, tr("PDF করুন")) { print() }
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

/** ক্যামেরা থেকে PDF: take photos of pages with the camera app and make one PDF. */
class CameraPdfActivity : GuardedActivity() {
    private val shots = ArrayList<Uri>()
    private var pending: Uri? = null
    private lateinit var listBox: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)
        val c = card(14)
        c.addView(pill(tr("ছবি তুলুন")) { shoot() })
        c.addView(label(tr("প্রতিটি ছবি একটি পেজ হবে। ছবিগুলো ফোনের গ্যালারিতেও থেকে যাবে।"), 12f, Ui.MUTED), lp(t = 8))
        box.addView(c, lp(b = 12))
        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        box.addView(listBox, lp(b = 12))
        status = label("", 14f, Ui.TEXT)
        box.addView(statusCard(status))
        setPdfScreen(tr("ক্যামেরা থেকে PDF"), scroll, tr("PDF বানান")) { go() }
        render()
    }

    private fun render() {
        listBox.removeAllViews()
        for ((i, u) in shots.withIndex()) {
            val row = card(12)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.addView(label(tr("পেজ ") + bn((i + 1).toString()), 14f, Ui.TEXT, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val x = label("✕", 16f, Ui.RED, true)
            x.gravity = Gravity.CENTER
            x.setOnClickListener { shots.remove(u); render() }
            row.addView(x, lp(width = dp(40), height = dp(40)))
            listBox.addView(row, lp(b = 6))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 98) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) shoot()
            else status.text = tr("ক্যামেরার অনুমতি দিন")
        }
    }

    private fun shoot() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 98)
            return
        }
        val cv = ContentValues()
        cv.put(MediaStore.Images.Media.DISPLAY_NAME, "khata_scan_" + System.currentTimeMillis() + ".jpg")
        cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AmarKhata")
        val u = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
        if (u == null) { status.text = tr("ক্যামেরা চালু করা যায়নি"); return }
        pending = u
        val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        i.putExtra(MediaStore.EXTRA_OUTPUT, u)
        try {
            startActivityForResult(i, 97)
        } catch (e: Throwable) {
            try { contentResolver.delete(u, null, null) } catch (x: Throwable) { }
            pending = null
            status.text = tr("ক্যামেরা অ্যাপ পাওয়া যায়নি")
        }
    }

    private fun load(u: Uri): Bitmap? {
        return try {
            val o = BitmapFactory.Options()
            o.inJustDecodeBounds = true
            contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (Math.max(o.outWidth, o.outHeight) / s > 2000) s *= 2
            val o2 = BitmapFactory.Options()
            o2.inSampleSize = s
            var b = contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return null
            val ex = contentResolver.openInputStream(u)?.use { ExifInterface(it) }
            val deg = when (ex?.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (deg != 0f) {
                val m = Matrix()
                m.postRotate(deg)
                b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
            }
            b
        } catch (e: Throwable) { null }
    }

    private fun go() {
        if (shots.isEmpty()) { status.text = tr("আগে অন্তত একটি ছবি তুলুন"); return }
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        i.putExtra(Intent.EXTRA_TITLE, "scan.pdf")
        saveOrAsk(i, 95)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 97) {
            val u = pending
            pending = null
            if (u != null) {
                if (resultCode == Activity.RESULT_OK) { shots.add(u); render() }
                else try { contentResolver.delete(u, null, null) } catch (e: Throwable) { }
            }
        } else if (requestCode == 95 && resultCode == Activity.RESULT_OK) {
            val out = data?.data ?: return
            val list = shots.toList()
            status.text = tr("তৈরি হচ্ছে…")
            Thread {
                val msg = try {
                    val pdf = PdfDocument()
                    var n = 0
                    for (u in list) {
                        val b = load(u) ?: continue
                        n++
                        fitPage(pdf, n, b)
                        b.recycle()
                    }
                    if (n > 0) contentResolver.openOutputStream(out)?.use { pdf.writeTo(it) }
                    pdf.close()
                    if (n == 0) tr("কাজটি করা যায়নি") else tr("সংরক্ষিত হয়েছে: ") + bn(n.toString()) + tr(" পেজের PDF")
                } catch (e: Throwable) { tr("কাজটি করা যায়নি") }
                runOnUiThread { status.text = msg }
            }.start()
        }
    }
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
        e.setTextColor(Ui.TEXT)
        e.inputType = type
        return e
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)
        val head = card(14)
        from = edit(tr("আপনার নাম / দোকানের নাম"))
        to = edit(tr("ক্রেতার নাম"))
        no = edit(tr("ইনভয়েস নম্বর (ঐচ্ছিক)"))
        head.addView(from); head.addView(to); head.addView(no)
        box.addView(head, lp(b = 12))
        val items = card(14)
        items.addView(label(tr("পণ্য / সেবা"), 12f, Ui.MUTED, true))
        rowBox = LinearLayout(this)
        rowBox.orientation = LinearLayout.VERTICAL
        items.addView(rowBox, lp(t = 4))
        val btns = LinearLayout(this)
        btns.addView(pill(tr("+ সারি যোগ")) { addRow() }, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, r = 8))
        btns.addView(pill(tr("শেষ সারি বাদ"), Ui.GREY, Ui.TEXT) {
            if (rows.size > 1) { val r = rows.removeAt(rows.size - 1); rowBox.removeView(r.name.parent as android.view.View) }
        }, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        items.addView(btns, lp(t = 8))
        box.addView(items, lp(b = 12))
        val nt = card(14)
        note = edit(tr("মন্তব্য (ঐচ্ছিক)"))
        nt.addView(note)
        box.addView(nt, lp(b = 12))
        status = label("", 14f, Ui.TEXT)
        box.addView(statusCard(status))
        setPdfScreen(tr("ইনভয়েস বানান"), scroll, tr("ইনভয়েস PDF বানান")) { go() }
        addRow(); addRow()
    }

    private fun addRow() {
        val line = LinearLayout(this)
        line.orientation = LinearLayout.HORIZONTAL
        val n = edit(tr("বিবরণ"))
        val q = edit(tr("পরিমাণ"), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val p = edit(tr("দাম"), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        q.setText("1")
        line.addView(n, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.2f))
        line.addView(q, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        line.addView(p, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f))
        rowBox.addView(line)
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
