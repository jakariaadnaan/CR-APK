package com.example.callrec

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.util.Matrix

private fun placeForm(dest: PDDocument, lu: LayerUtility, srcPage: PDPage?, page: PDPage, x: Float, y: Float, w: Float, h: Float) {
    if (srcPage == null) return
    val box = srcPage.cropBox
    val sw = box.width
    val sh = box.height
    if (sw <= 0f || sh <= 0f) return
    val s = Math.min(w / sw, h / sh)
    val form = lu.importPageAsForm(srcDocOf(lu) ?: return, srcPage)
    PDPageContentStream(dest, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
        cs.saveGraphicsState()
        cs.transform(Matrix.getTranslateInstance(x + (w - sw * s) / 2f - box.lowerLeftX * s, y + (h - sh * s) / 2f - box.lowerLeftY * s))
        cs.transform(Matrix.getScaleInstance(s, s))
        cs.drawForm(form)
        cs.restoreGraphicsState()
    }
}

private val srcHolder = HashMap<LayerUtility, PDDocument>()
private fun srcDocOf(lu: LayerUtility): PDDocument? = srcHolder[lu]

/** এক শিটে একাধিক পেজ ও বুকলেট: shrink several pages onto one sheet, or make a printable booklet. */
class PdfNupActivity : PdfBase() {
    private var mode = 1 // 0:2  1:4  2:6  3:9  4:booklet
    private lateinit var row: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val opt = card(14)
        opt.addView(label(tr("কেমন সাজাবেন"), 12f, Ui.MUTED, true))
        val hs = android.widget.HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        row = LinearLayout(this)
        hs.addView(row)
        opt.addView(hs, lp(t = 8))
        opt.addView(label(tr("বুকলেটে A4 কাগজের দুই পাশে ছাপিয়ে মাঝখানে ভাঁজ করলে বইয়ের মতো ক্রমে পেজ আসবে।"), 12f, Ui.MUTED), lp(t = 8))
        val act = actionCard(tr("নতুন PDF বানান"), "") { go() }
        screen(tr("এক শিটে একাধিক পেজ"), pickCard(), opt, act)
        chips()
    }

    private fun chips() {
        row.removeAllViews()
        val names = listOf(tr("২ পেজ"), tr("৪ পেজ"), tr("৬ পেজ"), tr("৯ পেজ"), tr("বুকলেট"))
        for (i in names.indices) row.addView(chip(names[i], mode == i) { mode = i; chips() }, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, r = 8))
    }

    private fun go() {
        if (!requireFile()) return
        if (encrypted) { status.text = tr("আগে PDF আনলক করুন (PDF লক / আনলক টুল)"); return }
        val m = mode
        askCreate(baseOut() + (if (m == 4) "_booklet.pdf" else "_sheets.pdf")) { out ->
            val f = src!!
            work(tr("তৈরি হচ্ছে…")) {
                PDDocument.load(f).use { doc ->
                    val nd = PDDocument()
                    val lu = LayerUtility(nd)
                    srcHolder[lu] = doc
                    try {
                        val a4w = PDRectangle.A4.width
                        val a4h = PDRectangle.A4.height
                        val n = doc.numberOfPages
                        if (m == 4) {
                            val total = ((n + 3) / 4) * 4
                            fun pg(i: Int): PDPage? = if (i in 0 until n) doc.getPage(i) else null
                            val sides = ArrayList<Pair<Int, Int>>()
                            for (k in 0 until total / 4) {
                                sides.add(Pair(total - 1 - 2 * k, 2 * k))
                                sides.add(Pair(2 * k + 1, total - 2 - 2 * k))
                            }
                            for (sd in sides) {
                                val page = PDPage(PDRectangle(a4h, a4w))
                                nd.addPage(page)
                                val cw = a4h / 2f
                                placeForm(nd, lu, pg(sd.first), page, 0f, 0f, cw, a4w)
                                placeForm(nd, lu, pg(sd.second), page, cw, 0f, cw, a4w)
                            }
                        } else {
                            val cols = intArrayOf(2, 2, 3, 3)[m]
                            val rows = intArrayOf(1, 2, 2, 3)[m]
                            val landscape = m == 0 || m == 2
                            val sw = if (landscape) a4h else a4w
                            val sh = if (landscape) a4w else a4h
                            val per = cols * rows
                            val cw = sw / cols
                            val ch = sh / rows
                            var i = 0
                            while (i < n) {
                                val page = PDPage(PDRectangle(sw, sh))
                                nd.addPage(page)
                                for (k in 0 until per) {
                                    if (i + k >= n) break
                                    val cx = (k % cols) * cw
                                    val cy = sh - ((k / cols) + 1) * ch
                                    placeForm(nd, lu, doc.getPage(i + k), page, cx + 4f, cy + 4f, cw - 8f, ch - 8f)
                                }
                                i += per
                            }
                        }
                        saveDoc(nd, out)
                    } finally {
                        srcHolder.remove(lu)
                        nd.close()
                    }
                }
                tr("সংরক্ষিত হয়েছে")
            }
        }
    }
}

/** ছবির কোলাজ: join up to 12 photos into one picture. */
class CollageActivity : GuardedActivity() {
    private val uris = ArrayList<Uri>()
    private var cols = 2
    private lateinit var info: TextView
    private lateinit var colRow: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)
        val c = card(14)
        c.addView(pill(tr("ছবি বাছুন")) {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "image/*"
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            startActivityForResult(i, 99)
        })
        info = label(tr("কোনো ছবি বাছা হয়নি"), 13f, Ui.MUTED)
        c.addView(info, lp(t = 8))
        box.addView(c, lp(b = 12))
        val o = card(14)
        o.addView(label(tr("প্রতি সারিতে কয়টি ছবি"), 12f, Ui.MUTED, true))
        colRow = LinearLayout(this)
        o.addView(colRow, lp(t = 8))
        box.addView(o, lp(b = 12))
        status = label("", 14f, Ui.TEXT)
        box.addView(statusCard(status))
        setPdfScreen(tr("ছবির কোলাজ"), scroll, tr("কোলাজ বানান")) { go() }
        chips()
    }

    private fun chips() {
        colRow.removeAllViews()
        for (n in listOf(1, 2, 3, 4)) {
            val on = cols == n
            val t = label(bn(n.toString()), 14f, if (on) Color.WHITE else Ui.PRIMARY, true)
            t.gravity = Gravity.CENTER
            t.background = bg(if (on) Ui.PRIMARY else Ui.CARD, 20, Ui.PRIMARY, 1)
            t.setPadding(dp(18), dp(8), dp(18), dp(8))
            t.setOnClickListener { cols = n; chips() }
            colRow.addView(t, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, r = 8))
        }
    }

    private fun go() {
        if (uris.size < 2) { status.text = tr("কমপক্ষে ২টি ছবি বাছুন"); return }
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/jpeg"
        i.putExtra(Intent.EXTRA_TITLE, "collage.jpg")
        saveOrAsk(i, 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return
        if (requestCode == 99) {
            uris.clear()
            val cd = data.clipData
            if (cd != null) for (k in 0 until Math.min(12, cd.itemCount)) uris.add(cd.getItemAt(k).uri)
            else data.data?.let { uris.add(it) }
            info.text = bn(uris.size.toString()) + tr("টি ছবি বাছা হয়েছে (সর্বোচ্চ ১২)")
        } else if (requestCode == 100) {
            val out = data.data ?: return
            val list = uris.toList()
            val c = cols
            status.text = tr("তৈরি হচ্ছে…")
            Thread {
                val msg = try {
                    val cell = 700
                    val gap = 12
                    val n = list.size
                    val cc = Math.min(c, n)
                    val rr = (n + cc - 1) / cc
                    val bmp = Bitmap.createBitmap(cc * cell + (cc + 1) * gap, rr * cell + (rr + 1) * gap, Bitmap.Config.ARGB_8888)
                    val cv = Canvas(bmp)
                    cv.drawColor(Color.WHITE)
                    val p = Paint(Paint.FILTER_BITMAP_FLAG)
                    for ((k, u) in list.withIndex()) {
                        val b = Img.decode(this, u, cell * 2) ?: continue
                        val side = Math.min(b.width, b.height)
                        val src = Rect((b.width - side) / 2, (b.height - side) / 2, (b.width + side) / 2, (b.height + side) / 2)
                        val x = gap + (k % cc) * (cell + gap)
                        val y = gap + (k / cc) * (cell + gap)
                        cv.drawBitmap(b, src, Rect(x, y, x + cell, y + cell), p)
                        b.recycle()
                    }
                    contentResolver.openOutputStream(out)?.use { it.write(Img.encode(bmp, 1, 92)) }
                    bmp.recycle()
                    tr("সংরক্ষিত হয়েছে")
                } catch (e: Throwable) { tr("কাজটি করা যায়নি") }
                runOnUiThread { status.text = msg }
            }.start()
        }
    }
}
