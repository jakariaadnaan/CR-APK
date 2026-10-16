package com.example.callrec

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** সেটিংস: অ্যাপ লক এবং ব্যাকআপ / ফিরিয়ে আনা। */
class SettingsActivity : GuardedActivity() {

    private val reqSave = 41
    private val reqOpen = 42
    private val dataKeys = listOf("ledger", "debts", "todos", "notes")

    private lateinit var box: LinearLayout
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)
        setScreen("সেটিংস ও ব্যাকআপ", scroll)
        render()
    }

    private fun row(title: String, sub: String, right: View?, onClick: () -> Unit): LinearLayout {
        val c = card(0)
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER_VERTICAL
        c.setPadding(dp(18), dp(16), dp(18), dp(16))
        val left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL
        val t = label(title, 17f, Ui.TEXT, true)
        t.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        left.addView(t)
        if (sub.isNotEmpty()) left.addView(label(sub, 14f, Ui.MUTED), lp(t = 2))
        c.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (right != null) {
            c.addView(right, lp(width = ViewGroup.LayoutParams.WRAP_CONTENT, l = 12))
        } else {
            c.addView(iconView(R.drawable.ic_chevron, 20, Ui.CHEV), lp(width = dp(20), height = dp(20), l = 12))
        }
        c.setOnClickListener { onClick() }
        return c
    }

    private fun switchView(on: Boolean): Switch {
        val s = Switch(this)
        s.isChecked = on
        s.isClickable = false
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        s.thumbTintList = android.content.res.ColorStateList(states, intArrayOf(android.graphics.Color.WHITE, android.graphics.Color.WHITE))
        s.trackTintList = android.content.res.ColorStateList(states, intArrayOf(Ui.PRIMARY, Ui.GREY))
        return s
    }

    private val dataNames = listOf("খরচের খাতা", "বাকির খাতা", "করণীয় তালিকা", "দ্রুত নোট")

    private fun askDelete() {
        val items = (dataNames + "সব ডেটা").toTypedArray()
        AlertDialog.Builder(this).setTitle("কী মুছবেন?").setItems(items) { _, i ->
            val keys = if (i < 4) listOf(dataKeys[i]) else dataKeys
            AlertDialog.Builder(this)
                .setTitle(items[i] + " মুছে ফেলবেন?")
                .setMessage("এটি আর ফেরানো যাবে না। আগে ব্যাকআপ নেওয়া থাকলে ভালো।")
                .setPositiveButton("মুছুন") { _, _ ->
                    for (k in keys) Store.write(this, k, org.json.JSONArray())
                    Toast.makeText(this, "মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("থাক", null)
                .show()
        }.show()
    }

    private fun showAbout() {
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Throwable) { "?" }
        val sb = StringBuilder()
        sb.append("সংস্করণ: ").append(bn(ver ?: "?")).append("\n\n")
        var total = 0
        for (i in dataKeys.indices) {
            val n = Store.read(this, dataKeys[i]).length()
            total += n
            sb.append(dataNames[i]).append(": ").append(bn(n.toString())).append("টি\n")
        }
        val size = try { java.io.File(applicationInfo.dataDir, "shared_prefs/appdata.xml").length() } catch (e: Throwable) { 0L }
        sb.append("\nমোট এন্ট্রি: ").append(bn(total.toString()))
        sb.append("\nজমানো ডেটার আকার: ").append(Img.human(size))
        sb.append("\n\nডেটা শুধু এই ফোনেই থাকে। ব্যাকআপ না নিলে অ্যাপ মুছলে ডেটাও যাবে।")
        AlertDialog.Builder(this).setTitle("আমার খাতা").setMessage(sb.toString()).setPositiveButton("ঠিক আছে", null).show()
    }

    private fun section(title: String) {
        box.addView(label(title, 13f, Ui.MUTED, true), lp(l = 4, t = 10, b = 6))
    }

    private fun render() {
        box.removeAllViews()

        // ---- Appearance
        section("চেহারা")
        val themeNames = arrayOf("ফোনের সেটিং অনুযায়ী", "হালকা", "গাঢ় (ডার্ক)")
        box.addView(
            row("থিম", themeNames[prefs.getInt("theme", 0).coerceIn(0, 2)], null) {
                AlertDialog.Builder(this).setTitle("থিম").setItems(themeNames) { _, i ->
                    prefs.edit().putInt("theme", i).apply()
                    Ui.stamp++
                    recreate()
                }.show()
            },
            lp(b = 8)
        )
        val digitNames = arrayOf("বাংলা (১২৩)", "ইংরেজি (123)")
        box.addView(
            row("সংখ্যার ধরন", if (prefs.getBoolean("digitsBn", true)) digitNames[0] else digitNames[1], null) {
                AlertDialog.Builder(this).setTitle("সংখ্যার ধরন").setItems(digitNames) { _, i ->
                    prefs.edit().putBoolean("digitsBn", i == 0).apply()
                    Ui.stamp++
                    recreate()
                }.show()
            },
            lp(b = 8)
        )
        val sizeNames = arrayOf("ছোট", "মাঝারি", "বড়", "অনেক বড়")
        val sizeVals = floatArrayOf(0.9f, 1f, 1.15f, 1.3f)
        val curSize = prefs.getFloat("fontScale", 1f)
        var curIdx = 1
        for (i in sizeVals.indices) if (Math.abs(sizeVals[i] - curSize) < 0.01f) curIdx = i
        box.addView(
            row("লেখার আকার", sizeNames[curIdx], null) {
                AlertDialog.Builder(this).setTitle("লেখার আকার").setItems(sizeNames) { _, i ->
                    prefs.edit().putFloat("fontScale", sizeVals[i]).apply()
                    Ui.stamp++
                    recreate()
                }.show()
            },
            lp(b = 8)
        )

        // ---- App lock
        section("অ্যাপ লক")
        val on = Lock.enabled(this)
        box.addView(
            row("অ্যাপ লক", if (on) "চালু আছে (" + (if (Lock.type(this) == "pattern") "প্যাটার্ন" else "পিন") + ")" else "বন্ধ আছে (পিন বা প্যাটার্ন)", switchView(on)) {
                if (on) askCurrent("লক বন্ধ করতে বর্তমান লক দিন") {
                    Lock.disable(this)
                    render()
                } else chooseType {
                    render()
                }
            },
            lp(b = 8)
        )
        if (on) {
            box.addView(
                row(
                    "লক বদলান",
                    "বর্তমান: " + (if (Lock.type(this) == "pattern") "প্যাটার্ন" else "পিন"),
                    null
                ) {
                    askCurrent("বর্তমান লক দিন") { chooseType { render() } }
                },
                lp(b = 8)
            )
            if (Lock.biometricAvailable(this)) {
                val bio = Lock.bioEnabled(this)
                box.addView(
                    row("ফিঙ্গারপ্রিন্ট দিয়ে খুলুন", if (bio) "চালু" else "বন্ধ", switchView(bio)) {
                        Lock.setBio(this, !bio)
                        render()
                    },
                    lp(b = 8)
                )
            }
            val g = Lock.graceMs(this)
            val gText = when (g) {
                30_000L -> "৩০ সেকেন্ড"
                60_000L -> "১ মিনিট"
                300_000L -> "৫ মিনিট"
                else -> "১৫ মিনিট"
            }
            box.addView(
                row("কতক্ষণ পরে আবার লক হবে", "অ্যাপ থেকে বের হওয়ার পর: " + gText, null) {
                    val names = arrayOf("৩০ সেকেন্ড", "১ মিনিট", "৫ মিনিট", "১৫ মিনিট")
                    val vals = longArrayOf(30_000L, 60_000L, 300_000L, 900_000L)
                    AlertDialog.Builder(this)
                        .setTitle("আবার লক হবে")
                        .setItems(names) { _, i ->
                            Lock.setGrace(this, vals[i])
                            render()
                        }
                        .show()
                },
                lp(b = 8)
            )
            val hide = screenHidden(this)
            box.addView(
                row("স্ক্রিনশট ও রিসেন্ট প্রিভিউ লুকান", if (hide) "চালু: স্ক্রিনশট নেওয়া যায় না" else "বন্ধ: স্ক্রিনশট নেওয়া যায়", switchView(hide)) {
                    prefs.edit().putBoolean("secure", !hide).apply()
                    Ui.stamp++
                    recreate()
                },
                lp(b = 8)
            )
            box.addView(
                label(
                    "পিন বা প্যাটার্ন ভুলে গেলে অ্যাপের ডেটা মুছে নতুন করে ইনস্টল ছাড়া উপায় নেই, তাই আগে ব্যাকআপ রাখুন।",
                    12f, Ui.MUTED
                ),
                lp(l = 4, b = 4)
            )
        }

        // ---- Backup
        section("হোম")
        box.addView(
            row("টুল দেখানো বা লুকানো", "যে টুল লাগে না সেটি লুকিয়ে রাখুন", null) {
                startActivity(Intent(this, HiddenToolsActivity::class.java))
            },
            lp(b = 8)
        )

        section("ব্যাকআপ")
        val last = prefs.getLong("lastBackup", 0L)
        val lastText = if (last == 0L) "এখনো ব্যাকআপ নেওয়া হয়নি" else "সর্বশেষ: " + bn(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(last)))
        box.addView(row("ব্যাকআপ তৈরি করুন", lastText, null) { startBackup() }, lp(b = 8))
        box.addView(row("ব্যাকআপ থেকে ফিরিয়ে আনুন", "আগের ব্যাকআপ ফাইল বেছে নিন", null) { startRestore() }, lp(b = 8))

        section("ডেটা ও অ্যাপ")
        box.addView(row("ডেটা মুছুন", "খাতা, নোট ইত্যাদি মুছে ফেলুন", null) { askDelete() }, lp(b = 8))
        box.addView(row("অ্যাপ সম্পর্কে", "সংস্করণ ও জমানো ডেটা", null) { showAbout() }, lp(b = 8))
        box.addView(
            label(
                "ব্যাকআপে খরচের খাতা, বাকির খাতা, করণীয় ও নোট থাকে। ফাইল সেভ করার সময় তালিকা থেকে “Drive” বেছে নিলে সরাসরি Google Drive-এ জমা হবে। ফাইলটি সাধারণ লেখা হিসেবে থাকে, তাই নিরাপদ জায়গায় রাখুন।",
                12f, Ui.MUTED
            ),
            lp(l = 4, b = 4)
        )

        section("অ্যাপ সম্পর্কে")
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        box.addView(label("আমার খাতা · সংস্করণ " + bn(ver), 13f, Ui.MUTED), lp(l = 4))
    }

    // ------------------------------------------------------------------ PIN dialogs

    private fun pinField(hint: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        return e
    }

    /** Asks for the current PIN and runs [onOk] when it is right. */
    private fun askPin(title: String, onOk: () -> Unit) {
        val f = pinField("পিন")
        val wrap = LinearLayout(this)
        wrap.setPadding(dp(20), dp(10), dp(20), 0)
        wrap.addView(f, lp())
        val dlg = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(wrap)
            .setPositiveButton("ঠিক আছে", null)
            .setNegativeButton("বাতিল", null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (Lock.check(this, asciiDigits(f.text.toString()))) {
                    dlg.dismiss()
                    onOk()
                } else {
                    Toast.makeText(this, "ভুল পিন", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
    }

    /** Sets a new 4 to 8 digit PIN (typed twice). */
    private fun showSetPin(onDone: () -> Unit) {
        val a = pinField("নতুন পিন (৪ থেকে ৮ সংখ্যা)")
        val b = pinField("আবার লিখুন")
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.setPadding(dp(20), dp(10), dp(20), 0)
        wrap.addView(a, lp())
        wrap.addView(b, lp(t = 4))
        val dlg = AlertDialog.Builder(this)
            .setTitle("নতুন পিন")
            .setView(wrap)
            .setPositiveButton("সংরক্ষণ", null)
            .setNegativeButton("বাতিল", null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val p1 = asciiDigits(a.text.toString())
                val p2 = asciiDigits(b.text.toString())
                if (p1.length < 4 || p1.length > 8 || !p1.all { it in '0'..'9' }) {
                    Toast.makeText(this, "৪ থেকে ৮ সংখ্যার পিন দিন", Toast.LENGTH_SHORT).show()
                } else if (p1 != p2) {
                    Toast.makeText(this, "দুটো পিন মেলেনি", Toast.LENGTH_SHORT).show()
                } else {
                    Lock.setPin(this, p1)
                    dlg.dismiss()
                    onDone()
                }
            }
        }
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
    }

    // ------------------------------------------------------------------ lock type and pattern dialogs

    private fun chooseType(onDone: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("লকের ধরন")
            .setItems(arrayOf("পিন (৪ থেকে ৮ সংখ্যা)", "প্যাটার্ন")) { _, i ->
                if (i == 0) showSetPin(onDone) else showSetPattern(onDone)
            }
            .setNegativeButton("বাতিল", null)
            .show()
    }

    /** Asks for the current PIN or pattern, whichever is set, and runs [onOk] when it is right. */
    private fun askCurrent(title: String, onOk: () -> Unit) {
        if (Lock.type(this) != "pattern") {
            askPin(title, onOk)
            return
        }
        val pv = patternView()
        val dlg = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(wrapPattern(pv, null))
            .setNegativeButton("বাতিল", null)
            .create()
        pv.onComplete = { s ->
            if (Lock.check(this, s)) {
                dlg.dismiss()
                onOk()
            } else {
                pv.flashError()
                Toast.makeText(this, "ভুল প্যাটার্ন", Toast.LENGTH_SHORT).show()
            }
        }
        dlg.show()
    }

    private fun patternView(): PatternView {
        val pv = PatternView(this)
        pv.baseColor = Ui.GREY
        pv.activeColor = Ui.PRIMARY
        return pv
    }

    private fun wrapPattern(pv: PatternView, hint: TextView?): LinearLayout {
        val w = LinearLayout(this)
        w.orientation = LinearLayout.VERTICAL
        w.gravity = Gravity.CENTER_HORIZONTAL
        w.setPadding(dp(20), dp(10), dp(20), dp(10))
        if (hint != null) w.addView(hint, lp(b = 8))
        w.addView(pv, LinearLayout.LayoutParams(dp(260), dp(260)))
        return w
    }

    /** Draw a pattern of at least 4 dots, then draw it again to confirm. */
    private fun showSetPattern(onDone: () -> Unit) {
        val pv = patternView()
        val hint = label("কমপক্ষে ৪টি বিন্দু যোগ করে প্যাটার্ন আঁকুন", 13f, Ui.MUTED)
        var first: String? = null
        val dlg = AlertDialog.Builder(this)
            .setTitle("নতুন প্যাটার্ন")
            .setView(wrapPattern(pv, hint))
            .setNegativeButton("বাতিল", null)
            .create()
        pv.onComplete = { s ->
            val f = first
            if (f == null) {
                if (s.length < 4) {
                    hint.text = "কমপক্ষে ৪টি বিন্দু লাগবে, আবার আঁকুন"
                    pv.flashError()
                } else {
                    first = s
                    hint.text = "নিশ্চিত করতে আবার আঁকুন"
                    pv.postDelayed({ pv.reset() }, 400)
                }
            } else if (f == s) {
                Lock.setSecret(this, "pattern", s)
                dlg.dismiss()
                onDone()
            } else {
                first = null
                hint.text = "মেলেনি, প্রথম থেকে আঁকুন"
                pv.flashError()
            }
        }
        dlg.show()
    }

    // ------------------------------------------------------------------ backup / restore

    private fun startBackup() {
        val name = "amar-khata-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json"
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/json"
        i.putExtra(Intent.EXTRA_TITLE, name)
        try {
            startActivityForResult(i, reqSave)
        } catch (e: Exception) {
            Toast.makeText(this, "ফাইল বেছে নেওয়ার অ্যাপ পাওয়া যায়নি", Toast.LENGTH_LONG).show()
        }
    }

    private fun startRestore() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        try {
            startActivityForResult(i, reqOpen)
        } catch (e: Exception) {
            Toast.makeText(this, "ফাইল বেছে নেওয়ার অ্যাপ পাওয়া যায়নি", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildBackup(): String {
        val data = JSONObject()
        for (k in dataKeys) data.put(k, Store.read(this, k))
        val extra = JSONObject()
        val land = getSharedPreferences("land_prefs", MODE_PRIVATE)
        extra.put("katha", land.getString("katha", "720"))
        extra.put("bigha", land.getString("bigha", "20"))
        extra.put("hijriAdj", getSharedPreferences("date_prefs", MODE_PRIVATE).getInt("hijriAdj", 0))
        val o = JSONObject()
        o.put("app", "amar-khata")
        o.put("version", 1)
        o.put("created", System.currentTimeMillis())
        o.put("data", data)
        o.put("prefs", extra)
        return o.toString(2)
    }

    @Deprecated("Uses the classic result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        if (requestCode == reqSave) {
            try {
                val bytes = buildBackup().toByteArray(Charsets.UTF_8)
                val out = contentResolver.openOutputStream(uri, "wt")
                if (out == null) throw IllegalStateException()
                out.use { it.write(bytes) }
                prefs.edit().putLong("lastBackup", System.currentTimeMillis()).apply()
                Toast.makeText(this, "ব্যাকআপ সংরক্ষিত হয়েছে", Toast.LENGTH_LONG).show()
                render()
            } catch (e: Exception) {
                Toast.makeText(this, "ব্যাকআপ সংরক্ষণ করা যায়নি", Toast.LENGTH_LONG).show()
            }
        } else if (requestCode == reqOpen) {
            val text = try {
                contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
            } catch (e: Exception) {
                null
            }
            confirmRestore(text)
        }
    }

    private fun confirmRestore(text: String?) {
        val parsed: JSONObject? = try {
            if (text == null) null else JSONObject(text)
        } catch (e: Exception) {
            null
        }
        val data = parsed?.optJSONObject("data")
        if (parsed == null || parsed.optString("app") != "amar-khata" || data == null) {
            Toast.makeText(this, "এটি সঠিক ব্যাকআপ ফাইল নয়", Toast.LENGTH_LONG).show()
            return
        }
        val msg = "ব্যাকআপে আছে:\n" +
            "• খরচের লেনদেন: " + bn((data.optJSONArray("ledger")?.length() ?: 0).toString()) + "\n" +
            "• বাকি: " + bn((data.optJSONArray("debts")?.length() ?: 0).toString()) + "\n" +
            "• করণীয়: " + bn((data.optJSONArray("todos")?.length() ?: 0).toString()) + "\n" +
            "• নোট: " + bn((data.optJSONArray("notes")?.length() ?: 0).toString()) +
            "\n\nএখনকার সব তথ্য মুছে এগুলো বসবে। এগিয়ে যাবেন?"
        AlertDialog.Builder(this)
            .setTitle("ফিরিয়ে আনুন")
            .setMessage(msg)
            .setPositiveButton("হ্যাঁ, ফিরিয়ে আনুন") { _, _ ->
                for (k in dataKeys) {
                    val arr = data.optJSONArray(k) ?: org.json.JSONArray()
                    Store.write(this, k, arr)
                }
                val p = parsed.optJSONObject("prefs")
                if (p != null) {
                    getSharedPreferences("land_prefs", MODE_PRIVATE).edit()
                        .putString("katha", p.optString("katha", "720"))
                        .putString("bigha", p.optString("bigha", "20"))
                        .apply()
                    getSharedPreferences("date_prefs", MODE_PRIVATE).edit()
                        .putInt("hijriAdj", p.optInt("hijriAdj", 0))
                        .apply()
                }
                Toast.makeText(this, "ফিরিয়ে আনা হয়েছে", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("না", null)
            .show()
    }
}
