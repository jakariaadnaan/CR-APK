package com.example.callrec

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.util.Locale

/** Shared helpers for the image tools. */
object Img {
    const val REQ_PICK = 71
    const val REQ_SAVE = 72

    fun decode(ctx: Context, uri: Uri, maxSide: Int): Bitmap? {
        try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            val o = BitmapFactory.Options()
            o.inSampleSize = sample
            var b = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) } ?: return null
            val deg = rotation(ctx, uri)
            if (deg != 0) {
                val m = Matrix()
                m.postRotate(deg.toFloat())
                val r = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
                if (r !== b) b.recycle()
                b = r
            }
            return b
        } catch (e: Throwable) {
            return null
        }
    }

    private fun rotation(ctx: Context, uri: Uri): Int {
        return try {
            ctx.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Throwable) {
            0
        }
    }

    fun name(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use {
                val i = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && it.moveToFirst()) return it.getString(i) ?: "image"
            }
        } catch (e: Throwable) {
        }
        return "image"
    }

    fun size(ctx: Context, uri: Uri): Long {
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use {
                val i = it.getColumnIndex(OpenableColumns.SIZE)
                if (i >= 0 && it.moveToFirst() && !it.isNull(i)) return it.getLong(i)
            }
        } catch (e: Throwable) {
        }
        return 0L
    }

    /** fmt: 0 = PNG, 1 = JPG, 2 = WebP. */
    fun encode(src: Bitmap, fmt: Int, quality: Int): ByteArray {
        var b = src
        if (fmt == 1 && src.hasAlpha()) {
            val flat = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val c = Canvas(flat)
            c.drawColor(Color.WHITE)
            c.drawBitmap(src, 0f, 0f, Paint())
            b = flat
        }
        val out = ByteArrayOutputStream()
        when (fmt) {
            0 -> b.compress(Bitmap.CompressFormat.PNG, 100, out)
            1 -> b.compress(Bitmap.CompressFormat.JPEG, quality, out)
            else -> {
                if (Build.VERSION.SDK_INT >= 30) {
                    b.compress(if (quality >= 100) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY, quality, out)
                } else {
                    @Suppress("DEPRECATION")
                    b.compress(Bitmap.CompressFormat.WEBP, quality, out)
                }
            }
        }
        if (b !== src) b.recycle()
        return out.toByteArray()
    }

    fun human(bytes: Long): String {
        val s = when {
            bytes < 1024 -> bytes.toString() + " বাইট"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f", bytes / 1024.0) + " KB"
            else -> String.format(Locale.US, "%.2f", bytes / 1048576.0) + " MB"
        }
        return bn(s)
    }
}

/** Base screen for tools that start from one picked image. */
abstract class ImageToolBase : GuardedActivity() {

    protected var bmp: Bitmap? = null
    protected var srcName = "image"
    protected var srcSize = 0L
    protected var maxSide = 4096
    private var pendingBytes: ByteArray? = null

    protected lateinit var preview: ImageView
    protected lateinit var info: TextView

    protected abstract fun onImageLoaded()

    /** The card with the pick button, a short info line and a preview. */
    protected fun pickCard(previewDp: Int = 240): LinearLayout {
        val c = card(14)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(pill("ছবি বাছুন") { pick() }, lp(r = 12))
        info = label("কোনো ছবি বাছা হয়নি", 13f, Ui.MUTED)
        row.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(row, lp(b = 10))
        preview = ImageView(this)
        preview.scaleType = ImageView.ScaleType.FIT_CENTER
        preview.setBackgroundColor(Color.parseColor("#EEF1EF"))
        c.addView(preview, lp(height = previewDp))
        return c
    }

    private fun pick() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        startActivityForResult(i, Img.REQ_PICK)
    }

    protected fun askSave(bytes: ByteArray, fileName: String, mime: String) {
        pendingBytes = bytes
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = mime
        i.putExtra(Intent.EXTRA_TITLE, fileName)
        startActivityForResult(i, Img.REQ_SAVE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val uri = data.data!!
        if (requestCode == Img.REQ_PICK) {
            info.text = "লোড হচ্ছে…"
            Thread {
                val b = Img.decode(this, uri, maxSide)
                val n = Img.name(this, uri)
                val s = Img.size(this, uri)
                runOnUiThread {
                    if (b == null) {
                        Toast.makeText(this, "ছবিটি খোলা যায়নি", Toast.LENGTH_LONG).show()
                        info.text = "কোনো ছবি বাছা হয়নি"
                    } else {
                        bmp = b
                        srcName = n
                        srcSize = s
                        preview.setImageBitmap(b)
                        onImageLoaded()
                    }
                }
            }.start()
        } else if (requestCode == Img.REQ_SAVE) {
            val bytes = pendingBytes ?: return
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                Toast.makeText(this, "সংরক্ষিত হয়েছে", Toast.LENGTH_SHORT).show()
            } catch (e: Throwable) {
                Toast.makeText(this, "সংরক্ষণ করা যায়নি", Toast.LENGTH_LONG).show()
            }
        }
    }

    protected fun baseName(): String {
        val n = srcName
        val d = n.lastIndexOf('.')
        return if (d > 0) n.substring(0, d) else n
    }
}
