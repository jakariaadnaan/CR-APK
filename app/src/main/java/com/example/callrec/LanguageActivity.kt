package com.example.callrec

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** Language page: the app can be shown in English or Bangla now; the other languages are listed for later. */
class LanguageActivity : GuardedActivity() {

    private class L(val native: String, val english: String, val ready: Boolean, val code: String)

    private val langs = listOf(
        L("বাংলা", "Bengali", true, "bn"),
        L("English", "English", true, "en"),
        L("हिन्दी", "Hindi", false, "hi"),
        L("اردو", "Urdu", false, "ur"),
        L("العربية", "Arabic", false, "ar"),
        L("Español", "Spanish", false, "es"),
        L("Français", "French", false, "fr"),
        L("Deutsch", "German", false, "de"),
        L("Português", "Portuguese", false, "pt"),
        L("Русский", "Russian", false, "ru"),
        L("日本語", "Japanese", false, "ja"),
        L("中文", "Chinese", false, "zh"),
        L("한국어", "Korean", false, "ko"),
        L("Türkçe", "Turkish", false, "tr"),
        L("Bahasa Indonesia", "Indonesian", false, "id")
    )

    private lateinit var box: LinearLayout
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)
        setScreen(tr("ভাষা"), scroll)
        render()
    }

    private fun changed() {
        Ui.stamp++
        recreate()
    }

    private fun section(t: String) {
        box.addView(label(t, 13f, Ui.PRIMARY, true), lp(l = 6, t = 14, b = 8))
    }

    private fun render() {
        box.removeAllViews()
        box.addView(label(tr("পুরো অ্যাপের ভাষা বদলাবে: টুল, সেটিংস, বিজ্ঞপ্তি সব।"), 13f, Ui.MUTED), lp(l = 6, b = 4))
        section(tr("অ্যাপের ভাষা"))
        val cur = if (Lang.en) "en" else "bn"
        val rows = langs.map { l ->
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(18), dp(14), dp(18), dp(14))
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            val sel = l.code == cur
            col.addView(label(l.native, 16f, if (l.ready) Ui.TEXT else Ui.MUTED, sel))
            col.addView(label(l.english, 12f, Ui.MUTED), lp(t = 1))
            r.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (sel) {
                r.addView(label("✓", 18f, Ui.PRIMARY, true))
            } else if (!l.ready) {
                val soon = label(tr("শীঘ্রই"), 11f, Ui.MUTED, true)
                soon.background = bg(Ui.BG, 10)
                soon.setPadding(dp(8), dp(3), dp(8), dp(3))
                r.addView(soon)
            }
            r.setOnClickListener {
                it.tapFx()
                if (!l.ready) {
                    Toast.makeText(this, tr("এই ভাষা শীঘ্রই আসছে"), Toast.LENGTH_SHORT).show()
                } else if (!sel) {
                    Lang.set(this, l.code == "en")
                    changed()
                }
            }
            r as View
        }
        box.addView(groupCard(rows), lp())

        section(tr("সংখ্যার ধরন"))
        val bnDigits = prefs.getBoolean("digitsBn", true)
        val digitRows = listOf(tr("বাংলা (১২৩)"), tr("ইংরেজি (123)")).mapIndexed { i, n ->
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(18), dp(16), dp(18), dp(16))
            val on = (i == 0) == bnDigits
            r.addView(label(n, 16f, Ui.TEXT, on), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (on) r.addView(label("✓", 18f, Ui.PRIMARY, true))
            r.setOnClickListener {
                it.tapFx()
                if (!on) {
                    prefs.edit().putBoolean("digitsBn", i == 0).apply()
                    changed()
                }
            }
            r as View
        }
        box.addView(groupCard(digitRows), lp())
    }
}
