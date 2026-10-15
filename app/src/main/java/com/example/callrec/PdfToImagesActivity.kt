package com.example.callrec

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** PDF → ছবি: every page of a PDF saved as a PNG or JPG in a folder you choose. */
class PdfToImagesActivity : GuardedActivity() {

    private var pdf: Uri? = null
    private var pdfName = "document"
    private var pages = 0
    private var png = true
    private var dpi = 150

    private lateinit var info: TextView
    private lateinit var fmtRow: LinearLayout
    private lateinit var dpiRow: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)

        val c = card(14)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = android.view.Gravity.CENTER_VERTICAL
        row.addView(pill("PDF বাছুন") { pick() }, lp(r = 12))
        info = label("কোনো PDF বাছা হয়নি", 13f, Ui.MUTED)
        row.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(row, lp())
        box.addView(c, lp(b = 12))

        val opt = card(14)
        opt.addView(label("ছবির ফরম্যাট", 12f, Ui.MUTED, true))
        fmtRow = LinearLayout(this)
        fmtRow.orientation = LinearLayout.HORIZONTAL
        opt.addView(fmtRow, lp(t = 6, b = 10))
        opt.addView(label("মান (DPI)", 12f, Ui.MUTED, true))
        dpiRow = LinearLayout(this)
        dpiRow.orientation = LinearLayout.HORIZONTAL
        opt.addView(dpiRow, lp(t = 6))
        box.addView(opt, lp(b = 12))

        val res = card(14)
        status = label("", 14f, Ui.TEXT)
        res.addView(status, lp(b = 10))
        res.addView(pill("ফোল্ডার বেছে সংরক্ষণ") { chooseFolder() })
        box.addView(res, lp())

        setScreen("PDF → ছবি", scroll)
        chips()
    }

    private fun chip(text: String, on: Boolean, click: () -> Unit): TextView {
        val c = label(text, 14f, if (on) Color.WHITE else Ui.PRIMARY, true)
        c.gravity = android.view.Gravity.CENTER
        c.background = bg(if (on) Ui.PRIMARY else Ui.CARD, 20, Ui.PRIMARY, 1)
        c.setPadding(dp(16), dp(8), dp(16), dp(8))
        c.setOnClickListener { click() }
        return c
    }

    private fun chips() {
        fmtRow.removeAllViews()
        fmtRow.addView(chip("PNG", png) { png = true; chips() }, lp(r = 8))
        fmtRow.addView(chip("JPG", !png) { png = false; chips() })
        dpiRow.removeAllViews()
        for (d in listOf(100, 150, 200)) dpiRow.addView(chip(bn(d.toString()), d == dpi) { dpi = d; chips() }, lp(r = 8))
    }

    private fun pick() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        startActivityForResult(i, 83)
    }

    private fun chooseFolder() {
        if (pdf == null || pages == 0) {
            Toast.makeText(this, "আগে একটি PDF বাছুন", Toast.LENGTH_SHORT).show()
            return
        }
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 84)
    }

    /** Copies the PDF into the app cache so PdfRenderer always gets a seekable file. */
    private fun cacheCopy(u: Uri): File? {
        return try {
            val f = File(cacheDir, "in.pdf")
            contentResolver.openInputStream(u)?.use { ins -> f.outputStream().use { ins.copyTo(it) } }
            f
        } catch (e: Throwable) {
            null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val u = data.data!!
        if (requestCode == 83) {
            info.text = "খোলা হচ্ছে…"
            Thread {
                var n = 0
                try {
                    val f = cacheCopy(u)
                    if (f != null) {
                        val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                        val r = PdfRenderer(pfd)
                        n = r.pageCount
                        r.close()
                        pfd.close()
                    }
                } catch (e: Throwable) {
                    n = 0
                }
                val name = Img.name(this, u)
                val cnt = n
                runOnUiThread {
                    if (cnt == 0) {
                        info.text = "কোনো PDF বাছা হয়নি"
                        Toast.makeText(this, "PDF খোলা যায়নি (পাসওয়ার্ড দেওয়া বা নষ্ট হতে পারে)", Toast.LENGTH_LONG).show()
                    } else {
                        pdf = u
                        pages = cnt
                        pdfName = if (name.lowercase().endsWith(".pdf")) name.substring(0, name.length - 4) else name
                        info.text = name + " · " + bn(cnt.toString()) + " পেজ"
                    }
                }
            }.start()
        } else if (requestCode == 84) {
            export(u)
        }
    }

    private fun export(tree: Uri) {
        status.text = "ছবি তৈরি হচ্ছে…"
        val usePng = png
        val scale = dpi / 72f
        val base = pdfName
        Thread {
            var done = 0
            var ok = true
            try {
                val f = File(cacheDir, "in.pdf")
                val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                val r = PdfRenderer(pfd)
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                for (i in 0 until r.pageCount) {
                    val page = r.openPage(i)
                    var w = (page.width * scale).toInt()
                    var h = (page.height * scale).toInt()
                    val big = Math.max(w, h)
                    if (big > 4000) {
                        w = w * 4000 / big
                        h = h * 4000 / big
                    }
                    val bmp = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888)
                    Canvas(bmp).drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    val bytes = Img.encode(bmp, if (usePng) 0 else 1, 92)
                    bmp.recycle()
                    val nm = base + "_page" + (i + 1) + (if (usePng) ".png" else ".jpg")
                    val doc = DocumentsContract.createDocument(contentResolver, parent, if (usePng) "image/png" else "image/jpeg", nm)
                    if (doc != null) {
                        contentResolver.openOutputStream(doc)?.use { it.write(bytes) }
                        done++
                    }
                    val d = done
                    runOnUiThread { status.text = "হচ্ছে… " + bn(d.toString()) + "/" + bn(r.pageCount.toString()) }
                }
                r.close()
                pfd.close()
            } catch (e: Throwable) {
                ok = false
            }
            val d = done
            runOnUiThread {
                status.text = if (ok) "সংরক্ষিত হয়েছে: " + bn(d.toString()) + "টি ছবি" else "কিছু পেজ সংরক্ষণ করা যায়নি (" + bn(d.toString()) + "টি হয়েছে)"
            }
        }.start()
    }
}
