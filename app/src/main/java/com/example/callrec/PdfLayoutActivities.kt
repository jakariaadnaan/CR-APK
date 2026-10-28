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
import android.view.View
import android.view.ViewGroup
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
        val act = actionCard(tr("লেআউট প্রয়োগ করুন"), "") { go() }
        val pv = card(10)
        pv.addView(captionLabel(tr("আউটপুট প্রিভিউ")))
        preview = SheetPreview(this)
        pv.addView(preview, lp(height = dp(190)))
        screen(tr("এক শিটে একাধিক পেজ"), pickCard(), opt, pv, act)
        chips()
    }

    private lateinit var preview: SheetPreview

    /** Draws one output sheet with small page boxes in the chosen arrangement. */
    private class SheetPreview(c: android.content.Context) : View(c) {
        var mode = 1
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(cv: Canvas) {
            val d = resources.displayMetrics.density
            val landscape = mode == 0 || mode == 2 || mode == 4
            val ph = height * 0.88f
            val pw = if (landscape) Math.min(ph * 1.414f, width * 0.9f) else ph / 1.414f
            val h = if (landscape) pw / 1.414f else ph
            val l = (width - pw) / 2f
            val t = (height - h) / 2f
            p.style = Paint.Style.FILL
            p.color = Ui.CARD
            cv.drawRect(l, t, l + pw, t + h, p)
            p.style = Paint.Style.STROKE
            p.color = Ui.LINE
            p.strokeWidth = d
            cv.drawRect(l, t, l + pw, t + h, p)
            val cols = intArrayOf(2, 2, 3, 3, 2)[mode]
            val rows = intArrayOf(1, 2, 2, 3, 1)[mode]
            val cw = pw / cols
            val ch = h / rows
            for (r in 0 until rows) for (c in 0 until cols) {
                val x = l + c * cw + 5 * d
                val y = t + r * ch + 5 * d
                val w = cw - 10 * d
                val hh = ch - 10 * d
                p.style = Paint.Style.FILL
                p.color = Ui.PREVIEW
                cv.drawRoundRect(x, y, x + w, y + hh, 3 * d, 3 * d, p)
                p.color = Ui.GREY
                val lines = Math.max(2, (hh / (7 * d)).toInt() - 1)
                for (k in 0 until lines) cv.drawRect(x + 5 * d, y + 6 * d + k * 6 * d, x + w - (if (k % 3 == 2) 18 * d else 5 * d), y + 7.5f * d + k * 6 * d, p)
            }
        }
    }

    private fun chips() {
        if (::preview.isInitialized) { preview.mode = mode; preview.invalidate() }
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
    private var spacing = 8
    private val thumbs = ArrayList<Bitmap>()
    private lateinit var info: TextView
    private lateinit var colRow: LinearLayout
    private lateinit var status: TextView
    private lateinit var canvasBox: LinearLayout
    private lateinit var spLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scroll.addView(box)

        val hdr = LinearLayout(this)
        hdr.gravity = Gravity.CENTER_VERTICAL
        hdr.addView(sectionLabel(tr("কোলাজ ক্যানভাস"), 0).also { it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { q -> q.bottomMargin = dp(8) } })
        info = label(tr("কোনো ছবি বাছা হয়নি"), 12f, Ui.MUTED)
        hdr.addView(info)
        box.addView(hdr)

        val c = card(10)
        canvasBox = LinearLayout(this)
        canvasBox.orientation = LinearLayout.VERTICAL
        c.addView(canvasBox)
        val pickZone = LinearLayout(this)
        pickZone.orientation = LinearLayout.VERTICAL
        pickZone.gravity = Gravity.CENTER
        pickZone.setPadding(dp(8), dp(22), dp(8), dp(22))
        pickZone.addView(iconBox(R.drawable.ic_image, Ui.KEY_OP, Ui.PRIMARY, 44, 22, 22))
        pickZone.addView(label(tr("ছবি বাছুন"), 14f, Ui.TEXT, true), lp(t = 10))
        pickZone.addView(label(tr("একসাথে সর্বোচ্চ ১২টি"), 11f, Ui.MUTED), lp(t = 2))
        pickZone.setOnClickListener { pickImages() }
        c.addView(pickZone)
        pickRef = pickZone
        box.addView(c, lp(b = 12))

        box.addView(sectionLabel(tr("প্রতি সারিতে কয়টি ছবি"), 2))
        colRow = LinearLayout(this)
        box.addView(colRow, lp(b = 10))

        val sp = card(14)
        val sh = LinearLayout(this)
        sh.gravity = Gravity.CENTER_VERTICAL
        sh.addView(label(tr("ফাঁক"), 13f, Ui.TEXT, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        spLabel = label(bn(spacing.toString()) + " px", 12f, Ui.MUTED)
        sh.addView(spLabel)
        sp.addView(sh)
        val bar = android.widget.SeekBar(this)
        bar.max = 40
        bar.progress = spacing
        bar.progressTintList = android.content.res.ColorStateList.valueOf(Ui.PRIMARY)
        bar.thumbTintList = android.content.res.ColorStateList.valueOf(Ui.PRIMARY)
        bar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                spacing = p
                spLabel.text = bn(p.toString()) + " px"
                renderCanvas()
            }
            override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
        })
        sp.addView(bar)
        box.addView(sp, lp(b = 12))

        status = label("", 14f, Ui.TEXT)
        box.addView(statusCard(status))
        setPdfScreen(tr("ছবির কোলাজ"), scroll, tr("কোলাজ সংরক্ষণ")) { go() }
        chips()
    }

    private var pickRef: View? = null

    private fun pickImages() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(i, 99)
    }

    private fun renderCanvas() {
        canvasBox.removeAllViews()
        if (thumbs.isEmpty()) return
        val cc = Math.min(cols, thumbs.size)
        val g = dp(Math.max(2, spacing / 2))
        for (row in thumbs.chunked(cc)) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            for ((i, b) in row.withIndex()) {
                val iv = android.widget.ImageView(this)
                iv.setImageBitmap(b)
                iv.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                iv.background = bg(Ui.PREVIEW, 8)
                r.addView(iv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).also { it.setMargins(if (i > 0) g else 0, 0, 0, 0) })
            }
            for (k in row.size until cc) r.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).also { it.leftMargin = g })
            canvasBox.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.widthPixels - dp(60)) / cc).also { it.bottomMargin = g })
        }
    }

    private fun chips() {
        colRow.removeAllViews()
        for (n in listOf(1, 2, 3, 4)) colRow.addView(choiceChip(bn(n.toString()) + tr(" টি"), cols == n) { cols = n; chips(); renderCanvas() }, lp(r = 8))
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
            info.text = bn(uris.size.toString()) + tr(" টি ছবি")
            pickRef?.visibility = View.GONE
            thumbs.clear()
            val list0 = uris.toList()
            Thread {
                val ts = list0.mapNotNull { Img.decode(this, it, 360) }
                runOnUiThread { thumbs.addAll(ts); renderCanvas() }
            }.start()
        } else if (requestCode == 100) {
            val out = data.data ?: return
            val list = uris.toList()
            val c = cols
            status.text = tr("তৈরি হচ্ছে…")
            Thread {
                val msg = try {
                    val cell = 700
                    val gap = Math.max(2, spacing * 2)
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
