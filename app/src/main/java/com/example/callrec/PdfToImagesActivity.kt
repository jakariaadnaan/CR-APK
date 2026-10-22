package com.example.callrec

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File

import android.provider.DocumentsContract
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
/** PDF → ছবি, step by step: choose file, format / quality / pages, convert with progress, result. */
class PdfToImagesActivity : GuardedActivity() {

    private var stage = 0 // 0 choose, 1 settings, 2 progress / result
    private lateinit var root: LinearLayout

    private var uri: Uri? = null
    private var name = ""
    private var size = 0L
    private var pages = 0
    private var thumb: Bitmap? = null
    private var src: File? = null

    private var png = true
    private var dpi = 150
    private var allPages = true
    private var spec = ""

    private var percent = 0
    private var step = 0
    private var done = false
    private var failed: String? = null
    private var outUri: Uri? = null
    private var outName = ""
    private var outCount = 0
    private var outSize = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.NAV
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (Ui.dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)
        setContentView(root)
        render()
    }

    @Deprecated("Back moves one step back")
    override fun onBackPressed() {
        when {
            stage == 2 && !done && failed == null -> { }
            stage == 2 -> finish()
            stage == 1 -> { stage = 0; render() }
            else -> super.onBackPressed()
        }
    }

    private fun render() {
        root.removeAllViews()
        when (stage) {
            0 -> renderChoose()
            1 -> renderSettings()
            else -> renderProgress()
        }
    }

    private fun human(b: Long) = Img.human(b)

    private fun fileCard(): View {
        val r = LinearLayout(this)
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = bg(Ui.CARD, 14, Ui.LINE, 1)
        r.setPadding(dp(12), dp(10), dp(12), dp(10))
        val tv = thumbView(44, 58)
        val t = thumb
        if (t != null) tv.setImageBitmap(t)
        r.addView(tv, lp(width = dp(44), height = dp(58), r = 12))
        val m = LinearLayout(this)
        m.orientation = LinearLayout.VERTICAL
        val nm = label(name, 14f, Ui.TEXT, true)
        nm.maxLines = 1
        nm.ellipsize = android.text.TextUtils.TruncateAt.END
        m.addView(nm)
        m.addView(label(human(size) + (if (pages > 0) " · " + bn(pages.toString()) + tr(" পেজ") else ""), 12f, Ui.MUTED), lp(t = 2))
        r.addView(m, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return r
    }

    // ------------------------------------------------------------------ step 1: choose file

    private fun renderChoose() {
        root.addView(pdfHeader(tr("PDF → ছবি"), tr("প্রতিটি পেজ ছবি হিসেবে সংরক্ষণ করুন"), { finish() }), lp())
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
        zone.setPadding(dp(16), dp(26), dp(16), dp(26))
        val ib = LinearLayout(this)
        ib.gravity = Gravity.CENTER
        ib.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.82f), 18)
        ib.addView(iconView(R.drawable.ic_folder, 34, Ui.PRIMARY))
        zone.addView(ib, lp(width = dp(70), height = dp(70)))
        val t = label(if (uri == null) tr("PDF ফাইল বাছুন") else tr("অন্য ফাইল বাছুন"), 17f, Ui.PRIMARY, true)
        t.gravity = Gravity.CENTER
        zone.addView(t, lp(t = 12))
        val s = label(tr("চেপে ফোন থেকে একটি PDF বাছুন"), 12f, Ui.MUTED)
        s.gravity = Gravity.CENTER
        zone.addView(s, lp(t = 4))
        zone.setOnClickListener { it.tapFx(); pick() }
        col.addView(zone, lp())

        if (uri != null) {
            col.addView(label(tr("বাছা ফাইল"), 14f, Ui.TEXT, true), lp(t = 18, b = 8, l = 2))
            col.addView(fileCard(), lp())
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        bar.addView(bigButton(tr("পরবর্তী →"), uri != null && pages > 0) { stage = 1; render() }, lp(height = dp(52)))
        root.addView(bottomBar(bar), lp())
    }

    private fun pick() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        startActivityForResult(i, 83)
    }

    private fun setFile(u: Uri) {
        uri = u
        name = Img.name(this, u)
        size = Img.size(this, u)
        pages = 0
        thumb = null
        src = null
        failed = null
        render()
        Thread {
            try {
                val f = File(cacheDir, "p2i_src.pdf")
                contentResolver.openInputStream(u)?.use { ins -> f.outputStream().use { o -> ins.copyTo(o) } }
                if (uri != u) return@Thread
                src = f
                pdfThumb(u, 160) { bmp, pg ->
                    if (uri != u) return@pdfThumb
                    thumb = bmp
                    pages = pg
                    if (pg <= 0) Toast.makeText(this, tr("এই PDF খোলা যায়নি (পাসওয়ার্ড দেওয়া থাকতে পারে)"), Toast.LENGTH_LONG).show()
                    if (stage == 0) render()
                }
            } catch (e: Throwable) {
                runOnUiThread { Toast.makeText(this, tr("ফাইল পড়া যায়নি"), Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    // ------------------------------------------------------------------ step 2: settings

    private fun selectBg(on: Boolean): GradientDrawable {
        val g = GradientDrawable()
        g.cornerRadius = dp(14).toFloat()
        g.setColor(if (on) Ui.mix(Ui.PRIMARY, Ui.CARD, 0.9f) else Ui.CARD)
        g.setStroke(if (on) dp(2) else dp(1), if (on) Ui.PRIMARY else Ui.LINE)
        return g
    }

    private fun tick(on: Boolean): View {
        val v = LinearLayout(this)
        v.gravity = Gravity.CENTER
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        if (on) g.setColor(Ui.PRIMARY) else { g.setColor(Color.TRANSPARENT); g.setStroke(dp(2), Ui.LINE) }
        v.background = g
        if (on) v.addView(label("✓", 12f, Color.WHITE, true))
        v.layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
        return v
    }

    private var summary: TextView? = null

    private fun inputField(hint: String, value: String, number: Boolean, onChange: (String) -> Unit): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setText(value)
        e.setTextColor(Ui.TEXT)
        e.setHintTextColor(Ui.MUTED)
        e.textSize = 15f
        e.inputType = if (number) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
        e.background = bg(Ui.CARD, 12, Ui.LINE, 1)
        e.setPadding(dp(14), dp(12), dp(14), dp(12))
        e.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { onChange(s?.toString() ?: ""); showSummary() }
        })
        return e
    }

    private fun parsedPages(): List<Int>? =
        if (allPages) (0 until pages).toList() else parsePageSpec(spec, pages)

    private fun showSummary() {
        val v = summary ?: return
        val p = parsedPages()
        v.text = if (p == null) tr("পেজ নম্বর ঠিক নেই (মোট ") + bn(pages.toString()) + tr(" পেজ)")
        else bn(p.size.toString()) + tr("টি ছবি তৈরি হবে")
        v.setTextColor(if (p == null) Ui.RED else Ui.PRIMARY)
    }

    private fun optionCard(title: String, sub: String, on: Boolean, tag: String = "", click: () -> Unit): View {
        val r = LinearLayout(this)
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = selectBg(on)
        r.setPadding(dp(14), dp(13), dp(14), dp(13))
        val tx = LinearLayout(this)
        tx.orientation = LinearLayout.VERTICAL
        val top = LinearLayout(this)
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(label(title, 14f, Ui.TEXT, true))
        if (tag.isNotEmpty()) {
            val tg = label(tag, 10f, Ui.PRIMARY, true)
            tg.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.85f), 8)
            tg.setPadding(dp(8), dp(2), dp(8), dp(2))
            top.addView(tg, lp(l = 8))
        }
        tx.addView(top)
        tx.addView(label(sub, 12f, Ui.MUTED), lp(t = 2))
        r.addView(tx, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(tick(on))
        r.setOnClickListener { it.tapFx(); click() }
        return r
    }

    private fun sectionTitle(t: String, top: Int = 0): View = label(t, 14f, Ui.TEXT, true).also { it.setPadding(dp(2), 0, 0, 0) }
        .let { v -> val w = LinearLayout(this); w.addView(v); w.layoutParams = lp(t = top, b = 8); w }

    private fun renderSettings() {
        root.addView(pdfHeader(tr("সেটিংস"), tr("ছবির ফরম্যাট ও মান ঠিক করুন"), { stage = 0; render() }), lp())
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(8), dp(16), dp(20))
        scroll.addView(col)

        col.addView(fileCard(), lp(b = 14))
        col.addView(sectionTitle(tr("ছবির ফরম্যাট")))
        col.addView(optionCard("PNG", tr("সেরা মান, লেখা পরিষ্কার থাকে"), png, tr("প্রস্তাবিত")) { png = true; render() }, lp(b = 8))
        col.addView(optionCard("JPG", tr("ছোট সাইজ, ছবিওয়ালা পেজের জন্য ভালো"), !png) { png = false; render() }, lp(b = 8))

        col.addView(sectionTitle(tr("ছবির মান (DPI)"), 10))
        val ds = listOf(100, 150, 200, 300)
        val dn = listOf(tr("দ্রুত, ছোট ছবি"), tr("মাঝারি, সাধারণ ব্যবহারের জন্য"), tr("ভালো, স্পষ্ট লেখা"), tr("সর্বোচ্চ, বড় ফাইল হবে"))
        for (k in ds.indices) col.addView(optionCard(bn(ds[k].toString()) + " DPI", dn[k], dpi == ds[k], if (ds[k] == 150) tr("প্রস্তাবিত") else "") { dpi = ds[k]; render() }, lp(b = 8))

        col.addView(sectionTitle(tr("কোন পেজগুলো"), 10))
        col.addView(optionCard(tr("সব পেজ"), tr("পুরো PDF-এর প্রতিটি পেজ"), allPages) { allPages = true; render() }, lp(b = 8))
        col.addView(optionCard(tr("নির্বাচিত পেজ"), tr("যেমন 1-3, 5, 8-"), !allPages) { allPages = false; render() }, lp(b = 8))
        if (!allPages) {
            val e = EditText(this)
            e.hint = tr("যেমন: 1-3, 5, 8-")
            e.setText(spec)
            e.setTextColor(Ui.TEXT)
            e.setHintTextColor(Ui.MUTED)
            e.textSize = 15f
            e.inputType = InputType.TYPE_CLASS_TEXT
            e.background = bg(Ui.CARD, 12, Ui.LINE, 1)
            e.setPadding(dp(14), dp(12), dp(14), dp(12))
            e.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { spec = s?.toString() ?: ""; showSummary() }
            })
            col.addView(e, lp())
        }

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.93f), 16, Ui.LINE, 1)
        box.setPadding(dp(14), dp(12), dp(14), dp(12))
        box.addView(label(tr("আউটপুট"), 12f, Ui.MUTED))
        val sm = label("", 15f, Ui.PRIMARY, true)
        summary = sm
        box.addView(sm, lp(t = 2))
        box.addView(label(tr("ফাইলের নাম হবে নাম_page1, নাম_page2…"), 11f, Ui.MUTED), lp(t = 4))
        col.addView(box, lp(t = 14))
        showSummary()

        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        bar.addView(bigButton(tr("ছবিতে রূপান্তর করুন")) { start() }, lp(height = dp(52)))
        root.addView(bottomBar(bar), lp())
    }

    // ------------------------------------------------------------------ step 3: progress / result

    private fun baseName() = name.removeSuffix(".pdf").removeSuffix(".PDF")

    private fun start() {
        if (parsedPages() == null) {
            Toast.makeText(this, tr("পেজ নম্বর ঠিক নেই (মোট ") + bn(pages.toString()) + tr(" পেজ)"), Toast.LENGTH_LONG).show()
            return
        }
        treeOrAsk(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 86)
    }

    private fun setProgress(p: Int, s: Int) {
        runOnUiThread {
            percent = p
            step = s
            if (stage == 2) render()
        }
    }

    private fun export(tree: Uri) {
        val list = parsedPages() ?: return
        stage = 2
        done = false
        failed = null
        percent = 0
        step = 0
        render()
        val f = src
        val usePng = png
        val scale = dpi / 72f
        val base = baseName()
        Thread {
            var made = 0
            var bytesTotal = 0L
            var err: String? = null
            try {
                if (f == null) throw IllegalStateException()
                setProgress(3, 0)
                val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                val r = PdfRenderer(pfd)
                try {
                    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                    for ((k, i) in list.withIndex()) {
                        setProgress(8 + 85 * k / list.size, 1)
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
                        setProgress(8 + 85 * k / list.size, 2)
                        val bytes = Img.encode(bmp, if (usePng) 0 else 1, 92)
                        bmp.recycle()
                        val nm = base + "_page" + (i + 1) + (if (usePng) ".png" else ".jpg")
                        val doc = DocumentsContract.createDocument(contentResolver, parent, if (usePng) "image/png" else "image/jpeg", nm)
                        if (doc != null) {
                            contentResolver.openOutputStream(doc)?.use { it.write(bytes) }
                            made++
                            bytesTotal += bytes.size
                        }
                    }
                } finally {
                    r.close()
                    pfd.close()
                }
                setProgress(96, 3)
                if (made == 0) throw IllegalStateException()
            } catch (e: Throwable) {
                err = tr("কাজটি করা যায়নি")
            }
            val ok = err == null
            runOnUiThread {
                if (ok) {
                    outCount = made
                    outSize = bytesTotal
                    done = true
                    percent = 100
                    Usage.done(this, "PdfToImages", bytesTotal, "")
                } else failed = err
                render()
            }
        }.start()
    }

    private fun renderProgress() {
        root.addView(pdfHeader(tr("PDF → ছবি"), if (done) tr("কাজ শেষ") else tr("একটু অপেক্ষা করুন"), if (done || failed != null) ({ finish() }) else null), lp())
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(24), dp(12), dp(24), dp(16))
        scroll.addView(col)

        val ring = PercentRing(this)
        ring.percent = if (done) 100 else percent
        col.addView(ring, lp(width = dp(170), height = dp(170), t = 12))
        val title = label(
            when {
                failed != null -> tr("রূপান্তর করা যায়নি")
                done -> tr("ছবি তৈরি হয়ে গেছে")
                else -> tr("ছবি তৈরি হচ্ছে…")
            }, 18f, if (failed != null) Ui.RED else Ui.PRIMARY, true
        )
        title.gravity = Gravity.CENTER
        col.addView(title, lp(t = 16))
        val sub = label(failed ?: if (done) tr("নিচে ফলাফল দেখুন") else tr("কয়েক সেকেন্ড লাগতে পারে"), 13f, Ui.MUTED)
        sub.gravity = Gravity.CENTER
        col.addView(sub, lp(t = 4))

        if (failed == null) {
            val steps = listOf(tr("ফাইল পড়া হচ্ছে"), tr("পেজ ছবি করা হচ্ছে"), tr("ছবি সংরক্ষণ হচ্ছে"), tr("চূড়ান্ত করা হচ্ছে"))
            val sc = LinearLayout(this)
            sc.orientation = LinearLayout.VERTICAL
            for ((k, n) in steps.withIndex()) {
                val r = LinearLayout(this)
                r.gravity = Gravity.CENTER_VERTICAL
                val state = if (done || k < step) 2 else if (k == step) 1 else 0
                val mark = label(if (state == 2) "✓" else if (state == 1) "●" else "○", 15f, if (state == 0) Ui.MUTED else Ui.PRIMARY, true)
                mark.gravity = Gravity.CENTER
                r.addView(mark, lp(width = dp(26)))
                r.addView(label(n, 14f, if (state == 0) Ui.MUTED else Ui.TEXT, state == 1))
                sc.addView(r, lp(t = 8))
            }
            col.addView(sc, lp(t = 18))
        }

        if (done) {
            val o = LinearLayout(this)
            o.orientation = LinearLayout.VERTICAL
            o.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.93f), 16, Ui.LINE, 1)
            o.setPadding(dp(14), dp(10), dp(14), dp(12))
            o.addView(label(tr("তৈরি ফাইল"), 11f, Ui.MUTED))
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            val ib = LinearLayout(this)
            ib.gravity = Gravity.CENTER
            ib.background = bg(Color.parseColor("#43A047"), 10)
            ib.addView(iconView(R.drawable.ic_image, 20, Color.WHITE))
            r.addView(ib, lp(width = dp(40), height = dp(40), r = 12))
            val m = LinearLayout(this)
            m.orientation = LinearLayout.VERTICAL
            val nm = label(bn(outCount.toString()) + tr("টি ছবি"), 14f, Ui.TEXT, true)
            nm.maxLines = 1
            nm.ellipsize = android.text.TextUtils.TruncateAt.END
            m.addView(nm)
            m.addView(label(human(outSize), 12f, Ui.MUTED))
            r.addView(m, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(label("✓", 18f, Ui.PRIMARY, true))
            o.addView(r, lp(t = 6))
            col.addView(o, lp(t = 20))
            col.addView(label(tr("ফাইলগুলো বাছা ফোল্ডারে সংরক্ষিত হয়েছে।"), 12f, Ui.MUTED).also { it.gravity = Gravity.CENTER }, lp(t = 10))
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        if (done || failed != null) {
            val bar = LinearLayout(this)
            bar.orientation = LinearLayout.VERTICAL
            if (done) {
                bar.addView(bigButton(tr("সম্পন্ন")) { finish() }, lp(height = dp(52)))
            } else {
                bar.addView(bigButton(tr("আবার চেষ্টা করুন")) { failed = null; stage = 1; render() }, lp(height = dp(52)))
            }
            root.addView(bottomBar(bar), lp())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return
        when (requestCode) {
            83 -> data.data?.let { setFile(it) }
            86 -> data.data?.let { export(it) }
        }
    }
}
