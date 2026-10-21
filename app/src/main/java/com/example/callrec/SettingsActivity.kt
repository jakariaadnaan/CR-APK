package com.example.callrec

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
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
    private val reqCsvLedger = 43
    private val reqCsvDebt = 44
    private val reqTree = 45
    private val reqDefFolder = 46
    private val dataKeys = listOf("ledger", "debts", "todos", "notes")

    private lateinit var box: LinearLayout
    private val page by lazy { intent.getStringExtra("page") ?: "main" }
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)
        val title = when (page) {
            "lock" -> tr("অ্যাপ লক")
            "backup" -> tr("ব্যাকআপ")
            "data" -> tr("ডেটা ও অ্যাপ")
            else -> tl("Settings")
        }
        if (page == "main") setScreen(title, scroll, back = false, bottom = navBar(3)) else setScreen(title, scroll)
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

    private fun switchView(on: Boolean): Switch = styledSwitch(on)

    private val dataNames = listOf(tr("খরচের খাতা"), tr("বাকির খাতা"), tr("করণীয় তালিকা"), tr("দ্রুত নোট"))

    private fun askDelete() {
        val items = (dataNames + tr("সব ডেটা")).toTypedArray()
        AlertDialog.Builder(this).setTitle(tr("কী মুছবেন?")).setItems(items) { _, i ->
            val keys = if (i < 4) listOf(dataKeys[i]) else dataKeys
            AlertDialog.Builder(this)
                .setTitle(items[i] + tr(" মুছে ফেলবেন?"))
                .setMessage(tr("এটি আর ফেরানো যাবে না। আগে ব্যাকআপ নেওয়া থাকলে ভালো।"))
                .setPositiveButton(tr("মুছুন")) { _, _ ->
                    for (k in keys) Store.write(this, k, org.json.JSONArray())
                    Toast.makeText(this, tr("মুছে ফেলা হয়েছে"), Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(tr("থাক"), null)
                .show()
        }.show()
    }

    private fun showAbout() {
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Throwable) { "?" }
        val sb = StringBuilder()
        sb.append(tr("সংস্করণ: ")).append(bn(ver ?: "?")).append("\n\n")
        var total = 0
        for (i in dataKeys.indices) {
            val n = Store.read(this, dataKeys[i]).length()
            total += n
            sb.append(dataNames[i]).append(": ").append(bn(n.toString())).append(tr("টি\n"))
        }
        val size = try { java.io.File(applicationInfo.dataDir, "shared_prefs/appdata.xml").length() } catch (e: Throwable) { 0L }
        sb.append(tr("\nমোট এন্ট্রি: ")).append(bn(total.toString()))
        sb.append(tr("\nজমানো ডেটার আকার: ")).append(Img.human(size))
        sb.append(tr("\n\nডেটা শুধু এই ফোনেই থাকে। ব্যাকআপ না নিলে অ্যাপ মুছলে ডেটাও যাবে।"))
        AlertDialog.Builder(this).setTitle(tr("আমার খাতা")).setMessage(sb.toString()).setPositiveButton(tr("ঠিক আছে"), null).show()
    }

    override fun onResume() {
        super.onResume()
        if (::box.isInitialized) render()
    }

    private fun section(title: String) {
        box.addView(label(title, 13f, Ui.MUTED, true), lp(l = 4, t = 10, b = 6))
    }

    private fun render() {
        box.removeAllViews()
        when (page) {
            "lock" -> renderLock()
            "backup" -> renderBackup()
            "data" -> renderData()
            else -> renderMain()
        }
    }

    private fun go(p: String) {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra("page", p))
    }

    private fun secTitle(s: String) {
        box.addView(label(s, 13f, Ui.PRIMARY, true), lp(l = 6, t = 14, b = 8))
    }

    private fun cacheSize(): Long {
        fun size(f: java.io.File?): Long {
            if (f == null || !f.exists()) return 0L
            if (f.isFile) return f.length()
            var n = 0L
            f.listFiles()?.forEach { n += size(it) }
            return n
        }
        return size(cacheDir) + size(externalCacheDir)
    }

    private fun clearDir(f: java.io.File?) {
        f?.listFiles()?.forEach { if (it.isDirectory) { clearDir(it); it.delete() } else it.delete() }
    }

    private fun pickDefFolder() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        defaultFolderUri(this)?.let { i.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, it) }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        startActivityForResult(i, reqDefFolder)
    }

    private fun folderName(): String {
        val s = prefs.getString("defFolder", null) ?: return tl("Internal Storage")
        return try {
            val id = android.provider.DocumentsContract.getTreeDocumentId(Uri.parse(s))
            val n = id.substringAfter(":", id).substringAfterLast("/")
            if (n.isEmpty()) tl("Internal Storage") else n
        } catch (e: Throwable) { tl("Selected folder") }
    }

    private fun simpleDialog(title: String, msg: String, extra: String? = null, extraClick: (() -> Unit)? = null) {
        val b = AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton(tl("OK"), null)
        if (extra != null) b.setNeutralButton(extra) { _, _ -> extraClick?.invoke() }
        b.show()
    }

    private fun renderMain() {
        val themeNames = arrayOf(tl("System"), tl("Light Mode"), tl("Dark Mode"))
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        val last = prefs.getLong("lastBackup", 0L)
        val lastText = if (last == 0L) tl("Never") else SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(last))

        // profile card
        val top = card(14)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.addView(iconBox(R.drawable.ic_user, Ui.PRIMARY, Color.WHITE, 48, 26, 24, 14))
        val mid = LinearLayout(this)
        mid.orientation = LinearLayout.VERTICAL
        mid.addView(label(tl("Guest User"), 17f, Ui.TEXT, true))
        mid.addView(label(tl("Sign in to sync your data"), 12f, Ui.MUTED), lp(t = 2))
        top.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(pill(tl("Sign In")) {
            simpleDialog(
                tl("Sign In"),
                tl("Online sign-in and sync are not available yet. All your data is safely stored on this phone only. Use Backup to keep a copy."),
                tl("Open Backup")
            ) { go("backup") }
        }, lp(width = ViewGroup.LayoutParams.WRAP_CONTENT, l = 8))
        box.addView(top, lp())

        secTitle(tl("Preferences"))
        val hap = prefs.getBoolean("haptic", true)
        val snd = prefs.getBoolean("sound", true)
        box.addView(groupCard(listOf(
            rowOf(R.drawable.ic_palette, tl("Appearance"), "", extra = value(themeNames[prefs.getInt("theme", 0).coerceIn(0, 2)])) {
                startActivity(Intent(this, AppearanceActivity::class.java))
            },
            rowOf(R.drawable.ic_globe, tl("Language"), "", extra = value(if (Lang.en) "English" else "বাংলা")) {
                startActivity(Intent(this, LanguageActivity::class.java))
            },
            rowOf(R.drawable.ic_folder, tl("Default Folder"), "", extra = value(folderName())) {
                if (SaveTarget.folder(this) == null) pickDefFolder() else {
                    AlertDialog.Builder(this)
                        .setTitle(tr("ডিফল্ট ফোল্ডার"))
                        .setMessage(tr("এখন সব ফাইল (PDF, ছবি, অডিও, ব্যাকআপ ইত্যাদি) সরাসরি এই ফোল্ডারে সংরক্ষিত হয়: ") + folderName())
                        .setPositiveButton(tr("ফোল্ডার বদলান")) { _, _ -> pickDefFolder() }
                        .setNeutralButton(tr("সরিয়ে দিন")) { _, _ ->
                            prefs.edit().remove("defFolder").apply()
                            Toast.makeText(this, tr("এখন থেকে প্রতিবার ফোল্ডার জিজ্ঞেস করা হবে"), Toast.LENGTH_LONG).show()
                            render()
                        }
                        .setNegativeButton(tr("বাতিল"), null)
                        .show()
                }
            },
            rowOf(R.drawable.ic_bolt, tl("Hide Tools"), "") {
                startActivity(Intent(this, HiddenToolsActivity::class.java))
            },
            rowOf(R.drawable.ic_bell, tl("Notifications"), "", extra = value(if (prefs.getBoolean("notifAll", true)) tl("On") else tl("Off"))) {
                startActivity(Intent(this, NotifSettingsActivity::class.java))
            },
            rowOf(R.drawable.ic_vibrate, tl("Haptic Feedback"), "", extra = switchView(hap), chevron = false) {
                prefs.edit().putBoolean("haptic", !hap).apply()
                render()
            },
            rowOf(R.drawable.ic_volume, tl("Sound"), "", extra = switchView(snd), chevron = false) {
                prefs.edit().putBoolean("sound", !snd).apply()
                render()
            }
        )), lp())

        secTitle(tl("Security & Privacy"))
        box.addView(groupCard(listOf(
            rowOf(R.drawable.ic_lock, tl("App Lock"), "", extra = value(if (Lock.enabled(this)) tl("On") else tl("Off"))) { go("lock") },
            rowOf(R.drawable.ic_trash, tl("Clear Cache"), "", extra = value(Img.human(cacheSize()))) {
                AlertDialog.Builder(this).setTitle(tl("Clear Cache"))
                    .setMessage(tl("Temporary files will be deleted. Your ledger, notes and other data are not touched."))
                    .setPositiveButton(tl("Clear")) { _, _ ->
                        clearDir(cacheDir)
                        clearDir(externalCacheDir)
                        Toast.makeText(this, tl("Cache cleared"), Toast.LENGTH_SHORT).show()
                        render()
                    }
                    .setNegativeButton(tl("Cancel"), null).show()
            }
        )), lp())

        secTitle(tl("Backup & Data"))
        box.addView(groupCard(listOf(
            rowOf(R.drawable.ic_backup, tl("Backup & Restore"), "", extra = value(lastText)) { go("backup") },
            rowOf(R.drawable.ic_eye_off, tl("Delete Data"), "") { go("data") }
        )), lp())

        secTitle(tl("Support & About"))
        box.addView(groupCard(listOf(
            rowOf(R.drawable.ic_help, tl("Help & Support"), "") {
                startActivity(Intent(this, InfoPageActivity::class.java).putExtra("page", "help"))
            },
            rowOf(R.drawable.ic_doc, tl("Privacy Policy"), "") {
                startActivity(Intent(this, InfoPageActivity::class.java).putExtra("page", "privacy"))
            },
            rowOf(R.drawable.ic_star, tl("Rate Us"), "") {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
                } catch (e: Throwable) {
                    Toast.makeText(this, tl("Rating opens once the app is on a store"), Toast.LENGTH_LONG).show()
                }
            },
            rowOf(R.drawable.ic_share, tl("Share App"), "") {
                val i = Intent(Intent.ACTION_SEND)
                i.type = "text/plain"
                i.putExtra(Intent.EXTRA_TEXT, tl("Amar Khata — ledger, debts, notes and many handy tools in one app."))
                startActivity(Intent.createChooser(i, tl("Share App")))
            },
            rowOf(R.drawable.ic_info, tl("About"), "", extra = value("v$ver")) {
                startActivity(Intent(this, InfoPageActivity::class.java).putExtra("page", "about"))
            }
        )), lp(b = 12))
    }

    private fun rowOf(icon: Int, title: String, sub: String, extra: View? = null, chevron: Boolean = true, onClick: () -> Unit): LinearLayout {
        val v = listRow(icon, title, sub, Ui.TEXT, extra, chevron)
        v.setOnClickListener { v.tapFx(); onClick() }
        return v
    }

    private fun value(s: String): TextView = label(s, 13f, Ui.MUTED)

    private fun renderLock() {
        val on = Lock.enabled(this)
        val toggle = {
            if (on) askCurrent(tr("লক বন্ধ করতে বর্তমান লক দিন")) {
                Lock.disable(this)
                render()
            } else chooseType {
                render()
                if (Lock.question(this) == null) showSetQuestion(true) { render() }
            }
        }
        box.addView(
            groupCard(listOf(
                rowOf(
                    R.drawable.ic_shield, tr("অ্যাপ লক"),
                    if (on) tr("চালু আছে (") + lockTypeName() + ")" else tr("বন্ধ আছে (পিন, প্যাটার্ন বা পাসকোড)"),
                    extra = switchView(on), chevron = false
                ) { toggle() }
            )),
            lp(b = 6)
        )
        if (!on) {
            val info = LinearLayout(this)
            info.orientation = LinearLayout.VERTICAL
            info.background = bg(Ui.CARD, 18, Ui.LINE, 1)
            info.setPadding(dp(16), dp(14), dp(16), dp(16))
            info.addView(label(tr("আপনার হিসাব, নোট ও ফাইল সুরক্ষিত রাখুন"), 15f, Ui.TEXT, true))
            for (t in listOf(
                tr("পিন, প্যাটার্ন বা পাসকোড, যেটা পছন্দ"),
                tr("ফিঙ্গারপ্রিন্ট দিয়ে দ্রুত খোলা"),
                tr("ভুলে গেলে নিরাপত্তা-প্রশ্ন দিয়ে রিসেট"),
                tr("স্ক্রিনশট ও রিসেন্ট প্রিভিউ লুকানো")
            )) info.addView(label("•  " + t, 13f, Ui.MUTED), lp(t = 6))
            val go = label(tr("লক চালু করুন"), 15f, Color.WHITE, true)
            go.gravity = Gravity.CENTER
            go.background = bg(Ui.PRIMARY, 14)
            go.setOnClickListener { it.tapFx(); toggle() }
            info.addView(go, lp(height = dp(48), t = 16))
            box.addView(info, lp(t = 8))
            return
        }

        secTitle(tr("লক সেটিংস"))
        val g1 = ArrayList<View>()
        g1.add(rowOf(R.drawable.ic_lock, tr("লক বদলান"), "", extra = value(lockTypeName())) {
            askCurrent(tr("বর্তমান লক দিন")) { chooseType { render() } }
        })
        g1.add(rowOf(R.drawable.ic_help, tr("নিরাপত্তা-প্রশ্ন"), "", extra = value(if (Lock.question(this) != null) tr("সেট করা আছে") else tr("সেট করা নেই"))) {
            askCurrent(tr("বর্তমান লক দিন")) { showSetQuestion(false) { render() } }
        })
        if (Lock.type(this) == "pin") {
            val st = Lock.style(this)
            g1.add(rowOf(R.drawable.ic_phone, tr("লক স্ক্রিনের ধরন"), "", extra = value(if (st == 1) tr("সময় ও পিন") else tr("ক্লাসিক"))) {
                AlertDialog.Builder(this)
                    .setTitle(tr("লক স্ক্রিনের ধরন"))
                    .setSingleChoiceItems(arrayOf(tr("ক্লাসিক"), tr("সময় ও পিন")), st) { d, i ->
                        Lock.setStyle(this, i)
                        d.dismiss()
                        render()
                    }
                    .show()
            })
        }
        if (Lock.biometricAvailable(this)) {
            val bio = Lock.bioEnabled(this)
            g1.add(rowOf(R.drawable.ic_sparkle, tr("ফিঙ্গারপ্রিন্ট দিয়ে খুলুন"), "", extra = switchView(bio), chevron = false) {
                Lock.setBio(this, !bio)
                render()
            })
        }
        box.addView(groupCard(g1), lp())

        secTitle(tr("কখন লক হবে"))
        val g = Lock.graceMs(this)
        val gText = when (g) {
            30_000L -> tr("৩০ সেকেন্ড")
            60_000L -> tr("১ মিনিট")
            300_000L -> tr("৫ মিনিট")
            else -> tr("১৫ মিনিট")
        }
        box.addView(groupCard(listOf(
            rowOf(R.drawable.ic_clock, tr("কতক্ষণ পরে আবার লক হবে"), tr("অ্যাপ থেকে বের হওয়ার পর"), extra = value(gText)) {
                val names = arrayOf(tr("৩০ সেকেন্ড"), tr("১ মিনিট"), tr("৫ মিনিট"), tr("১৫ মিনিট"))
                val vals = longArrayOf(30_000L, 60_000L, 300_000L, 900_000L)
                AlertDialog.Builder(this)
                    .setTitle(tr("আবার লক হবে"))
                    .setItems(names) { _, i ->
                        Lock.setGrace(this, vals[i])
                        render()
                    }
                    .show()
            }
        )), lp())

        secTitle(tr("গোপনীয়তা"))
        val hide = screenHidden(this)
        box.addView(groupCard(listOf(
            rowOf(R.drawable.ic_eye_off, tr("স্ক্রিনশট ও রিসেন্ট প্রিভিউ লুকান"), if (hide) tr("চালু: স্ক্রিনশট নেওয়া যায় না") else tr("বন্ধ: স্ক্রিনশট নেওয়া যায়"), extra = switchView(hide), chevron = false) {
                prefs.edit().putBoolean("secure", !hide).apply()
                Ui.stamp++
                recreate()
            }
        )), lp())

        box.addView(
            label(tr("লক ভুলে গেলে নিরাপত্তা-প্রশ্ন দিয়ে রিসেট করা যায়। প্রশ্ন সেট না থাকলে অ্যাপের ডেটা মুছে নতুন করে শুরু করতে হয়, তাই আগে ব্যাকআপ রাখুন।"), 12f, Ui.MUTED),
            lp(l = 6, t = 12)
        )
    }

    private fun renderBackup() {
        val last = prefs.getLong("lastBackup", 0L)
        val lastText = if (last == 0L) tr("এখনো ব্যাকআপ নেওয়া হয়নি") else tr("সর্বশেষ: ") + bn(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(last)))
        box.addView(row(tr("ব্যাকআপ তৈরি করুন"), lastText, null) { startBackup() }, lp(b = 8))
        box.addView(row(tr("ব্যাকআপ থেকে ফিরিয়ে আনুন"), tr("আগের ব্যাকআপ ফাইল বেছে নিন"), null) { startRestore() }, lp(b = 8))
        val freqNames = arrayOf(tr("বন্ধ রাখুন"), tr("প্রতিদিন"), tr("প্রতি সপ্তাহে"), tr("প্রতি মাসে"))
        val af = AutoBackup.freq(this)
        val autoSub = if (af == 0 || AutoBackup.tree(this) == null) tr("বন্ধ আছে") else freqNames[af] + (if (AutoBackup.last(this) != 0L) " · " + tr("সর্বশেষ: ") + bn(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(AutoBackup.last(this)))) else "") + (if (AutoBackup.status(this) == "fail") " · " + tr("ব্যর্থ হয়েছে") else "")
        box.addView(row(tr("স্বয়ংক্রিয় ব্যাকআপ"), autoSub, null) { autoDialog() }, lp(b = 8))
        section(tr("এক্সপোর্ট (CSV)"))
        box.addView(row(tr("খরচের খাতা CSV"), tr("Excel বা Google Sheets-এ খোলা যায়"), null) { startCsv("ledger", reqCsvLedger) }, lp(b = 8))
        box.addView(row(tr("বাকির খাতা CSV"), tr("Excel বা Google Sheets-এ খোলা যায়"), null) { startCsv("debts", reqCsvDebt) }, lp(b = 8))

        box.addView(
            label(
                tr("ব্যাকআপে খরচের খাতা, বাকির খাতা, করণীয় ও নোট থাকে। ফাইল সেভ করার সময় তালিকা থেকে “Drive” বেছে নিলে সরাসরি Google Drive-এ জমা হবে। ফাইলটি সাধারণ লেখা হিসেবে থাকে, তাই নিরাপদ জায়গায় রাখুন।"),
                12f, Ui.MUTED
            ),
            lp(l = 4, b = 4)
        )

    }

    private fun renderData() {
        box.addView(row(tr("ডেটা মুছুন"), tr("খাতা, নোট ইত্যাদি মুছে ফেলুন"), null) { askDelete() }, lp(b = 8))
        box.addView(row(tr("অ্যাপ সম্পর্কে"), tr("সংস্করণ ও জমানো ডেটা"), null) { showAbout() }, lp(b = 8))
    }

    // ------------------------------------------------------------------ PIN dialogs

    private fun passField(hint: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        return e
    }

    private fun pinField(hint: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        return e
    }

    /** Asks for the current PIN and runs [onOk] when it is right. */
    private fun askPin(title: String, onOk: () -> Unit) {
        val isPw = Lock.type(this) == "password"
        val f = if (isPw) passField(tr("পাসকোড")) else pinField(tr("পিন"))
        val wrap = LinearLayout(this)
        wrap.setPadding(dp(20), dp(10), dp(20), 0)
        wrap.addView(f, lp())
        val dlg = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(wrap)
            .setPositiveButton(tr("ঠিক আছে"), null)
            .setNegativeButton(tr("বাতিল"), null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val typed = if (isPw) f.text.toString() else asciiDigits(f.text.toString())
                if (Lock.check(this, typed)) {
                    dlg.dismiss()
                    onOk()
                } else {
                    Toast.makeText(this, if (isPw) tr("ভুল পাসকোড") else tr("ভুল পিন"), Toast.LENGTH_SHORT).show()
                }
            }
        }
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
    }

    /** Sets a new 4 to 8 digit PIN (typed twice). */
    private fun showSetPin(onDone: () -> Unit) {
        val a = pinField(tr("নতুন পিন (৪ থেকে ৮ সংখ্যা)"))
        val b = pinField(tr("আবার লিখুন"))
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.setPadding(dp(20), dp(10), dp(20), 0)
        wrap.addView(a, lp())
        wrap.addView(b, lp(t = 4))
        val dlg = AlertDialog.Builder(this)
            .setTitle(tr("নতুন পিন"))
            .setView(wrap)
            .setPositiveButton(tr("সংরক্ষণ"), null)
            .setNegativeButton(tr("বাতিল"), null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val p1 = asciiDigits(a.text.toString())
                val p2 = asciiDigits(b.text.toString())
                if (p1.length < 4 || p1.length > 8 || !p1.all { it in '0'..'9' }) {
                    Toast.makeText(this, tr("৪ থেকে ৮ সংখ্যার পিন দিন"), Toast.LENGTH_SHORT).show()
                } else if (p1 != p2) {
                    Toast.makeText(this, tr("দুটো পিন মেলেনি"), Toast.LENGTH_SHORT).show()
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

    /** Sets a new passcode of 6 or more letters, digits or symbols (typed twice). */
    private fun showSetPassword(onDone: () -> Unit) {
        val a = passField(tr("নতুন পাসকোড (কমপক্ষে ৬ অক্ষর)"))
        val b = passField(tr("আবার লিখুন"))
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        wrap.setPadding(dp(20), dp(10), dp(20), 0)
        wrap.addView(a, lp())
        wrap.addView(b, lp(t = 4))
        val dlg = AlertDialog.Builder(this)
            .setTitle(tr("নতুন পাসকোড"))
            .setView(wrap)
            .setPositiveButton(tr("সংরক্ষণ"), null)
            .setNegativeButton(tr("বাতিল"), null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val p1 = a.text.toString()
                val p2 = b.text.toString()
                if (p1.length < 6 || p1.length > 32) {
                    Toast.makeText(this, tr("৬ থেকে ৩২ অক্ষরের পাসকোড দিন"), Toast.LENGTH_SHORT).show()
                } else if (p1 != p2) {
                    Toast.makeText(this, tr("দুটো পাসকোড মেলেনি"), Toast.LENGTH_SHORT).show()
                } else {
                    Lock.setSecret(this, "password", p1)
                    dlg.dismiss()
                    onDone()
                }
            }
        }
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
    }

    /** Picks a security question and a hashed answer; used to reset the lock without wiping data. */
    private fun showSetQuestion(offer: Boolean, onDone: () -> Unit) {
        val qs = arrayOf(
            tr("আপনার প্রথম স্কুলের নাম কী?"),
            tr("আপনার শৈশবের প্রিয় বন্ধুর নাম কী?"),
            tr("আপনার মায়ের জন্মস্থান কোথায়?"),
            tr("আপনার প্রথম মোবাইল নম্বরের শেষ ৪ সংখ্যা কত?"),
            tr("নিজের প্রশ্ন লিখব")
        )
        AlertDialog.Builder(this)
            .setTitle(tr("নিরাপত্তা-প্রশ্ন বাছুন"))
            .setItems(qs) { _, i ->
                if (i < qs.size - 1) askAnswer(qs[i], onDone) else {
                    val e = EditText(this)
                    e.hint = tr("আপনার প্রশ্ন")
                    val w = LinearLayout(this)
                    w.setPadding(dp(20), dp(10), dp(20), 0)
                    w.addView(e, lp())
                    AlertDialog.Builder(this)
                        .setTitle(tr("নিজের প্রশ্ন"))
                        .setView(w)
                        .setPositiveButton(tr("পরবর্তী")) { _, _ ->
                            val q = e.text.toString().trim()
                            if (q.length < 4) Toast.makeText(this, tr("প্রশ্নটি একটু বড় করে লিখুন"), Toast.LENGTH_SHORT).show()
                            else askAnswer(q, onDone)
                        }
                        .setNegativeButton(tr("বাতিল"), null)
                        .show()
                }
            }
            .setNegativeButton(if (offer) tr("এখন নয়") else tr("বাতিল"), null)
            .show()
    }

    private fun askAnswer(q: String, onDone: () -> Unit) {
        val a = EditText(this)
        a.hint = tr("উত্তর")
        a.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val w = LinearLayout(this)
        w.orientation = LinearLayout.VERTICAL
        w.setPadding(dp(20), dp(10), dp(20), 0)
        w.addView(label(q, 14f, Ui.MUTED), lp(b = 6))
        w.addView(a, lp())
        AlertDialog.Builder(this)
            .setTitle(tr("উত্তর লিখুন"))
            .setView(w)
            .setPositiveButton(tr("সংরক্ষণ")) { _, _ ->
                val ans = a.text.toString().trim()
                if (ans.length < 2) {
                    Toast.makeText(this, tr("উত্তর লিখতে হবে"), Toast.LENGTH_SHORT).show()
                } else {
                    Lock.setQuestion(this, q, ans)
                    Toast.makeText(this, tr("নিরাপত্তা-প্রশ্ন সংরক্ষিত হয়েছে"), Toast.LENGTH_SHORT).show()
                    onDone()
                }
            }
            .setNegativeButton(tr("বাতিল"), null)
            .show()
    }

    private fun lockTypeName(): String = when (Lock.type(this)) {
        "pattern" -> tr("প্যাটার্ন")
        "password" -> tr("পাসকোড")
        else -> tr("পিন")
    }

    // ------------------------------------------------------------------ lock type and pattern dialogs

    private fun chooseType(onDone: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(tr("লকের ধরন"))
            .setItems(arrayOf(tr("পিন (৪ থেকে ৮ সংখ্যা)"), tr("প্যাটার্ন"), tr("পাসকোড (অক্ষর ও সংখ্যা)"))) { _, i ->
                when (i) { 0 -> showSetPin(onDone); 1 -> showSetPattern(onDone); else -> showSetPassword(onDone) }
            }
            .setNegativeButton(tr("বাতিল"), null)
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
            .setNegativeButton(tr("বাতিল"), null)
            .create()
        pv.onComplete = { s ->
            if (Lock.check(this, s)) {
                dlg.dismiss()
                onOk()
            } else {
                pv.flashError()
                Toast.makeText(this, tr("ভুল প্যাটার্ন"), Toast.LENGTH_SHORT).show()
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
        val hint = label(tr("কমপক্ষে ৪টি বিন্দু যোগ করে প্যাটার্ন আঁকুন"), 13f, Ui.MUTED)
        var first: String? = null
        val dlg = AlertDialog.Builder(this)
            .setTitle(tr("নতুন প্যাটার্ন"))
            .setView(wrapPattern(pv, hint))
            .setNegativeButton(tr("বাতিল"), null)
            .create()
        pv.onComplete = { s ->
            val f = first
            if (f == null) {
                if (s.length < 4) {
                    hint.text = tr("কমপক্ষে ৪টি বিন্দু লাগবে, আবার আঁকুন")
                    pv.flashError()
                } else {
                    first = s
                    hint.text = tr("নিশ্চিত করতে আবার আঁকুন")
                    pv.postDelayed({ pv.reset() }, 400)
                }
            } else if (f == s) {
                Lock.setSecret(this, "pattern", s)
                dlg.dismiss()
                onDone()
            } else {
                first = null
                hint.text = tr("মেলেনি, প্রথম থেকে আঁকুন")
                pv.flashError()
            }
        }
        dlg.show()
    }

    // ------------------------------------------------------------------ backup / restore

    private var pendingPass: CharArray? = null

    private fun startBackup() {
        val a = EditText(this)
        a.hint = tr("পাসওয়ার্ড (খালি রাখলে সুরক্ষা ছাড়া)")
        a.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val b = EditText(this)
        b.hint = tr("পাসওয়ার্ড আবার লিখুন")
        b.inputType = a.inputType
        val w = LinearLayout(this)
        w.orientation = LinearLayout.VERTICAL
        w.setPadding(dp(20), dp(10), dp(20), 0)
        w.addView(label(tr("পাসওয়ার্ড দিলে ব্যাকআপ ফাইল আর কেউ পড়তে পারবে না। পাসওয়ার্ড ভুলে গেলে ব্যাকআপ খোলার উপায় নেই, তাই মনে রাখুন।"), 12f, Ui.MUTED), lp(b = 8))
        w.addView(a, lp())
        w.addView(b, lp(t = 4))
        val dlg = AlertDialog.Builder(this).setTitle(tr("ব্যাকআপ সুরক্ষা")).setView(w)
            .setPositiveButton(tr("এগিয়ে যান"), null).setNegativeButton(tr("বাতিল"), null).create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val p1 = a.text.toString()
                if (p1.isNotEmpty() && p1.length < 4) {
                    Toast.makeText(this, tr("কমপক্ষে ৪ অক্ষরের পাসওয়ার্ড দিন"), Toast.LENGTH_SHORT).show()
                } else if (p1 != b.text.toString()) {
                    Toast.makeText(this, tr("দুটি পাসওয়ার্ড মেলেনি"), Toast.LENGTH_SHORT).show()
                } else {
                    pendingPass = if (p1.isEmpty()) null else p1.toCharArray()
                    dlg.dismiss()
                    pickBackupFile()
                }
            }
        }
        dlg.show()
    }

    private fun pickBackupFile() {
        val name = "amar-khata-" + (if (pendingPass != null) "locked-" else "") + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json"
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/json"
        i.putExtra(Intent.EXTRA_TITLE, name)
        try {
            saveOrAsk(i, reqSave)
        } catch (e: Exception) {
            Toast.makeText(this, tr("ফাইল বেছে নেওয়ার অ্যাপ পাওয়া যায়নি"), Toast.LENGTH_LONG).show()
        }
    }

    private var pickedFreq = 0
    private var pickedPass = ""

    private fun autoDialog() {
        val names = arrayOf(tr("বন্ধ রাখুন"), tr("প্রতিদিন"), tr("প্রতি সপ্তাহে"), tr("প্রতি মাসে"))
        val w = LinearLayout(this)
        w.orientation = LinearLayout.VERTICAL
        w.setPadding(dp(20), dp(10), dp(20), 0)
        val group = android.widget.RadioGroup(this)
        for ((i, n) in names.withIndex()) {
            val rb = android.widget.RadioButton(this)
            rb.text = n
            rb.id = 100 + i
            group.addView(rb)
        }
        group.check(100 + AutoBackup.freq(this))
        w.addView(group)
        val pw = EditText(this)
        pw.hint = tr("পাসওয়ার্ড (ঐচ্ছিক)")
        pw.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        pw.setText(AutoBackup.pass(this))
        w.addView(pw, lp(t = 8))
        w.addView(label(tr("রাতে ৩টার দিকে ফোল্ডারে (ডিফল্ট ফোল্ডার সেট থাকলে সেখানেই) নতুন ফাইল জমা হবে, সর্বশেষ ৫টি রাখা হবে। পাসওয়ার্ড দিলে ফাইল এনক্রিপ্ট হয়; সেই পাসওয়ার্ড এই ফোনের অ্যাপেই রাখা হয়, তাই ভুলবেন না।"), 12f, Ui.MUTED), lp(t = 8))
        AlertDialog.Builder(this).setTitle(tr("স্বয়ংক্রিয় ব্যাকআপ")).setView(w)
            .setPositiveButton(tr("সংরক্ষণ")) { _, _ ->
                val f = group.checkedRadioButtonId - 100
                val p = pw.text.toString()
                if (p.isNotEmpty() && p.length < 4) {
                    Toast.makeText(this, tr("কমপক্ষে ৪ অক্ষরের পাসওয়ার্ড দিন"), Toast.LENGTH_SHORT).show()
                } else if (f == 0) {
                    AutoBackup.save(this, 0, AutoBackup.tree(this), p)
                    render()
                } else {
                    pickedFreq = f
                    pickedPass = p
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    try {
                        treeOrAsk(i, reqTree)
                    } catch (e: Exception) {
                        Toast.makeText(this, tr("ফাইল বেছে নেওয়ার অ্যাপ পাওয়া যায়নি"), Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton(tr("বাতিল"), null).show()
    }

    private fun startCsv(kind: String, req: Int) {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "text/csv"
        i.putExtra(Intent.EXTRA_TITLE, "amar-khata-" + kind + "-" + SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) + ".csv")
        try {
            saveOrAsk(i, req)
        } catch (e: Exception) {
            Toast.makeText(this, tr("ফাইল বেছে নেওয়ার অ্যাপ পাওয়া যায়নি"), Toast.LENGTH_LONG).show()
        }
    }

    private fun csvCell(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

    private fun buildCsv(ledger: Boolean): String {
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sb = StringBuilder()
        if (ledger) {
            sb.append(listOf(tr("তারিখ"), tr("ধরন"), tr("ক্যাটাগরি"), tr("পরিমাণ"), tr("নোট")).joinToString(",") { csvCell(it) }).append("\r\n")
            for (e in LedgerStore.all(this).sortedBy { it.date }) {
                sb.append(csvCell(df.format(Date(e.date)))).append(',')
                    .append(csvCell(if (e.income) tr("আয়") else tr("ব্যয়"))).append(',')
                    .append(csvCell(tr(e.category))).append(',')
                    .append(if (e.amount % 1.0 == 0.0) e.amount.toLong().toString() else e.amount.toString()).append(',')
                    .append(csvCell(e.note)).append("\r\n")
            }
        } else {
            sb.append(listOf(tr("তারিখ"), tr("ধরন"), tr("নাম"), tr("পরিমাণ"), tr("নোট")).joinToString(",") { csvCell(it) }).append("\r\n")
            for (d in DebtStore.all(this).sortedBy { it.date }) {
                sb.append(csvCell(df.format(Date(d.date)))).append(',')
                    .append(csvCell(if (d.owe) tr("দেবো") else tr("পাবো"))).append(',')
                    .append(csvCell(d.name)).append(',')
                    .append(if (d.amount % 1.0 == 0.0) d.amount.toLong().toString() else d.amount.toString()).append(',')
                    .append(csvCell(d.note)).append("\r\n")
            }
        }
        return sb.toString()
    }

    private fun startRestore() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        try {
            startActivityForResult(i, reqOpen)
        } catch (e: Exception) {
            Toast.makeText(this, tr("ফাইল বেছে নেওয়ার অ্যাপ পাওয়া যায়নি"), Toast.LENGTH_LONG).show()
        }
    }

    private fun buildBackup(): String = BackupData.build(this)

    private fun restorePrefs(name: String, src: JSONObject?) {
        if (src == null) return
        val ed = getSharedPreferences(name, MODE_PRIVATE).edit().clear()
        for (k in src.keys()) {
            val e = src.optJSONObject(k) ?: continue
            when (e.optString("t")) {
                "b" -> ed.putBoolean(k, e.optBoolean("v"))
                "i" -> ed.putInt(k, e.optInt("v"))
                "f" -> ed.putFloat(k, e.optDouble("v").toFloat())
                "l" -> ed.putLong(k, e.optLong("v"))
                "s" -> ed.putString(k, e.optString("v"))
            }
        }
        ed.apply()
    }

    @Deprecated("Uses the classic result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        if (requestCode == reqDefFolder) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (e: Throwable) { }
            prefs.edit().putString("defFolder", uri.toString()).apply()
            Toast.makeText(this, tr("এখন থেকে সব ফাইল এই ফোল্ডারে সংরক্ষিত হবে"), Toast.LENGTH_LONG).show()
            render()
            return
        }
        if (requestCode == reqTree) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                AutoBackup.save(this, pickedFreq, uri.toString(), pickedPass)
                Thread {
                    val ok = AutoBackup.run(this)
                    runOnUiThread {
                        Toast.makeText(this, if (ok) tr("প্রথম ব্যাকআপ জমা হয়েছে") else tr("ফোল্ডারে ব্যাকআপ রাখা যায়নি"), Toast.LENGTH_LONG).show()
                        render()
                    }
                }.start()
            } catch (e: Exception) {
                Toast.makeText(this, tr("ফোল্ডারে ব্যাকআপ রাখা যায়নি"), Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode == reqCsvLedger || requestCode == reqCsvDebt) {
            try {
                val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + buildCsv(requestCode == reqCsvLedger).toByteArray(Charsets.UTF_8)
                val out = contentResolver.openOutputStream(uri, "wt") ?: throw IllegalStateException()
                out.use { it.write(bytes) }
                Toast.makeText(this, tr("ফাইল সংরক্ষিত হয়েছে"), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, tr("ফাইল সংরক্ষণ করা যায়নি"), Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode == reqSave) {
            try {
                val plain = buildBackup()
                val pw = pendingPass
                val bytes = (if (pw != null) BackupCrypto.encrypt(plain, pw) else plain).toByteArray(Charsets.UTF_8)
                pendingPass = null
                val out = contentResolver.openOutputStream(uri, "wt")
                if (out == null) throw IllegalStateException()
                out.use { it.write(bytes) }
                prefs.edit().putLong("lastBackup", System.currentTimeMillis()).apply()
                Toast.makeText(this, tr("ব্যাকআপ সংরক্ষিত হয়েছে"), Toast.LENGTH_LONG).show()
                render()
            } catch (e: Exception) {
                Toast.makeText(this, tr("ব্যাকআপ সংরক্ষণ করা যায়নি"), Toast.LENGTH_LONG).show()
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
        if (parsed != null && parsed.optString("app") == "amar-khata" && BackupCrypto.isEncrypted(parsed)) {
            val f = EditText(this)
            f.hint = tr("ব্যাকআপের পাসওয়ার্ড")
            f.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            val w = LinearLayout(this)
            w.setPadding(dp(20), dp(10), dp(20), 0)
            w.addView(f, lp())
            val d = AlertDialog.Builder(this).setTitle(tr("সুরক্ষিত ব্যাকআপ")).setView(w)
                .setPositiveButton(tr("খুলুন"), null).setNegativeButton(tr("বাতিল"), null).create()
            d.setOnShowListener {
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val plain = BackupCrypto.decrypt(parsed, f.text.toString().toCharArray())
                    if (plain == null) {
                        Toast.makeText(this, tr("পাসওয়ার্ড ভুল বা ফাইল নষ্ট"), Toast.LENGTH_LONG).show()
                    } else {
                        d.dismiss()
                        confirmRestore(plain)
                    }
                }
            }
            d.show()
            return
        }
        val data = parsed?.optJSONObject("data")
        if (parsed == null || parsed.optString("app") != "amar-khata" || data == null) {
            Toast.makeText(this, tr("এটি সঠিক ব্যাকআপ ফাইল নয়"), Toast.LENGTH_LONG).show()
            return
        }
        val msg = tr("ব্যাকআপে আছে:\n") +
            tr("• খরচের লেনদেন: ") + bn((data.optJSONArray("ledger")?.length() ?: 0).toString()) + "\n" +
            tr("• বাকি: ") + bn((data.optJSONArray("debts")?.length() ?: 0).toString()) + "\n" +
            tr("• করণীয়: ") + bn((data.optJSONArray("todos")?.length() ?: 0).toString()) + "\n" +
            tr("• নোট: ") + bn((data.optJSONArray("notes")?.length() ?: 0).toString()) +
            tr("\n\nএখনকার সব তথ্য মুছে এগুলো বসবে। এগিয়ে যাবেন?")
        AlertDialog.Builder(this)
            .setTitle(tr("ফিরিয়ে আনুন"))
            .setMessage(msg)
            .setPositiveButton(tr("হ্যাঁ, ফিরিয়ে আনুন")) { _, _ ->
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
                val more = parsed.optJSONObject("more")
                if (more != null) {
                    restorePrefs("budget", more.optJSONObject("budget"))
                    restorePrefs("prayer", more.optJSONObject("prayer"))
                }
                DebtReminder.rescheduleAll(this)
                TodoReminder.rescheduleAll(this)
                PrayerReminder.scheduleAll(this)
                Toast.makeText(this, tr("ফিরিয়ে আনা হয়েছে"), Toast.LENGTH_LONG).show()
            }
            .setNegativeButton(tr("না"), null)
            .show()
    }
}
