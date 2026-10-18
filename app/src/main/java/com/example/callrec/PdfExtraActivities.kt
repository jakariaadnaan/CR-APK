package com.example.callrec

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File

/** PDF সাদাকালো: turn every page into a grey picture (smaller, print-friendly). */
class PdfGrayActivity : PdfBase() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val act = actionCard(tr("সাদাকালো PDF বানান"), tr("পেজগুলো ছবি হয়ে যায়, তাই লেখা আর বাছা বা কপি করা যাবে না।")) { go() }
        screen(tr("PDF সাদাকালো"), pickCard(), act)
    }

    private fun go() {
        if (!requireFile()) return
        if (encrypted) { status.text = tr("আগে PDF আনলক করুন (PDF লক / আনলক টুল)"); return }
        askCreate(baseOut() + "_gray.pdf") { out ->
            val f = src!!
            work(tr("তৈরি হচ্ছে…")) {
                val tmp = File(cacheDir, "pdf_gray.pdf")
                val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val nd = PDDocument()
                val gp = Paint()
                val cm = ColorMatrix()
                cm.setSaturation(0f)
                gp.colorFilter = ColorMatrixColorFilter(cm)
                try {
                    for (i in 0 until renderer.pageCount) {
                        val pg = renderer.openPage(i)
                        val w = Math.max(1, Math.round(pg.width * 150 / 72f))
                        val h = Math.max(1, Math.round(pg.height * 150 / 72f))
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val page = PDPage(PDRectangle(pg.width.toFloat(), pg.height.toFloat()))
                        pg.close()
                        val grey = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(grey).drawBitmap(bmp, 0f, 0f, gp)
                        bmp.recycle()
                        nd.addPage(page)
                        val img = JPEGFactory.createFromImage(nd, grey, 0.8f)
                        PDPageContentStream(nd, page).use { cs -> cs.drawImage(img, 0f, 0f, page.mediaBox.width, page.mediaBox.height) }
                        grey.recycle()
                    }
                    tmp.outputStream().use { nd.save(it) }
                } finally {
                    nd.close(); renderer.close(); pfd.close()
                }
                contentResolver.openOutputStream(out)?.use { o -> tmp.inputStream().use { it.copyTo(o) } }
                tr("সংরক্ষিত হয়েছে")
            }
        }
    }
}

/** PDF ক্রপ: trim a percentage off each side of every page (text stays selectable). */
class PdfCropActivity : PdfBase() {
    private lateinit var fl: EditText
    private lateinit var ft: EditText
    private lateinit var fr: EditText
    private lateinit var fb: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val opt = card(14)
        opt.addView(label(tr("প্রতি পাশ থেকে কত শতাংশ কাটবেন (০–৪০)"), 12f, Ui.MUTED, true))
        fl = field(tr("বাঁ"), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        ft = field(tr("উপর"), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        fr = field(tr("ডান"), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        fb = field(tr("নিচ"), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        for (e in listOf(fl, ft, fr, fb)) { e.setText("0"); opt.addView(e) }
        opt.addView(label(tr("সব পেজে একই মাপে কাটা হবে।"), 12f, Ui.MUTED), lp(t = 6))
        val act = actionCard(tr("ক্রপ করে নতুন PDF বানান"), "") { go() }
        screen(tr("PDF ক্রপ"), pickCard(), opt, act)
    }

    private fun pct(e: EditText): Float? {
        val v = asciiDigits(e.text.toString()).trim().toFloatOrNull() ?: return if (e.text.isBlank()) 0f else null
        return if (v < 0f || v > 40f) null else v
    }

    private fun go() {
        if (!requireFile()) return
        if (encrypted) { status.text = tr("আগে PDF আনলক করুন (PDF লক / আনলক টুল)"); return }
        val l = pct(fl); val t = pct(ft); val r = pct(fr); val b = pct(fb)
        if (l == null || t == null || r == null || b == null) { status.text = tr("শতাংশ ০ থেকে ৪০-এর মধ্যে দিন"); return }
        if (l + r >= 90f || t + b >= 90f) { status.text = tr("এত বেশি কাটা যাবে না"); return }
        askCreate(baseOut() + "_cropped.pdf") { out ->
            val f = src!!
            work(tr("তৈরি হচ্ছে…")) {
                PDDocument.load(f).use { doc ->
                    for (i in 0 until doc.numberOfPages) {
                        val pg = doc.getPage(i)
                        val m = pg.mediaBox
                        val x = m.lowerLeftX + m.width * l / 100f
                        val y = m.lowerLeftY + m.height * b / 100f
                        val w = m.width * (1f - (l + r) / 100f)
                        val h = m.height * (1f - (t + b) / 100f)
                        val rect = PDRectangle(x, y, w, h)
                        pg.cropBox = rect
                        pg.mediaBox = rect
                    }
                    saveDoc(doc, out)
                }
                tr("সংরক্ষিত হয়েছে")
            }
        }
    }
}

/** PDF থেকে লেখা: pull the text out of a PDF into a .txt file. */
class PdfTextActivity : PdfBase() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val act = actionCard(tr("লেখা বের করে সংরক্ষণ করুন"), tr("স্ক্যান করা (ছবির) PDF-এ লেখা পাওয়া যাবে না। কিছু বাংলা ফন্টের লেখা ভেঙে যেতে পারে।")) { go() }
        screen(tr("PDF থেকে লেখা"), pickCard(), act)
    }

    private fun go() {
        if (!requireFile()) return
        if (encrypted) { status.text = tr("আগে PDF আনলক করুন (PDF লক / আনলক টুল)"); return }
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_TITLE, baseOut() + ".txt")
        startActivityForResult(i, 94)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 94) {
            val out = data?.data
            if (resultCode == Activity.RESULT_OK && out != null) {
                val f = src ?: return
                work(tr("লেখা বের হচ্ছে…")) {
                    var len = 0
                    PDDocument.load(f).use { doc ->
                        val text = PDFTextStripper().getText(doc)
                        len = text.trim().length
                        if (len > 0) contentResolver.openOutputStream(out)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    }
                    if (len == 0) {
                        try { DocumentsContract.deleteDocument(contentResolver, out) } catch (e: Throwable) { }
                        tr("কোনো লেখা পাওয়া যায়নি (এটি স্ক্যান করা PDF হতে পারে)")
                    } else tr("সংরক্ষিত হয়েছে: ") + bn(len.toString()) + tr(" অক্ষর")
                }
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }
}

/** PDF থেকে ছবি: save the pictures inside a PDF into a folder. */
class PdfImagesActivity : PdfBase() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val act = actionCard(tr("ফোল্ডার বেছে ছবি বের করুন"), tr("PDF-এর ভেতরের আসল ছবিগুলো আলাদা JPG ফাইল হবে। খুব ছোট ছবি বাদ যায়।")) { go() }
        screen(tr("PDF থেকে ছবি"), pickCard(), act)
    }

    private fun go() {
        if (!requireFile()) return
        if (encrypted) { status.text = tr("আগে PDF আনলক করুন (PDF লক / আনলক টুল)"); return }
        askTree { tree ->
            val f = src!!
            val base = baseOut()
            work(tr("ছবি বের হচ্ছে…")) {
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                var made = 0
                PDDocument.load(f).use { doc ->
                    for (p in 0 until doc.numberOfPages) {
                        val res = doc.getPage(p).resources ?: continue
                        var k = 0
                        for (name in res.xObjectNames) {
                            val x = try { res.getXObject(name) } catch (e: Throwable) { null }
                            if (x !is PDImageXObject) continue
                            val bmp = try { x.image } catch (e: Throwable) { null } ?: continue
                            if (bmp.width < 40 || bmp.height < 40) { bmp.recycle(); continue }
                            val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                            flat.eraseColor(Color.WHITE)
                            Canvas(flat).drawBitmap(bmp, 0f, 0f, null)
                            bmp.recycle()
                            val bos = ByteArrayOutputStream()
                            flat.compress(Bitmap.CompressFormat.JPEG, 92, bos)
                            flat.recycle()
                            k++
                            val u = DocumentsContract.createDocument(contentResolver, parent, "image/jpeg", base + "_p" + (p + 1) + "_" + k + ".jpg")
                            if (u != null) {
                                contentResolver.openOutputStream(u)?.use { it.write(bos.toByteArray()) }
                                made++
                            }
                        }
                    }
                }
                if (made == 0) tr("এই PDF-এ আলাদা করার মতো ছবি নেই") else tr("সংরক্ষিত হয়েছে: ") + bn(made.toString()) + tr("টি ছবি")
            }
        }
    }
}
