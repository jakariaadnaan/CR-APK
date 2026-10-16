package com.example.callrec

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
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)
        setScreen(tr("জমির মাপ"), scroll)
        build()
    }

    private fun build() {
        box.removeAllViews()

        val c = card(16)
        val input = EditText(this)
        input.hint = tr("পরিমাণ লিখুন")
        input.textSize = 22f
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        c.addView(input, lp())

        val from = Spinner(this)
        val to = Spinner(this)
        from.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        to.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        from.setSelection(0)
        to.setSelection(1)

        val swap = label("⇄", 24f, Ui.PRIMARY, true)
        swap.gravity = Gravity.CENTER
        swap.setPadding(dp(10), 0, dp(10), 0)
        val pick = LinearLayout(this)
        pick.orientation = LinearLayout.HORIZONTAL
        pick.gravity = Gravity.CENTER_VERTICAL
        pick.addView(from, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        pick.addView(swap)
        pick.addView(to, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(pick, lp(t = 8))

        val result = label("", 26f, Ui.PRIMARY, true)
        result.gravity = Gravity.CENTER
        c.addView(result, lp(t = 16, b = 4))
        box.addView(c, lp(b = 12))

        // All units at once
        val all = card(16)
        all.addView(label(tr("সব এককে"), 15f, Ui.TEXT, true), lp(b = 6))
        val vals = ArrayList<TextView>()
        for (n in names) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.addView(label(n, 14f, Ui.MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val v = label("—", 14f, Ui.TEXT, true)
            vals.add(v)
            row.addView(v)
            all.addView(row, lp(t = 6))
        }
        box.addView(all, lp(b = 12))

        // Current definitions + edit
        val defs = card(16)
        defs.addView(label(tr("ব্যবহৃত মান"), 15f, Ui.TEXT, true))
        defs.addView(
            label(
                tr("১ শতাংশ = ৪৩৫.৬ বর্গফুট\n১ কাঠা = ") + bn(fmtNum(katha, 4)) + tr(" বর্গফুট\n১ বিঘা = ") +
                    bn(fmtNum(bigha, 4)) + tr(" কাঠা\n১ একর = ১০০ শতাংশ"),
                13f, Ui.MUTED
            ),
            lp(t = 4, b = 10)
        )
        defs.addView(pill(tr("কাঠা ও বিঘার মান বদলান"), Ui.BG, Ui.PRIMARY) { showSettings() })
        box.addView(defs, lp())

        fun update() {
            val v = asciiDigits(input.text.toString()).replace(",", "").trim().toDoubleOrNull()
            if (v == null) {
                result.text = ""
                for (t in vals) t.text = "—"
                return
            }
            val f = factors()
            val sqft = v * f[from.selectedItemPosition]
            val b = to.selectedItemPosition
            result.text = bn(fmtNum(sqft / f[b], 4)) + " " + names[b]
            for (i in names.indices) vals[i].text = bn(fmtNum(sqft / f[i], 4))
        }

        input.onChange { update() }
        val sel = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                update()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        from.onItemSelectedListener = sel
        to.onItemSelectedListener = sel
        swap.setOnClickListener {
            val a = from.selectedItemPosition
            from.setSelection(to.selectedItemPosition)
            to.setSelection(a)
        }
    }

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
