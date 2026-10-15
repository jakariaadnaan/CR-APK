package com.example.callrec

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
        c.setRequestProperty("User-Agent", "AmarKhata/1.0")
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

    protected fun build(title: String): LinearLayout {
        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)
        setScreen(title, scroll)
        return box
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
                err = if (e.code == 404) "404" else "সার্ভার সাড়া দিচ্ছে না (" + bn(e.code.toString()) + ")"
            } catch (e: Throwable) {
                err = "ইন্টারনেট সংযোগ নেই বা সার্ভারে পৌঁছানো যায়নি"
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
        build("কারেন্সি কনভার্টার")
        val c = card(14)
        c.addView(label("পরিমাণ", 12f, Ui.MUTED, true))
        amount = field("যেমন: ১০০", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        amount.setText("1")
        amount.onChange { calc() }
        c.addView(amount, lp(b = 8))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        from = Spinner(this)
        to = Spinner(this)
        row.addView(from, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(pill("⇄", Ui.BG, Ui.PRIMARY) {
            val a = from.selectedItemPosition
            from.setSelection(to.selectedItemPosition)
            to.setSelection(a)
        }, lp(l = 6, r = 6))
        row.addView(to, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(row, lp())
        box.addView(c, lp(b = 12))

        val r = card(14)
        result = label("রেট আনা হচ্ছে…", 20f, Ui.PRIMARY, true)
        r.addView(result)
        note = label("", 12f, Ui.MUTED)
        r.addView(note, lp(t = 6, b = 8))
        r.addView(pill("রেট নতুন করে আনুন", Ui.BG, Ui.PRIMARY) { load() })
        box.addView(r, lp())

        val prefs = getSharedPreferences("currency", 0)
        val cached = prefs.getString("rates", null)
        if (cached != null) {
            try { setRates(JSONObject(cached), prefs.getLong("time", 0L), true) } catch (e: Throwable) {}
        }
        load()
    }

    private fun load() {
        note.text = "রেট আনা হচ্ছে…"
        fetch("https://open.er-api.com/v6/latest/USD", { body ->
            try {
                val j = JSONObject(body)
                val r = j.getJSONObject("rates")
                val now = System.currentTimeMillis()
                getSharedPreferences("currency", 0).edit().putString("rates", r.toString()).putLong("time", now).apply()
                setRates(r, now, false)
            } catch (e: Throwable) {
                note.text = "রেট পড়া যায়নি"
            }
        }, { err ->
            note.text = if (rates.length() > 0) "অফলাইন: সর্বশেষ সংরক্ষিত রেট দেখানো হচ্ছে" else err
            if (rates.length() == 0) result.text = "রেট পাওয়া যায়নি"
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
        note.text = (if (cached) "সংরক্ষিত রেট · " else "আপডেট: ") + dateText(time)
        calc()
    }

    private fun calc() {
        if (codes.isEmpty() || rates.length() == 0) return
        val a = asciiDigits(amount.text.toString()).toDoubleOrNull()
        if (a == null) { result.text = "পরিমাণ লিখুন"; return }
        val f = from.selectedItem as String
        val t = to.selectedItem as String
        val v = a / rates.getDouble(f) * rates.getDouble(t)
        val one = rates.getDouble(t) / rates.getDouble(f)
        result.text = bn(String.format(Locale.US, "%,.2f", v)) + " " + t
        note.text = bn("1 " + f + " = " + String.format(Locale.US, "%.4f", one) + " " + t) + "\n" + note.text.toString().substringAfter("\n")
    }
}

/** অভিধান: English word meanings from a free dictionary service. */
class DictionaryActivity : OnlineBase() {

    private lateinit var input: EditText
    private lateinit var out: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build("অভিধান")
        val c = card(14)
        input = field("ইংরেজি শব্দ লিখুন, যেমন: book")
        input.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        input.setOnEditorActionListener { _, _, _ -> search(); true }
        c.addView(input, lp(b = 8))
        c.addView(pill("খুঁজুন") { search() })
        box.addView(c, lp(b = 12))
        out = LinearLayout(this)
        out.orientation = LinearLayout.VERTICAL
        box.addView(out, lp())
        box.addView(label("ইংরেজি শব্দের ইংরেজি অর্থ ও উদাহরণ দেখায়; বাংলা অর্থ নেই।", 12f, Ui.MUTED), lp(l = 4, t = 8))
    }

    private fun search() {
        val w = input.text.toString().trim().lowercase()
        if (w.isEmpty()) return
        out.removeAllViews()
        out.addView(label("খোঁজা হচ্ছে…", 13f, Ui.MUTED))
        fetch("https://api.dictionaryapi.dev/api/v2/entries/en/" + NetUtil.enc(w), { body ->
            out.removeAllViews()
            try {
                show(JSONArray(body))
            } catch (e: Throwable) {
                out.addView(label("ফলাফল পড়া যায়নি", 13f, Ui.MUTED))
            }
        }, { err ->
            out.removeAllViews()
            out.addView(label(if (err == "404") "শব্দটি পাওয়া যায়নি" else err, 14f, Ui.MUTED))
        })
    }

    private fun show(arr: JSONArray) {
        val first = arr.getJSONObject(0)
        val head = card(14)
        head.addView(label(first.optString("word"), 22f, Ui.TEXT, true))
        val ph = first.optString("phonetic")
        if (ph.isNotEmpty()) head.addView(label(ph, 14f, Ui.PRIMARY))
        out.addView(head, lp(b = 10))
        for (i in 0 until arr.length()) {
            val meanings = arr.getJSONObject(i).optJSONArray("meanings") ?: continue
            for (k in 0 until meanings.length()) {
                val m = meanings.getJSONObject(k)
                val c = card(14)
                c.addView(label(m.optString("partOfSpeech"), 13f, Ui.PRIMARY, true))
                val defs = m.optJSONArray("definitions")
                if (defs != null) {
                    for (d in 0 until Math.min(3, defs.length())) {
                        val o = defs.getJSONObject(d)
                        c.addView(label(bn((d + 1).toString()) + ". " + o.optString("definition"), 14f, Ui.TEXT), lp(t = 6))
                        val ex = o.optString("example")
                        if (ex.isNotEmpty()) c.addView(label("“" + ex + "”", 13f, Ui.MUTED), lp(t = 2, l = 14))
                    }
                }
                val syn = m.optJSONArray("synonyms")
                if (syn != null && syn.length() > 0) {
                    val l = ArrayList<String>()
                    for (s in 0 until Math.min(6, syn.length())) l.add(syn.getString(s))
                    c.addView(label("সমার্থক: " + l.joinToString(", "), 13f, Ui.MUTED), lp(t = 8))
                }
                out.addView(c, lp(b = 10))
            }
        }
    }
}
