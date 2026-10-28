package com.memoredia.app

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/** ট্রান্সপারেন্ট PNG মেকার: removes one background colour (colour-key), with adjustable tolerance. */
class TransparentActivity : ImageToolBase() {

    private var key = Color.WHITE
    private var tol = 40
    private var out: Bitmap? = null
    private var gen = 0

    private lateinit var swatch: View
    private lateinit var tolLabel: TextView
    private lateinit var result: TextView

    init {
        maxSide = 2048
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scroll.addView(box)

        box.addView(pickCard(280), lp(b = 6))
        preview.background = checker()
        preview.setOnTouchListener { v, e ->
            val b = bmp
            if (b != null && e.actionMasked == MotionEvent.ACTION_UP) {
                v.performClick()
                val inv = Matrix()
                if (preview.imageMatrix.invert(inv)) {
                    val p = floatArrayOf(e.x, e.y)
                    inv.mapPoints(p)
                    val x = p[0].toInt()
                    val y = p[1].toInt()
                    if (x in 0 until b.width && y in 0 until b.height) {
                        key = b.getPixel(x, y) or (0xFF shl 24)
                        swatch.setBackgroundColor(key)
                        recompute()
                    }
                }
            }
            b != null
        }
        box.addView(label(tr("ছবিতে যে রঙ মুছতে চান সেখানে ছুঁয়ে দিন"), 12f, Ui.MUTED), lp(l = 4, b = 10))

        val opt = card(14)
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = android.view.Gravity.CENTER_VERTICAL
        swatch = View(this)
        swatch.background = bg(key, 8, Ui.LINE, 1)
        top.addView(swatch, lp(width = dp(36), height = dp(36), r = 12))
        top.addView(label(tr("মুছে ফেলার রঙ"), 14f, Ui.TEXT, true))
        opt.addView(top, lp(b = 10))
        tolLabel = label("", 12f, Ui.MUTED, true)
        opt.addView(tolLabel)
        val bar = SeekBar(this)
        bar.progressTintList = android.content.res.ColorStateList.valueOf(Ui.PRIMARY)
        bar.thumbTintList = android.content.res.ColorStateList.valueOf(Ui.PRIMARY)
        bar.max = 200
        bar.progress = tol
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                tol = p
                tolLabel.text = tr("সহনশীলতা: ") + bn(p.toString()) + tr(" (বেশি হলে কাছাকাছি রঙও মুছবে)")
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { recompute() }
        })
        opt.addView(bar, lp(b = 4))
        tolLabel.text = tr("সহনশীলতা: ") + bn(tol.toString()) + tr(" (বেশি হলে কাছাকাছি রঙও মুছবে)")
        box.addView(opt, lp(b = 12))

        result = label(tr("আগে একটি ছবি বাছুন"), 13f, Ui.MUTED)
        box.addView(result, lp(l = 4, b = 6))
        box.addView(label(tr("একরঙা ব্যাকগ্রাউন্ডে ভালো কাজ করে; জটিল ছবিতে নিখুঁত হবে না। মূল ছবি অক্ষত থাকে।"), 12f, Ui.MUTED), lp(l = 4))

        screenWithBottom(tr("ট্রান্সপারেন্ট PNG মেকার"), scroll, primaryButton(tr("PNG সংরক্ষণ")) { save() })
    }

    private fun checker(): BitmapDrawable {
        val s = dp(10)
        val t = Bitmap.createBitmap(s * 2, s * 2, Bitmap.Config.ARGB_8888)
        val c = Canvas(t)
        c.drawColor(Color.parseColor("#FFFFFF"))
        val p = Paint()
        p.color = Color.parseColor("#D9DEDB")
        c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), p)
        c.drawRect(s.toFloat(), s.toFloat(), (s * 2).toFloat(), (s * 2).toFloat(), p)
        val d = BitmapDrawable(resources, t)
        d.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        return d
    }

    override fun onImageLoaded() {
        val b = bmp!!
        info.text = srcName + " · " + bn(b.width.toString()) + "×" + bn(b.height.toString())
        key = b.getPixel(0, 0) or (0xFF shl 24)
        swatch.setBackgroundColor(key)
        recompute()
    }

    private fun recompute() {
        val src = bmp ?: return
        val my = ++gen
        result.text = tr("হিসাব চলছে…")
        val k = key
        val t = tol
        Thread {
            var res: Bitmap? = null
            try {
                val w = src.width
                val h = src.height
                val px = IntArray(w * h)
                src.getPixels(px, 0, w, 0, 0, w, h)
                val kr = Color.red(k)
                val kg = Color.green(k)
                val kb = Color.blue(k)
                val soft = t / 3 + 10
                for (i in px.indices) {
                    val c = px[i]
                    val dr = Color.red(c) - kr
                    val dg = Color.green(c) - kg
                    val db = Color.blue(c) - kb
                    val d = Math.sqrt((dr * dr + dg * dg + db * db).toDouble())
                    val a = when {
                        d <= t -> 0
                        d < t + soft -> ((d - t) / soft * Color.alpha(c)).toInt()
                        else -> Color.alpha(c)
                    }
                    px[i] = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
                }
                res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                res.setPixels(px, 0, w, 0, 0, w, h)
            } catch (e: Throwable) {
                res = null
            }
            runOnUiThread {
                if (my != gen) return@runOnUiThread
                out = res
                if (res == null) {
                    result.text = tr("প্রক্রিয়া করা যায়নি")
                } else {
                    preview.setImageBitmap(res)
                    result.text = tr("প্রস্তুত। চাইলে অন্য রঙে ছুঁয়ে আবার দেখুন।")
                }
            }
        }.start()
    }

    private fun save() {
        val b = out
        if (b == null) {
            Toast.makeText(this, tr("আগে একটি ছবি বাছুন"), Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            val bytes = try { Img.encode(b, 0, 100) } catch (e: Throwable) { null }
            runOnUiThread {
                if (bytes == null) Toast.makeText(this, tr("সংরক্ষণ করা যায়নি"), Toast.LENGTH_LONG).show()
                else askSave(bytes, baseName() + "_transparent.png", "image/png")
            }
        }.start()
    }
}
