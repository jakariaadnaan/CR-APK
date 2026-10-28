package com.memoredia.app

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/** জমির মাপ: শতাংশ, কাঠা, বিঘা, একর, বর্গফুট ইত্যাদির মধ্যে রূপান্তর। কাঠা ও বিঘার মান বদলানো যায়। */
class LandActivity : GuardedActivity() {

    private val prefs by lazy { getSharedPreferences("land_prefs", MODE_PRIVATE) }
    private var katha = 720.0 // square feet in one কাঠা
    private var bigha = 20.0 // কাঠা in one বিঘা

    private lateinit var box: LinearLayout

    private val names = listOf(tr("শতাংশ"), tr("কাঠা"), tr("বিঘা"), tr("একর"), tr("বর্গফুট"), tr("বর্গমিটার"), tr("বর্গহাত"))

    /** Square feet in one of each unit, in the same order as [names]. */
    private fun factors(): DoubleArray =
        doubleArrayOf(435.6, katha, katha * bigha, 43560.0, 1.0, 10.7639104, 2.25)

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        katha = prefs.getString("katha", "720")?.toDoubleOrNull() ?: 720.0
        bigha = prefs.getString("bigha", "20")?.toDoubleOrNull() ?: 20.0

        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scroll.addView(box)
        setScreen(tr("জমির মাপ"), scroll, null, true, bottomBar(LinearLayout(this).also { it.addView(primaryButton(tr("ক্ষেত্রফল হিসাব করুন")) { refresh(); currentFocus?.clearFocus() }, lp(height = dp(48))) }))
        build()
    }

    private var shape = 0 // 0 rectangle, 1 triangle, 2 circle, 3 direct area
    private var lenUnit = 0
    private val lenNames get() = listOf(tr("ফুট"), tr("মিটার"), tr("হাত"), tr("গজ"))
    private val lenFeet = doubleArrayOf(1.0, 3.28084, 1.5, 3.0)

    /** Draws the plot shape with its measurements, like the preview in the mockup. */
    private inner class PlotView : View(this@LandActivity) {
        var t1 = ""
        var t2 = ""
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: android.graphics.Canvas) {
            val d = resources.displayMetrics.density
            val w = width.toFloat()
            val h = height.toFloat()
            val l = w * 0.2f
            val r = w * 0.8f
            val tp = h * 0.12f
            val bt = h * 0.72f
            val path = android.graphics.Path()
            when (shape) {
                1 -> { path.moveTo((l + r) / 2, tp); path.lineTo(r, bt); path.lineTo(l, bt); path.close() }
                2 -> path.addCircle(w / 2, (tp + bt) / 2, (bt - tp) / 2, android.graphics.Path.Direction.CW)
                else -> { path.moveTo(l + w * 0.05f, tp); path.lineTo(r - w * 0.05f, tp); path.lineTo(r, bt); path.lineTo(l, bt); path.close() }
            }
            p.style = android.graphics.Paint.Style.FILL
            p.color = Ui.mix(Ui.PRIMARY, Ui.CARD, 0.88f)
            c.drawPath(path, p)
            p.style = android.graphics.Paint.Style.STROKE
            p.strokeWidth = 1.2f * d
            p.color = Ui.mix(Ui.PRIMARY, Ui.CARD, 0.45f)
            c.drawPath(path, p)
            p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(4 * d, 3 * d), 0f)
            p.color = Ui.PRIMARY
            c.drawLine(w / 2, tp, w / 2, bt, p)
            c.drawLine(l, bt - 6 * d, r, bt - 6 * d, p)
            p.pathEffect = null
            p.style = android.graphics.Paint.Style.FILL
            p.textSize = 11f * d
            p.color = Ui.TEXT
            c.drawText(t1, w / 2 + 6 * d, (tp + bt) / 2, p)
            c.drawText(t2, w / 2 - p.measureText(t2) / 2, bt + 14 * d, p)
        }
    }

    private fun build() {
        box.removeAllViews()
        val shapeNames = listOf(tr("আয়তক্ষেত্র"), tr("ত্রিভুজ"), tr("বৃত্ত"), tr("সরাসরি ক্ষেত্রফল"))
        box.addView(sectionLabel(tr("প্লটের মাপ"), 0))
        box.addView(chipRow(shapeNames, shape) { shape = it; build() }, lp(b = 10))

        val in1 = EditText(this)
        val in2 = EditText(this)
        for (e in listOf(in1, in2)) e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val from = Spinner(this)
        from.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        val plot = PlotView()

        if (shape == 3) {
            box.addView(fieldBox(tr("পরিমাণ"), in1), lp(b = 8))
            val sp = card(6)
            sp.addView(from)
            box.addView(sp, lp(b = 10))
        } else {
            val cap1 = when (shape) { 1 -> tr("ভূমি"); 2 -> tr("ব্যাসার্ধ"); else -> tr("দৈর্ঘ্য") }
            val cap2 = if (shape == 1) tr("উচ্চতা") else tr("প্রস্থ")
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.addView(fieldBox(cap1, in1), lp(width = 0, r = 5).also { it.weight = 1f })
            if (shape != 2) row.addView(fieldBox(cap2, in2), lp(width = 0, l = 5).also { it.weight = 1f })
            box.addView(row, lp(b = 10))
            box.addView(chipRow(lenNames, lenUnit) { lenUnit = it; refresh() }, lp(b = 10))
            box.addView(captionLabel(tr("প্লটের প্রিভিউ")))
            val pc = card(8)
            pc.addView(plot, lp(height = dp(150)))
            box.addView(pc, lp(b = 12))
        }

        val res = resultBox(tr("আনুমানিক ক্ষেত্রফল"), "—", "")
        box.addView(res, lp(b = 12))
        val resVal = (res.getChildAt(1) as TextView)
        val resNote = TextView(this)
        resNote.setTextColor(Ui.MUTED)
        resNote.textSize = 12f
        res.addView(resNote, lp(t = 10))

        box.addView(sectionLabel(tr("এককে রূপান্তর"), 4))
        val all = card(12)
        val vals = ArrayList<TextView>()
        for ((i, n) in names.withIndex()) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.setPadding(dp(4), dp(10), dp(4), dp(10))
            row.addView(label(n, 14f, Ui.MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val v = label("—", 14f, Ui.TEXT, true)
            vals.add(v)
            row.addView(v)
            all.addView(row)
            if (i < names.size - 1) all.addView(View(this).also { it.setBackgroundColor(Ui.LINE) }, lp(height = 1))
        }
        box.addView(all, lp(b = 12))

        val defs = card(16)
        defs.addView(label(tr("ব্যবহৃত মান"), 14f, Ui.TEXT, true))
        defs.addView(
            label(
                tr("১ শতাংশ = ৪৩৫.৬ বর্গফুট\n১ কাঠা = ") + bn(fmtNum(katha, 4)) + tr(" বর্গফুট\n১ বিঘা = ") +
                    bn(fmtNum(bigha, 4)) + tr(" কাঠা\n১ একর = ১০০ শতাংশ"),
                13f, Ui.MUTED
            ),
            lp(t = 4, b = 10)
        )
        defs.addView(pill(tr("কাঠা ও বিঘার মান বদলান"), Ui.KEY_OP, Ui.PRIMARY) { showSettings() })
        box.addView(defs, lp(b = 8))
        box.addView(label(tr("ফলাফল আনুমানিক; সঠিকতা দেওয়া মাপের ওপর নির্ভর করে।"), 12f, Ui.MUTED), lp(l = 4))

        fun num(e: EditText) = asciiDigits(e.text.toString()).replace(",", "").trim().toDoubleOrNull()

        refreshHook = {
            val f = factors()
            var sqft: Double? = null
            if (shape == 3) {
                num(in1)?.let { sqft = it * f[from.selectedItemPosition] }
            } else {
                val a = num(in1)
                val b = num(in2)
                val k = lenFeet[lenUnit]
                val u = lenNames[lenUnit]
                plot.t1 = if (shape == 1) in2.text.toString() + " " + u else in1.text.toString() + " " + u
                plot.t2 = if (shape == 2) "" else in1.text.toString() + " " + u
                if (shape == 0) { plot.t1 = in1.text.toString() + " " + u; plot.t2 = in2.text.toString() + " " + u }
                if (shape == 1) { plot.t1 = in2.text.toString() + " " + u; plot.t2 = in1.text.toString() + " " + u }
                if (shape == 2) { plot.t1 = in1.text.toString() + " " + u; plot.t2 = "" }
                plot.invalidate()
                sqft = when (shape) {
                    0 -> if (a != null && b != null) a * k * b * k else null
                    1 -> if (a != null && b != null) 0.5 * a * k * b * k else null
                    else -> if (a != null) Math.PI * a * k * a * k else null
                }
            }
            val sq = sqft
            if (sq == null) {
                resVal.text = "—"
                resNote.text = ""
                for (t in vals) t.text = "—"
            } else {
                resVal.text = bn(groupIndian(Math.round(sq))) + " " + names[4]
                resNote.text = "≈ " + bn(fmtNum(sq / f[0], 2)) + " " + names[0] + " · " + tr("আনুমানিক")
                for (i in names.indices) vals[i].text = bn(fmtNum(sq / f[i], 4))
            }
        }
        in1.onChange { refresh() }
        in2.onChange { refresh() }
        from.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { refresh() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        refresh()
    }

    private var refreshHook: (() -> Unit)? = null
    private fun refresh() { refreshHook?.invoke() }

    private fun showSettings() {
        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(20), dp(10), dp(20), 0)

        form.addView(label(tr("১ কাঠা = কত বর্গফুট"), 13f, Ui.MUTED))
        val k = EditText(this)
        k.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        k.setText(fmtNum(katha, 4))
        form.addView(k, lp())

        form.addView(label(tr("১ বিঘা = কত কাঠা"), 13f, Ui.MUTED), lp(t = 10))
        val b = EditText(this)
        b.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        b.setText(fmtNum(bigha, 4))
        form.addView(b, lp())

        val dlg = AlertDialog.Builder(this)
            .setTitle(tr("কাঠা ও বিঘার মান"))
            .setView(form)
            .setPositiveButton(tr("সংরক্ষণ"), null)
            .setNegativeButton(tr("বাতিল"), null)
            .setNeutralButton(tr("ডিফল্ট (৭২০, ২০)"), null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val kv = asciiDigits(k.text.toString()).trim().toDoubleOrNull()
                val bv = asciiDigits(b.text.toString()).trim().toDoubleOrNull()
                if (kv == null || bv == null || kv <= 0.0 || bv <= 0.0) {
                    Toast.makeText(this, tr("সঠিক সংখ্যা লিখুন"), Toast.LENGTH_SHORT).show()
                } else {
                    katha = kv
                    bigha = bv
                    prefs.edit().putString("katha", kv.toString()).putString("bigha", bv.toString()).apply()
                    dlg.dismiss()
                    build()
                }
            }
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                k.setText("720")
                b.setText("20")
            }
        }
        dlg.show()
    }
}
