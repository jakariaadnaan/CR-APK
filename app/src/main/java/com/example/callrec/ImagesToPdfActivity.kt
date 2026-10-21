package com.example.callrec

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** ছবি → PDF: several pictures, in the chosen order, become one PDF. */
class ImagesToPdfActivity : GuardedActivity() {

    private val uris = ArrayList<Uri>()
    private val names = ArrayList<String>()
    private var a4 = true

    private lateinit var listBox: LinearLayout
    private lateinit var sizeRow: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)

        val c = card(14)
        c.addView(pill(tr("ছবি যোগ করুন")) { pick() }, lp(b = 10))
        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        c.addView(listBox, lp())
        box.addView(c, lp(b = 12))

        val opt = card(14)
        opt.addView(label(tr("পেজের মাপ"), 12f, Ui.MUTED, true))
        sizeRow = LinearLayout(this)
        sizeRow.orientation = LinearLayout.HORIZONTAL
        opt.addView(sizeRow, lp(t = 6))
        box.addView(opt, lp(b = 12))

        val res = card(14)
        status = label("", 14f, Ui.TEXT)
        res.addView(status, lp(b = 10))
        res.addView(pill(tr("PDF বানান")) { save() })
        box.addView(res, lp())

        setScreen(tr("ছবি → PDF"), scroll)
        render()
    }

    private fun chip(text: String, on: Boolean, click: () -> Unit): TextView {
        val c = label(text, 14f, if (on) Color.WHITE else Ui.PRIMARY, true)
        c.gravity = android.view.Gravity.CENTER
        c.background = bg(if (on) Ui.PRIMARY else Ui.CARD, 20, Ui.PRIMARY, 1)
        c.setPadding(dp(16), dp(8), dp(16), dp(8))
        c.setOnClickListener { click() }
        return c
    }

    private fun render() {
        sizeRow.removeAllViews()
        sizeRow.addView(chip("A4", a4) { a4 = true; render() }, lp(r = 8))
        sizeRow.addView(chip(tr("ছবির মাপ অনুযায়ী"), !a4) { a4 = false; render() })

        listBox.removeAllViews()
        if (uris.isEmpty()) {
            listBox.addView(label(tr("এখনো কোনো ছবি নেই"), 13f, Ui.MUTED))
        }
        for (i in uris.indices) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            row.addView(label(bn((i + 1).toString()) + ". " + names[i], 14f, Ui.TEXT).also { it.maxLines = 1; it.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(pill("↑", Ui.BG, Ui.PRIMARY) { move(i, -1) }, lp(l = 6))
            row.addView(pill("↓", Ui.BG, Ui.PRIMARY) { move(i, 1) }, lp(l = 6))
            row.addView(pill("✕", Ui.BG, Ui.RED) { uris.removeAt(i); names.removeAt(i); render() }, lp(l = 6))
            listBox.addView(row, lp(b = 6))
        }
        status.text = if (uris.isEmpty()) tr("আগে ছবি যোগ করুন") else bn(uris.size.toString()) + tr("টি ছবি → ") + bn(uris.size.toString()) + tr(" পেজের PDF")
    }

    private fun move(i: Int, d: Int) {
        val j = i + d
        if (j < 0 || j >= uris.size) return
        uris.add(j, uris.removeAt(i))
        names.add(j, names.removeAt(i))
        render()
    }

    private fun pick() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(i, 81)
    }

    private fun save() {
        if (uris.isEmpty()) {
            Toast.makeText(this, tr("আগে ছবি যোগ করুন"), Toast.LENGTH_SHORT).show()
            return
        }
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        i.putExtra(Intent.EXTRA_TITLE, "images.pdf")
        saveOrAsk(i, 82)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return
        if (requestCode == 81) {
            val clip = data.clipData
            if (clip != null) {
                for (k in 0 until clip.itemCount) add(clip.getItemAt(k).uri)
            } else if (data.data != null) {
                add(data.data!!)
            }
            render()
        } else if (requestCode == 82) {
            val out = data.data ?: return
            build(out)
        }
    }

    private fun add(u: Uri) {
        uris.add(u)
        names.add(Img.name(this, u))
    }

    private fun build(out: Uri) {
        status.text = tr("PDF তৈরি হচ্ছে…")
        val list = ArrayList(uris)
        val useA4 = a4
        Thread {
            var ok = true
            var pages = 0
            try {
                val doc = PdfDocument()
                for (u in list) {
                    val b = Img.decode(this, u, 2200) ?: continue
                    val pw: Int
                    val ph: Int
                    if (useA4) {
                        if (b.width > b.height) { pw = 842; ph = 595 } else { pw = 595; ph = 842 }
                    } else {
                        val s = 842f / Math.max(b.width, b.height)
                        pw = Math.max(1, (b.width * s).toInt())
                        ph = Math.max(1, (b.height * s).toInt())
                    }
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pages + 1).create())
                    val cv: Canvas = page.canvas
                    cv.drawColor(Color.WHITE)
                    val m = if (useA4) 24f else 0f
                    val s = Math.min((pw - 2 * m) / b.width, (ph - 2 * m) / b.height)
                    val w = b.width * s
                    val h = b.height * s
                    val l = (pw - w) / 2f
                    val t = (ph - h) / 2f
                    val p = Paint(Paint.FILTER_BITMAP_FLAG)
                    cv.drawBitmap(b, null, RectF(l, t, l + w, t + h), p)
                    doc.finishPage(page)
                    b.recycle()
                    pages++
                }
                if (pages == 0) {
                    ok = false
                } else {
                    contentResolver.openOutputStream(out)?.use { doc.writeTo(it) }
                }
                doc.close()
            } catch (e: Throwable) {
                ok = false
            }
            val n = pages
            runOnUiThread {
                if (ok) {
                    status.text = tr("সংরক্ষিত হয়েছে: ") + bn(n.toString()) + tr(" পেজের PDF")
                    Usage.done(this, "ImagesToPdf")
                } else {
                    status.text = tr("PDF বানানো যায়নি")
                    Toast.makeText(this, tr("PDF বানানো যায়নি"), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
