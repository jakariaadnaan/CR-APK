package com.example.callrec

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
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
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.DocumentsContract
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
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
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
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
    private var placing = false
    private var sigBmp: Bitmap? = null
    private var placeView: PlaceView? = null
    private var lockPass: String? = null
    private var lockAes256 = true
    private var pendingSplit: List<Int>? = null
    private var pendingEvery = 1
    private var exportFmt = 1
    private var wmSizeText = 0.96f
    private var wmSizeLogo = 0.5f
    private var wmTab = 0
    private var wmText = ""
    private var wmLogo: Uri? = null
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
        val mn = label("⋮", 22f, dText, true)
        mn.setPadding(dp(10), dp(4), dp(4), dp(4))
        mn.setOnClickListener { if (!busy && !cropping && !placing) openMenu() }
        head.addView(mn)
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
        if (placing && curBmp != null && sigBmp != null) {
            val pv = PlaceView(this)
            pv.page = curBmp
            pv.sig = sigBmp
            placeView = pv
            area.removeAllViews()
            area.addView(pv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).also { it.setMargins(dp(8), dp(4), dp(8), dp(4)) })
        }
        root.addView(area, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val pl = label("", 12f, dMuted)
        pl.gravity = Gravity.CENTER
        pageLabel = pl
        root.addView(pl, lp(t = 4, b = 6))

        if (placing) {
            val br = LinearLayout(this)
            br.setPadding(dp(16), dp(10), dp(16), dp(14))
            br.addView(outlineButton(tr("বাতিল")) { placing = false; render() }, LinearLayout.LayoutParams(0, dp(52), 1f).also { it.setMargins(0, 0, dp(8), 0) })
            br.addView(bigButton(tr("সই বসান")) { applySignature() }, LinearLayout.LayoutParams(0, dp(52), 1f))
            root.addView(br, lp())
        } else if (cropping) {
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
        if (cropping || placing) return
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
    private fun apply(after: (() -> Unit)? = null, edit: (PDDocument) -> Unit) {
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
                if (ok) after?.invoke()
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

    // ------------------------------------------------------------------ file menu (more entries come later)

    private fun sheet(title: String, rows: List<Triple<String, String, () -> Unit>>) {
        val d = android.app.Dialog(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val g = GradientDrawable()
        g.setColor(dCard)
        g.cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f)
        box.background = g
        box.setPadding(dp(8), dp(14), dp(8), dp(16))
        val tt = label(title, 16f, dText, true)
        tt.setPadding(dp(14), 0, dp(14), dp(8))
        box.addView(tt)
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(this)
        sv.addView(list)
        box.addView(sv)
        for ((glyph, name, act) in rows) {
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(14), dp(13), dp(14), dp(13))
            val gl = label(glyph, 18f, dText, true)
            gl.gravity = Gravity.CENTER
            r.addView(gl, lp(width = dp(30), r = 12))
            r.addView(label(name, 15f, dText))
            r.setOnClickListener { d.dismiss(); act() }
            list.addView(r)
        }
        d.setContentView(box)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        d.window?.setGravity(Gravity.BOTTOM)
        d.show()
    }

    private fun openMenu() {
        val rows = ArrayList<Triple<String, String, () -> Unit>>()
        rows.add(Triple("Ⓦ", tr("ওয়াটারমার্ক যোগ করুন"), { watermarkDialog() }))
        rows.add(Triple("✍", tr("ডিজিটাল সই যোগ করুন"), { signDialog() }))
        rows.add(Triple("✂", tr("PDF ভাগ করুন"), { splitDialog() }))
        rows.add(Triple("⊕", tr("PDF জোড়া দিন"), { pickMore() }))
        rows.add(Triple("🔒", if (lockPass != null) tr("PDF লক (চালু আছে)") else tr("PDF লক করুন"), { protectDialog() }))
        rows.add(Triple("⇲", tr("PDF ছোট করুন"), { compressDialog() }))
        rows.add(Triple("✎", tr("নাম বদলান"), { renameDialog() }))
        rows.add(Triple("⎙", tr("প্রিন্ট"), { printDoc() }))
        rows.add(Triple("⇪", tr("শেয়ার"), { shareSheet() }))
        rows.add(Triple("🖼", tr("ছবি হিসেবে এক্সপোর্ট"), { exportDialog() }))
        sheet(baseName, rows)
    }

    // ------------------------------------------------------------------ watermark

    private fun watermarkDialog() {
        val d = android.app.Dialog(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val g = GradientDrawable()
        g.setColor(dCard)
        g.cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f)
        box.background = g
        box.setPadding(dp(18), dp(16), dp(18), dp(18))
        box.addView(label(tr("ওয়াটারমার্ক যোগ করুন"), 16f, dText, true))
        val tabs = LinearLayout(this)
        for ((k, n) in listOf(tr("লেখা"), tr("লোগো")).withIndex()) {
            val on = wmTab == k
            val t = label(n, 14f, if (on) Ui.PRIMARY else dMuted, true)
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(12), 0, dp(10))
            t.setOnClickListener { wmTab = k; d.dismiss(); watermarkDialog() }
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            c.addView(t)
            val ln = View(this)
            ln.setBackgroundColor(if (on) Ui.PRIMARY else dLine)
            c.addView(ln, lp(height = if (on) dp(2) else 1))
            tabs.addView(c, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        box.addView(tabs, lp(t = 6, b = 14))
        var input: android.widget.EditText? = null
        if (wmTab == 0) {
            box.addView(label(tr("আপনার লেখা"), 12f, dMuted))
            val e = android.widget.EditText(this)
            e.setText(wmText)
            e.setTextColor(dText)
            e.setHintTextColor(dMuted)
            e.hint = tr("যেমন: গোপনীয়")
            e.textSize = 15f
            e.setSingleLine(true)
            e.background = bg(dBg, 12, dLine, 1)
            e.setPadding(dp(14), dp(12), dp(14), dp(12))
            box.addView(e, lp(t = 6))
            input = e
        } else {
            val pick = label(if (wmLogo == null) tr("ছবি বাছুন") else Img.name(this, wmLogo!!), 14f, Ui.PRIMARY, true)
            pick.gravity = Gravity.CENTER
            pick.background = bg(dBg, 12, dLine, 1)
            pick.setPadding(dp(14), dp(14), dp(14), dp(14))
            pick.setOnClickListener {
                d.dismiss()
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                i.addCategory(Intent.CATEGORY_OPENABLE)
                i.type = "image/*"
                startActivityForResult(i, 74)
            }
            box.addView(pick, lp())
            box.addView(label(tr("লোগো বা ছবি পেজের মাঝখানে হালকা করে বসবে।"), 11f, dMuted), lp(t = 6))
        }
        // size: from the tiniest mark up to the whole page width
        val sizeNow = { if (wmTab == 0) wmSizeText else wmSizeLogo }
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(label(tr("আকার"), 12f, dMuted), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val pct = label(bn(Math.round(sizeNow() * 100).toString()) + "%", 12f, dText, true)
        head.addView(pct)
        box.addView(head, lp(t = 14))
        val sb = android.widget.SeekBar(this)
        sb.max = 1000
        sb.progress = Math.round((sizeNow() - 0.01f) / 0.99f * 1000f).coerceIn(0, 1000)
        sb.progressTintList = android.content.res.ColorStateList.valueOf(Ui.PRIMARY)
        sb.thumbTintList = android.content.res.ColorStateList.valueOf(Ui.PRIMARY)
        sb.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: android.widget.SeekBar?, v: Int, fromUser: Boolean) {
                val f = 0.01f + v / 1000f * 0.99f
                if (wmTab == 0) wmSizeText = f else wmSizeLogo = f
                pct.text = bn(Math.max(1, Math.round(f * 100)).toString()) + "%"
            }
            override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
        })
        box.addView(sb, lp(t = 2))
        val row = LinearLayout(this)
        row.addView(outlineButton(tr("বাতিল")) { d.dismiss() }, LinearLayout.LayoutParams(0, dp(48), 1f).also { it.setMargins(0, 0, dp(8), 0) })
        row.addView(bigButton(tr("চালিয়ে যান")) {
            if (wmTab == 0) wmText = input?.text?.toString()?.trim() ?: ""
            if (wmTab == 0 && wmText.isEmpty()) { Toast.makeText(this, tr("ওয়াটারমার্কের লেখা দিন"), Toast.LENGTH_SHORT).show(); return@bigButton }
            if (wmTab == 1 && wmLogo == null) { Toast.makeText(this, tr("আগে একটি ছবি বাছুন"), Toast.LENGTH_SHORT).show(); return@bigButton }
            d.dismiss()
            applyWatermark()
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        box.addView(row, lp(t = 16))
        d.setContentView(box)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        d.window?.setGravity(Gravity.BOTTOM)
        d.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        d.show()
    }

    /** The watermark as a transparent picture, so any language (also Bangla) can be used. */
    private fun textBitmap(text: String): Bitmap {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.textSize = 140f
        p.color = Color.parseColor("#555555")
        p.isFakeBoldText = true
        val w = Math.ceil(p.measureText(text).toDouble()).toInt() + 40
        val fm = p.fontMetrics
        val h = Math.ceil((fm.descent - fm.ascent).toDouble()).toInt() + 40
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(b).drawText(text, 20f, 20f - fm.ascent, p)
        return b
    }

    private fun applyWatermark() {
        val tab = wmTab
        val text = wmText
        val logo = wmLogo
        val sizeF = if (tab == 0) wmSizeText else wmSizeLogo
        apply { doc ->
            val bmp = (if (tab == 0) textBitmap(text) else Img.decode(this, logo!!, 900)) ?: throw IllegalStateException()
            val img = LosslessFactory.createFromImage(doc, bmp)
            val gs = PDExtendedGraphicsState()
            gs.nonStrokingAlphaConstant = if (tab == 0) 0.22f else 0.3f
            gs.strokingAlphaConstant = gs.nonStrokingAlphaConstant
            val aspect = bmp.height.toFloat() / bmp.width
            for (k in 0 until doc.numberOfPages) {
                val page = doc.getPage(k)
                val box = page.cropBox
                val m = Math.min(box.width, box.height)
                val tw = m * sizeF
                val th = tw * aspect
                val cx = box.lowerLeftX + box.width / 2f
                val cy = box.lowerLeftY + box.height / 2f
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.setGraphicsStateParameters(gs)
                    if (tab == 0) cs.transform(Matrix.getRotateInstance(Math.toRadians(45.0), cx, cy))
                    cs.drawImage(img, cx - tw / 2f, cy - th / 2f, tw, th)
                }
            }
            bmp.recycle()
        }
    }

    // ------------------------------------------------------------------ digital signature

    private fun savedSigFile() = File(filesDir, "signature.png")

    private fun signDialog() {
        val d = android.app.Dialog(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val g = GradientDrawable()
        g.setColor(dCard)
        g.cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f)
        box.background = g
        box.setPadding(dp(18), dp(16), dp(18), dp(18))
        box.addView(label(tr("ডিজিটাল সই"), 16f, dText, true))
        box.addView(label(tr("নিচের সাদা জায়গায় আঙুল দিয়ে সই আঁকুন"), 12f, dMuted), lp(t = 2, b = 10))
        val pad = SignPad(this)
        box.addView(pad, lp(height = dp(200)))
        val clr = label(tr("মুছে আবার আঁকুন"), 13f, Ui.PRIMARY, true)
        clr.setPadding(0, dp(10), 0, dp(4))
        clr.setOnClickListener { pad.clear() }
        box.addView(clr)
        val saved = savedSigFile()
        if (saved.exists()) {
            val us = label(tr("আগের সংরক্ষিত সই ব্যবহার করুন"), 13f, Ui.PRIMARY, true)
            us.setPadding(0, dp(6), 0, dp(4))
            us.setOnClickListener {
                val b = android.graphics.BitmapFactory.decodeFile(saved.absolutePath)
                if (b != null) { d.dismiss(); startPlacing(b) }
            }
            box.addView(us)
        }
        val row = LinearLayout(this)
        row.addView(outlineButton(tr("বাতিল")) { d.dismiss() }, LinearLayout.LayoutParams(0, dp(48), 1f).also { it.setMargins(0, 0, dp(8), 0) })
        row.addView(bigButton(tr("চালিয়ে যান")) {
            val b = pad.result()
            if (b == null) { Toast.makeText(this, tr("আগে সই আঁকুন"), Toast.LENGTH_SHORT).show(); return@bigButton }
            try { saved.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } } catch (e: Throwable) { }
            d.dismiss()
            startPlacing(b)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        box.addView(row, lp(t = 12))
        d.setContentView(box)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        d.window?.setGravity(Gravity.BOTTOM)
        d.show()
    }

    private fun startPlacing(b: Bitmap) {
        if (curBmp == null) return
        sigBmp = b
        placing = true
        render()
    }

    private fun applySignature() {
        val pv = placeView ?: return
        val sig = sigBmp ?: return
        val fr = pv.fractions() // centre x, centre y, width, all as fractions of the page
        val i = pageIdx
        placing = false
        render()
        apply { doc ->
            val page = doc.getPage(i)
            val cb = page.cropBox
            val x0 = cb.lowerLeftX
            val y0 = cb.lowerLeftY
            val w = cb.width
            val h = cb.height
            val rot = ((page.rotation % 360) + 360) % 360
            val fx = fr[0]
            val fy = fr[1]
            val cx: Float
            val cy: Float
            when (rot) {
                90 -> { cx = x0 + fy * w; cy = y0 + fx * h }
                180 -> { cx = x0 + (1 - fx) * w; cy = y0 + fy * h }
                270 -> { cx = x0 + (1 - fy) * w; cy = y0 + (1 - fx) * h }
                else -> { cx = x0 + fx * w; cy = y0 + (1 - fy) * h }
            }
            val dispW = if (rot == 90 || rot == 270) h else w
            val sw = fr[2] * dispW
            val sh = sw * sig.height / sig.width
            val img = LosslessFactory.createFromImage(doc, sig)
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.transform(Matrix.getRotateInstance(Math.toRadians(rot.toDouble()), cx, cy))
                cs.drawImage(img, cx - sw / 2f, cy - sh / 2f, sw, sh)
            }
        }
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
    private fun filterPages(doc: PDDocument, srcFile: File, list: List<Int>, mode: Int, dpi: Int = 150, q: Float = 0.85f) {
        val pfd = ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val rd = PdfRenderer(pfd)
        try {
            for (idx in list) {
                val pg = rd.openPage(idx)
                val ptsW = pg.width
                val ptsH = pg.height
                val sc = Math.min(dpi / 72f, 2000f / Math.max(ptsW, ptsH))
                val bw = Math.max(1, Math.round(ptsW * sc))
                val bh = Math.max(1, Math.round(ptsH * sc))
                val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pg.close()
                val out = if (mode < 0) bmp else filtered(bmp, mode)
                val np = PDPage(PDRectangle(ptsW.toFloat(), ptsH.toFloat()))
                val old = doc.getPage(idx)
                doc.pages.insertBefore(np, old)
                doc.removePage(idx + 1)
                val img = JPEGFactory.createFromImage(doc, out, q)
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


    // ------------------------------------------------------------------ file menu

    /** Copies the current file out, adding the password first when the lock is on. */
    private fun writeCurTo(f: File, os: java.io.OutputStream) {
        val pw = lockPass
        if (pw == null) {
            f.inputStream().use { it.copyTo(os) }
        } else {
            PDDocument.load(f).use { doc ->
                val policy = StandardProtectionPolicy(pw, pw, AccessPermission())
                policy.encryptionKeyLength = if (lockAes256) 256 else 128
                policy.isPreferAES = true
                doc.protect(policy)
                doc.save(os)
            }
        }
    }

    private fun darkField(hint: String, value: String, password: Boolean = false, number: Boolean = false): android.widget.EditText {
        val e = android.widget.EditText(this)
        e.setText(value)
        e.hint = hint
        e.setTextColor(dText)
        e.setHintTextColor(dMuted)
        e.textSize = 15f
        e.setSingleLine(true)
        e.inputType = when {
            password -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            number -> android.text.InputType.TYPE_CLASS_NUMBER
            else -> android.text.InputType.TYPE_CLASS_TEXT
        }
        e.background = bg(dBg, 12, dLine, 1)
        e.setPadding(dp(14), dp(12), dp(14), dp(12))
        return e
    }

    private fun darkOption(title: String, on: Boolean, click: () -> Unit): View {
        val r = LinearLayout(this)
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = bg(if (on) Ui.mix(Ui.PRIMARY, dCard, 0.8f) else dBg, 12, if (on) Ui.PRIMARY else dLine, if (on) 2 else 1)
        r.setPadding(dp(14), dp(12), dp(14), dp(12))
        r.addView(label(title, 14f, dText, on), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (on) r.addView(label("✓", 14f, Ui.PRIMARY, true))
        r.setOnClickListener { click() }
        return r
    }

    /** A dark bottom form: a title, content built by [content], and Cancel / OK buttons. */
    private fun formSheet(title: String, ok: String, content: (LinearLayout, () -> Unit) -> Unit, onOk: () -> Boolean) {
        val d = android.app.Dialog(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val g = GradientDrawable()
        g.setColor(dCard)
        g.cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f)
        box.background = g
        box.setPadding(dp(18), dp(16), dp(18), dp(18))
        box.addView(label(title, 16f, dText, true))
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(this)
        sv.addView(body)
        box.addView(sv, lp(t = 12))
        lateinit var rebuild: () -> Unit
        rebuild = { body.removeAllViews(); content(body, rebuild) }
        rebuild()
        val row = LinearLayout(this)
        row.addView(outlineButton(tr("বাতিল")) { d.dismiss() }, LinearLayout.LayoutParams(0, dp(48), 1f).also { it.setMargins(0, 0, dp(8), 0) })
        row.addView(bigButton(ok) { if (onOk()) d.dismiss() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        box.addView(row, lp(t = 16))
        d.setContentView(box)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        d.window?.setGravity(Gravity.BOTTOM)
        d.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        d.show()
    }

    private fun renameDialog() {
        var f: android.widget.EditText? = null
        formSheet(tr("নাম বদলান"), tr("নাম বদলান"), { body, _ ->
            body.addView(label(tr("নতুন নাম"), 12f, dMuted))
            val e = darkField(tr("ফাইলের নাম"), baseName)
            f = e
            body.addView(e, lp(t = 6))
        }) {
            val n = f?.text?.toString()?.trim()?.replace(Regex("[\\\\/:*?\"<>|]"), "_")?.removeSuffix(".pdf") ?: ""
            if (n.isEmpty()) { Toast.makeText(this, tr("নাম লিখুন"), Toast.LENGTH_SHORT).show(); false }
            else { baseName = n; render(); true }
        }
    }

    private fun protectDialog() {
        var p1: android.widget.EditText? = null
        var p2: android.widget.EditText? = null
        var strong = lockAes256
        formSheet(tr("PDF লক করুন"), tr("ঠিক আছে"), { body, rebuild ->
            body.addView(label(tr("সংরক্ষণ বা শেয়ারের সময় PDF-এ এই পাসওয়ার্ড বসবে। ফাঁকা রাখলে লক বন্ধ থাকবে।"), 12f, dMuted))
            val a = darkField(tr("পাসওয়ার্ড"), p1?.text?.toString() ?: lockPass ?: "", password = true)
            val b = darkField(tr("আবার লিখুন"), p2?.text?.toString() ?: lockPass ?: "", password = true)
            p1 = a
            p2 = b
            body.addView(a, lp(t = 10))
            body.addView(b, lp(t = 8))
            body.addView(label(tr("নিরাপত্তার মাত্রা"), 12f, dMuted), lp(t = 12, b = 6))
            body.addView(darkOption("AES-256 (" + tr("প্রস্তাবিত") + ")", strong) { strong = true; rebuild() }, lp(b = 6))
            body.addView(darkOption("AES-128", !strong) { strong = false; rebuild() }, lp())
        }) {
            val a = p1?.text?.toString() ?: ""
            val b = p2?.text?.toString() ?: ""
            if (a.isEmpty() && b.isEmpty()) { lockPass = null; Toast.makeText(this, tr("লক বন্ধ আছে"), Toast.LENGTH_SHORT).show(); true }
            else if (a != b) { Toast.makeText(this, tr("পাসওয়ার্ড মেলেনি"), Toast.LENGTH_SHORT).show(); false }
            else { lockPass = a; lockAes256 = strong; Toast.makeText(this, tr("সংরক্ষণের সময় PDF লক হবে"), Toast.LENGTH_SHORT).show(); true }
        }
    }

    private fun compressDialog() {
        var level = 1
        formSheet(tr("PDF ছোট করুন"), tr("ছোট করুন"), { body, rebuild ->
            body.addView(label(tr("পেজগুলো ছবি হয়ে যায়, তাই লেখা সিলেক্ট বা কপি করা যাবে না।"), 12f, dMuted))
            val names = listOf(tr("কম (সেরা মান)"), tr("মাঝারি (প্রস্তাবিত)"), tr("বেশি (সবচেয়ে ছোট)"))
            for (k in 0..2) body.addView(darkOption(names[k], level == k) { level = k; rebuild() }, lp(t = if (k == 0) 10 else 6))
        }) {
            val dpi = intArrayOf(150, 110, 72)[level]
            val q = floatArrayOf(0.8f, 0.65f, 0.45f)[level]
            val before = cur().length()
            val src = cur()
            apply({
                val after = cur().length()
                Toast.makeText(this, tr("আগে: ") + Img.human(before) + tr("  →  এখন: ") + Img.human(after), Toast.LENGTH_LONG).show()
            }) { doc -> filterPages(doc, src, (0 until doc.numberOfPages).toList(), -1, dpi, q) }
            true
        }
    }

    private fun splitDialog() {
        var mode = 0
        var spec: android.widget.EditText? = null
        var every: android.widget.EditText? = null
        formSheet(tr("PDF ভাগ করুন"), tr("ভাগ করুন"), { body, rebuild ->
            body.addView(darkOption(tr("বাছা পেজ দিয়ে নতুন PDF"), mode == 0) { mode = 0; rebuild() }, lp(b = 6))
            body.addView(darkOption(tr("প্রতি কয়েক পেজে আলাদা ফাইল"), mode == 1) { mode = 1; rebuild() }, lp())
            if (mode == 0) {
                val e = darkField(tr("যেমন: 1-3, 5, 8-"), spec?.text?.toString() ?: "")
                spec = e
                body.addView(e, lp(t = 10))
                body.addView(label(tr("মোট ") + bn(pageCount.toString()) + tr(" পেজ। এডিটর আগের মতোই থাকবে, ভাগ করা ফাইল আলাদা সংরক্ষিত হবে।"), 11f, dMuted), lp(t = 6))
            } else {
                val e = darkField("1", every?.text?.toString() ?: "1", number = true)
                every = e
                body.addView(e, lp(t = 10))
                body.addView(label(tr("ফোল্ডার বাছতে হবে, ফাইলের নাম হবে নাম_part1.pdf, নাম_part2.pdf…"), 11f, dMuted), lp(t = 6))
            }
        }) {
            if (mode == 0) {
                val l = parsePageSpec(spec?.text?.toString() ?: "", pageCount)
                if (l == null) { Toast.makeText(this, tr("পেজ নম্বর ঠিক নেই (মোট ") + bn(pageCount.toString()) + tr(" পেজ)"), Toast.LENGTH_LONG).show(); false }
                else {
                    pendingSplit = l
                    val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    i.addCategory(Intent.CATEGORY_OPENABLE)
                    i.type = "application/pdf"
                    i.putExtra(Intent.EXTRA_TITLE, baseName + "_pages.pdf")
                    saveOrAsk(i, 75)
                    true
                }
            } else {
                val n = asciiDigits(every?.text?.toString() ?: "").toIntOrNull()
                if (n == null || n < 1) { Toast.makeText(this, tr("পেজ সংখ্যা ঠিক নেই"), Toast.LENGTH_SHORT).show(); false }
                else { pendingEvery = n; treeOrAsk(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 76); true }
            }
        }
    }

    private fun doSplitPages(out: Uri) {
        val list = pendingSplit ?: return
        val f = cur()
        busy = true
        Thread {
            var ok = true
            try {
                PDDocument.load(f).use { doc ->
                    val nd = PDDocument()
                    for (i in list) nd.importPage(doc.getPage(i))
                    contentResolver.openOutputStream(out)?.use { nd.save(it) }
                    nd.close()
                }
            } catch (e: Throwable) { ok = false }
            runOnUiThread {
                busy = false
                if (ok) Usage.done(this, "PdfEditorActivity", Img.size(this, out), Img.name(this, out))
                Toast.makeText(this, if (ok) tr("সংরক্ষিত হয়েছে: ") + bn(list.size.toString()) + tr(" পেজের PDF") else tr("কাজটি করা যায়নি"), Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun doSplitEvery(tree: Uri) {
        val f = cur()
        val n = pendingEvery
        val base = baseName
        busy = true
        Thread {
            var made = 0
            try {
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                PDDocument.load(f).use { doc ->
                    val sp = Splitter()
                    sp.setSplitAtPage(n)
                    for ((i, part) in sp.split(doc).withIndex()) {
                        val u = DocumentsContract.createDocument(contentResolver, parent, "application/pdf", base + "_part" + (i + 1) + ".pdf")
                        if (u != null) {
                            contentResolver.openOutputStream(u)?.use { part.save(it) }
                            made++
                        }
                        part.close()
                    }
                }
            } catch (e: Throwable) { }
            runOnUiThread {
                busy = false
                if (made > 0) Usage.done(this, "PdfEditorActivity", 0, "")
                Toast.makeText(this, if (made > 0) tr("সংরক্ষিত হয়েছে: ") + bn(made.toString()) + tr("টি ফাইল") else tr("কাজটি করা যায়নি"), Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun pickMore() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        i.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/*"))
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(i, 77)
    }

    /** Adds the pages of the chosen PDFs, or the chosen pictures as new pages, after the current pages. */
    private fun mergeIn(list: List<Uri>) {
        apply { doc ->
            val util = PDFMergerUtility()
            for ((k, u) in list.withIndex()) {
                val mime = contentResolver.getType(u) ?: ""
                val tmp = File(cacheDir, "merge_in_$k.pdf")
                if (mime.startsWith("image/")) {
                    val b = Img.decode(this, u, 2200) ?: continue
                    val pd = PdfDocument()
                    val s = 842f / Math.max(b.width, b.height)
                    val pw = Math.max(1, (b.width * s).toInt())
                    val ph = Math.max(1, (b.height * s).toInt())
                    val page = pd.startPage(PdfDocument.PageInfo.Builder(pw, ph, 1).create())
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawBitmap(b, null, RectF(0f, 0f, pw.toFloat(), ph.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                    pd.finishPage(page)
                    tmp.outputStream().use { pd.writeTo(it) }
                    pd.close()
                    b.recycle()
                } else {
                    contentResolver.openInputStream(u)?.use { ins -> tmp.outputStream().use { ins.copyTo(it) } }
                }
                PDDocument.load(tmp).use { other -> util.appendDocument(doc, other) }
            }
        }
    }

    private fun printDoc() {
        val f = cur()
        val n = pageCount
        val name = baseName
        val pm = getSystemService(PRINT_SERVICE) as PrintManager
        pm.print(name, object : PrintDocumentAdapter() {
            override fun onLayout(old: PrintAttributes?, attrs: PrintAttributes?, cancel: android.os.CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
                if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
                cb.onLayoutFinished(PrintDocumentInfo.Builder("$name.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(n).build(), true)
            }
            override fun onWrite(pages: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: android.os.CancellationSignal?, cb: WriteResultCallback) {
                try {
                    java.io.FileOutputStream(dest.fileDescriptor).use { o -> f.inputStream().use { it.copyTo(o) } }
                    cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Throwable) { cb.onWriteFailed(e.message) }
            }
        }, null)
    }

    private fun shareSheet() {
        val rows = ArrayList<Triple<String, String, () -> Unit>>()
        rows.add(Triple("PDF", tr("PDF শেয়ার করুন"), { shareFile(0) }))
        rows.add(Triple("JPG", tr("এই পেজ JPG হিসেবে"), { shareFile(1) }))
        rows.add(Triple("PNG", tr("এই পেজ PNG হিসেবে"), { shareFile(2) }))
        sheet(tr("শেয়ার"), rows)
    }

    private fun renderBitmap(f: File, idx: Int, dpi: Int): Bitmap? {
        return try {
            val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
            val rd = PdfRenderer(pfd)
            try {
                val pg = rd.openPage(idx)
                val sc = Math.min(dpi / 72f, 3000f / Math.max(pg.width, pg.height))
                val b = Bitmap.createBitmap(Math.max(1, Math.round(pg.width * sc)), Math.max(1, Math.round(pg.height * sc)), Bitmap.Config.ARGB_8888)
                b.eraseColor(Color.WHITE)
                pg.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pg.close()
                b
            } finally {
                rd.close()
                pfd.close()
            }
        } catch (e: Throwable) { null }
    }

    private fun shareFile(kind: Int) {
        if (busy) return
        busy = true
        val f = cur()
        val idx = pageIdx
        val base = baseName.replace(Regex("[^A-Za-z0-9_-]"), "_").ifEmpty { "document" }
        Thread {
            var name = ""
            var mime = "application/pdf"
            try {
                val dir = File(cacheDir, "share")
                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }
                when (kind) {
                    0 -> { name = "$base.pdf"; File(dir, name).outputStream().use { writeCurTo(f, it) } }
                    else -> {
                        val b = renderBitmap(f, idx, 200) ?: throw IllegalStateException()
                        val png = kind == 2
                        name = base + "_page" + (idx + 1) + (if (png) ".png" else ".jpg")
                        mime = if (png) "image/png" else "image/jpeg"
                        File(dir, name).writeBytes(Img.encode(b, if (png) 0 else 1, 92))
                        b.recycle()
                    }
                }
            } catch (e: Throwable) { name = "" }
            runOnUiThread {
                busy = false
                if (name.isEmpty()) { Toast.makeText(this, tr("কাজটি করা যায়নি"), Toast.LENGTH_SHORT).show(); return@runOnUiThread }
                val u = Uri.parse("content://com.example.callrec.share/" + Uri.encode(name))
                val i = Intent(Intent.ACTION_SEND)
                i.type = mime
                i.putExtra(Intent.EXTRA_STREAM, u)
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(i, tr("শেয়ার")))
            }
        }.start()
    }

    private fun exportDialog() {
        formSheet(tr("ছবি হিসেবে এক্সপোর্ট"), tr("ফোল্ডার বাছুন"), { body, rebuild ->
            body.addView(label(tr("প্রতিটি পেজ আলাদা ছবি হয়ে বাছা ফোল্ডারে যাবে।"), 12f, dMuted))
            body.addView(darkOption("JPG", exportFmt == 1) { exportFmt = 1; rebuild() }, lp(t = 10, b = 6))
            body.addView(darkOption("PNG", exportFmt == 0) { exportFmt = 0; rebuild() }, lp())
        }) {
            treeOrAsk(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 78)
            true
        }
    }

    private fun doExport(tree: Uri) {
        val f = cur()
        val n = pageCount
        val fmt = exportFmt
        val base = baseName
        busy = true
        Thread {
            var made = 0
            try {
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                for (i in 0 until n) {
                    val b = renderBitmap(f, i, 200) ?: continue
                    val bytes = Img.encode(b, fmt, 92)
                    b.recycle()
                    val u = DocumentsContract.createDocument(contentResolver, parent, if (fmt == 0) "image/png" else "image/jpeg", base + "_page" + (i + 1) + (if (fmt == 0) ".png" else ".jpg"))
                    if (u != null) {
                        contentResolver.openOutputStream(u)?.use { it.write(bytes) }
                        made++
                    }
                }
            } catch (e: Throwable) { }
            runOnUiThread {
                busy = false
                if (made > 0) Usage.done(this, "PdfEditorActivity", 0, "")
                Toast.makeText(this, if (made > 0) tr("সংরক্ষিত হয়েছে: ") + bn(made.toString()) + tr("টি ছবি") else tr("কাজটি করা যায়নি"), Toast.LENGTH_LONG).show()
            }
        }.start()
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
                contentResolver.openOutputStream(out)?.use { o -> writeCurTo(f, o) }
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
        if (requestCode == 74) {
            if (resultCode == Activity.RESULT_OK) data?.data?.let { wmLogo = it }
            wmTab = 1
            if (stage == 1) watermarkDialog()
            return
        }
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
            75 -> data.data?.let { doSplitPages(it) }
            76 -> data.data?.let { doSplitEvery(it) }
            77 -> {
                val cd = data.clipData
                val l = ArrayList<Uri>()
                if (cd != null) for (k in 0 until cd.itemCount) l.add(cd.getItemAt(k).uri) else data.data?.let { l.add(it) }
                if (l.isNotEmpty()) mergeIn(l)
            }
            78 -> data.data?.let { doExport(it) }
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

/** A white box to draw a signature in; [result] gives the ink, cropped, on a clear background. */
private class SignPad(ctx: Context) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val path = Path()
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val back = Paint()
    private var minX = 1e9f
    private var minY = 1e9f
    private var maxX = -1e9f
    private var maxY = -1e9f
    private var any = false

    init {
        ink.style = Paint.Style.STROKE
        ink.strokeWidth = 3.5f * d
        ink.strokeCap = Paint.Cap.ROUND
        ink.strokeJoin = Paint.Join.ROUND
        ink.color = Color.parseColor("#14151C")
        back.color = Color.WHITE
    }

    fun clear() {
        path.reset()
        any = false
        minX = 1e9f; minY = 1e9f; maxX = -1e9f; maxY = -1e9f
        invalidate()
    }

    private fun track(x: Float, y: Float) {
        minX = Math.min(minX, x); minY = Math.min(minY, y)
        maxX = Math.max(maxX, x); maxY = Math.max(maxY, y)
    }

    override fun onDraw(c: Canvas) {
        c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 14f * d, 14f * d, back)
        c.drawPath(path, ink)
    }

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { path.moveTo(e.x, e.y); track(e.x, e.y); any = true; parent?.requestDisallowInterceptTouchEvent(true) }
            android.view.MotionEvent.ACTION_MOVE -> { path.lineTo(e.x, e.y); track(e.x, e.y) }
            android.view.MotionEvent.ACTION_UP -> { path.lineTo(e.x + 0.1f, e.y); track(e.x, e.y) }
        }
        invalidate()
        return true
    }

    fun result(): Bitmap? {
        if (!any || maxX - minX < 8f * d && maxY - minY < 8f * d) return null
        val pad = 6f * d
        val l = Math.max(0f, minX - pad)
        val t = Math.max(0f, minY - pad)
        val r = Math.min(width.toFloat(), maxX + pad)
        val b = Math.min(height.toFloat(), maxY + pad)
        val w = Math.max(1, (r - l).toInt())
        val h = Math.max(1, (b - t).toInt())
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.translate(-l, -t)
        cv.drawPath(path, ink)
        return bmp
    }
}

/** Shows the page with the signature on top; drag to move it, pinch to resize. */
private class PlaceView(ctx: Context) : View(ctx) {
    var page: Bitmap? = null
    var sig: Bitmap? = null
    private val img = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var cx = 0.5f   // centre, fractions of the page
    private var cy = 0.85f
    private var wf = 0.3f   // width as a fraction of the page width
    private var lastX = 0f
    private var lastY = 0f
    private var moving = false
    private val scaler = android.view.ScaleGestureDetector(ctx, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: android.view.ScaleGestureDetector): Boolean {
            wf = (wf * d.scaleFactor).coerceIn(0.08f, 0.9f)
            invalidate()
            return true
        }
    })

    fun fractions(): FloatArray = floatArrayOf(cx, cy, wf)

    private fun fit() {
        val bm = page ?: return
        val s = Math.min(width.toFloat() / bm.width, height.toFloat() / bm.height)
        val w = bm.width * s
        val h = bm.height * s
        img.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
    }

    private fun sigRect(): RectF {
        val sg = sig ?: return RectF()
        val w = wf * img.width()
        val h = w * sg.height / sg.width
        val x = img.left + cx * img.width()
        val y = img.top + cy * img.height()
        return RectF(x - w / 2f, y - h / 2f, x + w / 2f, y + h / 2f)
    }

    override fun onDraw(c: Canvas) {
        val bm = page ?: return
        val sg = sig ?: return
        fit()
        c.drawBitmap(bm, null, img, paint)
        val r = sigRect()
        c.drawBitmap(sg, null, r, paint)
        val d = resources.displayMetrics.density
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * d
        paint.color = Ui.PRIMARY
        c.drawRect(r, paint)
        paint.style = Paint.Style.FILL
    }

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        scaler.onTouchEvent(e)
        if (scaler.isInProgress) return true
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                val pad = 24f * resources.displayMetrics.density
                val r = sigRect()
                r.inset(-pad, -pad)
                moving = r.contains(e.x, e.y)
                lastX = e.x
                lastY = e.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (moving && img.width() > 0f) {
                    cx = (cx + (e.x - lastX) / img.width()).coerceIn(0f, 1f)
                    cy = (cy + (e.y - lastY) / img.height()).coerceIn(0f, 1f)
                    invalidate()
                }
                lastX = e.x
                lastY = e.y
            }
            else -> moving = false
        }
        return true
    }
}
