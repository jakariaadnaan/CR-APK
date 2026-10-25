package com.example.callrec

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
import android.widget.EditText
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

/** PDF থেকে ছবি: save the pictures inside a PDF into a folder. */
class PdfImagesActivity : PdfBase() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val act = actionCard(tr("ফোল্ডার বেছে ছবি বের করুন"), tr("PDF-এর ভেতরের আসল ছবিগুলো আলাদা JPG ফাইল হবে। খুব ছোট ছবি বাদ যায়।")) { go() }
        screen(tr("PDF থেকে ছবি"), pickCard(), act)
    }

    private fun go() {
        if (!requireFile()) return
        if (encrypted) { status.text = tr("আগে PDF আনলক করুন (PDF লক / আনলক টুল)"); return }
        askTree { tree ->
            val f = src!!
            val base = baseOut()
            work(tr("ছবি বের হচ্ছে…")) {
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                var made = 0
                PDDocument.load(f).use { doc ->
                    for (p in 0 until doc.numberOfPages) {
                        val res = doc.getPage(p).resources ?: continue
                        var k = 0
                        for (name in res.xObjectNames) {
                            val x = try { res.getXObject(name) } catch (e: Throwable) { null }
                            if (x !is PDImageXObject) continue
                            val bmp = try { x.image } catch (e: Throwable) { null } ?: continue
                            if (bmp.width < 40 || bmp.height < 40) { bmp.recycle(); continue }
                            val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                            flat.eraseColor(Color.WHITE)
                            Canvas(flat).drawBitmap(bmp, 0f, 0f, null)
                            bmp.recycle()
                            val bos = ByteArrayOutputStream()
                            flat.compress(Bitmap.CompressFormat.JPEG, 92, bos)
                            flat.recycle()
                            k++
                            val u = DocumentsContract.createDocument(contentResolver, parent, "image/jpeg", base + "_p" + (p + 1) + "_" + k + ".jpg")
                            if (u != null) {
                                contentResolver.openOutputStream(u)?.use { it.write(bos.toByteArray()) }
                                made++
                            }
                        }
                    }
                }
                if (made == 0) tr("এই PDF-এ আলাদা করার মতো ছবি নেই") else tr("সংরক্ষিত হয়েছে: ") + bn(made.toString()) + tr("টি ছবি")
            }
        }
    }
}
