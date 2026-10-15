package com.example.callrec

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

/** One category's tools, or every tool grouped by category when the extra "cat" is "all". */
class ToolListActivity : GuardedActivity() {

    private lateinit var box: LinearLayout
    private var catId = "all"
    private var search: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        catId = intent.getStringExtra("cat") ?: "all"

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL

        if (catId == "all") {
            val s = EditText(this)
            s.hint = "টুল খুঁজুন"
            s.textSize = 15f
            s.setTextColor(Ui.TEXT)
            s.inputType = InputType.TYPE_CLASS_TEXT
            s.isSingleLine = true
            s.background = bg(Ui.CARD, 12)
            s.setPadding(dp(14), dp(10), dp(14), dp(10))
            s.onChange { render() }
            search = s
            col.addView(s, lp(l = 14, t = 12, r = 14, b = 4))
        }

        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(12), dp(14), dp(20))
        scroll.addView(box)
        col.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val title = if (catId == "all") "সব টুল" else Registry.cat(catId).name
        setScreen(title, col, null, true, navBar(1))
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::box.isInitialized) render()
    }

    private fun render() {
        box.removeAllViews()
        val q = search?.text?.toString() ?: ""
        val cats = if (catId == "all") Registry.cats else listOf(Registry.cat(catId))
        var any = false
        for (c in cats) {
            val list = Registry.inCat(c.id).filter { Registry.matches(it, q) }
            if (list.isEmpty()) continue
            any = true
            if (catId == "all") {
                box.addView(label(c.name + " (" + bn(list.size.toString()) + ")", 14f, Ui.MUTED, true), lp(l = 4, t = 8, b = 6))
            }
            for (t in list) box.addView(toolRow(t) { render() }, lp(b = 8))
        }
        if (!any) {
            val e = label("কিছু পাওয়া যায়নি।", 14f, Ui.MUTED)
            e.gravity = Gravity.CENTER
            e.setPadding(0, dp(30), 0, dp(30))
            box.addView(e, lp())
        }
        box.addView(label("প্রিয় করতে বা সরাতে টুলে চেপে ধরে রাখুন।", 12f, Ui.MUTED), lp(l = 4, t = 4))
    }
}
