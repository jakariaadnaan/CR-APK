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
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File

import android.provider.DocumentsContract
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
/** পেজ নম্বর, ওয়াটারমার্ক ও তথ্য, step by step: choose file, pick what to add, process, result. */
class PdfStampActivity : GuardedActivity() {

    private var stage = 0 // 0 choose, 1 settings, 2 progress / result
    private lateinit var root: LinearLayout

    private var uri: Uri? = null
    private var name = ""
    private var size = 0L
    private var pages = 0
    private var thumb: Bitmap? = null
    private var src: File? = null

    private var mode = 0 // 0 page numbers, 1 watermark, 2 info
    private var pos = 1 // 0 left, 1 centre, 2 right
    private var startNo = ""
    private var mark = ""
    private var infoTitle = ""
    private var infoAuthor = ""
    private var infoSubject = ""

    private var percent = 0
    private var step = 0
    private var done = false
    private var failed: String? = null
    private var outUri: Uri? = null
    private var outName = ""
    private var outPages = 0
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
        root.addView(pdfHeader(tr("PDF স্ট্যাম্প ও তথ্য"), tr("পেজ নম্বর, ওয়াটারমার্ক বা PDF-এর তথ্য"), { finish() }), lp())
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
                val f = File(cacheDir, "stamp_src.pdf")
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

    private fun latinOnly(s: String): Boolean = s.all { it.code in 32..126 }

    private fun problem(): String? = when (mode) {
        1 -> if (mark.trim().isEmpty()) tr("ওয়াটারমার্কের লেখা দিন")
             else if (!latinOnly(mark.trim())) tr("ওয়াটারমার্কে শুধু ইংরেজি অক্ষর ও সংখ্যা চলবে") else null
        else -> null
    }

    private fun showSummary() {
        val v = summary ?: return
        val p = problem()
        v.text = p ?: when (mode) {
            0 -> tr("সব ") + bn(pages.toString()) + tr(" পেজের নিচে নম্বর বসবে")
            1 -> tr("সব ") + bn(pages.toString()) + tr(" পেজে আড়াআড়ি ওয়াটারমার্ক বসবে")
            else -> tr("PDF-এর তথ্য বদলানো হবে")
        }
        v.setTextColor(if (p != null) Ui.RED else Ui.PRIMARY)
    }

    private fun optionCard(title: String, sub: String, on: Boolean, click: () -> Unit): View {
        val r = LinearLayout(this)
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = selectBg(on)
        r.setPadding(dp(14), dp(13), dp(14), dp(13))
        val tx = LinearLayout(this)
        tx.orientation = LinearLayout.VERTICAL
        tx.addView(label(title, 14f, Ui.TEXT, true))
        tx.addView(label(sub, 12f, Ui.MUTED), lp(t = 2))
        r.addView(tx, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(tick(on))
        r.setOnClickListener { it.tapFx(); click() }
        return r
    }

    private fun sectionTitle(t: String, top: Int = 0): View {
        val w = LinearLayout(this)
        w.addView(label(t, 14f, Ui.TEXT, true).also { it.setPadding(dp(2), 0, 0, 0) })
        w.layoutParams = lp(t = top, b = 8)
        return w
    }

    private fun textField(hint: String, value: String, number: Boolean, onChange: (String) -> Unit): View {
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

    private fun renderSettings() {
        root.addView(pdfHeader(tr("সেটিংস"), tr("কী যোগ বা বদল করবেন ঠিক করুন"), { stage = 0; render() }), lp())
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(8), dp(16), dp(20))
        scroll.addView(col)

        col.addView(fileCard(), lp(b = 14))
        col.addView(sectionTitle(tr("কাজের ধরন")))
        val names = listOf(tr("পেজ নম্বর"), tr("ওয়াটারমার্ক"), tr("PDF-এর তথ্য"))
        val subs = listOf(tr("প্রতিটি পেজের নিচে \"১ / ১০\" ধাঁচে নম্বর"), tr("সব পেজে হালকা আড়াআড়ি লেখা"), tr("শিরোনাম, লেখক ও বিষয় বদলান"))
        for (k in 0..2) col.addView(optionCard(names[k], subs[k], mode == k) { mode = k; render() }, lp(b = 8))

        when (mode) {
            0 -> {
                col.addView(sectionTitle(tr("নম্বর কোথায় বসবে"), 10))
                val pr = LinearLayout(this)
                for ((k, n) in listOf(tr("বাঁয়ে"), tr("মাঝে"), tr("ডানে")).withIndex()) {
                    val on = pos == k
                    val t = LinearLayout(this)
                    t.gravity = Gravity.CENTER
                    t.background = selectBg(on)
                    t.setPadding(dp(4), dp(14), dp(4), dp(14))
                    t.addView(label(n, 14f, if (on) Ui.PRIMARY else Ui.TEXT, true))
                    t.setOnClickListener { _ -> pos = k; render() }
                    pr.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { p -> p.setMargins(if (k == 0) 0 else dp(4), 0, if (k == 2) 0 else dp(4), 0) })
                }
                col.addView(pr, lp())
                col.addView(sectionTitle(tr("কত নম্বর থেকে শুরু (ঐচ্ছিক)"), 12))
                col.addView(textField("1", startNo, true) { startNo = it }, lp())
                col.addView(label(tr("নম্বর সংখ্যা ইংরেজিতে বসবে।"), 11f, Ui.MUTED), lp(l = 2, t = 6))
            }
            1 -> {
                col.addView(sectionTitle(tr("ওয়াটারমার্কের লেখা"), 10))
                col.addView(textField(tr("যেমন: CONFIDENTIAL"), mark, false) { mark = it }, lp())
                col.addView(label(tr("শুধু ইংরেজি অক্ষর ও সংখ্যা চলবে।"), 11f, Ui.MUTED), lp(l = 2, t = 6))
            }
            else -> {
                col.addView(sectionTitle(tr("শিরোনাম"), 10))
                col.addView(textField(tr("শিরোনাম"), infoTitle, false) { infoTitle = it }, lp())
                col.addView(sectionTitle(tr("লেখক"), 10))
                col.addView(textField(tr("লেখক"), infoAuthor, false) { infoAuthor = it }, lp())
                col.addView(sectionTitle(tr("বিষয়"), 10))
                col.addView(textField(tr("বিষয়"), infoSubject, false) { infoSubject = it }, lp())
                col.addView(label(tr("কীওয়ার্ড, তৈরির অ্যাপের নাম ইত্যাদি তথ্য মুছে যাবে।"), 11f, Ui.MUTED), lp(l = 2, t = 6))
            }
        }

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.93f), 16, Ui.LINE, 1)
        box.setPadding(dp(14), dp(12), dp(14), dp(12))
        box.addView(label(tr("ফলাফল"), 12f, Ui.MUTED))
        val sm = label("", 14f, Ui.PRIMARY, true)
        summary = sm
        box.addView(sm, lp(t = 2))
        col.addView(box, lp(t = 14))
        showSummary()

        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        bar.addView(bigButton(tr("নতুন PDF বানান")) { start() }, lp(height = dp(52)))
        root.addView(bottomBar(bar), lp())
    }

    // ------------------------------------------------------------------ step 3: progress / result

    private fun baseName() = name.removeSuffix(".pdf").removeSuffix(".PDF")

    private fun start() {
        val p = problem()
        if (p != null) { Toast.makeText(this, p, Toast.LENGTH_LONG).show(); return }
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        i.putExtra(Intent.EXTRA_TITLE, baseName() + (if (mode == 0) "_numbered.pdf" else if (mode == 1) "_marked.pdf" else "_info.pdf"))
        saveOrAsk(i, 85)
    }

    private fun setProgress(p: Int, s: Int) {
        runOnUiThread {
            percent = p
            step = s
            if (stage == 2) render()
        }
    }

    private fun process(out: Uri) {
        stage = 2
        done = false
        failed = null
        percent = 0
        step = 0
        outUri = out
        render()
        val f = src
        val m = mode
        val p = pos
        val t1 = mark.trim()
        val first = (asciiDigits(startNo).toIntOrNull() ?: 1).coerceAtLeast(0)
        val tt = infoTitle.trim()
        val ta = infoAuthor.trim()
        val ts = infoSubject.trim()
        Thread {
            var err: String? = null
            var n = 0
            try {
                if (f == null) throw IllegalStateException()
                setProgress(10, 0)
                PDDocument.load(f).use { doc ->
                    n = doc.numberOfPages
                    setProgress(35, 1)
                    when (m) {
                        0 -> {
                            val font = PDType1Font.HELVETICA
                            for (i in 0 until n) {
                                if (i % 5 == 0) setProgress(35 + 40 * i / n, 2)
                                val page = doc.getPage(i)
                                val box = page.mediaBox
                                val text = (first + i).toString() + " / " + (first + n - 1).toString()
                                val tw = font.getStringWidth(text) / 1000f * 11f
                                val x = when (p) { 0 -> 28f; 1 -> (box.width - tw) / 2f; else -> box.width - 28f - tw }
                                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                                    cs.beginText()
                                    cs.setFont(font, 11f)
                                    cs.setNonStrokingColor(70, 70, 70)
                                    cs.newLineAtOffset(box.lowerLeftX + x, box.lowerLeftY + 20f)
                                    cs.showText(text)
                                    cs.endText()
                                }
                            }
                        }
                        1 -> {
                            val font = PDType1Font.HELVETICA_BOLD
                            val gs = PDExtendedGraphicsState()
                            gs.nonStrokingAlphaConstant = 0.22f
                            for (i in 0 until n) {
                                if (i % 5 == 0) setProgress(35 + 40 * i / n, 2)
                                val page = doc.getPage(i)
                                val box = page.mediaBox
                                val size = Math.min(80f, Math.max(24f, box.width * 1.1f / Math.max(4, t1.length) * 1.4f))
                                val tw = font.getStringWidth(t1) / 1000f * size
                                val ang = Math.toRadians(45.0)
                                val cx = box.lowerLeftX + box.width / 2f
                                val cy = box.lowerLeftY + box.height / 2f
                                val ox = (cx - (tw / 2f) * Math.cos(ang) + (size / 3f) * Math.sin(ang)).toFloat()
                                val oy = (cy - (tw / 2f) * Math.sin(ang) - (size / 3f) * Math.cos(ang)).toFloat()
                                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                                    cs.setGraphicsStateParameters(gs)
                                    cs.beginText()
                                    cs.setFont(font, size)
                                    cs.setNonStrokingColor(90, 90, 90)
                                    cs.setTextMatrix(Matrix.getRotateInstance(ang, ox, oy))
                                    cs.showText(t1)
                                    cs.endText()
                                }
                            }
                        }
                        else -> {
                            val info = doc.documentInformation
                            info.title = if (tt.isEmpty()) null else tt
                            info.author = if (ta.isEmpty()) null else ta
                            info.subject = if (ts.isEmpty()) null else ts
                            info.keywords = null
                            info.creator = null
                            info.producer = null
                            doc.documentInformation = info
                        }
                    }
                    setProgress(80, 2)
                    contentResolver.openOutputStream(out)?.use { doc.save(it) }
                }
                setProgress(95, 3)
            } catch (e: Throwable) {
                err = tr("কাজটি করা যায়নি")
            }
            val ok = err == null
            runOnUiThread {
                if (ok) {
                    outPages = n
                    outSize = Img.size(this, out)
                    outName = Img.name(this, out)
                    done = true
                    percent = 100
                    Usage.done(this, "PdfStampActivity", outSize, outName)
                } else failed = err
                render()
            }
        }.start()
    }

    private fun renderProgress() {
        root.addView(pdfHeader(tr("PDF স্ট্যাম্প ও তথ্য"), if (done) tr("কাজ শেষ") else tr("একটু অপেক্ষা করুন"), if (done || failed != null) ({ finish() }) else null), lp())
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
                failed != null -> tr("কাজটি করা যায়নি")
                done -> tr("কাজ হয়ে গেছে")
                else -> tr("তৈরি হচ্ছে…")
            }, 18f, if (failed != null) Ui.RED else Ui.PRIMARY, true
        )
        title.gravity = Gravity.CENTER
        col.addView(title, lp(t = 16))
        val sub = label(failed ?: if (done) tr("নিচে তৈরি ফাইলটি দেখুন") else tr("কয়েক সেকেন্ড লাগতে পারে"), 13f, Ui.MUTED)
        sub.gravity = Gravity.CENTER
        col.addView(sub, lp(t = 4))

        if (failed == null) {
            val steps = listOf(tr("ফাইল পড়া হচ্ছে"), tr("পেজে যোগ করা হচ্ছে"), tr("নতুন PDF বানানো হচ্ছে"), tr("চূড়ান্ত করা হচ্ছে"))
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
            ib.background = bg(Color.parseColor("#E53935"), 10)
            ib.addView(iconView(R.drawable.ic_pdf, 20, Color.WHITE))
            r.addView(ib, lp(width = dp(40), height = dp(40), r = 12))
            val m = LinearLayout(this)
            m.orientation = LinearLayout.VERTICAL
            val nm = label(outName, 14f, Ui.TEXT, true)
            nm.maxLines = 1
            nm.ellipsize = android.text.TextUtils.TruncateAt.END
            m.addView(nm)
            m.addView(label(human(outSize) + " · " + bn(outPages.toString()) + tr(" পেজ"), 12f, Ui.MUTED))
            r.addView(m, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(label("✓", 18f, Ui.PRIMARY, true))
            o.addView(r, lp(t = 6))
            col.addView(o, lp(t = 20))
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        if (done || failed != null) {
            val bar = LinearLayout(this)
            bar.orientation = LinearLayout.VERTICAL
            if (done) {
                bar.addView(bigButton(tr("ফাইল দেখুন")) { viewFile() }, lp(height = dp(52), b = 8))
                bar.addView(outlineButton(tr("সম্পন্ন")) { finish() }, lp(height = dp(48)))
            } else {
                bar.addView(bigButton(tr("আবার চেষ্টা করুন")) { failed = null; stage = 1; render() }, lp(height = dp(52)))
            }
            root.addView(bottomBar(bar), lp())
        }
    }

    private fun viewFile() {
        val u = outUri ?: return
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/pdf")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: Throwable) {
            Toast.makeText(this, tr("PDF দেখার কোনো অ্যাপ পাওয়া যায়নি"), Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return
        when (requestCode) {
            83 -> data.data?.let { setFile(it) }
            85 -> data.data?.let { process(it) }
        }
    }
}
