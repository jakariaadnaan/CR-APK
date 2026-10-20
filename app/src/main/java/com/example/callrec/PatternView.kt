package com.example.callrec

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * A 3x3 pattern pad. Dots are numbered 0..8 (row by row). When the finger lifts, [onComplete]
 * receives the visited dots as a digit string such as "01258". Skipped middle dots are added
 * automatically, like the system pattern lock.
 */
class PatternView(ctx: Context) : View(ctx) {

    var onComplete: ((String) -> Unit)? = null
    var baseColor: Int = Color.WHITE
    var activeColor: Int = Color.WHITE
    var errorColor: Int = Color.parseColor("#FF6B5E")

    private val sel = ArrayList<Int>()
    private var cx = 0f
    private var cy = 0f
    private var drawing = false
    private var error = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        paint.strokeCap = Paint.Cap.ROUND
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = resolveSize(context.dp(280), widthMeasureSpec)
        setMeasuredDimension(s, s)
    }

    fun reset() {
        sel.clear()
        error = false
        invalidate()
    }

    fun flashError() {
        error = true
        invalidate()
        postDelayed({ reset() }, 700)
    }

    private fun px(i: Int): Float = (i % 3 + 0.5f) * width / 3f
    private fun py(i: Int): Float = (i / 3 + 0.5f) * height / 3f

    private fun addDot(i: Int) {
        if (sel.isNotEmpty()) {
            val l = sel.last()
            val dr = i / 3 - l / 3
            val dc = i % 3 - l % 3
            if (dr % 2 == 0 && dc % 2 == 0) {
                val mid = ((l / 3 + i / 3) / 2) * 3 + (l % 3 + i % 3) / 2
                if (mid !in sel && mid != i) sel.add(mid)
            }
        }
        sel.add(i)
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun hit(x: Float, y: Float) {
        val r = width / 3f * 0.32f
        for (i in 0..8) {
            if (i in sel) continue
            val dx = x - px(i)
            val dy = y - py(i)
            if (dx * dx + dy * dy <= r * r) {
                addDot(i)
                return
            }
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                reset()
                drawing = true
                cx = e.x
                cy = e.y
                hit(e.x, e.y)
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                cx = e.x
                cy = e.y
                hit(e.x, e.y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                drawing = false
                invalidate()
                if (sel.isNotEmpty()) onComplete?.invoke(sel.joinToString(""))
            }
            MotionEvent.ACTION_CANCEL -> {
                drawing = false
                sel.clear()
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val col = if (error) errorColor else activeColor

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.dp(4).toFloat()
        paint.color = col
        paint.alpha = 200
        for (k in 1 until sel.size) {
            canvas.drawLine(px(sel[k - 1]), py(sel[k - 1]), px(sel[k]), py(sel[k]), paint)
        }
        if (drawing && sel.isNotEmpty()) {
            val last = sel.last()
            canvas.drawLine(px(last), py(last), cx, cy, paint)
        }

        for (i in 0..8) {
            if (i in sel) {
                paint.style = Paint.Style.FILL
                paint.color = col
                paint.alpha = 255
                canvas.drawCircle(px(i), py(i), context.dp(10).toFloat(), paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = context.dp(2).toFloat()
                paint.alpha = 140
                canvas.drawCircle(px(i), py(i), context.dp(22).toFloat(), paint)
                paint.style = Paint.Style.FILL
                paint.alpha = 45
                canvas.drawCircle(px(i), py(i), context.dp(22).toFloat(), paint)
            } else {
                paint.style = Paint.Style.FILL
                paint.color = baseColor
                paint.alpha = 40
                canvas.drawCircle(px(i), py(i), context.dp(24).toFloat(), paint)
                paint.alpha = 255
                canvas.drawCircle(px(i), py(i), context.dp(6).toFloat(), paint)
            }
        }
    }
}
