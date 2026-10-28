package com.memoredia.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.tom_roush.pdfbox.cos.COSName
import android.widget.LinearLayout
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File

/** PDF থেকে ছবি: look at the pictures inside a PDF, pick some or all, and save them into a folder. */
class PdfImagesActivity : PdfBase() {
    private class Found(val page: Int, val name: String, val w: Int, val h: Int, val thumb: Bitmap, var on: Boolean = true)

    private val found = ArrayList<Found>()
    private lateinit var head: TextView
    private lateinit var grid: LinearLayout
    private lateinit var sum: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val act = actionCard(tr("নির্বাচিত ছবি সংরক্ষণ"), tr("আসল ছবিগুলো আলাদা JPG ফাইল হবে। খুব ছোট ছবি বাদ যায়।")) { go() }
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val hr = LinearLayout(this)
        hr.gravity = android.view.Gravity.CENTER_VERTICAL
        head = label(tr("কোনো PDF বাছা হয়নি"), 14f, Ui.TEXT, true)
        hr.addView(head, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val all = label(tr("সব বাছুন"), 12f, Ui.PRIMARY, true)
        all.setPadding(dp(10), dp(6), dp(2), dp(6))
        all.setOnClickListener {
            val on = found.any { !it.on }
            for (f in found) f.on = on
            draw()
        }
        hr.addView(all)
        box.addView(hr, lp(b = 8))
        grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        box.addView(grid)
        val sc = LinearLayout(this)
        sc.orientation = LinearLayout.VERTICAL
        sc.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.9f), Ui.cardR, Ui.mix(Ui.PRIMARY, Ui.CARD, 0.6f), 1)
        sc.setPadding(dp(14), dp(12), dp(14), dp(12))
        sum = label("", 13f, Ui.TEXT, true)
        sc.addView(sum)
        sc.addView(label(tr("আসল ছবির মান অক্ষুণ্ণ থাকে"), 11f, Ui.MUTED), lp(t = 2))
        box.addView(sc, lp(t = 10))
        screen(tr("PDF থেকে ছবি"), pickCard(), box, act)
    }

    override fun onLoaded() {
        found.clear()
        draw()
        if (encrypted) { head.text = tr("আগে PDF আনলক করুন"); return }
        head.text = tr("খোঁজা হচ্ছে…")
        val f = src ?: return
        Thread {
            val list = ArrayList<Found>()
            try {
                PDDocument.load(f).use { doc ->
                    loop@ for (p in 0 until doc.numberOfPages) {
                        val res = doc.getPage(p).resources ?: continue
                        for (name in res.xObjectNames) {
                            if (list.size >= 60) break@loop
                            val x = try { res.getXObject(name) } catch (e: Throwable) { null }
                            if (x !is PDImageXObject) continue
                            val bmp = try { x.image } catch (e: Throwable) { null } ?: continue
                            if (bmp.width < 40 || bmp.height < 40) { bmp.recycle(); continue }
                            val tw = 240
                            val th = Math.max(1, bmp.height * tw / bmp.width)
                            val t = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
                            t.eraseColor(Color.WHITE)
                            Canvas(t).drawBitmap(Bitmap.createScaledBitmap(bmp, tw, th, true), 0f, 0f, null)
                            list.add(Found(p, name.name, bmp.width, bmp.height, t))
                            bmp.recycle()
                        }
                    }
                }
            } catch (e: Throwable) { }
            runOnUiThread { found.clear(); found.addAll(list); draw() }
        }.start()
    }

    private fun draw() {
        grid.removeAllViews()
        if (src != null && !encrypted) head.text = bn(found.size.toString()) + tr(" টি ছবি পাওয়া গেছে")
        val cols = 3
        for (rowItems in found.chunked(cols)) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for ((i, it) in rowItems.withIndex()) {
                val cell = LinearLayout(this)
                cell.orientation = LinearLayout.VERTICAL
                val fr = android.widget.FrameLayout(this)
                val iv = android.widget.ImageView(this)
                iv.setImageBitmap(it.thumb)
                iv.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                iv.background = bg(Ui.PREVIEW, 10, if (it.on) Ui.PRIMARY else Ui.LINE, 1)
                fr.addView(iv, android.widget.FrameLayout.LayoutParams(-1, dp(100)))
                val ck = label(if (it.on) "✓" else "", 12f, if (it.on) Color.WHITE else Ui.PRIMARY, true)
                ck.gravity = android.view.Gravity.CENTER
                ck.background = if (it.on) bg(Ui.PRIMARY, 6) else bg(Ui.CARD, 6, Ui.GREY, 1)
                fr.addView(ck, android.widget.FrameLayout.LayoutParams(dp(20), dp(20), android.view.Gravity.TOP or android.view.Gravity.END).also { q -> q.setMargins(0, dp(6), dp(6), 0) })
                cell.addView(fr)
                cell.addView(label(bn(it.w.toString()) + "×" + bn(it.h.toString()), 10f, Ui.MUTED), lp(t = 3))
                cell.setOnClickListener { _ -> it.on = !it.on; draw() }
                row.addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { q -> q.setMargins(if (i > 0) dp(4) else 0, 0, if (i < cols - 1) dp(4) else 0, dp(8)) })
            }
            for (k in rowItems.size until cols) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).also { q -> q.setMargins(dp(4), 0, 0, 0) })
            grid.addView(row)
        }
        sum.text = bn(found.count { it.on }.toString()) + tr(" টি বাছা হয়েছে") + " · JPG"
    }

    private fun go() {
        if (!requireFile()) return
        if (encrypted) { status.text = tr("আগে PDF আনলক করুন (PDF লক / আনলক টুল)"); return }
        val pick = found.filter { it.on }
        if (pick.isEmpty()) { status.text = tr("কমপক্ষে একটি ছবি বাছুন"); return }
        askTree { tree ->
            val f = src!!
            val base = baseOut()
            work(tr("ছবি বের হচ্ছে…")) {
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                var made = 0
                PDDocument.load(f).use { doc ->
                    var k = 0
                    for (e in pick) {
                        val res = doc.getPage(e.page).resources ?: continue
                        val x = try { res.getXObject(COSName.getPDFName(e.name)) } catch (ex: Throwable) { null }
                        if (x !is PDImageXObject) continue
                        val bmp = try { x.image } catch (ex: Throwable) { null } ?: continue
                        val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                        flat.eraseColor(Color.WHITE)
                        Canvas(flat).drawBitmap(bmp, 0f, 0f, null)
                        bmp.recycle()
                        val bos = ByteArrayOutputStream()
                        flat.compress(Bitmap.CompressFormat.JPEG, 92, bos)
                        flat.recycle()
                        k++
                        val u = DocumentsContract.createDocument(contentResolver, parent, "image/jpeg", base + "_p" + (e.page + 1) + "_" + k + ".jpg")
                        if (u != null) {
                            contentResolver.openOutputStream(u)?.use { it.write(bos.toByteArray()) }
                            made++
                        }
                    }
                }
                if (made == 0) tr("এই PDF-এ আলাদা করার মতো ছবি নেই") else tr("সংরক্ষিত হয়েছে: ") + bn(made.toString()) + tr("টি ছবি")
            }
        }
    }
}
