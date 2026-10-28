package com.memoredia.app

import android.os.Bundle
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class HttpErr(val code: Int) : Exception("HTTP $code")

object NetUtil {
    fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "Memoredia/1.0")
        c.setRequestProperty("Accept", "application/json")
        try {
            val code = c.responseCode
            if (code >= 400) throw HttpErr(code)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

/** Shared pieces of the screens that call a web service. */
abstract class OnlineBase : GuardedActivity() {

    protected lateinit var box: LinearLayout

    private var screenTitle = ""
    private lateinit var scrollRef: ScrollView

    protected fun build(title: String): LinearLayout {
        scrollRef = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scrollRef.addView(box)
        screenTitle = title
        setScreen(title, scrollRef)
        return box
    }

    /** Puts the mockup's wide bottom button under the content (call once, after [build]). */
    protected fun addBottom(btn: View) {
        (scrollRef.parent as? android.view.ViewGroup)?.removeView(scrollRef)
        val w = LinearLayout(this)
        w.addView(btn, lp(height = dp(48)))
        setScreen(screenTitle, scrollRef, null, true, bottomBar(w))
    }

    protected fun addBottomRow(w: LinearLayout) {
        (scrollRef.parent as? android.view.ViewGroup)?.removeView(scrollRef)
        setScreen(screenTitle, scrollRef, null, true, bottomBar(w))
    }

    protected fun field(hint: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
        val e = EditText(this)
        e.hint = hint
        e.inputType = type
        e.isSingleLine = true
        e.setTextColor(Ui.TEXT)
        return e
    }

    protected fun kv(into: LinearLayout, k: String, v: String) {
        if (v.isBlank()) return
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.addView(label(k, 13f, Ui.MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val t = label(v, 14f, Ui.TEXT, true)
        t.gravity = Gravity.END
        t.setTextIsSelectable(true)
        r.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f))
        into.addView(r, lp(b = 8))
    }

    /** Runs [call] off the main thread and hands the text or a friendly error to the UI. */
    protected fun fetch(url: String, onOk: (String) -> Unit, onErr: (String) -> Unit) {
        Thread {
            var body: String? = null
            var err = ""
            try {
                body = NetUtil.get(url)
            } catch (e: HttpErr) {
                err = if (e.code == 404) "404" else tr("সার্ভার সাড়া দিচ্ছে না (") + bn(e.code.toString()) + ")"
            } catch (e: Throwable) {
                err = tr("ইন্টারনেট সংযোগ নেই বা সার্ভারে পৌঁছানো যায়নি")
            }
            val b = body
            runOnUiThread { if (b != null) onOk(b) else onErr(err) }
        }.start()
    }
}

/** কারেন্সি কনভার্টার: live rates from a free service, last rates kept for offline use. */
class CurrencyActivity : OnlineBase() {

    private var rates = JSONObject()
    private var codes = listOf<String>()
    private lateinit var amount: EditText
    private lateinit var from: Spinner
    private lateinit var to: Spinner
    private lateinit var result: TextView
    private lateinit var note: TextView
    private val top = listOf("BDT", "USD", "EUR", "GBP", "SAR", "AED", "KWD", "QAR", "MYR", "SGD", "INR", "JPY", "CNY", "AUD", "CAD")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build(tr("কারেন্সি কনভার্টার"))
        // "You pay" card
        val c = card(14)
        c.addView(captionLabel(tr("আপনি দিচ্ছেন")))
        val top1 = LinearLayout(this)
        top1.orientation = LinearLayout.HORIZONTAL
        top1.gravity = Gravity.CENTER_VERTICAL
        amount = field(tr("যেমন: ১০০"), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        amount.setText("1")
        amount.textSize = 26f
        amount.setTypeface(null, android.graphics.Typeface.BOLD)
        amount.background = null
        amount.setPadding(0, dp(4), 0, dp(4))
        amount.onChange { calc() }
        from = Spinner(this)
        to = Spinner(this)
        top1.addView(amount, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top1.addView(from, LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT))
        c.addView(top1)
        box.addView(c, lp())

        val swap = label("⇅", 20f, Ui.PRIMARY, true)
        swap.gravity = Gravity.CENTER
        swap.background = bg(Ui.KEY_OP, 20, Ui.mix(Ui.PRIMARY, Ui.CARD, 0.5f), 1)
        swap.setOnClickListener {
            val a = from.selectedItemPosition
            from.setSelection(to.selectedItemPosition)
            to.setSelection(a)
        }
        box.addView(swap, lp(width = dp(40), height = dp(40), t = 6, b = 6).also { it.gravity = Gravity.CENTER_HORIZONTAL })

        // "You receive" card
        val r = LinearLayout(this)
        r.orientation = LinearLayout.VERTICAL
        r.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.9f), Ui.cardR, Ui.mix(Ui.PRIMARY, Ui.CARD, 0.6f), 1)
        r.setPadding(dp(16), dp(14), dp(16), dp(14))
        r.addView(captionLabel(tr("আপনি পাচ্ছেন")).also { it.setTextColor(Ui.PRIMARY) })
        val top2 = LinearLayout(this)
        top2.orientation = LinearLayout.HORIZONTAL
        top2.gravity = Gravity.CENTER_VERTICAL
        result = label(tr("রেট আনা হচ্ছে…"), 26f, Ui.TEXT, true)
        top2.addView(result, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top2.addView(to, LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT))
        r.addView(top2)
        box.addView(r, lp(b = 12))

        note = label("", 12f, Ui.MUTED)
        box.addView(note, lp(l = 4, b = 8))
        box.addView(label(tr("রেট ইন্টারনেট থেকে আসে; ডিভাইস অফলাইন থাকলে সর্বশেষ সংরক্ষিত রেট দেখায়।"), 12f, Ui.MUTED), lp(l = 4))
        addBottom(primaryButton(tr("রেট নতুন করে আনুন")) { load() })

        val prefs = getSharedPreferences("currency", 0)
        val cached = prefs.getString("rates", null)
        if (cached != null) {
            try { setRates(JSONObject(cached), prefs.getLong("time", 0L), true) } catch (e: Throwable) {}
        }
        load()
    }

    private fun load() {
        note.text = tr("রেট আনা হচ্ছে…")
        fetch("https://open.er-api.com/v6/latest/USD", { body ->
            try {
                val j = JSONObject(body)
                val r = j.getJSONObject("rates")
                val now = System.currentTimeMillis()
                getSharedPreferences("currency", 0).edit().putString("rates", r.toString()).putLong("time", now).apply()
                setRates(r, now, false)
            } catch (e: Throwable) {
                note.text = tr("রেট পড়া যায়নি")
            }
        }, { err ->
            note.text = if (rates.length() > 0) tr("অফলাইন: সর্বশেষ সংরক্ষিত রেট দেখানো হচ্ছে") else err
            if (rates.length() == 0) result.text = tr("রেট পাওয়া যায়নি")
        })
    }

    private fun setRates(r: JSONObject, time: Long, cached: Boolean) {
        rates = r
        val all = ArrayList<String>()
        val it = r.keys()
        while (it.hasNext()) all.add(it.next())
        all.sort()
        val list = ArrayList<String>()
        for (t in top) if (all.contains(t)) list.add(t)
        for (a in all) if (!list.contains(a)) list.add(a)
        val keepFrom = if (codes.isNotEmpty()) from.selectedItem as String else "USD"
        val keepTo = if (codes.isNotEmpty()) to.selectedItem as String else "BDT"
        codes = list
        val ad = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, codes)
        from.adapter = ad
        to.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, codes)
        from.setSelection(Math.max(0, codes.indexOf(keepFrom)))
        to.setSelection(Math.max(0, codes.indexOf(keepTo)))
        val l = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { calc() }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        from.onItemSelectedListener = l
        to.onItemSelectedListener = l
        note.text = (if (cached) tr("সংরক্ষিত রেট · ") else tr("আপডেট: ")) + dateText(time)
        calc()
    }

    private fun calc() {
        if (codes.isEmpty() || rates.length() == 0) return
        val a = asciiDigits(amount.text.toString()).toDoubleOrNull()
        if (a == null) { result.text = tr("পরিমাণ লিখুন"); return }
        val f = from.selectedItem as String
        val t = to.selectedItem as String
        val v = a / rates.getDouble(f) * rates.getDouble(t)
        val one = rates.getDouble(t) / rates.getDouble(f)
        result.text = bn(String.format(Locale.US, "%,.2f", v))
        note.text = bn("1 " + f + " = " + String.format(Locale.US, "%.4f", one) + " " + t) + "\n" + note.text.toString().substringAfter("\n")
    }
}

/** অভিধান: English word meanings from a free dictionary service. */
class DictionaryActivity : OnlineBase() {

    private lateinit var input: EditText
    private lateinit var out: LinearLayout
    private lateinit var savedBox: LinearLayout
    private var curWord = ""
    private val sp by lazy { getSharedPreferences("dict", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build(tr("অভিধান"))
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        input = field(tr("ইংরেজি শব্দ খুঁজুন, যেমন: book"))
        input.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        input.setOnEditorActionListener { _, _, _ -> search(input.text.toString()); true }
        styleInput(input)
        row.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val go = iconBox(R.drawable.ic_search, Ui.PRIMARY, android.graphics.Color.WHITE, 46, 22, 10, 0)
        go.setOnClickListener { search(input.text.toString()) }
        row.addView(go, lp(width = dp(46), height = dp(46), l = 8))
        box.addView(row, lp(b = 12))
        out = LinearLayout(this)
        out.orientation = LinearLayout.VERTICAL
        box.addView(out, lp())
        savedBox = LinearLayout(this)
        savedBox.orientation = LinearLayout.VERTICAL
        box.addView(savedBox, lp(t = 6))
        box.addView(label(tr("ইংরেজি শব্দের ইংরেজি অর্থ ও উদাহরণ দেখায়; বাংলা অর্থ নেই। তথ্য dictionaryapi.dev থেকে আসে।"), 11f, Ui.MUTED), lp(l = 4, t = 8))
        addBottom(primaryButton(tr("শব্দ সংরক্ষণ")) { saveWord() })
        drawSaved()
    }

    private fun saved(): List<String> = sp.getString("saved", "")!!.split("\n").filter { it.isNotEmpty() }

    private fun saveWord() {
        if (curWord.isEmpty()) { android.widget.Toast.makeText(this, tr("আগে একটি শব্দ খুঁজুন"), android.widget.Toast.LENGTH_SHORT).show(); return }
        val l = saved().toMutableList()
        if (!l.contains(curWord)) l.add(0, curWord)
        sp.edit().putString("saved", l.take(100).joinToString("\n")).apply()
        android.widget.Toast.makeText(this, tr("সংরক্ষিত হয়েছে"), android.widget.Toast.LENGTH_SHORT).show()
        drawSaved()
    }

    private fun drawSaved() {
        savedBox.removeAllViews()
        val l = saved()
        if (l.isEmpty()) return
        savedBox.addView(sectionLabel(tr("সংরক্ষিত শব্দ")))
        val f = FlowLayout(this)
        for (w in l) {
            val c = choiceChip(w, false) { input.setText(w); search(w) }
            c.setOnLongClickListener {
                sp.edit().putString("saved", saved().filter { it != w }.joinToString("\n")).apply()
                drawSaved()
                true
            }
            f.addView(c)
        }
        savedBox.addView(f)
        savedBox.addView(label(tr("শব্দ মুছতে চেপে ধরে রাখুন"), 11f, Ui.MUTED), lp(t = 6, l = 2))
    }

    private fun search(word: String) {
        val w = word.trim().lowercase()
        if (w.isEmpty()) return
        out.removeAllViews()
        out.addView(label(tr("খোঁজা হচ্ছে…"), 13f, Ui.MUTED))
        fetch("https://api.dictionaryapi.dev/api/v2/entries/en/" + NetUtil.enc(w), { body ->
            out.removeAllViews()
            try {
                show(JSONArray(body))
            } catch (e: Throwable) {
                out.addView(label(tr("ফলাফল পড়া যায়নি"), 13f, Ui.MUTED))
            }
        }, { err ->
            out.removeAllViews()
            out.addView(label(if (err == "404") tr("শব্দটি পাওয়া যায়নি") else err, 14f, Ui.MUTED))
        })
    }

    private fun show(arr: JSONArray) {
        val first = arr.getJSONObject(0)
        curWord = first.optString("word")
        out.addView(label(curWord, 30f, Ui.TEXT, true), lp(l = 2))
        val ph = first.optString("phonetic")
        val firstPos = arr.getJSONObject(0).optJSONArray("meanings")?.getJSONObject(0)?.optString("partOfSpeech") ?: ""
        out.addView(label((if (ph.isNotEmpty()) ph + "   " else "") + firstPos, 13f, Ui.MUTED), lp(l = 2, t = 2, b = 12))
        val syns = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            val meanings = arr.getJSONObject(i).optJSONArray("meanings") ?: continue
            for (k in 0 until meanings.length()) {
                val m = meanings.getJSONObject(k)
                val c = card(14)
                c.addView(captionLabel(tr("সংজ্ঞা") + " · " + m.optString("partOfSpeech")).also { it.setTextColor(Ui.PRIMARY) })
                val defs = m.optJSONArray("definitions")
                if (defs != null) {
                    for (d in 0 until Math.min(3, defs.length())) {
                        val o = defs.getJSONObject(d)
                        c.addView(label(bn((d + 1).toString()) + ". " + o.optString("definition"), 14f, Ui.TEXT), lp(t = 6))
                        val ex = o.optString("example")
                        if (ex.isNotEmpty()) {
                            c.addView(captionLabel(tr("উদাহরণ")).also { it.layoutParams = lp(t = 6, b = 2, l = 14) })
                            c.addView(label("“" + ex + "”", 13f, Ui.TEXT), lp(l = 14))
                        }
                    }
                }
                val syn = m.optJSONArray("synonyms")
                if (syn != null) for (x in 0 until syn.length()) syns.add(syn.getString(x))
                out.addView(c, lp(b = 10))
            }
        }
        if (syns.isNotEmpty()) {
            out.addView(sectionLabel(tr("সম্পর্কিত শব্দ"), 4))
            val f = FlowLayout(this)
            for (w in syns.take(10)) f.addView(choiceChip(w, false) { input.setText(w); search(w) })
            out.addView(f, lp(b = 6))
        }
    }
}
