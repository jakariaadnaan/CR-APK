package com.example.callrec

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.util.Matrix
import java.io.File

/** PDF Edit: open a PDF or pictures, edit the pages on a dark editor screen, save the result. */
class PdfEditorActivity : GuardedActivity() {

    private var stage = 0 // 0 upload, 1 editor, 2 saved
    private lateinit var root: LinearLayout

    // dark editor palette
    private val dBg = Color.parseColor("#14151C")
    private val dCard = Color.parseColor("#232532")
    private val dLine = Color.parseColor("#33364A")
    private val dText = Color.parseColor("#F2F3F8")
    private val dMuted = Color.parseColor("#9A9DB2")

    private var baseName = "document"
    private val history = ArrayList<File>() // history.last() is the file being edited
    private var versionNo = 0
    private var pageCount = 0
    private var pageIdx = 0
    private var busy = false
    private var loading = false
    private var renderToken = 0

    private var outUri: Uri? = null
    private var outSize = 0L

    private var cropping = false
    private var curBmp: Bitmap? = null
    private var cropView: CropView? = null
    private var pageView: ImageView? = null
    private var pageLabel: TextView? = null
    private var undoBtn: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        setContentView(root)
        render()
    }

    @Deprecated("Back leaves the editor after a check")
    override fun onBackPressed() {
        when (stage) {
            1 -> if (history.size > 1) confirmLeave() else { stage = 0; history.clear(); render() }
            2 -> { stage = 1; render() }
            else -> super.onBackPressed()
        }
    }

    private fun confirmLeave() {
        android.app.AlertDialog.Builder(this)
            .setMessage(tr("সংরক্ষণ না করলে এডিটগুলো হারিয়ে যাবে। বের হবেন?"))
            .setPositiveButton(tr("বের হই")) { _, _ -> stage = 0; history.clear(); render() }
            .setNegativeButton(tr("থাকি"), null)
            .show()
    }

    private fun render() {
        root.removeAllViews()
        val dark = stage == 1
        root.setBackgroundColor(if (dark) dBg else Ui.BG)
        window.statusBarColor = if (dark) dBg else Ui.BG
        window.navigationBarColor = if (dark) dBg else Ui.NAV
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (dark || Ui.dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        when (stage) {
            0 -> renderUpload()
            1 -> renderEditor()
            else -> renderSaved()
        }
    }

    private fun cur(): File = history.last()

    // ------------------------------------------------------------------ step 1: upload

    private fun renderUpload() {
        root.addView(pdfHeader(tr("PDF Edit"), tr("PDF বা ছবি খুলে এডিট করুন"), { finish() }), lp())
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(8), dp(16), dp(16))
        scroll.addView(col)

        val zone = LinearLayout(this)
        zone.orientation = LinearLayout.VERTICAL
        zone.gravity = Gravity.CENTER
        val dz = GradientDrawable()
        dz.setColor(Ui.mix(Ui.PRIMARY, Ui.BG, if (Ui.dark) 0.14f else 0.07f))
        dz.cornerRadius = dp(18).toFloat()
        dz.setStroke(dp(1), Ui.mix(Ui.PRIMARY, Ui.BG, 0.45f), dp(6).toFloat(), dp(4).toFloat())
        zone.background = dz
        zone.setPadding(dp(16), dp(28), dp(16), dp(28))
        val ib = LinearLayout(this)
        ib.gravity = Gravity.CENTER
        ib.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.82f), 18)
        ib.addView(iconView(R.drawable.ic_pdf, 34, Ui.PRIMARY))
        zone.addView(ib, lp(width = dp(70), height = dp(70)))
        val t = label(if (loading) tr("খোলা হচ্ছে…") else tr("এডিট করতে ফাইল খুলুন"), 17f, Ui.PRIMARY, true)
        t.gravity = Gravity.CENTER
        zone.addView(t, lp(t = 12))
        val s = label(tr("ফোন থেকে একটি PDF অথবা এক বা একাধিক ছবি বাছুন"), 12f, Ui.MUTED)
        s.gravity = Gravity.CENTER
        zone.addView(s, lp(t = 4))
        col.addView(zone, lp())
        if (!loading) {
            col.addView(bigButton(tr("PDF বাছুন")) { pickPdf() }, lp(height = dp(52), t = 18))
            col.addView(outlineButton(tr("ছবি বাছুন")) { pickImages() }, lp(height = dp(52), t = 10))
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun pickPdf() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        startActivityForResult(i, 71)
    }

    private fun pickImages() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(i, 72)
    }

    private fun newVersion(): File = File(cacheDir, "edit_v" + (versionNo++) + ".pdf")

    private fun openPdf(u: Uri) {
        loading = true
        render()
        Thread {
            var ok = false
            var err = ""
            val f = newVersion()
            try {
                contentResolver.openInputStream(u)?.use { ins -> f.outputStream().use { ins.copyTo(it) } }
                PDDocument.load(f).use { pageCount = it.numberOfPages }
                ok = pageCount > 0
            } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
                err = tr("এই PDF-এ পাসওয়ার্ড দেওয়া আছে, আগে আনলক করুন")
            } catch (e: Throwable) {
                err = tr("PDF খোলা যায়নি")
            }
            val nm = Img.name(this, u)
            runOnUiThread { started(ok, f, nm.removeSuffix(".pdf").removeSuffix(".PDF"), err) }
        }.start()
    }

    private fun openImages(list: List<Uri>) {
        loading = true
        render()
        Thread {
            var ok = false
            val f = newVersion()
            try {
                val doc = PdfDocument()
                var n = 0
                for (u in list) {
                    val b = Img.decode(this, u, 2200) ?: continue
                    val s = 842f / Math.max(b.width, b.height)
                    val pw = Math.max(1, (b.width * s).toInt())
                    val ph = Math.max(1, (b.height * s).toInt())
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, n + 1).create())
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawBitmap(b, null, RectF(0f, 0f, pw.toFloat(), ph.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                    doc.finishPage(page)
                    b.recycle()
                    n++
                }
                if (n > 0) f.outputStream().use { doc.writeTo(it) }
                doc.close()
                pageCount = n
                ok = n > 0
            } catch (e: Throwable) { }
            val nm = if (list.size == 1) Img.name(this, list[0]).substringBeforeLast('.') else "images"
            runOnUiThread { started(ok, f, nm, tr("ছবি খোলা যায়নি")) }
        }.start()
    }

    private fun started(ok: Boolean, f: File, name: String, err: String) {
        loading = false
        if (!ok) {
            Toast.makeText(this, err, Toast.LENGTH_LONG).show()
            render()
            return
        }
        baseName = name
        history.clear()
        history.add(f)
        pageIdx = 0
        stage = 1
        render()
    }

    // ------------------------------------------------------------------ step 2: editor

    private fun glyphTool(glyph: String, name: String, onClick: () -> Unit): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.setPadding(0, dp(10), 0, dp(8))
        val g = label(glyph, 22f, dText, true)
        g.gravity = Gravity.CENTER
        c.addView(g)
        val l = label(name, 11f, dMuted)
        l.gravity = Gravity.CENTER
        c.addView(l, lp(t = 2))
        c.setOnClickListener { if (!busy) { it.tapFx(); onClick() } }
        return c
    }

    private fun renderEditor() {
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(8), dp(10), dp(12), dp(8))
        val b = LinearLayout(this)
        b.gravity = Gravity.CENTER
        b.addView(iconView(R.drawable.ic_back, 24, dText))
        b.setOnClickListener { onBackPressed() }
        head.addView(b, LinearLayout.LayoutParams(dp(44), dp(44)))
        val tt = label(baseName, 17f, dText, true)
        tt.maxLines = 1
        tt.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        head.addView(tt, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val un = label(tr("↶ পূর্বাবস্থা"), 13f, Ui.PRIMARY, true)
        un.setPadding(dp(10), dp(8), dp(6), dp(8))
        un.setOnClickListener { undo() }
        undoBtn = un
        head.addView(un)
        root.addView(head, lp())

        val area = FrameLayout(this)
        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.adjustViewBounds = true
        pageView = iv
        area.addView(iv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).also { it.setMargins(dp(16), dp(4), dp(16), dp(4)) })
        fun arrow(text: String, gravity: Int, d: Int): View {
            val a = label(text, 26f, Color.WHITE, true)
            a.gravity = Gravity.CENTER
            a.background = bg(Color.parseColor("#88000000"), 22)
            a.setOnClickListener { go(d) }
            val lpA = FrameLayout.LayoutParams(dp(44), dp(44), gravity or Gravity.CENTER_VERTICAL)
            lpA.setMargins(dp(8), 0, dp(8), 0)
            area.addView(a, lpA)
            return a
        }
        arrow("‹", Gravity.START, -1)
        arrow("›", Gravity.END, 1)
        val det = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: android.view.MotionEvent): Boolean = true
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, vx: Float, vy: Float): Boolean {
                if (Math.abs(vx) > Math.abs(vy) && Math.abs(vx) > 600) { go(if (vx < 0) 1 else -1); return true }
                return false
            }
        })
        iv.setOnTouchListener { _, ev -> det.onTouchEvent(ev) }
        if (cropping && curBmp != null) {
            val cv = CropView(this)
            cv.bmp = curBmp
            cropView = cv
            area.removeAllViews()
            area.addView(cv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).also { it.setMargins(dp(8), dp(4), dp(8), dp(4)) })
        }
        root.addView(area, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val pl = label("", 12f, dMuted)
        pl.gravity = Gravity.CENTER
        pageLabel = pl
        root.addView(pl, lp(t = 4, b = 6))

        if (cropping) {
            val br = LinearLayout(this)
            br.setPadding(dp(16), dp(10), dp(16), dp(14))
            br.addView(outlineButton(tr("বাতিল")) { cropping = false; render() }, LinearLayout.LayoutParams(0, dp(52), 1f).also { it.setMargins(0, 0, dp(8), 0) })
            br.addView(bigButton(tr("ক্রপ করুন")) { applyCrop() }, LinearLayout.LayoutParams(0, dp(52), 1f))
            root.addView(br, lp())
        } else {
            val tools = LinearLayout(this)
            tools.background = bg(dCard, 16)
            val w = { LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
            tools.addView(glyphTool("⌗", tr("ক্রপ")) { startCrop() }, w())
            tools.addView(glyphTool("↻", tr("ঘোরান")) { rotate() }, w())
            tools.addView(glyphTool("◐", tr("ফিল্টার")) { filter() }, w())
            tools.addView(glyphTool("⤢", tr("রিসাইজ")) { resize() }, w())
            tools.addView(glyphTool("✕", tr("মুছুন")) { deletePage() }, w())
            root.addView(tools, lp(l = 12, r = 12))

            val bar = LinearLayout(this)
            bar.orientation = LinearLayout.VERTICAL
            bar.setPadding(dp(16), dp(10), dp(16), dp(14))
            bar.addView(bigButton(tr("PDF সংরক্ষণ")) { save() }, lp(height = dp(52)))
            root.addView(bar, lp())
        }
        refreshPage()
    }

    private fun go(d: Int) {
        val n = pageIdx + d
        if (n < 0 || n >= pageCount || busy) return
        pageIdx = n
        refreshPage()
    }

    private fun refreshPage() {
        pageLabel?.text = tr("পেজ ") + bn((pageIdx + 1).toString()) + " / " + bn(pageCount.toString())
        undoBtn?.alpha = if (history.size > 1) 1f else 0.35f
        if (cropping) return
        val f = cur()
        val idx = pageIdx
        val token = ++renderToken
        val wpx = resources.displayMetrics.widthPixels
        Thread {
            var bmp: Bitmap? = null
            try {
                val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                val rd = PdfRenderer(pfd)
                try {
                    val pg = rd.openPage(Math.min(idx, rd.pageCount - 1))
                    val w = wpx
                    val h = Math.max(1, (pg.height.toFloat() * w / pg.width).toInt())
                    val b = Bitmap.createBitmap(w, Math.min(h, 3000), Bitmap.Config.ARGB_8888)
                    b.eraseColor(Color.WHITE)
                    pg.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pg.close()
                    bmp = b
                } finally {
                    rd.close()
                    pfd.close()
                }
            } catch (e: Throwable) { }
            runOnUiThread { if (token == renderToken && bmp != null) { curBmp = bmp; pageView?.setImageBitmap(bmp) } }
        }.start()
    }

    /** Runs an edit on a copy of the current file; the copy becomes the new current file. */
    private fun apply(edit: (PDDocument) -> Unit) {
        if (busy) return
        busy = true
        pageLabel?.text = tr("কাজ হচ্ছে…")
        val from = cur()
        val to = newVersion()
        Thread {
            var ok = true
            var n = pageCount
            try {
                PDDocument.load(from).use { doc ->
                    edit(doc)
                    n = doc.numberOfPages
                    doc.save(to)
                }
            } catch (e: Throwable) { ok = false }
            runOnUiThread {
                busy = false
                if (ok) {
                    history.add(to)
                    pageCount = n
                    if (pageIdx >= n) pageIdx = n - 1
                } else Toast.makeText(this, tr("কাজটি করা যায়নি"), Toast.LENGTH_SHORT).show()
                refreshPage()
            }
        }.start()
    }

    private fun undo() {
        if (busy || history.size <= 1) return
        history.removeAt(history.size - 1)
        try { PDDocument.load(cur()).use { pageCount = it.numberOfPages } } catch (e: Throwable) { }
        if (pageIdx >= pageCount) pageIdx = pageCount - 1
        refreshPage()
    }

    private fun rotate() {
        val i = pageIdx
        apply { doc ->
            val pg = doc.getPage(i)
            pg.rotation = (pg.rotation + 90) % 360
        }
    }

    private fun deletePage() {
        if (pageCount <= 1) {
            Toast.makeText(this, tr("শেষ পেজটি মুছে ফেলা যাবে না"), Toast.LENGTH_SHORT).show()
            return
        }
        val i = pageIdx
        android.app.AlertDialog.Builder(this)
            .setTitle(tr("পেজ মুছুন"))
            .setMessage(tr("পেজ ") + bn((i + 1).toString()) + tr(" মুছে ফেলবেন?"))
            .setPositiveButton(tr("হ্যাঁ, মুছুন")) { _, _ -> apply { doc -> doc.removePage(i) } }
            .setNegativeButton(tr("বাতিল"), null)
            .show()
    }

    private fun startCrop() {
        if (curBmp == null || busy) return
        cropping = true
        render()
    }

    private fun applyCrop() {
        val fr = cropView?.fractions() ?: return
        val i = pageIdx
        cropping = false
        render()
        apply { doc -> cropPage(doc.getPage(i), fr[0], fr[1], fr[2], fr[3]) }
    }

    /** Cuts a page down to the box given as fractions (left, top, right, bottom) of what is shown. */
    private fun cropPage(page: PDPage, l: Float, t: Float, r: Float, b: Float) {
        val cb = page.cropBox
        val x0 = cb.lowerLeftX
        val y0 = cb.lowerLeftY
        val w = cb.width
        val h = cb.height
        val rot = ((page.rotation % 360) + 360) % 360
        fun map(fx: Float, fy: Float): FloatArray = when (rot) {
            90 -> floatArrayOf(x0 + fy * w, y0 + fx * h)
            180 -> floatArrayOf(x0 + (1 - fx) * w, y0 + fy * h)
            270 -> floatArrayOf(x0 + (1 - fy) * w, y0 + (1 - fx) * h)
            else -> floatArrayOf(x0 + fx * w, y0 + (1 - fy) * h)
        }
        val a = map(l, t)
        val c = map(r, b)
        val lx = Math.min(a[0], c[0])
        val ly = Math.min(a[1], c[1])
        val box = PDRectangle(lx, ly, Math.abs(a[0] - c[0]), Math.abs(a[1] - c[1]))
        page.mediaBox = box
        page.cropBox = box
    }

    private fun filter() {
        val names = arrayOf(tr("সাদাকালো (গ্রেস্কেল)"), tr("ডকুমেন্ট (কালো-সাদা)"), tr("উজ্জ্বল ও স্পষ্ট"))
        android.app.AlertDialog.Builder(this)
            .setTitle(tr("ফিল্টার"))
            .setItems(names) { _, mode ->
                val scopes = arrayOf(tr("শুধু এই পেজ"), tr("সব পেজ"))
                android.app.AlertDialog.Builder(this)
                    .setTitle(tr("কোন পেজে"))
                    .setItems(scopes) { _, sc ->
                        val list = if (sc == 0) listOf(pageIdx) else (0 until pageCount).toList()
                        val src = cur()
                        apply { doc -> filterPages(doc, src, list, mode) }
                    }
                    .show()
            }
            .show()
    }

    /** Replaces the chosen pages with filtered pictures of themselves (the text stops being selectable). */
    private fun filterPages(doc: PDDocument, srcFile: File, list: List<Int>, mode: Int) {
        val pfd = ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val rd = PdfRenderer(pfd)
        try {
            for (idx in list) {
                val pg = rd.openPage(idx)
                val ptsW = pg.width
                val ptsH = pg.height
                val sc = Math.min(150f / 72f, 2000f / Math.max(ptsW, ptsH))
                val bw = Math.max(1, Math.round(ptsW * sc))
                val bh = Math.max(1, Math.round(ptsH * sc))
                val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pg.close()
                val out = filtered(bmp, mode)
                val np = PDPage(PDRectangle(ptsW.toFloat(), ptsH.toFloat()))
                val old = doc.getPage(idx)
                doc.pages.insertBefore(np, old)
                doc.removePage(idx + 1)
                val img = JPEGFactory.createFromImage(doc, out, 0.85f)
                PDPageContentStream(doc, np).use { cs -> cs.drawImage(img, 0f, 0f, ptsW.toFloat(), ptsH.toFloat()) }
                if (out !== bmp) bmp.recycle()
                out.recycle()
            }
        } finally {
            rd.close()
            pfd.close()
        }
    }

    private fun filtered(src: Bitmap, mode: Int): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val cm = ColorMatrix()
        when (mode) {
            0, 1 -> cm.setSaturation(0f)
            else -> {
                val c = 1.25f
                val br = 14f
                cm.set(floatArrayOf(c, 0f, 0f, 0f, br + (1 - c) * 128f, 0f, c, 0f, 0f, br + (1 - c) * 128f, 0f, 0f, c, 0f, br + (1 - c) * 128f, 0f, 0f, 0f, 1f, 0f))
            }
        }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        cv.drawBitmap(src, 0f, 0f, paint)
        if (mode == 1) {
            val n = out.width * out.height
            val px = IntArray(n)
            out.getPixels(px, 0, out.width, 0, 0, out.width, out.height)
            var sum = 0L
            for (v in px) sum += (v shr 8) and 0xFF
            val thr = (sum / Math.max(1, n) * 0.82f).toInt()
            for (k in 0 until n) px[k] = if (((px[k] shr 8) and 0xFF) > thr) Color.WHITE else Color.BLACK
            out.setPixels(px, 0, out.width, 0, 0, out.width, out.height)
        }
        return out
    }

    private fun resize() {
        val names = arrayOf("A4", "A3", "A5", "US Letter")
        val sizes = arrayOf(PDRectangle.A4, PDRectangle.A3, PDRectangle.A5, PDRectangle.LETTER)
        android.app.AlertDialog.Builder(this)
            .setTitle(tr("পেজ সাইজ"))
            .setItems(names) { _, w ->
                val target = sizes[w]
                apply { doc ->
                    for (k in 0 until doc.numberOfPages) fitTo(doc, doc.getPage(k), target)
                }
            }
            .show()
    }

    /** Scales a page's content to fit the target size (keeping its portrait or landscape direction), centred. */
    private fun fitTo(doc: PDDocument, page: PDPage, target: PDRectangle) {
        val cb = page.cropBox
        val rot = ((page.rotation % 360) + 360) % 360
        val swap = rot == 90 || rot == 270
        val cw = cb.width
        val ch = cb.height
        val dw = if (swap) ch else cw
        val dh = if (swap) cw else ch
        val land = dw > dh
        val lo = Math.min(target.width, target.height)
        val hi = Math.max(target.width, target.height)
        val tw = if (land) hi else lo
        val th = if (land) lo else hi
        val s = Math.min(tw / dw, th / dh)
        val nw = if (swap) th else tw
        val nh = if (swap) tw else th
        val tx = (nw - cw * s) / 2f - cb.lowerLeftX * s
        val ty = (nh - ch * s) / 2f - cb.lowerLeftY * s
        val before = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.PREPEND, true, false)
        before.saveGraphicsState()
        before.transform(Matrix(s, 0f, 0f, s, tx, ty))
        before.close()
        val after = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, false)
        after.restoreGraphicsState()
        after.close()
        val box = PDRectangle(nw, nh)
        page.mediaBox = box
        page.cropBox = box
    }

    // ------------------------------------------------------------------ save and result

    private fun save() {
        if (busy) return
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        i.putExtra(Intent.EXTRA_TITLE, baseName + "_edited.pdf")
        saveOrAsk(i, 73)
    }

    private fun writeOut(out: Uri) {
        busy = true
        val f = cur()
        Thread {
            var ok = true
            try {
                contentResolver.openOutputStream(out)?.use { o -> f.inputStream().use { it.copyTo(o) } }
            } catch (e: Throwable) { ok = false }
            runOnUiThread {
                busy = false
                if (ok) {
                    outUri = out
                    outSize = Img.size(this, out)
                    Usage.done(this, "PdfEditorActivity", outSize, Img.name(this, out))
                    stage = 2
                    render()
                } else Toast.makeText(this, tr("সংরক্ষণ করা যায়নি"), Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun renderSaved() {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(24), dp(60), dp(24), dp(16))
        val ring = PercentRing(this)
        ring.percent = 100
        col.addView(ring, lp(width = dp(150), height = dp(150)))
        val t = label(tr("সংরক্ষিত হয়েছে"), 18f, Ui.PRIMARY, true)
        t.gravity = Gravity.CENTER
        col.addView(t, lp(t = 16))
        val o = LinearLayout(this)
        o.orientation = LinearLayout.VERTICAL
        o.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.93f), 16, Ui.LINE, 1)
        o.setPadding(dp(14), dp(10), dp(14), dp(12))
        val nm = label(outUri?.let { Img.name(this, it) } ?: baseName, 14f, Ui.TEXT, true)
        nm.maxLines = 1
        nm.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        o.addView(nm)
        o.addView(label(Img.human(outSize) + " · " + bn(pageCount.toString()) + tr(" পেজ"), 12f, Ui.MUTED), lp(t = 2))
        col.addView(o, lp(t = 20))
        root.addView(col, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        bar.addView(bigButton(tr("ফাইল দেখুন")) { viewFile() }, lp(height = dp(52), b = 8))
        bar.addView(outlineButton(tr("আরও এডিট করুন")) { stage = 1; render() }, lp(height = dp(48), b = 8))
        bar.addView(outlineButton(tr("সম্পন্ন")) { finish() }, lp(height = dp(48)))
        root.addView(bottomBar(bar), lp())
    }

    private fun viewFile() {
        val u = outUri ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Throwable) {
            Toast.makeText(this, tr("PDF দেখার কোনো অ্যাপ পাওয়া যায়নি"), Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return
        when (requestCode) {
            71 -> data.data?.let { openPdf(it) }
            72 -> {
                val cd = data.clipData
                val l = ArrayList<Uri>()
                if (cd != null) for (k in 0 until cd.itemCount) l.add(cd.getItemAt(k).uri) else data.data?.let { l.add(it) }
                if (l.isNotEmpty()) openImages(l)
            }
            73 -> data.data?.let { writeOut(it) }
        }
    }
}

/** Shows a picture with a movable crop frame; the frame is kept as fractions of the picture. */
private class CropView(ctx: Context) : View(ctx) {
    var bmp: Bitmap? = null
    private var l = 0.06f
    private var t = 0.06f
    private var r = 0.94f
    private var b = 0.94f
    private val img = RectF()
    private val d = ctx.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var drag = -1
    private var lastX = 0f
    private var lastY = 0f

    fun fractions(): FloatArray = floatArrayOf(l, t, r, b)

    private fun fit() {
        val bm = bmp ?: return
        val s = Math.min(width.toFloat() / bm.width, height.toFloat() / bm.height)
        val w = bm.width * s
        val h = bm.height * s
        img.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
    }

    private fun px(f: Float) = img.left + f * img.width()
    private fun py(f: Float) = img.top + f * img.height()

    override fun onDraw(c: Canvas) {
        val bm = bmp ?: return
        fit()
        c.drawBitmap(bm, null, img, paint)
        val cl = px(l); val ct = py(t); val cr = px(r); val cb = py(b)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#99000000")
        c.drawRect(img.left, img.top, img.right, ct, paint)
        c.drawRect(img.left, cb, img.right, img.bottom, paint)
        c.drawRect(img.left, ct, cl, cb, paint)
        c.drawRect(cr, ct, img.right, cb, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * d
        paint.color = Color.WHITE
        c.drawRect(cl, ct, cr, cb, paint)
        paint.strokeWidth = 1f * d
        paint.color = Color.parseColor("#88FFFFFF")
        for (k in 1..2) {
            c.drawLine(cl + (cr - cl) * k / 3f, ct, cl + (cr - cl) * k / 3f, cb, paint)
            c.drawLine(cl, ct + (cb - ct) * k / 3f, cr, ct + (cb - ct) * k / 3f, paint)
        }
        paint.style = Paint.Style.FILL
        for (pt in listOf(floatArrayOf(cl, ct), floatArrayOf(cr, ct), floatArrayOf(cl, cb), floatArrayOf(cr, cb))) {
            paint.color = Color.WHITE
            c.drawCircle(pt[0], pt[1], 11f * d, paint)
            paint.color = Ui.PRIMARY
            c.drawCircle(pt[0], pt[1], 8f * d, paint)
        }
    }

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                val hit = 30f * d
                val pts = listOf(floatArrayOf(px(l), py(t)), floatArrayOf(px(r), py(t)), floatArrayOf(px(l), py(b)), floatArrayOf(px(r), py(b)))
                drag = -1
                var best = hit
                for ((k, p) in pts.withIndex()) {
                    val dd = Math.hypot((e.x - p[0]).toDouble(), (e.y - p[1]).toDouble()).toFloat()
                    if (dd < best) { best = dd; drag = k }
                }
                if (drag < 0 && e.x > px(l) && e.x < px(r) && e.y > py(t) && e.y < py(b)) drag = 4
                lastX = e.x
                lastY = e.y
                parent?.requestDisallowInterceptTouchEvent(true)
                return drag >= 0
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (drag < 0 || img.width() <= 0f) return false
                val dx = (e.x - lastX) / img.width()
                val dy = (e.y - lastY) / img.height()
                lastX = e.x
                lastY = e.y
                val min = 0.1f
                when (drag) {
                    0 -> { l = (l + dx).coerceIn(0f, r - min); t = (t + dy).coerceIn(0f, b - min) }
                    1 -> { r = (r + dx).coerceIn(l + min, 1f); t = (t + dy).coerceIn(0f, b - min) }
                    2 -> { l = (l + dx).coerceIn(0f, r - min); b = (b + dy).coerceIn(t + min, 1f) }
                    3 -> { r = (r + dx).coerceIn(l + min, 1f); b = (b + dy).coerceIn(t + min, 1f) }
                    else -> {
                        val w = r - l
                        val h = b - t
                        l = (l + dx).coerceIn(0f, 1f - w); r = l + w
                        t = (t + dy).coerceIn(0f, 1f - h); b = t + h
                    }
                }
                invalidate()
                return true
            }
            else -> { drag = -1; return true }
        }
    }
}
