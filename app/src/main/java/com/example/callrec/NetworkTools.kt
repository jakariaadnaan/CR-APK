package com.example.callrec

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** আমার IP ও IP তথ্য: your public IP on open, or look up any IP address or domain. */
class IpActivity : OnlineBase() {

    private lateinit var input: android.widget.EditText
    private lateinit var out: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build(tr("আমার IP ও IP তথ্য"))
        input = field(tr("যেমন: 8.8.8.8"))
        input.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        input.setOnEditorActionListener { _, _, _ -> go(); true }
        box.addView(fieldBox(tr("অন্য IP বা ডোমেইন (খালি রাখলে আপনার নিজের)"), input), lp(b = 12))
        out = LinearLayout(this)
        out.orientation = LinearLayout.VERTICAL
        box.addView(out, lp())
        val w = LinearLayout(this)
        w.addView(primaryButton(tr("খুঁজুন")) { go() }, LinearLayout.LayoutParams(0, dp(48), 1f).also { it.rightMargin = dp(8) })
        w.addView(outlineButton(tr("আমার IP")) { input.setText(""); go() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        addBottomRow(w)
        go()
    }

    private fun go() {
        val q = input.text.toString().trim()
        out.removeAllViews()
        out.addView(label(tr("খোঁজা হচ্ছে…"), 13f, Ui.MUTED))
        fetch("https://ipwho.is/" + NetUtil.enc(q), { body ->
            out.removeAllViews()
            try {
                val j = JSONObject(body)
                if (!j.optBoolean("success", false)) {
                    out.addView(label(tr("তথ্য পাওয়া যায়নি: ") + j.optString("message"), 14f, Ui.MUTED))
                } else {
                    out.addView(resultBox(tr("আপনার IP"), j.optString("ip"), j.optString("type"), 26f).also { (it.getChildAt(1) as TextView).setTextIsSelectable(true) }, lp(b = 12))
                    val c = card(14)
                    kv(c, tr("দেশ"), j.optString("flag").let { "" }.ifEmpty { j.optString("country") })
                    kv(c, tr("অঞ্চল"), j.optString("region"))
                    kv(c, tr("শহর"), j.optString("city"))
                    kv(c, tr("পোস্টকোড"), j.optString("postal"))
                    if (j.has("latitude")) kv(c, tr("অবস্থান"), String.format(Locale.US, "%.4f, %.4f", j.optDouble("latitude"), j.optDouble("longitude")))
                    j.optJSONObject("timezone")?.let { kv(c, tr("টাইমজোন"), it.optString("id") + " (" + it.optString("utc") + ")") }
                    j.optJSONObject("connection")?.let {
                        kv(c, "ISP", it.optString("isp"))
                        kv(c, tr("প্রতিষ্ঠান"), it.optString("org"))
                        kv(c, "ASN", it.optString("asn"))
                        kv(c, tr("ডোমেইন"), it.optString("domain"))
                    }
                    out.addView(c, lp())
                }
            } catch (e: Throwable) {
                out.addView(label(tr("ফলাফল পড়া যায়নি"), 13f, Ui.MUTED))
            }
        }, { err ->
            out.removeAllViews()
            out.addView(label(if (err == "404") tr("তথ্য পাওয়া যায়নি") else err, 14f, Ui.MUTED))
        })
    }
}

/** WHOIS লুকআপ: domain registration facts through the public RDAP service. */
class WhoisActivity : OnlineBase() {

    private lateinit var input: android.widget.EditText
    private lateinit var out: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build(tr("WHOIS লুকআপ"))
        input = field(tr("যেমন: example.com"))
        input.setOnEditorActionListener { _, _, _ -> go(); true }
        box.addView(fieldBox(tr("ডোমেইন"), input), lp(b = 12))
        out = LinearLayout(this)
        out.orientation = LinearLayout.VERTICAL
        box.addView(out, lp())
        box.addView(label(tr("মালিকের ব্যক্তিগত তথ্য সাধারণত গোপন থাকে। .bd ডোমেইনের তথ্য নাও আসতে পারে।"), 12f, Ui.MUTED), lp(l = 4, t = 8))
        addBottom(primaryButton(tr("খুঁজুন")) { go() })
    }

    private fun clean(s: String): String =
        s.trim().lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore("/").substringBefore("?")

    private fun go() {
        val d = clean(input.text.toString())
        if (d.isEmpty() || !d.contains(".")) {
            out.removeAllViews()
            out.addView(label(tr("সঠিক ডোমেইন লিখুন"), 13f, Ui.MUTED))
            return
        }
        out.removeAllViews()
        out.addView(label(tr("খোঁজা হচ্ছে…"), 13f, Ui.MUTED))
        fetch("https://rdap.org/domain/" + NetUtil.enc(d), { body ->
            out.removeAllViews()
            try {
                show(JSONObject(body), d)
            } catch (e: Throwable) {
                out.addView(label(tr("ফলাফল পড়া যায়নি"), 13f, Ui.MUTED))
            }
        }, { err ->
            out.removeAllViews()
            out.addView(label(if (err == "404") tr("ডোমেইন নিবন্ধিত নয়, বা এর তথ্য পাওয়া যায়নি") else err, 14f, Ui.MUTED))
        })
    }

    private fun show(j: JSONObject, d: String) {
        out.addView(resultBox(tr("ডোমেইন"), j.optString("ldhName", d).lowercase(), "", 22f), lp(b = 12))
        val c = card(14)
        val ev = j.optJSONArray("events")
        if (ev != null) {
            for (i in 0 until ev.length()) {
                val e = ev.getJSONObject(i)
                val name = when (e.optString("eventAction")) {
                    "registration" -> tr("নিবন্ধনের তারিখ")
                    "expiration" -> tr("মেয়াদ শেষ")
                    "last changed" -> tr("সর্বশেষ বদল")
                    else -> ""
                }
                if (name.isNotEmpty()) kv(c, name, e.optString("eventDate").take(10))
            }
        }
        val st = j.optJSONArray("status")
        if (st != null) {
            val l = ArrayList<String>()
            for (i in 0 until st.length()) l.add(st.getString(i))
            kv(c, tr("স্ট্যাটাস"), l.joinToString("\n"))
        }
        val ent = j.optJSONArray("entities")
        if (ent != null) {
            for (i in 0 until ent.length()) {
                val e = ent.getJSONObject(i)
                val roles = e.optJSONArray("roles")?.toString() ?: ""
                if (roles.contains("registrar")) {
                    val vc = e.optJSONArray("vcardArray")?.optJSONArray(1)
                    if (vc != null) {
                        for (k in 0 until vc.length()) {
                            val row = vc.optJSONArray(k) ?: continue
                            if (row.optString(0) == "fn") kv(c, tr("রেজিস্ট্রার"), row.optString(3))
                        }
                    }
                }
            }
        }
        val ns = j.optJSONArray("nameservers")
        if (ns != null) {
            val l = ArrayList<String>()
            for (i in 0 until ns.length()) l.add(ns.getJSONObject(i).optString("ldhName").lowercase())
            kv(c, tr("নেমসার্ভার"), l.joinToString("\n"))
        }
        out.addView(c, lp())
    }
}

/** ইন্টারনেট স্পিড টেস্ট: ping, download and upload against Cloudflare's public test endpoints. */
class SpeedTestActivity : OnlineBase() {

    @Volatile private var alive = true
    @Volatile private var running = false

    private lateinit var pingV: TextView
    private lateinit var downV: TextView
    private lateinit var upV: TextView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build(tr("ইন্টারনেট স্পিড টেস্ট"))
        val dr = resultBox(tr("ডাউনলোড"), "—", "", 34f)
        downV = dr.getChildAt(1) as TextView
        box.addView(dr, lp(b = 12))
        val tiles = LinearLayout(this)
        val pt = statTile(tr("পিং"), "—")
        val ut = statTile(tr("আপলোড"), "—")
        pingV = pt.getChildAt(1) as TextView
        upV = ut.getChildAt(1) as TextView
        tiles.addView(pt, LinearLayout.LayoutParams(0, -2, 1f).also { it.rightMargin = dp(6) })
        tiles.addView(ut, LinearLayout.LayoutParams(0, -2, 1f).also { it.leftMargin = dp(6) })
        box.addView(tiles, lp(b = 12))
        status = label(tr("শুরু করতে বোতাম চাপুন"), 13f, Ui.MUTED)
        status.gravity = android.view.Gravity.CENTER
        box.addView(status, lp(t = 4, b = 8))
        box.addView(label(tr("টেস্টে প্রায় ৩০ MB ডেটা খরচ হতে পারে। মোবাইল ডেটায় সতর্ক থাকুন।"), 12f, Ui.MUTED), lp(l = 4, t = 4))
        addBottom(primaryButton(tr("টেস্ট শুরু করুন")) { start() })
    }

    override fun onDestroy() {
        alive = false
        super.onDestroy()
    }

    private fun big(c: LinearLayout, name: String, v: String): TextView {
        c.addView(label(name, 12f, Ui.MUTED, true), lp(t = 6))
        val t = label(v, 26f, Ui.PRIMARY, true)
        c.addView(t)
        return t
    }

    private fun ui(f: () -> Unit) = runOnUiThread { if (alive) f() }

    private fun mbps(bytes: Long, ns: Long): String =
        bn(String.format(Locale.US, "%.1f", bytes * 8.0 / 1e6 / (ns / 1e9))) + " Mbps"

    private fun start() {
        if (running) return
        running = true
        pingV.text = "—"; downV.text = "—"; upV.text = "—"
        Thread {
            try {
                ui { status.text = tr("পিং মাপা হচ্ছে…") }
                var best = Long.MAX_VALUE
                for (i in 0 until 4) {
                    val t0 = System.nanoTime()
                    val c = URL("https://speed.cloudflare.com/__down?bytes=0").openConnection() as HttpURLConnection
                    c.connectTimeout = 8000; c.readTimeout = 8000
                    c.inputStream.use { it.readBytes() }
                    c.disconnect()
                    best = Math.min(best, System.nanoTime() - t0)
                }
                ui { pingV.text = bn((best / 1_000_000).toString()) + " ms" }

                ui { status.text = tr("ডাউনলোড মাপা হচ্ছে…") }
                val dc = URL("https://speed.cloudflare.com/__down?bytes=25000000").openConnection() as HttpURLConnection
                dc.connectTimeout = 8000; dc.readTimeout = 10000
                val buf = ByteArray(65536)
                var total = 0L
                val t0 = System.nanoTime()
                var lastShow = t0
                dc.inputStream.use { ins ->
                    while (alive) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        total += n
                        val now = System.nanoTime()
                        if (now - lastShow > 300_000_000L) {
                            lastShow = now
                            val tt = total
                            val el = now - t0
                            ui { downV.text = mbps(tt, el) }
                        }
                        if (now - t0 > 8_000_000_000L) break
                    }
                }
                dc.disconnect()
                val dEl = System.nanoTime() - t0
                val dTot = total
                ui { downV.text = mbps(dTot, dEl) }

                ui { status.text = tr("আপলোড মাপা হচ্ছে…") }
                val size = 6_000_000
                val uc = URL("https://speed.cloudflare.com/__up").openConnection() as HttpURLConnection
                uc.requestMethod = "POST"
                uc.doOutput = true
                uc.connectTimeout = 8000; uc.readTimeout = 20000
                uc.setFixedLengthStreamingMode(size)
                val u0 = System.nanoTime()
                uc.outputStream.use { os ->
                    val chunk = ByteArray(65536)
                    var sent = 0
                    while (sent < size && alive) {
                        val n = Math.min(chunk.size, size - sent)
                        os.write(chunk, 0, n)
                        sent += n
                    }
                }
                uc.inputStream.use { it.readBytes() }
                uc.disconnect()
                val uEl = System.nanoTime() - u0
                ui { upV.text = mbps(size.toLong(), uEl); status.text = tr("টেস্ট শেষ") }
            } catch (e: Throwable) {
                ui { status.text = tr("টেস্ট সম্পূর্ণ হয়নি (ইন্টারনেট বা সার্ভারের সমস্যা)") }
            }
            running = false
        }.start()
    }
}
