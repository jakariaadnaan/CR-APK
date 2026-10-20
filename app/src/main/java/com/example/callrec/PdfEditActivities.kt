package com.example.callrec

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File

/** Shared base for the PDF tools that open one PDF with the PDFBox library. */
abstract class PdfBase : GuardedActivity() {

    protected var src: File? = null
    protected var pdfName = "document"
    protected var pages = 0
    protected var encrypted = false

    protected lateinit var info: TextView
    protected lateinit var status: TextView
    private var onCreateDoc: ((Uri) -> Unit)? = null
    private var onTree: ((Uri) -> Unit)? = null

    protected open fun onLoaded() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
    }

    protected fun pickCard(): LinearLayout {
        val c = card(14)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = android.view.Gravity.CENTER_VERTICAL
        row.addView(pill(tr("PDF বাছুন")) {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "application/pdf"
            startActivityForResult(i, 85)
        }, lp(r = 12))
        info = label(tr("কোনো PDF বাছা হয়নি"), 13f, Ui.MUTED)
        row.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(row, lp())
        return c
    }

    protected fun chip(text: String, on: Boolean, click: () -> Unit): TextView {
        val c = label(text, 14f, if (on) Color.WHITE else Ui.PRIMARY, true)
        c.gravity = android.view.Gravity.CENTER
        c.background = bg(if (on) Ui.PRIMARY else Ui.CARD, 20, Ui.PRIMARY, 1)
        c.setPadding(dp(16), dp(8), dp(16), dp(8))
        c.setOnClickListener { click() }
        return c
    }

    protected fun field(hint: String, type: Int): EditText {
        val e = EditText(this)
        e.hint = hint
        e.inputType = type
        e.setTextColor(Ui.TEXT)
        return e
    }

    protected fun requireFile(): Boolean {
        if (src == null) {
            Toast.makeText(this, tr("আগে একটি PDF বাছুন"), Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    protected fun askCreate(name: String, then: (Uri) -> Unit) {
        onCreateDoc = then
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        i.putExtra(Intent.EXTRA_TITLE, name)
        defaultFolderUri(this)?.let { i.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, it) }
        startActivityForResult(i, 86)
    }

    protected fun askTree(then: (Uri) -> Unit) {
        onTree = then
        val ti = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        defaultFolderUri(this)?.let { ti.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, it) }
        startActivityForResult(ti, 87)
    }

    /** Runs [block] off the main thread; it returns the message to show, or throws. */
    private var lastOut: Uri? = null

    protected fun work(msg: String, block: () -> String) {
        status.text = msg
        Thread {
            var ok = true
            val res = try {
                block()
            } catch (e: InvalidPasswordException) {
                ok = false
                tr("পাসওয়ার্ড ভুল বা PDF লক করা")
            } catch (e: Throwable) {
                ok = false
                tr("কাজটি করা যায়নি")
            }
            if (ok) {
                var size = 0L
                try { lastOut?.let { u -> contentResolver.openFileDescriptor(u, "r")?.use { size = it.statSize } } } catch (e: Throwable) { }
                Usage.done(this, javaClass.simpleName, size, pdfName + ".pdf")
            }
            runOnUiThread { status.text = res; actionBuzz() }
        }.start()
    }

    protected fun baseOut(): String = pdfName

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val u = data.data!!
        when (requestCode) {
            85 -> load(u)
            86 -> { lastOut = u; onCreateDoc?.invoke(u) }
            87 -> { lastOut = null; onTree?.invoke(u) }
        }
    }

    private fun load(u: Uri) {
        info.text = tr("খোলা হচ্ছে…")
        Thread {
            var n = 0
            var enc = false
            var f: File? = File(cacheDir, "pdf_in.pdf")
            try {
                contentResolver.openInputStream(u)?.use { ins -> f!!.outputStream().use { ins.copyTo(it) } }
                PDDocument.load(f).use { n = it.numberOfPages }
            } catch (e: InvalidPasswordException) {
                enc = true
            } catch (e: Throwable) {
                f = null
            }
            val name = Img.name(this, u)
            val nn = n
            val ee = enc
            val ff = f
            runOnUiThread {
                if (ff == null) {
                    info.text = tr("কোনো PDF বাছা হয়নি")
                    Toast.makeText(this, tr("PDF খোলা যায়নি"), Toast.LENGTH_LONG).show()
                } else {
                    src = ff
                    encrypted = ee
                    pages = nn
                    pdfName = if (name.lowercase().endsWith(".pdf")) name.substring(0, name.length - 4) else name
                    info.text = name + (if (ee) tr(" · পাসওয়ার্ড দেওয়া") else " · " + bn(nn.toString()) + tr(" পেজ"))
                    status.text = ""
                    onLoaded()
                }
            }
        }.start()
    }

    protected fun saveDoc(doc: PDDocument, out: Uri) {
        contentResolver.openOutputStream(out)?.use { doc.save(it) }
    }

    protected fun screen(title: String, vararg cards: android.view.View) {
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)
        for (c in cards) box.addView(c, lp(b = 12))
        setScreen(title, scroll)
    }

    protected fun actionCard(btn: String, hintText: String, click: () -> Unit): LinearLayout {
        val res = card(14)
        status = label("", 14f, Ui.TEXT)
        res.addView(status, lp(b = 10))
        res.addView(pill(btn) { click() })
        if (hintText.isNotEmpty()) res.addView(label(hintText, 12f, Ui.MUTED), lp(t = 8))
        return res
    }
}

/** Turns "1-3, 5, 8-" into zero-based page indexes, or null when the text makes no sense. */
fun parsePageSpec(spec: String, total: Int): List<Int>? {
    val out = ArrayList<Int>()
    val s = asciiDigits(spec).replace(" ", "")
    if (s.isEmpty()) return null
    for (part in s.split(",")) {
        if (part.isEmpty()) continue
        val a: Int
        val b: Int
        if (part.contains("-")) {
            val x = part.split("-")
            if (x.size != 2) return null
            a = if (x[0].isEmpty()) 1 else (x[0].toIntOrNull() ?: return null)
            b = if (x[1].isEmpty()) total else (x[1].toIntOrNull() ?: return null)
        } else {
            a = part.toIntOrNull() ?: return null
            b = a
        }
        if (a < 1 || b < a || b > total) return null
        for (p in a..b) if (!out.contains(p - 1)) out.add(p - 1)
    }
    return if (out.isEmpty()) null else out
}

/** PDF ভাগ: cut a PDF into parts of N pages each. */
class PdfSplitActivity : PdfBase() {
    private lateinit var count: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val opt = card(14)
        opt.addView(label(tr("প্রতি কত পেজে একটি ফাইল"), 12f, Ui.MUTED, true))
        count = field(tr("যেমন: ১"), InputType.TYPE_CLASS_NUMBER)
        count.setText("1")
        opt.addView(count)
        val act = actionCard(tr("ফোল্ডার বেছে ভাগ করুন"), tr("ফাইলের নাম হবে নাম_part1.pdf, নাম_part2.pdf…")) { go() }
        screen(tr("PDF ভাগ"), pickCard(), opt, act)
    }

    private fun go() {
        if (!requireFile()) return
        if (encrypted) { status.text = tr("আগে PDF আনলক করুন (PDF লক / আনলক টুল)"); return }
        val n = asciiDigits(count.text.toString()).toIntOrNull()
        if (n == null || n < 1) { status.text = tr("পেজ সংখ্যা ঠিক নেই"); return }
        askTree { tree ->
            val f = src!!
            val base = baseOut()
            work(tr("ভাগ হচ্ছে…")) {
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                var made = 0
                PDDocument.load(f).use { doc ->
                    val sp = Splitter()
                    sp.setSplitAtPage(n)
                    val parts = sp.split(doc)
                    for ((i, p) in parts.withIndex()) {
                        val u = DocumentsContract.createDocument(contentResolver, parent, "application/pdf", base + "_part" + (i + 1) + ".pdf")
                        if (u != null) {
                            saveDoc(p, u)
                            made++
                        }
                        p.close()
                    }
                }
                tr("সংরক্ষিত হয়েছে: ") + bn(made.toString()) + tr("টি ফাইল")
            }
        }
    }
}

/** পেজ এক্সট্র্যাক্টর: choose pages like "1-3, 5, 8-" and save them as a new PDF. */
class PdfExtractActivity : PdfBase() {
    private lateinit var spec: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val opt = card(14)
        opt.addView(label(tr("কোন কোন পেজ চান"), 12f, Ui.MUTED, true))
        spec = field(tr("যেমন: 1-3, 5, 8-"), InputType.TYPE_CLASS_TEXT)
        opt.addView(spec)
        opt.addView(label(tr("কমা দিয়ে আলাদা করুন। 8- মানে ৮ থেকে শেষ পর্যন্ত।"), 12f, Ui.MUTED), lp(t = 6))
        val act = actionCard(tr("নতুন PDF বানান"), "") { go() }
        screen(tr("পেজ এক্সট্র্যাক্টর"), pickCard(), opt, act)
    }

    private fun go() {
        if (!requireFile()) return
        if (encrypted) { status.text = tr("আগে PDF আনলক করুন (PDF লক / আনলক টুল)"); return }
        val idx = parsePageSpec(spec.text.toString(), pages)
        if (idx == null) { status.text = tr("পেজ নম্বর ঠিক নেই (মোট ") + bn(pages.toString()) + tr(" পেজ)"); return }
        askCreate(baseOut() + "_pages.pdf") { out ->
            val f = src!!
            work(tr("তৈরি হচ্ছে…")) {
                PDDocument.load(f).use { doc ->
                    val nd = PDDocument()
                    for (i in idx) nd.importPage(doc.getPage(i))
                    saveDoc(nd, out)
                    nd.close()
                }
                tr("সংরক্ষিত হয়েছে: ") + bn(idx.size.toString()) + tr(" পেজের PDF")
            }
        }
    }
}

/** PDF লক / আনলক: add a password to a PDF, or remove it. */
class PdfLockActivity : PdfBase() {
    private var lock = true
    private lateinit var row: LinearLayout
    private lateinit var pw: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val opt = card(14)
        row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        opt.addView(row, lp(b = 10))
        opt.addView(label(tr("পাসওয়ার্ড"), 12f, Ui.MUTED, true))
        pw = field(tr("পাসওয়ার্ড লিখুন"), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        opt.addView(pw)
        opt.addView(label(tr("পাসওয়ার্ড ভুলে গেলে PDF খোলা যাবে না, তাই মনে রাখুন।"), 12f, Ui.MUTED), lp(t = 6))
        val act = actionCard(tr("নতুন PDF সংরক্ষণ"), "") { go() }
        screen(tr("PDF লক / আনলক"), pickCard(), opt, act)
        chips()
    }

    private fun chips() {
        row.removeAllViews()
        row.addView(chip(tr("লক করুন"), lock) { lock = true; chips() }, lp(r = 8))
        row.addView(chip(tr("আনলক করুন"), !lock) { lock = false; chips() })
    }

    private fun go() {
        if (!requireFile()) return
        val p = pw.text.toString()
        if (p.isEmpty()) { status.text = tr("পাসওয়ার্ড লিখুন"); return }
        val doLock = lock
        askCreate(baseOut() + (if (doLock) "_locked.pdf" else "_unlocked.pdf")) { out ->
            val f = src!!
            work(if (doLock) tr("লক করা হচ্ছে…") else tr("আনলক করা হচ্ছে…")) {
                if (doLock) {
                    PDDocument.load(f).use { doc ->
                        val policy = StandardProtectionPolicy(p, p, AccessPermission())
                        policy.encryptionKeyLength = 128
                        doc.protect(policy)
                        saveDoc(doc, out)
                    }
                    tr("লক করা PDF সংরক্ষিত হয়েছে")
                } else {
                    PDDocument.load(f, p).use { doc ->
                        doc.isAllSecurityToBeRemoved = true
                        saveDoc(doc, out)
                    }
                    tr("আনলক করা PDF সংরক্ষিত হয়েছে")
                }
            }
        }
    }
}

/** The folder chosen under Settings > Default Folder, used as the starting point of save pickers. */
fun defaultFolderUri(ctx: android.content.Context): Uri? {
    val s = ctx.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getString("defFolder", null) ?: return null
    return try { Uri.parse(s) } catch (e: Throwable) { null }
}
