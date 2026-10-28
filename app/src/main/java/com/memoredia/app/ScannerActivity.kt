package com.memoredia.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.Camera
import android.net.Uri
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** স্ক্যানার: live camera scan of QR codes and barcodes, or read one from a picture. */
@Suppress("DEPRECATION")
class ScannerActivity : GuardedActivity(), SurfaceHolder.Callback {

    private var cam: Camera? = null
    private var surfaceReady = false
    private var scanning = true
    private var decoding = false
    private var pw = 0
    private var ph = 0

    private lateinit var surface: SurfaceView
    private lateinit var resultV: TextView
    private lateinit var typeV: TextView
    private lateinit var camNote: TextView
    private lateinit var openBtn: TextView
    private var lastText = ""

    private val reader = MultiFormatReader().also {
        it.setHints(mapOf(DecodeHintType.TRY_HARDER to true))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scroll.addView(box)

        box.addView(captionLabel(tr("QR ও বারকোড স্ক্যানার")))
        val cc = card(10)
        val fr = android.widget.FrameLayout(this)
        surface = SurfaceView(this)
        surface.holder.addCallback(this)
        fr.addView(surface, android.widget.FrameLayout.LayoutParams(-1, dp(340)))
        fr.addView(object : android.view.View(this) {
            override fun onDraw(c: android.graphics.Canvas) {
                val d = resources.displayMetrics.density
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                p.style = android.graphics.Paint.Style.STROKE
                p.strokeWidth = 3 * d
                p.strokeCap = android.graphics.Paint.Cap.ROUND
                p.color = Ui.PRIMARY
                val m = 30 * d
                val l = 36 * d
                val w = width.toFloat()
                val h = height.toFloat()
                for (sx in listOf(0, 1)) for (sy in listOf(0, 1)) {
                    val x = if (sx == 0) m else w - m
                    val y = if (sy == 0) m else h - m
                    val dx = if (sx == 0) l else -l
                    val dy = if (sy == 0) l else -l
                    c.drawLine(x, y, x + dx, y, p)
                    c.drawLine(x, y, x, y + dy, p)
                }
            }
        }, android.widget.FrameLayout.LayoutParams(-1, -1))
        cc.addView(fr)
        camNote = label(tr("ক্যামেরা চালু হচ্ছে…"), 12f, Ui.MUTED)
        camNote.gravity = android.view.Gravity.CENTER
        cc.addView(camNote, lp(t = 8, b = 2))
        box.addView(cc, lp(b = 12))

        val rc = resultBox(tr("ফলাফল"), tr("QR বা বারকোডের দিকে ক্যামেরা ধরুন"), " ", 16f)
        resultV = rc.getChildAt(1) as TextView
        resultV.setTextIsSelectable(true)
        resultV.textSize = 15f
        typeV = rc.getChildAt(2) as TextView
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(pill(tr("কপি"), Ui.CARD, Ui.PRIMARY) { if (lastText.isNotEmpty()) copyText(lastText) }, lp(r = 8))
        openBtn = pill(tr("লিংক খুলুন"), Ui.CARD, Ui.PRIMARY) { openLink() }
        openBtn.visibility = android.view.View.GONE
        row.addView(openBtn, lp(r = 8))
        rc.addView(row, lp(t = 10))
        box.addView(rc, lp(b = 10))
        val docs = label(tr("কাগজ বা ডকুমেন্ট স্ক্যান করতে চাইলে PDF Edit খুলুন ›"), 12f, Ui.PRIMARY, true)
        docs.setPadding(dp(2), dp(6), dp(2), dp(6))
        docs.setOnClickListener { startActivity(Intent(this, PdfEditorActivity::class.java)) }
        box.addView(docs)

        val w = LinearLayout(this)
        w.orientation = LinearLayout.HORIZONTAL
        w.addView(primaryButton(tr("আবার স্ক্যান")) { again() }, lp(width = 0, height = dp(48), r = 4).also { it.weight = 1f })
        w.addView(primaryButton(tr("ছবি থেকে পড়ুন")) {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "image/*"
            startActivityForResult(i, 96)
        }, lp(width = 0, height = dp(48), l = 4).also { it.weight = 1f })
        setScreen(tr("স্ক্যানার"), scroll, null, true, bottomBar(w))
    }

    override fun surfaceCreated(h: SurfaceHolder) {
        surfaceReady = true
        ensureCamera()
    }

    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}

    override fun surfaceDestroyed(h: SurfaceHolder) {
        surfaceReady = false
        stopCamera()
    }

    override fun onResume() {
        super.onResume()
        if (surfaceReady) ensureCamera()
    }

    override fun onPause() {
        super.onPause()
        stopCamera()
    }

    private fun ensureCamera() {
        if (cam != null) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            camNote.text = tr("ক্যামেরার অনুমতি দিন")
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 97)
            return
        }
        try {
            val c = Camera.open()
            val p = c.parameters
            var best: Camera.Size? = null
            for (s in p.supportedPreviewSizes) {
                if (s.width <= 1280 && (best == null || s.width * s.height > best.width * best.height)) best = s
            }
            if (best != null) p.setPreviewSize(best.width, best.height)
            if (p.supportedFocusModes?.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) == true) {
                p.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            }
            c.parameters = p
            c.setDisplayOrientation(90)
            pw = p.previewSize.width
            ph = p.previewSize.height
            val lp = surface.layoutParams
            lp.height = (resources.displayMetrics.widthPixels - dp(52)) * pw / ph
            surface.layoutParams = lp
            c.setPreviewDisplay(surface.holder)
            c.setPreviewCallback { data, _ -> onFrame(data) }
            c.startPreview()
            cam = c
            camNote.text = tr("স্ক্যান চলছে…")
        } catch (e: Throwable) {
            camNote.text = tr("ক্যামেরা চালু করা যায়নি")
            cam = null
        }
    }

    private fun stopCamera() {
        try {
            cam?.setPreviewCallback(null)
            cam?.stopPreview()
            cam?.release()
        } catch (e: Throwable) {
        }
        cam = null
    }

    private fun onFrame(data: ByteArray) {
        if (!scanning || decoding) return
        decoding = true
        val copy = data.copyOf()
        val w = pw
        val h = ph
        Thread {
            var text: String? = null
            var type = ""
            try {
                var src = PlanarYUVLuminanceSource(copy, w, h, 0, 0, w, h, false)
                for (k in 0..1) {
                    try {
                        val r = reader.decodeWithState(BinaryBitmap(HybridBinarizer(src)))
                        text = r.text
                        type = r.barcodeFormat.toString()
                        break
                    } catch (e: Throwable) {
                    } finally {
                        reader.reset()
                    }
                    src = src.rotateCounterClockwise() as PlanarYUVLuminanceSource
                }
            } catch (e: Throwable) {
            }
            val t = text
            val ty = type
            runOnUiThread {
                decoding = false
                if (t != null && scanning) show(t, ty)
            }
        }.start()
    }

    private fun show(t: String, type: String) {
        scanning = false
        lastText = t
        typeV.text = type
        resultV.text = t
        openBtn.visibility = if (isUrl(t)) android.view.View.VISIBLE else android.view.View.GONE
        camNote.text = tr("পাওয়া গেছে। আবার স্ক্যান করতে নিচের বোতাম চাপুন।")
    }

    private fun isUrl(t: String) = t.startsWith("http://") || t.startsWith("https://")

    private fun openLink() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lastText)))
        } catch (e: Throwable) {
            Toast.makeText(this, tr("লিংক খোলা যায়নি"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun again() {
        scanning = true
        lastText = ""
        typeV.text = ""
        resultV.text = tr("QR বা বারকোডের দিকে ক্যামেরা ধরুন")
        openBtn.visibility = android.view.View.GONE
        camNote.text = tr("স্ক্যান চলছে…")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 97) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (surfaceReady) ensureCamera()
            } else {
                camNote.text = tr("অনুমতি ছাড়া ক্যামেরা চলবে না। চাইলে নিচে ছবি থেকে পড়তে পারেন।")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 96 || resultCode != Activity.RESULT_OK || data?.data == null) return
        val u = data.data!!
        Thread {
            var text: String? = null
            var type = ""
            try {
                val b = Img.decode(this, u, 2000)
                if (b != null) {
                    val px = IntArray(b.width * b.height)
                    b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
                    try {
                        val r = reader.decodeWithState(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(b.width, b.height, px))))
                        text = r.text
                        type = r.barcodeFormat.toString()
                    } catch (e: Throwable) {
                    } finally {
                        reader.reset()
                    }
                }
            } catch (e: Throwable) {
            }
            val t = text
            val ty = type
            runOnUiThread {
                if (t != null) show(t, ty) else Toast.makeText(this, tr("ছবিতে কোনো QR বা বারকোড পাওয়া যায়নি"), Toast.LENGTH_LONG).show()
            }
        }.start()
    }
}
