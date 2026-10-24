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
    private var wmPreview = false
    private var annotating = false
    private var annotMode = 0
    private var annotColor = Color.parseColor("#E53935")
    private val annotItems = ArrayList<AnnotItem>()
    private var annotView: AnnotView? = null
    private var ocrLang = 0
    private var ocrAll = true
    private var ocrText = ""
    private var enh = EnhParams()
    private var enhAll = false
    private var enhIv: ImageView? = null
    private var enhToken = 0
    private var subTitle = ""
    private var subSub = ""
    private var subBtn = ""
    private var subDark = false
    private var subBuild: ((LinearLayout) -> Unit)? = null
    private var subGo: (() -> Unit)? = null
    private var subCancel: (() -> Unit)? = null
    private val mergeAdd = ArrayList<Uri>()
    private var mergeName = ""

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
            3 -> { stage = 1; render() }
            4 -> { stage = 0; render() }
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
        val dark = stage == 1 || (stage == 3 && subDark)
        root.setBackgroundColor(if (dark) dBg else Ui.BG)
        window.statusBarColor = if (dark) dBg else Ui.BG
        window.navigationBarColor = if (dark) dBg else Ui.NAV
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (dark || Ui.dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        when (stage) {
            0 -> renderUpload()
            1 -> renderEditor()
            3 -> renderSub()
            4 -> renderRecent()
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
            col.addView(outlineButton(tr("ক্যামেরায় স্ক্যান করুন")) { scanStart(false) }, lp(height = dp(52), t = 10))
            col.addView(outlineButton(tr("সাম্প্রতিক ফাইল")) { stage = 4; render() }, lp(height = dp(52), t = 10))
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private var scanAppend = false

    /** Google's on-device scanner: edge detection, crop, perspective, clean-up, filters, many pages. */
    private fun scanStart(append: Boolean) {
        if (busy) return
        scanAppend = append
        try {
            val o = com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(30)
                .setResultFormats(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            com.google.mlkit.vision.documentscanner.GmsDocumentScanning.getClient(o).getStartScanIntent(this)
                .addOnSuccessListener { sender ->
                    try { startIntentSenderForResult(sender, 92, null, 0, 0, 0) } catch (e: Throwable) { scanFallback() }
                }
                .addOnFailureListener { scanFallback() }
        } catch (e: Throwable) { scanFallback() }
    }

    private fun scanFallback() {
        Toast.makeText(this, tr("স্ক্যানার পাওয়া যায়নি, সাধারণ ক্যামেরা খুলছে"), Toast.LENGTH_LONG).show()
        camStart()
    }

    private fun scanDone(data: Intent?) {
        val r = com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult.fromActivityResultIntent(data)
        val u = r?.pdf?.uri
        if (u == null) { Toast.makeText(this, tr("স্ক্যান করা যায়নি"), Toast.LENGTH_SHORT).show(); return }
        if (scanAppend && stage == 1) mergeIn(listOf(u)) else openPdf(u)
    }

    private var camUri: Uri? = null

    private fun camStart() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 90)
            return
        }
        try {
            val v = android.content.ContentValues()
            v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "scan_" + System.currentTimeMillis() + ".jpg")
            v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            val u = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: throw IllegalStateException()
            camUri = u
            val i = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, u)
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivityForResult(i, 91)
        } catch (e: Throwable) {
            Toast.makeText(this, tr("ক্যামেরা খোলা যায়নি"), Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 90) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) camStart()
            else Toast.makeText(this, tr("ক্যামেরার অনুমতি দিন"), Toast.LENGTH_LONG).show()
        }
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
        mn.setOnClickListener { if (!busy && !cropping && !placing && !wmPreview && !annotating) openMenu() }
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
        if (annotating && curBmp != null) {
            val av = AnnotView(this, annotItems)
            av.bmp = curBmp
            av.mode = annotMode
            av.color = annotColor
            av.askText = { u, v -> askAnnotText(u, v) }
            annotView = av
            area.removeAllViews()
            area.addView(av, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT).also { it.setMargins(dp(8), dp(4), dp(8), dp(4)) })
        }
        root.addView(area, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val pl = label("", 12f, dMuted)
        pl.gravity = Gravity.CENTER
        pageLabel = pl
        root.addView(pl, lp(t = 4, b = 6))

        if (annotating) {
            annotBar()
        } else if (placing) {
            val br = LinearLayout(this)
            br.setPadding(dp(16), dp(10), dp(16), dp(14))
            br.addView(outlineButton(tr("বাতিল")) { placing = false; render() }, LinearLayout.LayoutParams(0, dp(52), 1f).also { it.setMargins(0, 0, dp(8), 0) })
            br.addView(bigButton(tr("সই বসান")) { applySignature() }, LinearLayout.LayoutParams(0, dp(52), 1f))
            root.addView(br, lp())
        } else if (wmPreview) {
            val br = LinearLayout(this)
            br.setPadding(dp(16), dp(10), dp(16), dp(14))
            br.addView(outlineButton(tr("বাতিল")) { wmPreview = false; undo(); render() }, LinearLayout.LayoutParams(0, dp(52), 1f).also { it.setMargins(0, 0, dp(8), 0) })
            br.addView(bigButton(tr("রাখুন")) { wmPreview = false; render() }, LinearLayout.LayoutParams(0, dp(52), 1f))
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
            tools.addView(glyphTool("⟲", tr("আবার তুলুন")) { retakePick() }, w())
            tools.addView(glyphTool("⌗", tr("ক্রপ")) { startCrop() }, w())
            tools.addView(glyphTool("↻", tr("ঘোরান")) { rotate() }, w())
            tools.addView(glyphTool("◐", tr("ফিল্টার")) { enhancePage() }, w())
            tools.addView(glyphTool("⤢", tr("রিসাইজ")) { resizePage() }, w())
            tools.addView(glyphTool("✕", tr("মুছুন")) { deletePage() }, w())
            root.addView(tools, lp(l = 12, r = 12))

            val bar = LinearLayout(this)
            bar.orientation = LinearLayout.VERTICAL
            bar.setPadding(dp(16), dp(10), dp(16), dp(14))
            val brow = LinearLayout(this)
            brow.addView(outlineButton(tr("আরও স্ক্যান")) { scanStart(true) }, LinearLayout.LayoutParams(0, dp(52), 1f).also { it.setMargins(0, 0, dp(8), 0) })
            brow.addView(bigButton(tr("PDF সংরক্ষণ")) { save() }, LinearLayout.LayoutParams(0, dp(52), 1f))
            bar.addView(brow, lp())
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
        if (cropping || placing || annotating) return
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
        val d = android.app.Dialog(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val g = GradientDrawable()
        g.setColor(Ui.CARD)
        g.cornerRadii = floatArrayOf(dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), 0f, 0f, 0f, 0f)
        box.background = g
        box.setPadding(dp(16), dp(10), dp(16), dp(14))
        val handle = View(this)
        handle.background = bg(Ui.LINE, 3)
        box.addView(handle, lp(width = dp(40), height = dp(4)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        box.addView(fileCard(), lp(t = 12, b = 4))
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(this)
        sv.addView(list)
        box.addView(sv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        fun row(glyph: String, name: String, chev: Boolean, act: () -> Unit) {
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(6), dp(13), dp(6), dp(13))
            val gl = label(glyph, 17f, Ui.TEXT, true)
            gl.gravity = Gravity.CENTER
            r.addView(gl, lp(width = dp(30), r = 12))
            r.addView(label(name, 15f, Ui.TEXT), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (chev) r.addView(label("›", 20f, Ui.MUTED))
            r.setOnClickListener { d.dismiss(); act() }
            list.addView(r)
        }
        fun line() {
            val v = View(this)
            v.setBackgroundColor(Ui.LINE)
            list.addView(v, lp(height = dp(1), t = 4, b = 4))
        }
        row("⤓", tr("ডিভাইসে সংরক্ষণ"), true) { save() }
        row("⇪", tr("এক্সপোর্ট করুন…"), true) { exportPage() }
        line()
        row("✎", tr("লেখা, হাইলাইট ও আঁকা"), false) { startAnnot() }
        row("🔍", tr("ছবির লেখা চিনুন (OCR)"), false) { ocrPage() }
        row("Ⓦ", tr("ওয়াটারমার্ক যোগ করুন"), false) { watermarkDialog() }
        row("✍", tr("ডিজিটাল সই যোগ করুন"), false) { signDialog() }
        row("✂", tr("PDF ভাগ করুন"), false) { splitDialog() }
        row("⊕", tr("PDF জোড়া দিন"), false) { mergePage() }
        row("🔒", if (lockPass != null) tr("PDF লক (চালু আছে)") else tr("PDF লক করুন"), false) { protectPage() }
        row("⇲", tr("PDF ছোট করুন"), false) { compressPage() }
        line()
        row("✎", tr("নাম বদলান"), false) { renameDialog() }
        row("🗀", tr("ফোল্ডারে সরান"), false) { moveToFolder() }
        row("⇗", tr("শেয়ার"), false) { shareSheet() }
        row("⎙", tr("প্রিন্ট"), false) { printDoc() }
        row("🗑", tr("মুছুন"), false) { deleteDoc() }
        d.setContentView(box)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.86f).toInt())
        d.window?.setGravity(Gravity.BOTTOM)
        d.show()
    }

    /** A small card with the first-page picture, the name, the date and the size. */
    private fun fileCard(): View {
        val c = LinearLayout(this)
        c.gravity = Gravity.CENTER_VERTICAL
        c.background = bg(Ui.BG, 14, Ui.LINE, 1)
        c.setPadding(dp(10), dp(10), dp(10), dp(10))
        val th = thumbView(44, 56)
        curBmp?.let { th.setImageBitmap(it) }
        c.addView(th, lp(width = dp(44), height = dp(56), r = 12))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val n = label(baseName, 14f, Ui.TEXT, true)
        n.maxLines = 1
        n.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        col.addView(n)
        val dt = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.US).format(java.util.Date())
        col.addView(label(bn(dt) + " · " + Img.human(cur().length()), 12f, Ui.MUTED), lp(t = 2))
        c.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return c
    }

    /** A white bottom sheet with a title, content, and two buttons. [danger] paints the title and OK red. */
    private fun confirmSheet(title: String, body: View, ok: String, danger: Boolean, onOk: () -> Unit) {
        val d = android.app.Dialog(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val g = GradientDrawable()
        g.setColor(Ui.CARD)
        g.cornerRadii = floatArrayOf(dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), 0f, 0f, 0f, 0f)
        box.background = g
        box.setPadding(dp(18), dp(10), dp(18), dp(18))
        val handle = View(this)
        handle.background = bg(Ui.LINE, 3)
        box.addView(handle, lp(width = dp(40), height = dp(4)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        val t = label(title, 17f, if (danger) Ui.RED else Ui.TEXT, true)
        t.gravity = Gravity.CENTER
        box.addView(t, lp(t = 12))
        box.addView(body, lp(t = 14))
        val row = LinearLayout(this)
        row.addView(outlineButton(tr("বাতিল")) { d.dismiss() }, LinearLayout.LayoutParams(0, dp(48), 1f).also { it.setMargins(0, 0, dp(8), 0) })
        val okb = bigButton(ok) { d.dismiss(); onOk() }
        if (danger) okb.background = bg(Ui.RED, 14)
        row.addView(okb, LinearLayout.LayoutParams(0, dp(48), 1f))
        box.addView(row, lp(t = 18))
        d.setContentView(box)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        d.window?.setGravity(Gravity.BOTTOM)
        d.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        d.show()
    }

    private fun deleteDoc() {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(fileCard())
        val q = label(tr("আপনি কি এই ফাইলটি মুছে ফেলতে চান?"), 14f, Ui.TEXT)
        col.addView(q, lp(t = 14))
        col.addView(label(tr("এডিটরের কপি বন্ধ হবে। আগে সংরক্ষণ করা ফাইল ফোনে যেমন আছে তেমনই থাকবে।"), 12f, Ui.MUTED), lp(t = 4))
        confirmSheet(tr("মুছুন"), col, tr("হ্যাঁ, মুছুন"), true) {
            history.clear()
            lockPass = null
            stage = 0
            render()
        }
    }

    private fun moveToFolder() {
        if (busy) return
        treeOrAsk(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 79)
    }

    private fun doMove(tree: Uri) {
        val f = cur()
        val base = baseName
        busy = true
        Thread {
            var out: Uri? = null
            try {
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                val u = DocumentsContract.createDocument(contentResolver, parent, "application/pdf", "$base.pdf")
                if (u != null) {
                    contentResolver.openOutputStream(u)?.use { writeCurTo(f, it) }
                    out = u
                }
            } catch (e: Throwable) { }
            runOnUiThread {
                busy = false
                val u = out
                if (u != null) {
                    outUri = u
                    outSize = Img.size(this, u)
                    Usage.done(this, "PdfEditorActivity", outSize, Img.name(this, u))
                    RecentPdf.add(this, u, Img.name(this, u), outSize)
                    stage = 2
                    render()
                } else Toast.makeText(this, tr("সংরক্ষণ করা যায়নি"), Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    // Retake: replace the current page with a new picture
    private fun retakePick() {
        if (busy) return
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        startActivityForResult(i, 80)
    }

    private fun retakeWith(u: Uri) {
        val idx = pageIdx
        apply { doc ->
            val b = Img.decode(this, u, 2200) ?: throw IllegalStateException()
            val pd = PdfDocument()
            val sc = 842f / Math.max(b.width, b.height)
            val pw = Math.max(1, (b.width * sc).toInt())
            val ph = Math.max(1, (b.height * sc).toInt())
            val page = pd.startPage(PdfDocument.PageInfo.Builder(pw, ph, 1).create())
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawBitmap(b, null, RectF(0f, 0f, pw.toFloat(), ph.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
            pd.finishPage(page)
            val tmp = File(cacheDir, "retake.pdf")
            tmp.outputStream().use { pd.writeTo(it) }
            pd.close()
            b.recycle()
            PDDocument.load(tmp).use { other ->
                val old = doc.getPage(idx)
                val imp = doc.importPage(other.getPage(0))
                doc.removePage(doc.numberOfPages - 1)
                doc.pages.insertBefore(imp, old)
                doc.removePage(old)
            }
        }
    }

    // ------------------------------------------------------------------ watermark

    private fun watermarkDialog() {
        val d = android.app.Dialog(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val g = GradientDrawable()
        g.setColor(Ui.CARD)
        g.cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f)
        box.background = g
        box.setPadding(dp(18), dp(16), dp(18), dp(18))
        box.addView(label(tr("ওয়াটারমার্ক যোগ করুন"), 16f, Ui.TEXT, true))
        val tabs = LinearLayout(this)
        for ((k, n) in listOf(tr("লেখা"), tr("লোগো")).withIndex()) {
            val on = wmTab == k
            val t = label(n, 14f, if (on) Ui.PRIMARY else Ui.MUTED, true)
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(12), 0, dp(10))
            t.setOnClickListener { wmTab = k; d.dismiss(); watermarkDialog() }
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            c.addView(t)
            val ln = View(this)
            ln.setBackgroundColor(if (on) Ui.PRIMARY else Ui.LINE)
            c.addView(ln, lp(height = if (on) dp(2) else 1))
            tabs.addView(c, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        box.addView(tabs, lp(t = 6, b = 14))
        var input: android.widget.EditText? = null
        if (wmTab == 0) {
            box.addView(label(tr("আপনার লেখা"), 12f, Ui.MUTED))
            val e = android.widget.EditText(this)
            e.setText(wmText)
            e.setTextColor(Ui.TEXT)
            e.setHintTextColor(Ui.MUTED)
            e.hint = tr("যেমন: গোপনীয়")
            e.textSize = 15f
            e.setSingleLine(true)
            e.background = bg(Ui.BG, 12, Ui.LINE, 1)
            e.setPadding(dp(14), dp(12), dp(14), dp(12))
            box.addView(e, lp(t = 6))
            input = e
        } else {
            val pick = label(if (wmLogo == null) tr("ছবি বাছুন") else Img.name(this, wmLogo!!), 14f, Ui.PRIMARY, true)
            pick.gravity = Gravity.CENTER
            pick.background = bg(Ui.BG, 12, Ui.LINE, 1)
            pick.setPadding(dp(14), dp(14), dp(14), dp(14))
            pick.setOnClickListener {
                d.dismiss()
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                i.addCategory(Intent.CATEGORY_OPENABLE)
                i.type = "image/*"
                startActivityForResult(i, 74)
            }
            box.addView(pick, lp())
            box.addView(label(tr("লোগো বা ছবি পেজের মাঝখানে হালকা করে বসবে।"), 11f, Ui.MUTED), lp(t = 6))
        }
        // size: from the tiniest mark up to the whole page width
        val sizeNow = { if (wmTab == 0) wmSizeText else wmSizeLogo }
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(label(tr("আকার"), 12f, Ui.MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val pct = label(bn(Math.round(sizeNow() * 100).toString()) + "%", 12f, Ui.TEXT, true)
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
        apply({ wmPreview = true; render() }) { doc ->
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
                    if (tab == 0) cs.transform(rotAbout(Math.toRadians(45.0), cx, cy))
                    cs.drawImage(img, cx - tw / 2f, cy - th / 2f, tw, th)
                }
            }
            bmp.recycle()
        }
    }

    // ------------------------------------------------------------------ annotate: pen, highlight, text, shapes

    private fun startAnnot() {
        if (curBmp == null || busy) return
        annotItems.clear()
        annotMode = 0
        annotColor = Color.parseColor("#E53935")
        annotating = true
        render()
    }

    private fun annotBar() {
        val tools = listOf(
            Triple(0, "✎", tr("কলম")), Triple(1, "▮", tr("হাইলাইট")), Triple(2, "T", tr("লেখা")),
            Triple(3, "U", tr("আন্ডারলাইন")), Triple(4, "S", tr("কাটা দাগ")), Triple(5, "▭", tr("বর্গ")),
            Triple(6, "◯", tr("বৃত্ত")), Triple(7, "➜", tr("তীর")), Triple(8, "✓", tr("টিক"))
        )
        val hs = android.widget.HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        val row = LinearLayout(this)
        row.background = bg(dCard, 16)
        for ((m, g, n) in tools) {
            val on = annotMode == m
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            c.gravity = Gravity.CENTER
            c.setPadding(dp(14), dp(8), dp(14), dp(6))
            val gl = label(g, 20f, if (on) Ui.PRIMARY else dText, true)
            gl.gravity = Gravity.CENTER
            c.addView(gl)
            val l = label(n, 11f, if (on) Ui.PRIMARY else dMuted)
            l.gravity = Gravity.CENTER
            c.addView(l)
            c.setOnClickListener {
                annotMode = m
                if (m == 1) annotColor = Color.parseColor("#FFD400")
                if (m == 4) annotColor = Color.parseColor("#E53935")
                render()
            }
            row.addView(c)
        }
        hs.addView(row)
        root.addView(hs, lp(l = 12, r = 12))

        val cr = LinearLayout(this)
        cr.gravity = Gravity.CENTER_VERTICAL
        cr.setPadding(dp(16), dp(8), dp(16), dp(0))
        for (hex in listOf("#111111", "#E53935", "#1E63F5", "#2E9E5B", "#FFD400")) {
            val col = Color.parseColor(hex)
            val dot = View(this)
            dot.background = bg(col, 14, if (col == annotColor) Ui.PRIMARY else dLine, if (col == annotColor) 3 else 1)
            dot.setOnClickListener { annotColor = col; render() }
            cr.addView(dot, lp(width = dp(28), height = dp(28), r = 12))
        }
        cr.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        val un = label(tr("↶ শেষটা মুছুন"), 13f, Ui.PRIMARY, true)
        un.setOnClickListener { if (annotItems.isNotEmpty()) { annotItems.removeAt(annotItems.size - 1); annotView?.invalidate() } }
        cr.addView(un)
        root.addView(cr, lp())

        val br = LinearLayout(this)
        br.setPadding(dp(16), dp(10), dp(16), dp(14))
        br.addView(outlineButton(tr("বাতিল")) { annotating = false; annotItems.clear(); render() }, LinearLayout.LayoutParams(0, dp(52), 1f).also { it.setMargins(0, 0, dp(8), 0) })
        br.addView(bigButton(tr("প্রয়োগ করুন")) { applyAnnot() }, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(br, lp())
    }

    private fun askAnnotText(u: Float, v: Float) {
        val e = android.widget.EditText(this)
        e.hint = tr("এখানে লিখুন")
        android.app.AlertDialog.Builder(this).setView(e)
            .setPositiveButton(tr("ঠিক আছে")) { _, _ ->
                val t = e.text.toString().trim()
                if (t.isNotEmpty()) {
                    val a = AnnotItem(2, annotColor)
                    a.pts.add(android.graphics.PointF(u, v))
                    a.text = t
                    a.bmp = annotTextBitmap(t, annotColor)
                    annotItems.add(a)
                    annotView?.invalidate()
                }
            }.setNegativeButton(tr("বাতিল"), null).show()
    }

    private fun applyAnnot() {
        val list = annotItems.toList()
        val idx = pageIdx
        annotating = false
        annotItems.clear()
        render()
        if (list.isEmpty()) return
        apply { doc -> bakeAnnots(doc, doc.getPage(idx), list) }
    }

    private fun rotAbout(rad: Double, px: Float, py: Float): Matrix {
        val c = Math.cos(rad).toFloat()
        val s = Math.sin(rad).toFloat()
        return Matrix(c, s, -s, c, px - px * c + py * s, py - px * s - py * c)
    }

    private fun bakeAnnots(doc: PDDocument, page: PDPage, list: List<AnnotItem>) {
        val cb = page.cropBox
        val rot = ((page.rotation % 360) + 360) % 360
        val w = cb.width
        val h = cb.height
        val llx = cb.lowerLeftX
        val lly = cb.lowerLeftY
        val swap = rot == 90 || rot == 270
        val dw = if (swap) h else w
        val dh = if (swap) w else h
        fun mx(u: Float, v: Float): Float = when (rot) { 0 -> llx + u * w; 90 -> llx + v * w; 180 -> llx + (1 - u) * w; else -> llx + (1 - v) * w }
        fun my(u: Float, v: Float): Float = when (rot) { 0 -> lly + (1 - v) * h; 90 -> lly + u * h; 180 -> lly + v * h; else -> lly + (1 - u) * h }
        val lw = Math.max(1f, dw * 0.004f)
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            for (a in list) {
                val rr = Color.red(a.color)
                val gg = Color.green(a.color)
                val bb = Color.blue(a.color)
                cs.saveGraphicsState()
                cs.setStrokingColor(rr, gg, bb)
                cs.setLineCapStyle(1)
                cs.setLineJoinStyle(1)
                cs.setLineWidth(lw)
                fun poly(pts: List<android.graphics.PointF>, close: Boolean) {
                    cs.moveTo(mx(pts[0].x, pts[0].y), my(pts[0].x, pts[0].y))
                    for (k in 1 until pts.size) cs.lineTo(mx(pts[k].x, pts[k].y), my(pts[k].x, pts[k].y))
                    if (close) cs.closePath()
                    cs.stroke()
                }
                val p = a.pts
                when (a.mode) {
                    0 -> if (p.size >= 2) poly(p, false)
                    1 -> if (p.size >= 2) {
                        val gs = PDExtendedGraphicsState()
                        gs.strokingAlphaConstant = 0.35f
                        gs.nonStrokingAlphaConstant = 0.35f
                        cs.setGraphicsStateParameters(gs)
                        cs.setLineWidth(dw * 0.03f)
                        cs.setLineCapStyle(0)
                        poly(p, false)
                    }
                    3, 4 -> if (p.size >= 2) { cs.setLineWidth(lw * 1.4f); poly(listOf(p[0], p[p.size - 1]), false) }
                    5 -> if (p.size >= 2) {
                        val a0 = p[0]; val a1 = p[p.size - 1]
                        poly(listOf(a0, android.graphics.PointF(a1.x, a0.y), a1, android.graphics.PointF(a0.x, a1.y)), true)
                    }
                    6 -> if (p.size >= 2) {
                        val a0 = p[0]; val a1 = p[p.size - 1]
                        val cu = (a0.x + a1.x) / 2f; val cv = (a0.y + a1.y) / 2f
                        val ru = Math.abs(a1.x - a0.x) / 2f; val rv = Math.abs(a1.y - a0.y) / 2f
                        val pts = ArrayList<android.graphics.PointF>()
                        for (k in 0 until 48) {
                            val t = k * 2 * Math.PI / 48
                            pts.add(android.graphics.PointF(cu + (ru * Math.cos(t)).toFloat(), cv + (rv * Math.sin(t)).toFloat()))
                        }
                        poly(pts, true)
                    }
                    7 -> if (p.size >= 2) {
                        val a0 = p[0]; val a1 = p[p.size - 1]
                        poly(listOf(a0, a1), false)
                        val ang = Math.atan2(((a1.y - a0.y) * dh).toDouble(), ((a1.x - a0.x) * dw).toDouble())
                        val hl = dw * 0.03
                        for (sgn in intArrayOf(-1, 1)) {
                            val t = ang + Math.PI + sgn * 0.5
                            poly(listOf(a1, android.graphics.PointF(a1.x + (hl * Math.cos(t) / dw).toFloat(), a1.y + (hl * Math.sin(t) / dh).toFloat())), false)
                        }
                    }
                    8 -> if (p.isNotEmpty()) {
                        val sz = dw * 0.05f
                        val q = p[0]
                        cs.setLineWidth(lw * 2f)
                        poly(listOf(
                            android.graphics.PointF(q.x - 0.5f * sz / dw, q.y),
                            android.graphics.PointF(q.x - 0.15f * sz / dw, q.y + 0.4f * sz / dh),
                            android.graphics.PointF(q.x + 0.55f * sz / dw, q.y - 0.5f * sz / dh)), false)
                    }
                    2 -> {
                        val tb = a.bmp
                        if (tb != null && p.isNotEmpty()) {
                            val img = LosslessFactory.createFromImage(doc, tb)
                            val th = dh * 0.035f
                            val tw = th * tb.width / tb.height
                            val x0 = mx(p[0].x, p[0].y)
                            val y0 = my(p[0].x, p[0].y)
                            if (rot != 0) cs.transform(rotAbout(Math.toRadians(rot.toDouble()), x0, y0))
                            cs.drawImage(img, x0, y0 - th, tw, th)
                        }
                    }
                }
                cs.restoreGraphicsState()
            }
        }
    }

    // ------------------------------------------------------------------ OCR

    private fun ocrPage() {
        lateinit var build: (LinearLayout) -> Unit
        build = { body ->
            body.addView(label(tr("লেখার ভাষা"), 14f, Ui.TEXT, true), lp(b = 8))
            val langs = listOf(tr("ইংরেজি ও অন্যান্য লাতিন ভাষা"), tr("বাংলা"), tr("হিন্দি"), tr("চীনা"))
            for ((k, n) in langs.withIndex()) {
                val on = ocrLang == k
                val r = LinearLayout(this)
                r.gravity = Gravity.CENTER_VERTICAL
                r.background = bg(if (on) Ui.mix(Ui.PRIMARY, Ui.CARD, 0.9f) else Ui.CARD, 12, if (on) Ui.PRIMARY else Ui.LINE, if (on) 2 else 1)
                r.setPadding(dp(14), dp(13), dp(14), dp(13))
                r.addView(label(n, 14f, Ui.TEXT, on), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                if (on) r.addView(label("✓", 14f, Ui.PRIMARY, true))
                val kk = k
                r.setOnClickListener { ocrLang = kk; subBuild = build; render() }
                body.addView(r, lp(b = 6))
            }
            body.addView(label(tr("কোন পেজ"), 14f, Ui.TEXT, true), lp(t = 14, b = 8))
            for ((k, n) in listOf(tr("সব পেজ"), tr("শুধু এই পেজ")).withIndex()) {
                val on = (k == 0) == ocrAll
                val r = LinearLayout(this)
                r.gravity = Gravity.CENTER_VERTICAL
                r.background = bg(if (on) Ui.mix(Ui.PRIMARY, Ui.CARD, 0.9f) else Ui.CARD, 12, if (on) Ui.PRIMARY else Ui.LINE, if (on) 2 else 1)
                r.setPadding(dp(14), dp(13), dp(14), dp(13))
                r.addView(label(n, 14f, Ui.TEXT, on), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                if (on) r.addView(label("✓", 14f, Ui.PRIMARY, true))
                val kk = k
                r.setOnClickListener { ocrAll = kk == 0; subBuild = build; render() }
                body.addView(r, lp(b = 6))
            }
            body.addView(label(tr("বাংলা চেনা পরিষ্কার ছাপা লেখায় ভালো চলে, হাতের লেখায় ভুল হবে।"), 11f, Ui.MUTED), lp(t = 10))
        }
        openSub(tr("ছবির লেখা চিনুন"), tr("স্ক্যান করা পেজ বা ছবি থেকে লেখা বের করুন"), tr("লেখা চিনুন"), build = build) { runOcr() }
    }

    private fun runOcr() {
        if (busy) return
        busy = true
        val f = cur()
        val n = pageCount
        val pages = if (ocrAll) (0 until n).toList() else listOf(pageIdx)
        val lang = ocrLang
        val dlg = android.app.AlertDialog.Builder(this).setMessage(tr("লেখা চেনা হচ্ছে…")).setCancelable(false).create()
        dlg.show()
        Thread {
            val sb = StringBuilder()
            var failed = false
            try {
                var rec: com.google.mlkit.vision.text.TextRecognizer? = null
                var tess: com.googlecode.tesseract.android.TessBaseAPI? = null
                if (lang == 1) {
                    val root = File(filesDir, "tess")
                    val td = File(root, "tessdata")
                    td.mkdirs()
                    for (n in listOf("ben.traineddata", "eng.traineddata")) {
                        val o = File(td, n)
                        if (!o.exists() || o.length() == 0L) assets.open("tessdata/$n").use { i -> o.outputStream().use { i.copyTo(it) } }
                    }
                    val t = com.googlecode.tesseract.android.TessBaseAPI()
                    if (!t.init(root.absolutePath, "ben+eng")) throw IllegalStateException()
                    tess = t
                } else {
                    rec = when (lang) {
                        2 -> com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions.Builder().build())
                        3 -> com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build())
                        else -> com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
                    }
                }
                for ((k, i) in pages.withIndex()) {
                    runOnUiThread { dlg.setMessage(tr("লেখা চেনা হচ্ছে… ") + bn((k + 1).toString()) + " / " + bn(pages.size.toString())) }
                    val b = renderBitmap(f, i, if (tess != null) 300 else 220) ?: continue
                    val txt = if (tess != null) {
                        tess.setImage(b)
                        tess.utF8Text ?: ""
                    } else com.google.android.gms.tasks.Tasks.await(rec!!.process(com.google.mlkit.vision.common.InputImage.fromBitmap(b, 0))).text
                    b.recycle()
                    if (pages.size > 1) sb.append("--- ").append(tr("পেজ ")).append(i + 1).append(" ---\n")
                    sb.append(txt.trim()).append("\n\n")
                }
                rec?.close()
                tess?.recycle()
            } catch (e: Throwable) { failed = true }
            runOnUiThread {
                busy = false
                dlg.dismiss()
                val txt = sb.toString().trim()
                if (failed && txt.isEmpty()) Toast.makeText(this, tr("লেখা চেনা যায়নি। প্রথমবার ইন্টারনেট লাগতে পারে।"), Toast.LENGTH_LONG).show()
                else if (txt.isEmpty()) Toast.makeText(this, tr("কোনো লেখা পাওয়া যায়নি"), Toast.LENGTH_LONG).show()
                else ocrResult(txt)
            }
        }.start()
    }

    private fun ocrResult(text: String) {
        var et: android.widget.EditText? = null
        openSub(tr("চেনা লেখা"), tr("দরকারে লেখা ঠিক করে নিন"), tr("কপি করুন"), build = { body ->
            val e = android.widget.EditText(this)
            e.setText(if (ocrText.isNotEmpty()) ocrText else text)
            e.setTextColor(Ui.TEXT)
            e.textSize = 14f
            e.gravity = Gravity.TOP
            e.minLines = 10
            e.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            e.background = bg(Ui.CARD, 12, Ui.LINE, 1)
            e.setPadding(dp(14), dp(12), dp(14), dp(12))
            et = e
            body.addView(e, lp())
            val row = LinearLayout(this)
            row.addView(outlineButton(tr("TXT সংরক্ষণ")) { ocrText = e.text.toString(); saveText(false) }, LinearLayout.LayoutParams(0, dp(48), 1f).also { it.setMargins(0, 0, dp(8), 0) })
            row.addView(outlineButton(tr("DOCX সংরক্ষণ")) { ocrText = e.text.toString(); saveText(true) }, LinearLayout.LayoutParams(0, dp(48), 1f))
            body.addView(row, lp(t = 12))
        }) {
            val t = et?.text?.toString() ?: text
            (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("ocr", t))
            Toast.makeText(this, tr("কপি হয়েছে"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveText(docx: Boolean) {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = if (docx) "application/vnd.openxmlformats-officedocument.wordprocessingml.document" else "text/plain"
        i.putExtra(Intent.EXTRA_TITLE, baseName + (if (docx) ".docx" else ".txt"))
        saveOrAsk(i, if (docx) 94 else 93)
    }

    private fun writeText(u: Uri, docx: Boolean) {
        val t = ocrText
        var ok = true
        try {
            contentResolver.openOutputStream(u)?.use { o ->
                if (!docx) o.write(t.toByteArray(Charsets.UTF_8))
                else {
                    fun esc(x: String) = x.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    val body = StringBuilder()
                    for (line in t.split("\n")) body.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(esc(line)).append("</w:t></w:r></w:p>")
                    val z = java.util.zip.ZipOutputStream(o)
                    fun put(name: String, c: String) { z.putNextEntry(java.util.zip.ZipEntry(name)); z.write(c.toByteArray(Charsets.UTF_8)); z.closeEntry() }
                    put("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>")
                    put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>")
                    put("word/document.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>$body</w:body></w:document>")
                    z.finish()
                }
            }
        } catch (e: Throwable) { ok = false }
        if (ok) Usage.done(this, "PdfEditorActivity", Img.size(this, u), Img.name(this, u))
        Toast.makeText(this, if (ok) tr("সংরক্ষিত হয়েছে") else tr("সংরক্ষণ করা যায়নি"), Toast.LENGTH_LONG).show()
    }

    // ------------------------------------------------------------------ digital signature

    private fun savedSigFile() = File(filesDir, "signature.png")

    private fun sigDir() = File(filesDir, "sigs").also { it.mkdirs() }

    private fun signDialog() {
        // the older single saved signature moves into the library
        val old = savedSigFile()
        if (old.exists()) { try { old.copyTo(File(sigDir(), "sig_old.png"), true); old.delete() } catch (e: Throwable) { } }
        var pad: SignPad? = null
        openSub(tr("ডিজিটাল সই"), tr("আঙুল দিয়ে সই আঁকুন অথবা লাইব্রেরি থেকে বেছে নিন"), tr("চালিয়ে যান"), dark = true, cancel = true,
            build = { body ->
                val pp = SignPad(this)
                pad = pp
                body.addView(pp, lp(height = (resources.displayMetrics.heightPixels * 0.42f).toInt()))
                val clr = label(tr("মুছে আবার আঁকুন"), 13f, Ui.PRIMARY, true)
                clr.setPadding(0, dp(12), 0, dp(4))
                clr.setOnClickListener { pp.clear() }
                body.addView(clr)
                val files = (sigDir().listFiles() ?: emptyArray()).sortedByDescending { it.lastModified() }
                if (files.isNotEmpty()) {
                    body.addView(label(tr("সই লাইব্রেরি"), 12f, dMuted), lp(t = 14, b = 6))
                    val hs = android.widget.HorizontalScrollView(this)
                    hs.isHorizontalScrollBarEnabled = false
                    val row = LinearLayout(this)
                    for (f in files.take(8)) {
                        val bm = android.graphics.BitmapFactory.decodeFile(f.absolutePath) ?: continue
                        val iv = ImageView(this)
                        iv.setImageBitmap(bm)
                        iv.setBackgroundColor(Color.WHITE)
                        iv.setPadding(dp(8), dp(8), dp(8), dp(8))
                        iv.scaleType = ImageView.ScaleType.FIT_CENTER
                        iv.setOnClickListener { startPlacing(bm) }
                        iv.setOnLongClickListener {
                            f.delete()
                            subBuild?.let { _ -> render() }
                            true
                        }
                        row.addView(iv, lp(width = dp(110), height = dp(64), r = 8))
                    }
                    hs.addView(row)
                    body.addView(hs)
                    body.addView(label(tr("মুছতে সইয়ে চেপে ধরুন"), 11f, dMuted), lp(t = 6))
                }
            }) {
            val b = pad?.result()
            if (b == null) { Toast.makeText(this, tr("আগে সই আঁকুন"), Toast.LENGTH_SHORT).show(); return@openSub }
            try {
                File(sigDir(), "sig_" + System.currentTimeMillis() + ".png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                (sigDir().listFiles() ?: emptyArray()).sortedByDescending { it.lastModified() }.drop(8).forEach { it.delete() }
            } catch (e: Throwable) { }
            startPlacing(b)
        }
    }

    private fun startPlacing(b: Bitmap) {
        if (curBmp == null) return
        sigBmp = b
        placing = true
        stage = 1
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
    private fun filterPages(doc: PDDocument, srcFile: File, list: List<Int>, mode: Int, dpi: Int = 150, q: Float = 0.85f, params: EnhParams? = null) {
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
                val out = if (params != null) enhance(bmp, params) else if (mode < 0) bmp else filtered(bmp, mode)
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

    // ------------------------------------------------------------------ clean-up and filters

    private fun enhance(src: Bitmap, p: EnhParams): Bitmap {
        var bm = src
        if (p.clean) bm = whitenBackground(src)
        val out = Bitmap.createBitmap(bm.width, bm.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix()
        cm.setSaturation(if (p.gray || p.bw) 0f else p.sat)
        val c = p.contrast
        val b = p.bright + (1 - c) * 128f
        cm.postConcat(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, b, 0f, c, 0f, 0f, b, 0f, 0f, c, 0f, b, 0f, 0f, 0f, 1f, 0f)))
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        Canvas(out).drawBitmap(bm, 0f, 0f, paint)
        if (bm !== src) bm.recycle()
        if (p.bw) {
            val n = out.width * out.height
            val px = IntArray(n)
            out.getPixels(px, 0, out.width, 0, 0, out.width, out.height)
            var thr = 150
            if (!p.clean) {
                var sum = 0L
                for (v in px) sum += (v shr 8) and 0xFF
                thr = (sum / Math.max(1, n) * 0.82f).toInt()
            }
            for (k in 0 until n) px[k] = if (((px[k] shr 8) and 0xFF) > thr) Color.WHITE else Color.BLACK
            out.setPixels(px, 0, out.width, 0, 0, out.width, out.height)
        }
        return out
    }

    /** Divides the picture by a blurred copy of its own paper colour: removes shadows and uneven light, makes paper white. */
    private fun whitenBackground(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val sw = Math.max(4, w / 16)
        val sh = Math.max(4, h / 16)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val sp = IntArray(sw * sh)
        small.getPixels(sp, 0, sw, 0, 0, sw, sh)
        small.recycle()
        // grow the light parts (text is thin, so it disappears and only the paper is left)
        var cur = sp
        for (round in 0 until 2) {
            val nx = IntArray(sw * sh)
            for (y in 0 until sh) for (x in 0 until sw) {
                var r = 0; var g = 0; var b = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = (x + dx).coerceIn(0, sw - 1)
                    val yy = (y + dy).coerceIn(0, sh - 1)
                    val v = cur[yy * sw + xx]
                    r = Math.max(r, Color.red(v)); g = Math.max(g, Color.green(v)); b = Math.max(b, Color.blue(v))
                }
                nx[y * sw + x] = Color.rgb(r, g, b)
            }
            cur = nx
        }
        val bgSmall = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        bgSmall.setPixels(cur, 0, sw, 0, 0, sw, sh)
        val bgBig = Bitmap.createScaledBitmap(bgSmall, w, h, true)
        bgSmall.recycle()
        val a = IntArray(w * h)
        val bgp = IntArray(w * h)
        src.getPixels(a, 0, w, 0, 0, w, h)
        bgBig.getPixels(bgp, 0, w, 0, 0, w, h)
        bgBig.recycle()
        for (k in a.indices) {
            val v = a[k]
            val g = bgp[k]
            val r = Math.min(255, Color.red(v) * 255 / Math.max(40, Color.red(g)))
            val gr = Math.min(255, Color.green(v) * 255 / Math.max(40, Color.green(g)))
            val bl = Math.min(255, Color.blue(v) * 255 / Math.max(40, Color.blue(g)))
            a[k] = Color.rgb(r, gr, bl)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(a, 0, w, 0, 0, w, h)
        return out
    }

    private fun enhancePage() {
        if (curBmp == null || busy) return
        enh = EnhParams()
        enhAll = false
        lateinit var build: (LinearLayout) -> Unit
        build = { body ->
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            iv.setBackgroundColor(Color.WHITE)
            enhIv = iv
            body.addView(iv, lp(height = (resources.displayMetrics.heightPixels * 0.36f).toInt()))
            updateEnhPreview()
            val hs = android.widget.HorizontalScrollView(this)
            hs.isHorizontalScrollBarEnabled = false
            val row = LinearLayout(this)
            val presets = listOf(tr("মূল") to EnhParams(), tr("অটো") to EnhParams(clean = true, contrast = 1.1f, bright = 4f),
                tr("সাদাকালো") to EnhParams(clean = true, bw = true), tr("গ্রেস্কেল") to EnhParams(gray = true, clean = true),
                tr("উজ্জ্বল রঙ") to EnhParams(clean = true, contrast = 1.2f, bright = 8f, sat = 1.2f))
            for ((n, pr) in presets) {
                val c = label(n, 13f, dText, true)
                c.gravity = Gravity.CENTER
                c.background = bg(dCard, 18, dLine, 1)
                c.setPadding(dp(16), dp(9), dp(16), dp(9))
                c.setOnClickListener { enh = pr.copy(); subBuild = build; render() }
                row.addView(c, lp(r = 8))
            }
            hs.addView(row)
            body.addView(hs, lp(t = 12))
            fun slider(name: String, min: Float, max: Float, v: Float, set: (Float) -> Unit) {
                val hd = LinearLayout(this)
                hd.addView(label(name, 12f, dMuted), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                body.addView(hd, lp(t = 12))
                val sb = android.widget.SeekBar(this)
                sb.max = 1000
                sb.progress = ((v - min) / (max - min) * 1000f).toInt().coerceIn(0, 1000)
                sb.progressTintList = android.content.res.ColorStateList.valueOf(Ui.PRIMARY)
                sb.thumbTintList = android.content.res.ColorStateList.valueOf(Ui.PRIMARY)
                sb.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: android.widget.SeekBar?, pv: Int, fromUser: Boolean) {
                        if (fromUser) { set(min + pv / 1000f * (max - min)); updateEnhPreview() }
                    }
                    override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
                    override fun onStopTrackingTouch(s: android.widget.SeekBar?) {}
                })
                body.addView(sb)
            }
            slider(tr("উজ্জ্বলতা"), -60f, 60f, enh.bright) { enh.bright = it }
            slider(tr("কনট্রাস্ট"), 0.6f, 2f, enh.contrast) { enh.contrast = it }
            slider(tr("রঙের গাঢ়তা"), 0f, 2f, enh.sat) { enh.sat = it }
            val sw = android.widget.Switch(this)
            sw.text = tr("ব্যাকগ্রাউন্ড সাদা ও ছায়া কমানো")
            sw.setTextColor(dText)
            sw.isChecked = enh.clean
            sw.setOnCheckedChangeListener { _, on -> enh.clean = on; updateEnhPreview() }
            body.addView(sw, lp(t = 14))
            val sw2 = android.widget.Switch(this)
            sw2.text = tr("সব পেজে প্রয়োগ করুন")
            sw2.setTextColor(dText)
            sw2.isChecked = enhAll
            sw2.setOnCheckedChangeListener { _, on -> enhAll = on }
            body.addView(sw2, lp(t = 10))
            body.addView(label(tr("পরে ইচ্ছা হলে পূর্বাবস্থায় মূল ছবি ফিরে পাবেন।"), 11f, dMuted), lp(t = 8))
        }
        openSub(tr("ছবি পরিষ্কার"), "", tr("ঠিক আছে"), dark = true, cancel = true, build = build) {
            val list = if (enhAll) (0 until pageCount).toList() else listOf(pageIdx)
            val src = cur()
            val pr = enh.copy()
            stage = 1
            render()
            apply { doc -> filterPages(doc, src, list, -1, 150, 0.85f, pr) }
        }
    }

    private fun updateEnhPreview() {
        val src = curBmp ?: return
        val iv = enhIv ?: return
        val token = ++enhToken
        val pr = enh.copy()
        Thread {
            val s = Math.min(1f, 700f / src.width)
            val small = if (s < 1f) Bitmap.createScaledBitmap(src, Math.max(1, (src.width * s).toInt()), Math.max(1, (src.height * s).toInt()), true) else src
            val out = enhance(small, pr)
            runOnUiThread { if (token == enhToken && enhIv === iv) iv.setImageBitmap(out) }
        }.start()
    }

    // ------------------------------------------------------------------ Recent Files

    private fun renderRecent() {
        root.addView(pdfHeader(tr("সাম্প্রতিক ফাইল"), "", { stage = 0; render() }), lp())
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(8), dp(16), dp(16))
        scroll.addView(col)
        val items = RecentPdf.list(this)
        if (items.isEmpty()) {
            val t = label(tr("এখনো কোনো ফাইল সংরক্ষণ করা হয়নি"), 14f, Ui.MUTED)
            t.gravity = Gravity.CENTER
            col.addView(t, lp(t = 60))
        }
        for (it in items) {
            val u = Uri.parse(it.uri)
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            r.background = bg(Ui.CARD, 14, Ui.LINE, 1)
            r.setPadding(dp(10), dp(10), dp(6), dp(10))
            val th = thumbView(44, 56)
            pdfThumb(u, 150) { b, _ -> if (b != null) th.setImageBitmap(b) }
            r.addView(th, lp(width = dp(44), height = dp(56), r = 12))
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            val n = label(it.name, 14f, Ui.TEXT, true)
            n.maxLines = 2
            c.addView(n)
            val dt = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.US).format(java.util.Date(it.time))
            c.addView(label(bn(dt) + " · " + Img.human(it.size), 12f, Ui.MUTED), lp(t = 2))
            r.addView(c, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val sh = label("⇗", 19f, Ui.TEXT, true)
            sh.setPadding(dp(10), dp(8), dp(8), dp(8))
            sh.setOnClickListener { _ ->
                val i = Intent(Intent.ACTION_SEND)
                i.type = "application/pdf"
                i.putExtra(Intent.EXTRA_STREAM, u)
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try { startActivity(Intent.createChooser(i, tr("শেয়ার"))) } catch (e: Throwable) { Toast.makeText(this, tr("ফাইলটি পাওয়া যায়নি"), Toast.LENGTH_SHORT).show() }
            }
            r.addView(sh)
            val mn = label("⋮", 20f, Ui.TEXT, true)
            mn.setPadding(dp(8), dp(8), dp(8), dp(8))
            mn.setOnClickListener { _ -> recentMenu(it) }
            r.addView(mn)
            r.setOnClickListener { _ -> openPdf(u) }
            col.addView(r, lp(b = 8))
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun recentMenu(it: RecentPdf.Item) {
        val u = Uri.parse(it.uri)
        val items = arrayOf(tr("খুলুন"), tr("নাম বদলান"), tr("তালিকা থেকে সরান"), tr("ফাইল মুছুন"))
        android.app.AlertDialog.Builder(this).setTitle(it.name).setItems(items) { _, w ->
            when (w) {
                0 -> openPdf(u)
                1 -> {
                    val e = android.widget.EditText(this)
                    e.setText(it.name.removeSuffix(".pdf"))
                    android.app.AlertDialog.Builder(this).setTitle(tr("নাম বদলান")).setView(e)
                        .setPositiveButton(tr("নাম বদলান")) { _, _ ->
                            val nn = e.text.toString().trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
                            if (nn.isNotEmpty()) {
                                try { DocumentsContract.renameDocument(contentResolver, u, "$nn.pdf") } catch (x: Throwable) { }
                                RecentPdf.rename(this, it.uri, "$nn.pdf")
                                render()
                            }
                        }.setNegativeButton(tr("বাতিল"), null).show()
                }
                2 -> { RecentPdf.remove(this, it.uri); render() }
                3 -> android.app.AlertDialog.Builder(this).setMessage(tr("আপনি কি এই ফাইলটি মুছে ফেলতে চান?"))
                    .setPositiveButton(tr("হ্যাঁ, মুছুন")) { _, _ ->
                        try { DocumentsContract.deleteDocument(contentResolver, u) } catch (x: Throwable) { }
                        RecentPdf.remove(this, it.uri)
                        render()
                    }.setNegativeButton(tr("বাতিল"), null).show()
            }
        }.show()
    }

    // ------------------------------------------------------------------ full pages (stage 3)

    private fun openSub(title: String, sub: String, btn: String, dark: Boolean = false, cancel: Boolean = false,
                        build: (LinearLayout) -> Unit, go: () -> Unit) {
        subTitle = title
        subSub = sub
        subBtn = btn
        subDark = dark
        subBuild = build
        subGo = go
        subCancel = if (cancel) ({ stage = 1; render() }) else null
        stage = 3
        render()
    }

    private fun renderSub() {
        val tc = if (subDark) dText else Ui.TEXT
        val mc = if (subDark) dMuted else Ui.MUTED
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(8), dp(10), dp(12), dp(4))
        val b = LinearLayout(this)
        b.gravity = Gravity.CENTER
        b.addView(iconView(R.drawable.ic_back, 24, tc))
        b.setOnClickListener { onBackPressed() }
        head.addView(b, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(head, lp())
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(4), dp(20), dp(16))
        scroll.addView(col)
        col.addView(label(subTitle, 26f, tc, true))
        if (subSub.isNotEmpty()) col.addView(label(subSub, 13f, mc), lp(t = 6))
        val body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        col.addView(body, lp(t = 16))
        subBuild?.invoke(body)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(this)
        bar.setPadding(dp(16), dp(10), dp(16), dp(14))
        if (subCancel != null) bar.addView(outlineButton(tr("বাতিল")) { subCancel?.invoke() }, LinearLayout.LayoutParams(0, dp(52), 1f).also { it.setMargins(0, 0, dp(8), 0) })
        bar.addView(bigButton(subBtn) { if (!busy) subGo?.invoke() }, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(bar, lp())
    }

    private fun smallFileRow(name: String, sub: String, uri: Uri?, bmp: Bitmap?, onRemove: (() -> Unit)?): View {
        val c = LinearLayout(this)
        c.gravity = Gravity.CENTER_VERTICAL
        c.background = bg(Ui.CARD, 14, Ui.LINE, 1)
        c.setPadding(dp(10), dp(10), dp(12), dp(10))
        val th = thumbView(44, 56)
        if (bmp != null) th.setImageBitmap(bmp)
        else if (uri != null) pdfThumb(uri, 150) { b, _ -> if (b != null) th.setImageBitmap(b) }
        c.addView(th, lp(width = dp(44), height = dp(56), r = 12))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val n = label(name, 14f, Ui.TEXT, true)
        n.maxLines = 1
        n.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        col.addView(n)
        col.addView(label(sub, 12f, Ui.MUTED), lp(t = 2))
        c.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (onRemove != null) {
            val x = label("✕", 16f, Ui.MUTED, true)
            x.setPadding(dp(10), dp(6), dp(4), dp(6))
            x.setOnClickListener { onRemove() }
            c.addView(x)
        }
        return c
    }

    private fun curSub(): String = Img.human(cur().length()) + " · " + bn(pageCount.toString()) + tr(" পেজ")

    private fun mergePage() {
        mergeAdd.clear()
        mergeName = baseName + " - " + tr("জোড়া")
        var nameField: android.widget.EditText? = null
        lateinit var build: (LinearLayout) -> Unit
        build = { body ->
            body.addView(label(tr("ফাইলের নাম"), 12f, Ui.MUTED))
            val e = android.widget.EditText(this)
            e.setText(mergeName)
            styleInput(e)
            e.setSingleLine(true)
            nameField = e
            body.addView(e, lp(t = 6))
            body.addView(smallFileRow(baseName, curSub(), null, curBmp, null), lp(t = 16))
            for ((k, u) in mergeAdd.withIndex()) {
                val nm = Img.name(this, u)
                body.addView(smallFileRow(nm, Img.human(Img.size(this, u)), u, null) {
                    mergeName = nameField?.text?.toString() ?: mergeName
                    mergeAdd.removeAt(k)
                    subBuild = build
                    render()
                }, lp(t = 8))
            }
            body.addView(outlineButton("+  " + tr("আরও ফাইল যোগ করুন")) {
                mergeName = nameField?.text?.toString() ?: mergeName
                subBuild = build
                pickMergeFiles()
            }, lp(height = dp(48), t = 14))
        }
        openSub(tr("PDF জোড়া দিন"), tr("এই ফাইলের সাথে জোড়া দিতে আরও ফাইল বাছুন"), tr("জোড়া দিন"), build = build) {
            val n = (nameField?.text?.toString() ?: mergeName).trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").removeSuffix(".pdf")
            if (mergeAdd.isEmpty()) { Toast.makeText(this, tr("আরও অন্তত একটি ফাইল যোগ করুন"), Toast.LENGTH_SHORT).show(); return@openSub }
            if (n.isNotEmpty()) baseName = n
            val l = ArrayList(mergeAdd)
            mergeAdd.clear()
            stage = 1
            render()
            mergeIn(l)
        }
    }

    private fun pickMergeFiles() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        i.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "image/*"))
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(i, 81)
    }

    /** A password box with an eye button to show or hide the text. */
    private fun passRow(hint: String): Pair<View, android.widget.EditText> {
        val r = LinearLayout(this)
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = bg(Ui.CARD, 12, Ui.LINE, 1)
        val e = android.widget.EditText(this)
        e.hint = hint
        e.setTextColor(Ui.TEXT)
        e.setHintTextColor(Ui.MUTED)
        e.textSize = 15f
        e.setSingleLine(true)
        e.background = null
        e.setPadding(dp(14), dp(14), dp(6), dp(14))
        val hidden = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        val shown = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        e.inputType = hidden
        r.addView(e, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val eye = label("👁", 18f, Ui.PRIMARY, true)
        eye.setPadding(dp(8), dp(8), dp(14), dp(8))
        var vis = false
        eye.setOnClickListener {
            vis = !vis
            e.inputType = if (vis) shown else hidden
            e.setSelection(e.text.length)
            eye.alpha = if (vis) 1f else 0.6f
        }
        eye.alpha = 0.6f
        r.addView(eye)
        return Pair(r, e)
    }

    private fun protectPage() {
        var p1: android.widget.EditText? = null
        var p2: android.widget.EditText? = null
        openSub(tr("PDF লক করুন"),
            tr("সংরক্ষণ বা শেয়ারের সময় PDF-এ এই পাসওয়ার্ড বসবে। পাসওয়ার্ড ছাড়া ফাইল খোলা যাবে না। পাসওয়ার্ড ভুলে গেলে ফাইল আর খোলা যাবে না। ফাঁকা রেখে চাপলে লক বন্ধ হবে।"),
            tr("লক করুন"), build = { body ->
                body.addView(label(tr("পাসওয়ার্ড"), 12f, Ui.MUTED))
                val a = passRow("••••••••")
                a.second.setText(lockPass ?: "")
                p1 = a.second
                body.addView(a.first, lp(t = 6))
                body.addView(label(tr("পাসওয়ার্ড আবার লিখুন"), 12f, Ui.MUTED), lp(t = 16))
                val b = passRow("••••••••")
                b.second.setText(lockPass ?: "")
                p2 = b.second
                body.addView(b.first, lp(t = 6))
            }) {
            val a = p1?.text?.toString() ?: ""
            val b = p2?.text?.toString() ?: ""
            if (a != b) { Toast.makeText(this, tr("পাসওয়ার্ড মেলেনি"), Toast.LENGTH_SHORT).show(); return@openSub }
            lockPass = if (a.isEmpty()) null else a
            lockAes256 = true
            Toast.makeText(this, if (a.isEmpty()) tr("লক বন্ধ আছে") else tr("সংরক্ষণের সময় PDF লক হবে"), Toast.LENGTH_SHORT).show()
            stage = 1
            render()
        }
    }

    private fun compressPage() {
        var level = 1
        lateinit var build: (LinearLayout) -> Unit
        build = { body ->
            body.addView(smallFileRow(baseName, curSub(), null, curBmp, null))
            body.addView(label(tr("ছোট করার মাত্রা বাছুন"), 14f, Ui.TEXT, true), lp(t = 22, b = 8))
            val names = listOf(tr("বেশি ছোট করা"), tr("মাঝারি ছোট করা"), tr("কম ছোট করা"))
            val subs = listOf(tr("সবচেয়ে ছোট, মান কম"), tr("মাঝারি আকার, মাঝারি মান"), tr("বড় আকার, সেরা মান"))
            for (k in 0..2) {
                val r = LinearLayout(this)
                r.gravity = Gravity.CENTER_VERTICAL
                r.setPadding(dp(4), dp(10), dp(4), dp(10))
                val on = level == k
                val dot = LinearLayout(this)
                dot.gravity = Gravity.CENTER
                dot.background = bg(Color.TRANSPARENT, 11, Ui.PRIMARY, 2)
                if (on) { val in2 = View(this); in2.background = bg(Ui.PRIMARY, 6); dot.addView(in2, LinearLayout.LayoutParams(dp(12), dp(12))) }
                r.addView(dot, lp(width = dp(22), height = dp(22), r = 12))
                val c = LinearLayout(this)
                c.orientation = LinearLayout.VERTICAL
                c.addView(label(names[k], 15f, Ui.TEXT, true))
                c.addView(label(subs[k], 12f, Ui.MUTED), lp(t = 1))
                r.addView(c)
                val kk = k
                r.setOnClickListener { level = kk; subBuild = build; render() }
                body.addView(r)
            }
        }
        openSub(tr("PDF ছোট করুন"), tr("আপনার PDF ফাইলের আকার কমান"), tr("ছোট করুন"), build = build) {
            val lv = intArrayOf(2, 1, 0)[level]
            val dpi = intArrayOf(150, 110, 72)[lv]
            val q = floatArrayOf(0.8f, 0.65f, 0.45f)[lv]
            val before = cur().length()
            val src = cur()
            stage = 1
            render()
            apply({
                val after = cur().length()
                Toast.makeText(this, tr("আগে: ") + Img.human(before) + tr("  →  এখন: ") + Img.human(after), Toast.LENGTH_LONG).show()
            }) { doc -> filterPages(doc, src, (0 until doc.numberOfPages).toList(), -1, dpi, q) }
        }
    }

    private var resizePick = 0

    private fun resizePage() {
        resizePick = 0
        lateinit var build: (LinearLayout) -> Unit
        build = { body ->
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            iv.setBackgroundColor(Color.WHITE)
            curBmp?.let { iv.setImageBitmap(it) }
            body.addView(iv, lp(height = (resources.displayMetrics.heightPixels * 0.5f).toInt()))
            body.addView(label(tr("পেজ ") + bn((pageIdx + 1).toString()) + " / " + bn(pageCount.toString()), 12f, dMuted).also { it.gravity = Gravity.CENTER }, lp(t = 6))
            val hs = android.widget.HorizontalScrollView(this)
            hs.isHorizontalScrollBarEnabled = false
            val row = LinearLayout(this)
            val names = listOf(tr("অটো ফিট"), "A4", "A3", "A5", "US Letter")
            for ((k, nm) in names.withIndex()) {
                val c = LinearLayout(this)
                c.orientation = LinearLayout.VERTICAL
                c.gravity = Gravity.CENTER
                val on = resizePick == k
                c.background = bg(if (on) Ui.mix(Ui.PRIMARY, dCard, 0.7f) else dCard, 12, if (on) Ui.PRIMARY else dLine, if (on) 2 else 1)
                c.setPadding(dp(14), dp(10), dp(14), dp(10))
                c.addView(label(if (k == 0) "⛶" else "▯", 20f, if (on) Ui.PRIMARY else dText, true).also { it.gravity = Gravity.CENTER })
                c.addView(label(nm, 11f, if (on) Ui.PRIMARY else dMuted).also { it.gravity = Gravity.CENTER }, lp(t = 2))
                val kk = k
                c.setOnClickListener { resizePick = kk; subBuild = build; render() }
                row.addView(c, lp(r = 8))
            }
            hs.addView(row)
            body.addView(hs, lp(t = 14))
        }
        openSub(tr("রিসাইজ"), "", tr("ঠিক আছে"), dark = true, cancel = true, build = build) {
            val sizes = arrayOf(null, PDRectangle.A4, PDRectangle.A3, PDRectangle.A5, PDRectangle.LETTER)
            val target = sizes[resizePick]
            stage = 1
            render()
            if (target != null) apply { doc -> for (k in 0 until doc.numberOfPages) fitTo(doc, doc.getPage(k), target) }
        }
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
        e.setTextColor(Ui.TEXT)
        e.setHintTextColor(Ui.MUTED)
        e.textSize = 15f
        e.setSingleLine(true)
        e.inputType = when {
            password -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            number -> android.text.InputType.TYPE_CLASS_NUMBER
            else -> android.text.InputType.TYPE_CLASS_TEXT
        }
        e.background = bg(Ui.BG, 12, Ui.LINE, 1)
        e.setPadding(dp(14), dp(12), dp(14), dp(12))
        return e
    }

    private fun darkOption(title: String, on: Boolean, click: () -> Unit): View {
        val r = LinearLayout(this)
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = bg(if (on) Ui.mix(Ui.PRIMARY, Ui.CARD, 0.9f) else Ui.BG, 12, if (on) Ui.PRIMARY else Ui.LINE, if (on) 2 else 1)
        r.setPadding(dp(14), dp(12), dp(14), dp(12))
        r.addView(label(title, 14f, Ui.TEXT, on), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
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
        g.setColor(Ui.CARD)
        g.cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f)
        box.background = g
        box.setPadding(dp(18), dp(16), dp(18), dp(18))
        box.addView(label(title, 16f, Ui.TEXT, true))
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
        val d = android.app.Dialog(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val g = GradientDrawable()
        g.setColor(Ui.CARD)
        g.cornerRadii = floatArrayOf(dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), dp(22).toFloat(), 0f, 0f, 0f, 0f)
        box.background = g
        box.setPadding(dp(16), dp(10), dp(16), dp(18))
        val handle = View(this)
        handle.background = bg(Ui.LINE, 3)
        box.addView(handle, lp(width = dp(40), height = dp(4)).also { it.gravity = Gravity.CENTER_HORIZONTAL })
        val t = label(tr("শেয়ার"), 17f, Ui.TEXT, true)
        t.gravity = Gravity.CENTER
        box.addView(t, lp(t = 12, b = 6))
        val sizes = ArrayList<TextView>()
        fun row(glyph: String, name: String, kind: Int, size: String) {
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(6), dp(13), dp(6), dp(13))
            r.addView(label(glyph, 16f, Ui.TEXT, true).also { it.gravity = Gravity.CENTER }, lp(width = dp(32), r = 10))
            r.addView(label(name, 15f, Ui.TEXT))
            val sz = label(size, 12f, Ui.MUTED)
            r.addView(sz, lp(l = 8))
            sizes.add(sz)
            r.setOnClickListener { d.dismiss(); shareFile(kind) }
            box.addView(r)
        }
        row("PDF", tr("PDF শেয়ার"), 0, "(" + Img.human(cur().length()) + ")")
        row("JPG", tr("এই পেজ JPG"), 1, "")
        row("PNG", tr("এই পেজ PNG"), 2, "")
        val f = cur()
        val idx = pageIdx
        Thread {
            var jpg = 0
            var png = 0
            try {
                val b = renderBitmap(f, idx, 200)
                if (b != null) {
                    jpg = Img.encode(b, 1, 92).size
                    png = Img.encode(b, 0, 92).size
                    b.recycle()
                }
            } catch (e: Throwable) { }
            runOnUiThread {
                if (jpg > 0) sizes[1].text = "(" + Img.human(jpg.toLong()) + ")"
                if (png > 0) sizes[2].text = "(" + Img.human(png.toLong()) + ")"
            }
        }.start()
        d.setContentView(box)
        d.window?.setBackgroundDrawableResource(android.R.color.transparent)
        d.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        d.window?.setGravity(Gravity.BOTTOM)
        d.show()
    }

    private fun exportPage() {
        lateinit var build: (LinearLayout) -> Unit
        build = { body ->
            fun head(t: String) = body.addView(label(t, 12f, Ui.MUTED), lp(t = 14, b = 4))
            fun row(name: String, ext: String, sel: Boolean, soon: Boolean, click: () -> Unit) {
                val r = LinearLayout(this)
                r.gravity = Gravity.CENTER_VERTICAL
                r.setPadding(dp(4), dp(13), dp(4), dp(13))
                val n = label(name, 15f, if (soon) Ui.MUTED else Ui.TEXT, sel)
                r.addView(n)
                r.addView(label(" ($ext)", 12f, Ui.MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                if (soon) r.addView(label(tr("শীঘ্রই"), 11f, Ui.PRIMARY, true))
                else if (sel) r.addView(label("✓", 16f, Ui.PRIMARY, true))
                if (!soon) r.setOnClickListener { click() }
                body.addView(r)
                val v = View(this)
                v.setBackgroundColor(Ui.LINE)
                body.addView(v, lp(height = dp(1)))
            }
            head(tr("ডকুমেন্ট"))
            row("Microsoft Word", ".docx", false, true) {}
            row("Microsoft Excel", ".xlsx", false, true) {}
            row("Microsoft PowerPoint", ".pptx", false, true) {}
            row("Rich Text Format", ".rtf", false, true) {}
            head(tr("ছবি"))
            row("Joint Photographic", ".jpeg", exportFmt == 1, false) { exportFmt = 1; subBuild = build; render() }
            row("Portable Network Graphics", ".png", exportFmt == 0, false) { exportFmt = 0; subBuild = build; render() }
            row("Graphics Interchange", ".gif", false, true) {}
            row("Tagged Image File", ".tiff", false, true) {}
        }
        openSub(tr("এক্সপোর্ট করুন…"), "\"" + baseName + "\" " + tr("এক্সপোর্ট করুন"), tr("এক্সপোর্ট"), build = build) {
            treeOrAsk(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 78)
        }
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
        val e = android.widget.EditText(this)
        e.setText(baseName)
        styleInput(e)
        e.setSingleLine(true)
        e.setSelection(e.text.length)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(label(tr("ফাইলের নাম"), 12f, Ui.MUTED))
        col.addView(e, lp(t = 6))
        confirmSheet(tr("PDF সংরক্ষণ"), col, tr("সংরক্ষণ"), false) {
            val n = e.text.toString().trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").removeSuffix(".pdf")
            if (n.isNotEmpty()) baseName = n
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "application/pdf"
            i.putExtra(Intent.EXTRA_TITLE, baseName + ".pdf")
            saveOrAsk(i, 73)
        }
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
                    RecentPdf.add(this, out, Img.name(this, out), outSize)
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
        if (requestCode == 92) {
            if (resultCode == Activity.RESULT_OK) scanDone(data)
            return
        }
        if (requestCode == 91) {
            val u = camUri
            if (resultCode == Activity.RESULT_OK && u != null) openImages(listOf(u))
            else if (u != null) try { contentResolver.delete(u, null, null) } catch (e: Throwable) { }
            camUri = null
            return
        }
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
            81 -> {
                val cd = data.clipData
                if (cd != null) for (k in 0 until cd.itemCount) mergeAdd.add(cd.getItemAt(k).uri) else data.data?.let { mergeAdd.add(it) }
                if (stage == 3) render()
            }
            78 -> data.data?.let { doExport(it) }
            79 -> data.data?.let { doMove(it) }
            93 -> data.data?.let { writeText(it, false) }
            94 -> data.data?.let { writeText(it, true) }
            80 -> data.data?.let { retakeWith(it) }
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
    private val show = Paint(Paint.ANTI_ALIAS_FLAG)
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
        back.color = Color.parseColor("#232532")
        show.style = Paint.Style.STROKE
        show.strokeWidth = 3.5f * d
        show.strokeCap = Paint.Cap.ROUND
        show.strokeJoin = Paint.Join.ROUND
        show.color = Color.WHITE
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
        c.drawPath(path, show)
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

/** One drawn mark on the page, kept as fractions of the page picture (0..1, from the top-left). */
private class AnnotItem(val mode: Int, val color: Int) {
    val pts = ArrayList<android.graphics.PointF>()
    var text = ""
    var bmp: Bitmap? = null
}

/** Text as a transparent picture in the given colour (so Bangla works too). */
private fun annotTextBitmap(text: String, color: Int): Bitmap {
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.textSize = 120f
    p.color = color
    val w = Math.ceil(p.measureText(text).toDouble()).toInt() + 8
    val fm = p.fontMetrics
    val h = Math.ceil((fm.descent - fm.ascent).toDouble()).toInt() + 4
    val b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888)
    Canvas(b).drawText(text, 4f, 2f - fm.ascent, p)
    return b
}

/** Shows the page and lets the finger draw marks on it. */
private class AnnotView(ctx: Context, val items: ArrayList<AnnotItem>) : View(ctx) {
    var bmp: Bitmap? = null
    var mode = 0
    var color = Color.RED
    var askText: ((Float, Float) -> Unit)? = null
    private val img = RectF()
    private val d = ctx.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var cur: AnnotItem? = null
    private var downX = 0f
    private var downY = 0f
    private var moved = false

    private fun fit() {
        val bm = bmp ?: return
        val s = Math.min(width.toFloat() / bm.width, height.toFloat() / bm.height)
        val w = bm.width * s
        val h = bm.height * s
        img.set((width - w) / 2f, (height - h) / 2f, (width + w) / 2f, (height + h) / 2f)
    }

    private fun sx(u: Float) = img.left + u * img.width()
    private fun sy(v: Float) = img.top + v * img.height()

    private fun line(c: Canvas, pts: List<android.graphics.PointF>, close: Boolean) {
        val path = Path()
        path.moveTo(sx(pts[0].x), sy(pts[0].y))
        for (k in 1 until pts.size) path.lineTo(sx(pts[k].x), sy(pts[k].y))
        if (close) path.close()
        c.drawPath(path, paint)
    }

    private fun drawItem(c: Canvas, a: AnnotItem) {
        val p = a.pts
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = a.color
        paint.alpha = 255
        paint.strokeWidth = Math.max(2f * d, img.width() * 0.004f)
        val w = img.width()
        val h = img.height()
        when (a.mode) {
            0 -> if (p.size >= 2) line(c, p, false)
            1 -> if (p.size >= 2) {
                paint.strokeWidth = w * 0.03f
                paint.strokeCap = Paint.Cap.BUTT
                paint.alpha = 90
                line(c, p, false)
            }
            3, 4 -> if (p.size >= 2) { paint.strokeWidth *= 1.4f; line(c, listOf(p[0], p[p.size - 1]), false) }
            5 -> if (p.size >= 2) {
                val a0 = p[0]; val a1 = p[p.size - 1]
                line(c, listOf(a0, android.graphics.PointF(a1.x, a0.y), a1, android.graphics.PointF(a0.x, a1.y)), true)
            }
            6 -> if (p.size >= 2) {
                val a0 = p[0]; val a1 = p[p.size - 1]
                c.drawOval(RectF(sx(Math.min(a0.x, a1.x)), sy(Math.min(a0.y, a1.y)), sx(Math.max(a0.x, a1.x)), sy(Math.max(a0.y, a1.y))), paint)
            }
            7 -> if (p.size >= 2) {
                val a0 = p[0]; val a1 = p[p.size - 1]
                line(c, listOf(a0, a1), false)
                val ang = Math.atan2(((a1.y - a0.y) * h).toDouble(), ((a1.x - a0.x) * w).toDouble())
                val hl = w * 0.03
                for (sgn in intArrayOf(-1, 1)) {
                    val t = ang + Math.PI + sgn * 0.5
                    line(c, listOf(a1, android.graphics.PointF(a1.x + (hl * Math.cos(t) / w).toFloat(), a1.y + (hl * Math.sin(t) / h).toFloat())), false)
                }
            }
            8 -> if (p.isNotEmpty()) {
                val sz = w * 0.05f
                val q = p[0]
                paint.strokeWidth *= 2f
                line(c, listOf(
                    android.graphics.PointF(q.x - 0.5f * sz / w, q.y),
                    android.graphics.PointF(q.x - 0.15f * sz / w, q.y + 0.4f * sz / h),
                    android.graphics.PointF(q.x + 0.55f * sz / w, q.y - 0.5f * sz / h)), false)
            }
            2 -> {
                val tb = a.bmp
                if (tb != null && p.isNotEmpty()) {
                    val th = h * 0.035f
                    val tw = th * tb.width / tb.height
                    c.drawBitmap(tb, null, RectF(sx(p[0].x), sy(p[0].y), sx(p[0].x) + tw, sy(p[0].y) + th), Paint(Paint.FILTER_BITMAP_FLAG))
                }
            }
        }
    }

    override fun onDraw(c: Canvas) {
        val bm = bmp ?: return
        fit()
        c.drawBitmap(bm, null, img, paint)
        for (a in items) drawItem(c, a)
        cur?.let { drawItem(c, it) }
    }

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        if (img.width() <= 0f) return true
        val u = ((e.x - img.left) / img.width()).coerceIn(0f, 1f)
        val v = ((e.y - img.top) / img.height()).coerceIn(0f, 1f)
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = e.x; downY = e.y; moved = false
                if (mode != 2 && mode != 8) {
                    val a = AnnotItem(mode, color)
                    a.pts.add(android.graphics.PointF(u, v))
                    cur = a
                }
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (Math.abs(e.x - downX) > 8 * d || Math.abs(e.y - downY) > 8 * d) moved = true
                val a = cur
                if (a != null) {
                    when (mode) {
                        0, 1 -> a.pts.add(android.graphics.PointF(u, v))
                        3, 4 -> { while (a.pts.size > 1) a.pts.removeAt(1); a.pts.add(android.graphics.PointF(u, a.pts[0].y)) }
                        else -> { while (a.pts.size > 1) a.pts.removeAt(1); a.pts.add(android.graphics.PointF(u, v)) }
                    }
                }
            }
            android.view.MotionEvent.ACTION_UP -> {
                val a = cur
                if (a != null && moved && a.pts.size >= 2) items.add(a)
                cur = null
                if (!moved) {
                    if (mode == 2) askText?.invoke(u, v)
                    else if (mode == 8) {
                        val t = AnnotItem(8, color)
                        t.pts.add(android.graphics.PointF(u, v))
                        items.add(t)
                    }
                }
            }
            android.view.MotionEvent.ACTION_CANCEL -> cur = null
        }
        invalidate()
        return true
    }
}

/** The settings of the clean-up page: brightness, contrast, colour strength, and a few switches. */
private data class EnhParams(
    var bright: Float = 0f,
    var contrast: Float = 1f,
    var sat: Float = 1f,
    var gray: Boolean = false,
    var bw: Boolean = false,
    var clean: Boolean = false
)
