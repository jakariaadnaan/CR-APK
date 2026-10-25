package com.example.callrec

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout

/** Tools of one kind that share a single entry in the tool list; the header offers a mode switch between them. */
object Modes {
    class Group(val title: String, val modes: List<Pair<String, Class<*>>>)

    val groups: List<Group> = listOf(
        Group("ক্যালকুলেটর", listOf("সাধারণ" to CalcActivity::class.java, "সায়েন্টিফিক" to SciCalcActivity::class.java)),
        Group("খরচ ও বাকির খাতা", listOf("খরচের খাতা" to LedgerActivity::class.java, "বাকির খাতা" to DebtActivity::class.java)),
        Group("করণীয় ও নোট", listOf("করণীয়" to TodoActivity::class.java, "নোট" to NotesActivity::class.java)),
        Group("ছবি কনভার্টার", listOf("ফরম্যাট বদল" to ImageFormatActivity::class.java, "সাইজ কমানো" to ImageCompressActivity::class.java, "ছবি বড় করা" to UpscaleActivity::class.java)),
        Group("PDF বানান", listOf("লেখা থেকে" to TextToPdfActivity::class.java, "ওয়েবপেজ থেকে" to WebToPdfActivity::class.java, "ইনভয়েস" to InvoicePdfActivity::class.java)),
        Group("অডিও কনভার্টার", listOf("অডিও" to AudioConvertActivity::class.java, "ভিডিও থেকে অডিও" to VideoToAudioActivity::class.java)),
        Group("টেক্সট টুলস", listOf("কেস বদল" to TextCaseActivity::class.java, "শব্দ গণনা" to CounterActivity::class.java, "ইউনিকোড" to UnicodeActivity::class.java)),
        Group("QR ও বারকোড", listOf("QR তৈরি" to QrGenActivity::class.java, "বারকোড তৈরি" to BarcodeGenActivity::class.java, "স্ক্যান" to ScannerActivity::class.java)),
        Group("IP ও WHOIS", listOf("আমার IP" to IpActivity::class.java, "WHOIS" to WhoisActivity::class.java))
    )

    fun groupOf(a: Activity): Group? = groups.firstOrNull { g -> g.modes.any { it.second == a.javaClass } }

    /** The header title for a tool that belongs to a group, otherwise the given one. */
    fun title(a: Activity, own: String): String = groupOf(a)?.let { tr(it.title) } ?: own

    /** A small pill for the header: shows the current mode and opens the list of modes. Null when the tool has no siblings. */
    fun chip(a: Activity): View? {
        val g = groupOf(a) ?: return null
        val idx = g.modes.indexOfFirst { it.second == a.javaClass }
        val c = LinearLayout(a)
        c.gravity = Gravity.CENTER_VERTICAL
        c.background = a.bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.88f), 18, Ui.mix(Ui.PRIMARY, Ui.CARD, 0.5f), 1)
        c.setPadding(a.dp(12), a.dp(7), a.dp(10), a.dp(7))
        c.addView(a.label(tr(g.modes[idx].first), 12f, Ui.PRIMARY, true))
        c.addView(a.label(" ▾", 12f, Ui.PRIMARY, true))
        c.setOnClickListener {
            val names = g.modes.mapIndexed { i, m -> (if (i == idx) "✓  " else "     ") + tr(m.first) }.toTypedArray()
            android.app.AlertDialog.Builder(a).setTitle(tr("মোড বদলান")).setItems(names) { _, w ->
                if (w != idx) {
                    a.startActivity(Intent(a, g.modes[w].second))
                    a.overridePendingTransition(0, 0)
                    a.finish()
                }
            }.show()
        }
        return c
    }
}
