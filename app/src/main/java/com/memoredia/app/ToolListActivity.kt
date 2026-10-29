package com.memoredia.app

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
            s.hint = tr("টুল খুঁজুন")
            s.textSize = 15f
            s.setTextColor(Ui.TEXT)
            s.inputType = InputType.TYPE_CLASS_TEXT
            s.isSingleLine = true
            s.background = bg(Ui.CARD, 16, Ui.LINE, 1)
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

        val title = if (catId == "all") tr("সব টুল") else if (catId == "fav") tr("প্রিয় টুল") else Registry.cat(catId).name
        setScreen(title, col, null, true, navBar(if (catId == "fav") 2 else 1))
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::box.isInitialized) render()
    }

    private fun render() {
        box.removeAllViews()
        val q = search?.text?.toString() ?: ""
        if (catId == "fav") {
            val favs = Registry.favIds(this).mapNotNull { id -> Registry.tools.firstOrNull { it.id == id && it.id !in Registry.hidden } }
            if (favs.isEmpty()) {
                val e = label(tr("এখনো কোনো প্রিয় টুল নেই। যেকোনো টুলে চেপে ধরে রাখলে সেটি এখানে যোগ হবে।"), 14f, Ui.MUTED)
                e.gravity = Gravity.CENTER
                e.setPadding(dp(16), dp(40), dp(16), dp(30))
                box.addView(e, lp())
            } else {
                box.addView(groupCard(favs.map { toolRow(it) { render() } }), lp(b = 10))
            }
            return
        }
        val cats = if (catId == "all") Registry.cats else listOf(Registry.cat(catId))
        var any = false
        for (c in cats) {
            val list = Registry.inCat(c.id).filter { Registry.matches(it, q) }
            if (list.isEmpty()) continue
            any = true
            if (catId == "all") {
                box.addView(label(tr(c.name) + " (" + bn(list.size.toString()) + ")", 14f, Ui.MUTED, true), lp(l = 6, t = 6, b = 8))
            }
            box.addView(groupCard(list.map { toolRow(it) { render() } }), lp(b = 10))
        }
        if (!any) {
            val e = label(tr("কিছু পাওয়া যায়নি।"), 14f, Ui.MUTED)
            e.gravity = Gravity.CENTER
            e.setPadding(0, dp(30), 0, dp(30))
            box.addView(e, lp())
        }
    }
}
