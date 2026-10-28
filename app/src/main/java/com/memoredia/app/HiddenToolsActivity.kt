package com.memoredia.app

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch

/** টুল দেখানো বা লুকানো: switch off the tools you never use so home stays short. */
class HiddenToolsActivity : GuardedActivity() {

    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(10), dp(14), dp(24))
        scroll.addView(box)
        setScreen(tr("টুল দেখানো বা লুকানো"), scroll)
        render()
    }

    private fun sw(on: Boolean): Switch = styledSwitch(on)

    private fun render() {
        box.removeAllViews()
        box.addView(label(tr("বন্ধ করা টুল হোম, সার্চ ও তালিকায় দেখাবে না। ডেটা মুছবে না।"), 13f, Ui.MUTED), lp(l = 6, b = 10))
        for (c in Registry.cats) {
            val list = Registry.tools.filter { it.cat == c.id }
            if (list.isEmpty()) continue
            box.addView(label(c.name, 14f, Ui.MUTED, true), lp(l = 6, t = 6, b = 8))
            val rows = list.map { t ->
                val visible = t.id !in Registry.hidden
                val r = listRow(c.icon, t.title, t.sub, Ui.TEXT, sw(visible), false)
                r.setOnClickListener {
                    Registry.setHidden(this, t.id, visible)
                    render()
                }
                r as android.view.View
            }
            box.addView(groupCard(rows), lp(b = 10))
        }
    }
}
