package com.example.callrec

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR কোড তৈরি and বারকোড তৈরি: one screen, two modes. */
abstract class CodeGenBase(private val barcode: Boolean) : GuardedActivity() {

    private class Kind(val name: String, val f: BarcodeFormat, val hint: String)

    private val kinds = listOf(
        Kind("Code 128", BarcodeFormat.CODE_128, tr("যেকোনো অক্ষর ও সংখ্যা")),
        Kind("Code 39", BarcodeFormat.CODE_39, tr("বড় হাতের A–Z, সংখ্যা, - . $ / + % স্পেস")),
        Kind("EAN-13", BarcodeFormat.EAN_13, tr("১২ বা ১৩টি সংখ্যা")),
        Kind("EAN-8", BarcodeFormat.EAN_8, tr("৭ বা ৮টি সংখ্যা")),
        Kind("UPC-A", BarcodeFormat.UPC_A, tr("১১ বা ১২টি সংখ্যা"))
    )
    private val levels = listOf("L" to ErrorCorrectionLevel.L, "M" to ErrorCorrectionLevel.M, "Q" to ErrorCorrectionLevel.Q, "H" to ErrorCorrectionLevel.H)

    private var kind = 0
    private var level = 1
    private var bmp: Bitmap? = null

    private lateinit var input: EditText
    private lateinit var chipRow: com.example.callrec.FlowLayout
    private lateinit var hintV: TextView
    private lateinit var image: ImageView
    private lateinit var msg: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)

        val c = card(14)
        c.addView(label(if (barcode) tr("বারকোডের লেখা বা সংখ্যা") else tr("লেখা বা লিংক"), 12f, Ui.MUTED, true))
        input = EditText(this)
        input.hint = if (barcode) tr("যেমন: 123456789012") else tr("যেমন: https://example.com")
        input.inputType = InputType.TYPE_CLASS_TEXT or (if (barcode) 0 else InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        input.setTextColor(Ui.TEXT)
        input.onChange { generate() }
        c.addView(input, lp(t = 4, b = 8))
        chipRow = com.example.callrec.FlowLayout(this)
        c.addView(label(if (barcode) tr("বারকোডের ধরন") else tr("ত্রুটি সংশোধন (বেশি হলে নষ্ট QR-ও পড়া যায়)"), 12f, Ui.MUTED, true), lp(t = 4, b = 6))
        c.addView(chipRow, lp())
        hintV = label("", 12f, Ui.MUTED)
        if (barcode) c.addView(hintV, lp(t = 6))
        box.addView(c, lp(b = 12))

        val r = card(14)
        r.gravity = Gravity.CENTER_HORIZONTAL
        image = ImageView(this)
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        image.setBackgroundColor(Color.WHITE)
        r.addView(image, lp(height = 260))
        msg = label(tr("লেখা দিলেই কোড তৈরি হবে"), 13f, Ui.MUTED)
        msg.gravity = Gravity.CENTER
        r.addView(msg, lp(t = 8, b = 10))
        r.addView(pill(tr("PNG সংরক্ষণ")) { save() })
        box.addView(r, lp())

        setScreen(if (barcode) tr("বারকোড তৈরি") else tr("QR কোড তৈরি"), scroll)
        chips()
        generate()
    }

    private fun chips() {
        chipRow.removeAllViews()
        if (barcode) {
            for (i in kinds.indices) chipRow.addView(chip(kinds[i].name, i == kind) { kind = i; chips(); generate() })
            hintV.text = kinds[kind].hint
        } else {
            for (i in levels.indices) chipRow.addView(chip(levels[i].first, i == level) { level = i; chips(); generate() })
        }
    }

    private fun chip(text: String, on: Boolean, click: () -> Unit): TextView {
        val c = label(text, 14f, if (on) Color.WHITE else Ui.PRIMARY, true)
        c.gravity = Gravity.CENTER
        c.background = bg(if (on) Ui.PRIMARY else Ui.CARD, 20, Ui.PRIMARY, 1)
        c.setPadding(dp(16), dp(8), dp(16), dp(8))
        c.setOnClickListener { click() }
        return c
    }

    private fun generate() {
        val text = asciiDigits(input.text.toString()).let { if (barcode) it else input.text.toString() }
        if (text.isEmpty()) {
            bmp = null
            image.setImageBitmap(null)
            msg.text = tr("লেখা দিলেই কোড তৈরি হবে")
            return
        }
        try {
            val hints = HashMap<EncodeHintType, Any>()
            hints[EncodeHintType.MARGIN] = if (barcode) 10 else 1
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            val w: Int
            val h: Int
            val fmt: BarcodeFormat
            if (barcode) {
                fmt = kinds[kind].f
                w = 900
                h = 360
            } else {
                fmt = BarcodeFormat.QR_CODE
                w = 800
                h = 800
                hints[EncodeHintType.ERROR_CORRECTION] = levels[level].second
            }
            val m = MultiFormatWriter().encode(text, fmt, w, h, hints)
            val px = IntArray(m.width * m.height)
            for (y in 0 until m.height) for (x in 0 until m.width) px[y * m.width + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE
            val b = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
            b.setPixels(px, 0, m.width, 0, 0, m.width, m.height)
            bmp = b
            image.setImageBitmap(b)
            msg.text = tr("তৈরি হয়েছে")
        } catch (e: Throwable) {
            bmp = null
            image.setImageBitmap(null)
            msg.text = if (barcode) tr("এই লেখা এই বারকোডে চলে না: ") + kinds[kind].hint else tr("কোড তৈরি করা যায়নি (লেখা বেশি বড় হতে পারে)")
        }
    }

    private fun save() {
        if (bmp == null) {
            Toast.makeText(this, tr("আগে কোড তৈরি করুন"), Toast.LENGTH_SHORT).show()
            return
        }
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/png"
        i.putExtra(Intent.EXTRA_TITLE, if (barcode) "barcode.png" else "qrcode.png")
        startActivityForResult(i, 95)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 95 || resultCode != Activity.RESULT_OK || data?.data == null) return
        val b = bmp ?: return
        try {
            val bytes = Img.encode(b, 0, 100)
            contentResolver.openOutputStream(data.data!!)?.use { it.write(bytes) }
            Toast.makeText(this, tr("সংরক্ষিত হয়েছে"), Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            Toast.makeText(this, tr("সংরক্ষণ করা যায়নি"), Toast.LENGTH_LONG).show()
        }
    }
}

class QrGenActivity : CodeGenBase(false)
class BarcodeGenActivity : CodeGenBase(true)
