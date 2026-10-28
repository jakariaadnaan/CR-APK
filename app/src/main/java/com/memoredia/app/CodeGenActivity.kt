package com.memoredia.app

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
    private lateinit var chipRow: com.memoredia.app.FlowLayout
    private lateinit var hintV: TextView
    private lateinit var image: ImageView
    private lateinit var msg: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scroll.addView(box)

        typeRow = com.memoredia.app.FlowLayout(this)
        if (!barcode) box.addView(typeRow, lp(b = 10))
        input = EditText(this)
        input.hint = if (barcode) tr("যেমন: 8901234567890") else tr("যেমন: https://example.com")
        input.inputType = InputType.TYPE_CLASS_TEXT or (if (barcode) 0 else InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        input.onChange { generate() }
        capV = captionLabel(if (barcode) tr("বারকোডের মান") else tr("বিষয়বস্তু"))
        box.addView(capV)
        styleInput(input)
        box.addView(input, lp(b = 8))
        pass = EditText(this)
        pass.hint = tr("Wi-Fi পাসওয়ার্ড")
        pass.inputType = InputType.TYPE_CLASS_TEXT
        pass.onChange { generate() }
        styleInput(pass)
        pass.visibility = android.view.View.GONE
        box.addView(pass, lp(b = 8))
        chipRow = com.memoredia.app.FlowLayout(this)
        if (barcode) {
            box.addView(chipRow, lp(b = 10))
        }

        val r = card(14)
        r.gravity = Gravity.CENTER_HORIZONTAL
        image = ImageView(this)
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        image.setBackgroundColor(Color.WHITE)
        r.addView(image, lp(height = if (barcode) 170 else 220))
        msg = label(tr("লেখা দিলেই কোড তৈরি হবে"), 12f, Ui.TEXT)
        msg.gravity = Gravity.CENTER
        r.addView(msg, lp(t = 8))
        box.addView(r, lp(b = 10))

        hintV = label("", 12f, Ui.MUTED)
        if (barcode) {
            val v = resultBox(tr("ফরম্যাট যাচাই"), "", "", 14f)
            valV = v.getChildAt(1) as TextView
            valNote = TextView(this)
            valNote.setTextColor(Ui.MUTED)
            valNote.textSize = 12f
            v.addView(valNote, lp(t = 6))
            box.addView(v, lp(b = 8))
            box.addView(hintV, lp(l = 2))
        } else {
            box.addView(sectionLabel(tr("সাইজ"), 4))
            sizeRow = com.memoredia.app.FlowLayout(this)
            box.addView(sizeRow, lp(b = 8))
            box.addView(sectionLabel(tr("ত্রুটি সংশোধন (বেশি হলে নষ্ট QR-ও পড়া যায়)"), 4))
            box.addView(chipRow, lp(b = 8))
        }

        val w = LinearLayout(this)
        w.addView(primaryButton(if (barcode) tr("বারকোড এক্সপোর্ট") else tr("QR ডাউনলোড")) { save() }, lp(height = dp(48)))
        setScreen(if (barcode) tr("বারকোড তৈরি") else tr("QR কোড তৈরি"), scroll, null, true, bottomBar(w))
        chips()
        generate()
    }

    private lateinit var typeRow: com.memoredia.app.FlowLayout
    private lateinit var sizeRow: com.memoredia.app.FlowLayout
    private lateinit var capV: TextView
    private lateinit var pass: EditText
    private lateinit var valV: TextView
    private lateinit var valNote: TextView
    private var qrType = 0
    private var sizeIdx = 1

    /** The text that goes into the QR code for the chosen content type. */
    private fun payload(): String {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return ""
        return when (qrType) {
            2 -> if (t.startsWith("mailto:")) t else "mailto:$t"
            3 -> "WIFI:T:" + (if (pass.text.isEmpty()) "nopass" else "WPA") + ";S:" + t.replace(";", "\\;") + ";P:" + pass.text.toString().replace(";", "\\;") + ";;"
            else -> input.text.toString()
        }
    }

    private fun chips() {
        chipRow.removeAllViews()
        if (barcode) {
            for (i in kinds.indices) chipRow.addView(choiceChip(kinds[i].name, i == kind) { kind = i; chips(); generate() })
            hintV.text = kinds[kind].hint
        } else {
            for (i in levels.indices) chipRow.addView(choiceChip(levels[i].first, i == level) { level = i; chips(); generate() })
            typeRow.removeAllViews()
            val tn = listOf("URL", tr("লেখা"), tr("ইমেইল"), "Wi-Fi")
            for (i in tn.indices) typeRow.addView(choiceChip(tn[i], i == qrType) {
                qrType = i
                input.hint = when (i) { 0 -> tr("যেমন: https://example.com"); 1 -> tr("যেকোনো লেখা"); 2 -> tr("যেমন: name@example.com"); else -> tr("নেটওয়ার্কের নাম (SSID)") }
                capV.text = if (i == 3) tr("নেটওয়ার্কের নাম") else tr("বিষয়বস্তু")
                pass.visibility = if (i == 3) android.view.View.VISIBLE else android.view.View.GONE
                chips(); generate()
            })
            sizeRow.removeAllViews()
            val sn = listOf(tr("ছোট"), tr("মাঝারি"), tr("বড়"))
            for (i in sn.indices) sizeRow.addView(choiceChip(sn[i], i == sizeIdx) { sizeIdx = i; chips(); generate() })
        }
    }

    /** Length and checksum check for the numeric formats, shown above the export button. */
    private fun validate(t: String): Pair<String, String> {
        val f = kinds[kind].f
        val digits = t.all { it.isDigit() }
        fun ok(len: Int): Boolean {
            if (t.length != len) return false
            var sum = 0
            for (i in 0 until len - 1) {
                val d = t[len - 2 - i] - '0'
                sum += if (i % 2 == 0) d * 3 else d
            }
            return (10 - sum % 10) % 10 == t[len - 1] - '0'
        }
        return when (f) {
            BarcodeFormat.EAN_13 -> if (!digits) Pair(tr("অবৈধ"), tr("শুধু সংখ্যা চলবে")) else if (t.length == 12) Pair(tr("বৈধ"), tr("১২ সংখ্যা: চেকসাম নিজে যোগ হবে")) else if (ok(13)) Pair(tr("বৈধ"), tr("চেকসাম মিলেছে")) else Pair(tr("অবৈধ"), tr("১২ বা ১৩ সংখ্যা ও সঠিক চেকসাম দরকার"))
            BarcodeFormat.EAN_8 -> if (!digits) Pair(tr("অবৈধ"), tr("শুধু সংখ্যা চলবে")) else if (t.length == 7) Pair(tr("বৈধ"), tr("৭ সংখ্যা: চেকসাম নিজে যোগ হবে")) else if (ok(8)) Pair(tr("বৈধ"), tr("চেকসাম মিলেছে")) else Pair(tr("অবৈধ"), tr("৭ বা ৮ সংখ্যা ও সঠিক চেকসাম দরকার"))
            BarcodeFormat.UPC_A -> if (!digits) Pair(tr("অবৈধ"), tr("শুধু সংখ্যা চলবে")) else if (t.length == 11) Pair(tr("বৈধ"), tr("১১ সংখ্যা: চেকসাম নিজে যোগ হবে")) else if (ok(12)) Pair(tr("বৈধ"), tr("চেকসাম মিলেছে")) else Pair(tr("অবৈধ"), tr("১১ বা ১২ সংখ্যা ও সঠিক চেকসাম দরকার"))
            else -> Pair(tr("বৈধ"), tr("এই ধরনে চেকসাম লাগে না"))
        }
    }

    private fun generate() {
        val text = if (barcode) asciiDigits(input.text.toString()).trim() else payload()
        if (barcode) {
            if (text.isEmpty()) { valV.text = "—"; valNote.text = "" } else { val v = validate(text); valV.text = v.first; valNote.text = v.second }
        }
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
                val sz = intArrayOf(500, 800, 1200)[sizeIdx]
                w = sz
                h = sz
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
        saveOrAsk(i, 95)
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
