package com.example.callrec

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File

/** PDF জোড়া: join several PDFs, in the order shown, into one. */
class PdfMergeActivity : GuardedActivity() {
    private class Item(val uri: Uri, val name: String)

    private val items = ArrayList<Item>()
    private lateinit var listBox: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)

        val pick = card(14)
        pick.addView(pill(tr("PDF যোগ করুন")) {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "application/pdf"
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            startActivityForResult(i, 91)
        })
        pick.addView(label(tr("যে ক্রমে দেখানো হবে, সেই ক্রমে জোড়া লাগবে। ↑ ↓ দিয়ে ক্রম বদলান।"), 12f, Ui.MUTED), lp(t = 8))
        box.addView(pick, lp(b = 12))

        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        box.addView(listBox, lp(b = 12))

        val act = card(14)
        status = label("", 14f, Ui.TEXT)
        act.addView(status, lp(b = 10))
        act.addView(pill(tr("জোড়া দিয়ে নতুন PDF বানান")) { go() })
        box.addView(act)
        setScreen(tr("PDF জোড়া"), scroll)
        render()
    }

    private fun render() {
        listBox.removeAllViews()
        if (items.isEmpty()) {
            val e = label(tr("এখনো কোনো PDF যোগ করা হয়নি"), 13f, Ui.MUTED)
            e.gravity = Gravity.CENTER
            e.setPadding(0, dp(16), 0, dp(16))
            listBox.addView(e, lp())
            return
        }
        for ((i, it) in items.withIndex()) {
            val row = card(12)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.addView(label(bn((i + 1).toString()) + ".", 15f, Ui.MUTED, true), lp(width = dp(30)))
            row.addView(label(it.name, 14f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val up = label("↑", 20f, if (i > 0) Ui.PRIMARY else Ui.GREY, true)
            up.gravity = Gravity.CENTER
            up.setOnClickListener { if (i > 0) { items.add(i - 1, items.removeAt(i)); render() } }
            val down = label("↓", 20f, if (i < items.size - 1) Ui.PRIMARY else Ui.GREY, true)
            down.gravity = Gravity.CENTER
            down.setOnClickListener { if (i < items.size - 1) { items.add(i + 1, items.removeAt(i)); render() } }
            val x = label("✕", 16f, Ui.RED, true)
            x.gravity = Gravity.CENTER
            x.setOnClickListener { items.removeAt(i); render() }
            row.addView(up, lp(width = dp(38), height = dp(38)))
            row.addView(down, lp(width = dp(38), height = dp(38)))
            row.addView(x, lp(width = dp(38), height = dp(38)))
            listBox.addView(row, lp(b = 6))
        }
    }

    private fun go() {
        if (items.size < 2) {
            status.text = tr("কমপক্ষে ২টি PDF যোগ করুন")
            return
        }
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        i.putExtra(Intent.EXTRA_TITLE, "merged.pdf")
        saveOrAsk(i, 92)
    }

    private fun mergeInto(out: Uri) {
        status.text = tr("জোড়া হচ্ছে…")
        val snapshot = items.toList()
        Thread {
            val docs = ArrayList<PDDocument>()
            val msg = try {
                val dest = PDDocument()
                val util = PDFMergerUtility()
                var total = 0
                for ((i, it) in snapshot.withIndex()) {
                    val f = File(cacheDir, "merge_$i.pdf")
                    contentResolver.openInputStream(it.uri)?.use { ins -> f.outputStream().use { o -> ins.copyTo(o) } }
                    val d = PDDocument.load(f)
                    docs.add(d)
                    total += d.numberOfPages
                    util.appendDocument(dest, d)
                }
                contentResolver.openOutputStream(out)?.use { dest.save(it) }
                dest.close()
                tr("সংরক্ষিত হয়েছে: ") + bn(total.toString()) + tr(" পেজের PDF")
            } catch (e: InvalidPasswordException) {
                tr("কোনো PDF-এ পাসওয়ার্ড দেওয়া আছে, আগে আনলক করুন")
            } catch (e: Throwable) {
                tr("কাজটি করা যায়নি")
            }
            for (d in docs) try { d.close() } catch (e: Throwable) { }
            runOnUiThread { status.text = msg }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return
        if (requestCode == 91) {
            val cd = data.clipData
            if (cd != null) {
                for (k in 0 until cd.itemCount) addUri(cd.getItemAt(k).uri)
            } else data.data?.let { addUri(it) }
            render()
        } else if (requestCode == 92) {
            data.data?.let { mergeInto(it) }
        }
    }

    private fun addUri(u: Uri) {
        items.add(Item(u, Img.name(this, u)))
    }
}

/** পেজ সাজান: rotate, remove, reorder or reverse the pages of one PDF. */
class PdfOrganizeActivity : PdfBase() {
    private var mode = 0 // 0 rotate, 1 remove, 2 reorder, 3 reverse
    private var angle = 90
    private lateinit var modeRow: LinearLayout
    private lateinit var opt: LinearLayout
    private lateinit var spec: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        modeRow = LinearLayout(this)
        modeRow.orientation = LinearLayout.HORIZONTAL
        val mc = card(14)
        val hs = android.widget.HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(modeRow)
        mc.addView(hs)
        opt = LinearLayout(this)
        opt.orientation = LinearLayout.VERTICAL
        mc.addView(opt, lp(t = 12))
        val act = actionCard(tr("নতুন PDF বানান"), "") { go() }
        screen(tr("পেজ সাজান"), pickCard(), mc, act)
        spec = field("", InputType.TYPE_CLASS_TEXT)
        draw()
    }

    private fun draw() {
        modeRow.removeAllViews()
        val names = listOf(tr("ঘোরান"), tr("মুছুন"), tr("ক্রম বদলান"), tr("উল্টো করুন"))
        for (i in names.indices) modeRow.addView(chip(names[i], mode == i) { mode = i; draw() }, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, r = 8))
        opt.removeAllViews()
        spec = field("", InputType.TYPE_CLASS_TEXT)
        when (mode) {
            0 -> {
                opt.addView(label(tr("কোন কোন পেজ (খালি রাখলে সব পেজ)"), 12f, Ui.MUTED, true))
                spec.hint = tr("যেমন: 1-3, 5")
                opt.addView(spec)
                val r = LinearLayout(this)
                for (a in listOf(90, 180, 270)) r.addView(chip(bn(a.toString()) + "°", angle == a) { angle = a; draw() }, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, r = 8, t = 10))
                opt.addView(r)
                opt.addView(label(tr("ঘড়ির কাঁটার দিকে ঘুরবে।"), 12f, Ui.MUTED), lp(t = 6))
            }
            1 -> {
                opt.addView(label(tr("কোন কোন পেজ মুছবেন"), 12f, Ui.MUTED, true))
                spec.hint = tr("যেমন: 2, 4-6")
                opt.addView(spec)
            }
            2 -> {
                opt.addView(label(tr("নতুন ক্রম (সব পেজের নম্বর একবার করে)"), 12f, Ui.MUTED, true))
                spec.hint = tr("যেমন: 3, 1, 2, 4")
                opt.addView(spec)
                opt.addView(label(tr("যে ক্রমে লিখবেন, নতুন PDF-এ সেই ক্রমে পেজ থাকবে।"), 12f, Ui.MUTED), lp(t = 6))
            }
            else -> opt.addView(label(tr("সব পেজ শেষ থেকে শুরুর ক্রমে সাজানো হবে।"), 13f, Ui.MUTED))
        }
    }

    private fun go() {
        if (!requireFile()) return
        if (encrypted) { status.text = tr("আগে PDF আনলক করুন (PDF লক / আনলক টুল)"); return }
        val total = pages
        val text = spec.text.toString()
        var chosen: List<Int>? = null
        when (mode) {
            0 -> chosen = if (text.isBlank()) (0 until total).toList() else parsePageSpec(text, total)
            1 -> {
                chosen = parsePageSpec(text, total)
                if (chosen != null && chosen.size >= total) { status.text = tr("সব পেজ মুছে ফেলা যাবে না"); return }
            }
            2 -> {
                val order = ArrayList<Int>()
                for (p in asciiDigits(text).replace(" ", "").split(",")) {
                    val v = p.toIntOrNull()
                    if (v == null || v < 1 || v > total) { order.clear(); break }
                    order.add(v - 1)
                }
                chosen = if (order.size == total && order.toSet().size == total) order else null
            }
        }
        if (mode != 3 && chosen == null) {
            status.text = tr("পেজ নম্বর ঠিক নেই (মোট ") + bn(total.toString()) + tr(" পেজ)")
            return
        }
        val sel = chosen
        val m = mode
        val ang = angle
        askCreate(baseOut() + "_edited.pdf") { out ->
            val f = src!!
            work(tr("তৈরি হচ্ছে…")) {
                PDDocument.load(f).use { doc ->
                    when (m) {
                        0 -> {
                            for (i in sel!!) { val pg = doc.getPage(i); pg.rotation = (pg.rotation + ang) % 360 }
                            saveDoc(doc, out)
                        }
                        1 -> {
                            for (i in sel!!.sortedDescending()) doc.removePage(i)
                            saveDoc(doc, out)
                        }
                        else -> {
                            val order = if (m == 3) (total - 1 downTo 0).toList() else sel!!
                            val nd = PDDocument()
                            for (i in order) nd.importPage(doc.getPage(i))
                            saveDoc(nd, out)
                            nd.close()
                        }
                    }
                }
                tr("সংরক্ষিত হয়েছে")
            }
        }
    }
}

/** PDF ছোট করুন: turn each page into a JPEG picture at a lower quality to cut the file size. */
class PdfCompressActivity : PdfBase() {
    private var level = 1
    private lateinit var row: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val opt = card(14)
        opt.addView(label(tr("কতটা ছোট করবেন"), 12f, Ui.MUTED, true))
        row = LinearLayout(this)
        opt.addView(row, lp(t = 8))
        opt.addView(label(tr("পেজগুলো ছবি হয়ে যায়, তাই PDF-এর লেখা আর বাছা বা কপি করা যাবে না। স্ক্যান করা ও ছবিওয়ালা PDF-এ সবচেয়ে ভালো কাজ করে।"), 12f, Ui.MUTED), lp(t = 8))
        val act = actionCard(tr("ছোট করে নতুন PDF বানান"), "") { go() }
        screen(tr("PDF ছোট করুন"), pickCard(), opt, act)
        chips()
    }

    private fun chips() {
        row.removeAllViews()
        val names = listOf(tr("সামান্য"), tr("মাঝারি"), tr("অনেক"))
        for (i in names.indices) row.addView(chip(names[i], level == i) { level = i; chips() }, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, r = 8))
    }

    private fun go() {
        if (!requireFile()) return
        if (encrypted) { status.text = tr("আগে PDF আনলক করুন (PDF লক / আনলক টুল)"); return }
        val dpi = intArrayOf(130, 100, 72)[level]
        val q = floatArrayOf(0.75f, 0.6f, 0.45f)[level]
        askCreate(baseOut() + "_small.pdf") { out ->
            val f = src!!
            work(tr("ছোট করা হচ্ছে…")) {
                val tmp = File(cacheDir, "pdf_small.pdf")
                val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val nd = PDDocument()
                try {
                    for (i in 0 until renderer.pageCount) {
                        val pg = renderer.openPage(i)
                        val w = Math.max(1, Math.round(pg.width * dpi / 72f))
                        val h = Math.max(1, Math.round(pg.height * dpi / 72f))
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val page = PDPage(PDRectangle(pg.width.toFloat(), pg.height.toFloat()))
                        pg.close()
                        nd.addPage(page)
                        val img = JPEGFactory.createFromImage(nd, bmp, q)
                        PDPageContentStream(nd, page).use { cs -> cs.drawImage(img, 0f, 0f, page.mediaBox.width, page.mediaBox.height) }
                        bmp.recycle()
                    }
                    tmp.outputStream().use { nd.save(it) }
                } finally {
                    nd.close()
                    renderer.close()
                    pfd.close()
                }
                contentResolver.openOutputStream(out)?.use { o -> tmp.inputStream().use { it.copyTo(o) } }
                val before = f.length()
                val after = tmp.length()
                val line = tr("আগে: ") + Img.human(before) + tr("  →  এখন: ") + Img.human(after)
                if (after >= before) line + "\n" + tr("এই PDF আগেই ছোট ছিল, আসল ফাইলটাই রাখুন।") else line
            }
        }
    }
}

/** পেজ নম্বর, ওয়াটারমার্ক ও তথ্য: stamp page numbers or a watermark, or edit the PDF's info. */
class PdfStampActivity : PdfBase() {
    private var mode = 0 // 0 numbers, 1 watermark, 2 info
    private var pos = 1 // 0 left, 1 centre, 2 right
    private lateinit var modeRow: LinearLayout
    private lateinit var opt: LinearLayout
    private lateinit var f1: EditText
    private lateinit var f2: EditText
    private lateinit var f3: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        modeRow = LinearLayout(this)
        val mc = card(14)
        val hs = android.widget.HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(modeRow)
        mc.addView(hs)
        opt = LinearLayout(this)
        opt.orientation = LinearLayout.VERTICAL
        mc.addView(opt, lp(t = 12))
        val act = actionCard(tr("নতুন PDF বানান"), "") { go() }
        screen(tr("PDF স্ট্যাম্প ও তথ্য"), pickCard(), mc, act)
        draw()
    }

    private fun draw() {
        modeRow.removeAllViews()
        val names = listOf(tr("পেজ নম্বর"), tr("ওয়াটারমার্ক"), tr("PDF-এর তথ্য"))
        for (i in names.indices) modeRow.addView(chip(names[i], mode == i) { mode = i; draw() }, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, r = 8))
        opt.removeAllViews()
        f1 = field("", InputType.TYPE_CLASS_TEXT)
        f2 = field("", InputType.TYPE_CLASS_TEXT)
        f3 = field("", InputType.TYPE_CLASS_TEXT)
        when (mode) {
            0 -> {
                opt.addView(label(tr("নম্বর কোথায় বসবে"), 12f, Ui.MUTED, true))
                val r = LinearLayout(this)
                val names2 = listOf(tr("বাঁয়ে"), tr("মাঝে"), tr("ডানে"))
                for (i in names2.indices) r.addView(chip(names2[i], pos == i) { pos = i; draw() }, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, r = 8, t = 8))
                opt.addView(r)
                f1.hint = tr("কত নম্বর থেকে শুরু (ঐচ্ছিক)")
                f1.inputType = InputType.TYPE_CLASS_NUMBER
                opt.addView(f1, lp(t = 8))
                opt.addView(label(tr("নম্বর নিচের মাঝে বা কোণে \"১ / ১০\" ধাঁচে বসবে (সংখ্যা ইংরেজিতে)।"), 12f, Ui.MUTED), lp(t = 6))
            }
            1 -> {
                opt.addView(label(tr("ওয়াটারমার্কের লেখা"), 12f, Ui.MUTED, true))
                f1.hint = tr("যেমন: CONFIDENTIAL")
                opt.addView(f1)
                opt.addView(label(tr("লেখা ফিকে হয়ে কোনাকুনি সব পেজে বসবে। শুধু ইংরেজি অক্ষর ও সংখ্যা চলবে।"), 12f, Ui.MUTED), lp(t = 6))
            }
            else -> {
                opt.addView(label(tr("শিরোনাম, লেখক ও বিষয় বদলান। সব খালি রেখে সংরক্ষণ করলে এই তথ্য মুছে যাবে।"), 12f, Ui.MUTED))
                f1.hint = tr("শিরোনাম")
                f2.hint = tr("লেখক")
                f3.hint = tr("বিষয়")
                opt.addView(f1, lp(t = 6)); opt.addView(f2); opt.addView(f3)
            }
        }
        prefill()
    }

    override fun onLoaded() { prefill() }

    private fun prefill() {
        if (mode != 2) return
        val f = src ?: return
        try {
            PDDocument.load(f).use { d ->
                f1.setText(d.documentInformation.title ?: "")
                f2.setText(d.documentInformation.author ?: "")
                f3.setText(d.documentInformation.subject ?: "")
            }
        } catch (e: Throwable) { }
    }

    private fun latinOnly(s: String): Boolean = s.all { it.code in 32..126 }

    private fun go() {
        if (!requireFile()) return
        if (encrypted) { status.text = tr("আগে PDF আনলক করুন (PDF লক / আনলক টুল)"); return }
        val m = mode
        val p = pos
        val t1 = f1.text.toString().trim()
        val t2 = f2.text.toString().trim()
        val t3 = f3.text.toString().trim()
        if (m == 1) {
            if (t1.isEmpty()) { status.text = tr("ওয়াটারমার্কের লেখা দিন"); return }
            if (!latinOnly(t1)) { status.text = tr("ওয়াটারমার্কে শুধু ইংরেজি অক্ষর ও সংখ্যা চলবে"); return }
        }
        val startNo = (asciiDigits(t1).toIntOrNull() ?: 1).coerceAtLeast(0)
        askCreate(baseOut() + (if (m == 0) "_numbered.pdf" else if (m == 1) "_marked.pdf" else "_info.pdf")) { out ->
            val f = src!!
            work(tr("তৈরি হচ্ছে…")) {
                PDDocument.load(f).use { doc ->
                    val n = doc.numberOfPages
                    when (m) {
                        0 -> {
                            val font = PDType1Font.HELVETICA
                            for (i in 0 until n) {
                                val page = doc.getPage(i)
                                val box = page.mediaBox
                                val text = (startNo + i).toString() + " / " + (startNo + n - 1).toString()
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
                            info.title = if (t1.isEmpty()) null else t1
                            info.author = if (t2.isEmpty()) null else t2
                            info.subject = if (t3.isEmpty()) null else t3
                            info.keywords = null
                            info.creator = null
                            info.producer = null
                            doc.documentInformation = info
                        }
                    }
                    saveDoc(doc, out)
                }
                tr("সংরক্ষিত হয়েছে")
            }
        }
    }
}
