package com.example.callrec

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/** ওয়েবক্যাম টেস্টার: live preview from each camera with its resolution and real frame rate. */
@Suppress("DEPRECATION")
class WebcamActivity : GuardedActivity(), SurfaceHolder.Callback {

    private var cam: Camera? = null
    private var id = 0
    private var surfaceReady = false
    private var frames = 0
    private var lastTick = 0L

    private lateinit var surface: SurfaceView
    private lateinit var infoV: TextView
    private lateinit var fpsV: TextView
    private lateinit var switchBtn: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scroll.addView(box)

        box.addView(captionLabel(tr("ক্যামেরা প্রিভিউ")))
        val c = card(10)
        surface = SurfaceView(this)
        surface.holder.addCallback(this)
        c.addView(surface, lp(height = 340))
        box.addView(c, lp(b = 12))

        val fr = resultBox(tr("ফ্রেম রেট (সত্যিকারের)"), "—", "", 30f)
        fpsV = fr.getChildAt(1) as TextView
        box.addView(fr, lp(b = 12))
        val r = card(14)
        infoV = label("", 13f, Ui.TEXT)
        r.addView(infoV)
        box.addView(r, lp())

        val bar = LinearLayout(this)
        switchBtn = primaryButton(tr("ক্যামেরা বদলান")) { switchCamera() }
        bar.addView(switchBtn, lp(height = dp(48)))
        setScreen(tr("ওয়েবক্যাম টেস্টার"), scroll, null, true, bottomBar(bar))
    }

    override fun surfaceCreated(h: SurfaceHolder) { surfaceReady = true; start() }
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
    override fun surfaceDestroyed(h: SurfaceHolder) { surfaceReady = false; stop() }

    override fun onResume() {
        super.onResume()
        if (surfaceReady) start()
    }

    override fun onPause() {
        super.onPause()
        stop()
    }

    private fun switchCamera() {
        val n = Camera.getNumberOfCameras()
        if (n <= 1) {
            infoV.text = tr("এই ডিভাইসে একটিই ক্যামেরা আছে")
            return
        }
        stop()
        id = (id + 1) % n
        start()
    }

    private fun start() {
        if (cam != null) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            infoV.text = tr("ক্যামেরার অনুমতি দিন")
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 98)
            return
        }
        try {
            val c = Camera.open(id)
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(id, info)
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
            val deg = when (windowManager.defaultDisplay.rotation) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            val orient = if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) (360 - (info.orientation + deg) % 360) % 360
            else (info.orientation - deg + 360) % 360
            c.setDisplayOrientation(orient)
            val pw = p.previewSize.width
            val ph = p.previewSize.height
            val lp = surface.layoutParams
            lp.height = (resources.displayMetrics.widthPixels - dp(52)) * Math.max(pw, ph) / Math.min(pw, ph)
            if (orient % 180 != 0) lp.height = (resources.displayMetrics.widthPixels - dp(52)) * Math.min(pw, ph) / Math.max(pw, ph)
            surface.layoutParams = lp
            c.setPreviewDisplay(surface.holder)
            frames = 0
            lastTick = System.nanoTime()
            c.setPreviewCallback { _, _ ->
                frames++
                val now = System.nanoTime()
                if (now - lastTick >= 1_000_000_000L) {
                    val fps = frames * 1e9 / (now - lastTick)
                    frames = 0
                    lastTick = now
                    fpsV.text = bn(String.format(Locale.US, "%.1f", fps)) + " fps"
                }
            }
            c.startPreview()
            cam = c
            val sizes = p.supportedPreviewSizes.sortedByDescending { it.width * it.height }.take(4).joinToString(", ") { it.width.toString() + "×" + it.height }
            infoV.text = tr("ক্যামেরা ") + bn((id + 1).toString()) + "/" + bn(Camera.getNumberOfCameras().toString()) +
                " (" + (if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) tr("সামনের") else tr("পেছনের")) + ")\n" +
                tr("প্রিভিউ: ") + pw + "×" + ph + tr("\nসর্বোচ্চ সাপোর্টেড: ") + sizes
        } catch (e: Throwable) {
            infoV.text = tr("ক্যামেরা চালু করা যায়নি (অন্য অ্যাপ ব্যবহার করছে হতে পারে)")
            cam = null
        }
    }

    private fun stop() {
        try {
            cam?.setPreviewCallback(null)
            cam?.stopPreview()
            cam?.release()
        } catch (e: Throwable) {
        }
        cam = null
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 98) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (surfaceReady) start()
            } else {
                infoV.text = tr("অনুমতি ছাড়া ক্যামেরা চালানো যাবে না")
            }
        }
    }
}
