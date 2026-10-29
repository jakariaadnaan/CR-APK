package com.memoredia.app

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Full-screen text pages: Privacy Policy, Help & Support and About. They follow the app language. */
class InfoPageActivity : GuardedActivity() {

    private class Sec(val title: String, val paras: List<String> = emptyList(), val bullets: List<String> = emptyList())

    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val page = intent.getStringExtra("page") ?: "privacy"
        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(28))
        scroll.addView(box)
        when (page) {
            "privacy" -> {
                setScreen(tr("গোপনীয়তা নীতি"), scroll)
                privacy()
            }
            "help" -> {
                setScreen(tr("সাহায্য ও সহায়তা"), scroll)
                help()
            }
            "about" -> {
                setScreen(tr("অ্যাপ সম্পর্কে"), scroll)
                about()
            }
            else -> {
                setScreen(tr("গোপনীয়তা নীতি"), scroll)
                privacy()
            }
        }
    }

    private fun hero(icon: Int, title: String, line1: String, line2: String) {
        val c = card(18)
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.addView(iconBox(icon, Ui.PRIMARY, Color.WHITE, 56, 30, 28, 0))
        val t = label(title, 20f, Ui.TEXT, true)
        t.gravity = Gravity.CENTER
        c.addView(t, lp(t = 12))
        val a = label(line1, 13f, Ui.MUTED)
        a.gravity = Gravity.CENTER
        c.addView(a, lp(t = 4))
        val b = label(line2, 14f, Ui.TEXT)
        b.gravity = Gravity.CENTER
        b.setLineSpacing(dp(3).toFloat(), 1f)
        c.addView(b, lp(t = 10))
        box.addView(c, lp(b = 12))
    }

    private fun body(text: String, color: Int = Ui.TEXT): TextView {
        val t = label(text, 14f, color)
        t.setLineSpacing(dp(3).toFloat(), 1f)
        return t
    }

    private fun section(n: Int, s: Sec) {
        val c = card(16)
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val num = label(bn(n.toString()), 13f, Color.WHITE, true)
        num.gravity = Gravity.CENTER
        num.background = bg(Ui.PRIMARY, 12)
        head.addView(num, lp(width = dp(24), height = dp(24), r = 10))
        head.addView(label(s.title, 16f, Ui.TEXT, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(head)
        for (p in s.paras) c.addView(body(p), lp(t = 10))
        for (b in s.bullets) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.addView(label("•", 14f, Ui.PRIMARY, true), lp(width = dp(16)))
            row.addView(body(b), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            c.addView(row, lp(t = 8))
        }
        box.addView(c, lp(b = 10))
    }

    private fun privacy() {
        hero(
            R.drawable.ic_shield,
            tr("গোপনীয়তা নীতি"),
            tr("সর্বশেষ হালনাগাদ: ১৯ অক্টোবর ২০২৬"),
            tr("আপনার তথ্য আপনার ফোনেই থাকে। আমরা কিছু সংগ্রহ করি না, কিছু বিক্রি করি না।")
        )
        val secs = listOf(
            Sec(
                tr("আমরা কী তথ্য রাখি"),
                paras = listOf(tr("খরচের খাতা, বাকির খাতা, করণীয়, নোট, বাজেট, নামাজের সেটিং এবং অ্যাপের পছন্দ। এই সব তথ্য শুধু আপনার ফোনের ভেতরে থাকে।"))
            ),
            Sec(
                tr("আমরা কী সংগ্রহ করি না"),
                bullets = listOf(
                    tr("অ্যাকাউন্ট, ফোন নম্বর বা ইমেইল চাওয়া হয় না।"),
                    tr("কোনো বিজ্ঞাপন, অ্যানালিটিক্স বা ট্র্যাকিং নেই।"),
                    tr("আপনার খাতার ডেটা কোনো সার্ভারে পাঠানো হয় না।")
                )
            ),
            Sec(
                tr("ইন্টারনেট কখন লাগে"),
                paras = listOf(
                    tr("শুধু যে টুলগুলো সরাসরি অনলাইন তথ্য আনে, সেগুলোতে ইন্টারনেট লাগে। টুলের তালিকায় এগুলোর পাশে “ইন্টারনেট লাগে” লেখা থাকে।")
                ),
                bullets = listOf(
                    tr("মুদ্রার হার, অভিধান, আইপি ও ডোমেইন তথ্য"),
                    tr("ইন্টারনেট স্পিড টেস্ট"),
                    tr("ওয়েবপেজ থেকে PDF"),
                    tr("এই টুলগুলোতে আপনি যা লিখবেন (যেমন একটি শব্দ বা ডোমেইন), তা শুধু ওই পরিষেবায় যায়। আপনার খাতার ডেটা কখনো যায় না।")
                )
            ),
            Sec(
                tr("অনুমতি ও কেন লাগে"),
                bullets = listOf(
                    tr("ক্যামেরা: শুধু “ক্যামেরা থেকে PDF” ও QR টুলে।"),
                    tr("নোটিফিকেশন: বাকি, করণীয় ও নামাজের রিমাইন্ডারে।"),
                    tr("ভাইব্রেশন: কাজ শেষের ছোট কাঁপুনিতে।"),
                    tr("ফোল্ডার ও ফাইল: আপনি নিজে বাছলে তবেই, ব্যাকআপ ও PDF সংরক্ষণে।")
                )
            ),
            Sec(
                tr("ব্যাকআপ ও আপনার দায়িত্ব"),
                paras = listOf(
                    tr("ব্যাকআপ ফাইল আপনি নিজে সংরক্ষণ করেন, কোথায় রাখবেন তা আপনার সিদ্ধান্ত। পাসওয়ার্ড দিলে ফাইল এনক্রিপ্ট হয়, আর পাসওয়ার্ড ভুলে গেলে ফাইল খোলার উপায় নেই। পাসওয়ার্ড ছাড়া ব্যাকআপ ফাইল যে কেউ পড়তে পারে, তাই নিরাপদ জায়গায় রাখুন।"),
                    tr("স্বয়ংক্রিয় ব্যাকআপের পাসওয়ার্ড এই ফোনের অ্যাপেই রাখা থাকে।")
                )
            ),
            Sec(
                tr("অ্যাপ লক ও নিরাপত্তা"),
                paras = listOf(tr("পিন বা প্যাটার্ন শুধু আপনার ফোনে থাকে। ভুলে গেলে অ্যাপের ডেটা মুছে নতুন করে শুরু করা ছাড়া উপায় নেই, তাই নিয়মিত ব্যাকআপ রাখুন।"))
            ),
            Sec(
                tr("ডেটা মুছে ফেলা"),
                paras = listOf(tr("Settings থেকে যেকোনো সময় আলাদা করে বা সব ডেটা মোছা যায়। অ্যাপ মুছে ফেললে ফোন থেকে সব ডেটাও চলে যায়।"))
            ),
            Sec(
                tr("নীতি বদলালে"),
                paras = listOf(tr("নীতিতে কোনো পরিবর্তন হলে এই পেজে নতুন তারিখসহ জানানো হবে।"))
            ),
            Sec(
                tr("যোগাযোগ"),
                paras = listOf(tr("যোগাযোগের ব্যবস্থা শীঘ্রই আসছে"))
            )
        )
        for ((i, s) in secs.withIndex()) section(i + 1, s)
    }

    private fun faq(q: String, a: String) {
        val c = card(0)
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(16), dp(14), dp(16), dp(14))
        head.addView(label(q, 15f, Ui.TEXT, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sign = label("+", 20f, Ui.PRIMARY, true)
        head.addView(sign, lp(width = ViewGroup.LayoutParams.WRAP_CONTENT, l = 10))
        c.addView(head)
        val ans = body(a, Ui.MUTED)
        ans.setPadding(dp(16), 0, dp(16), dp(16))
        ans.visibility = android.view.View.GONE
        c.addView(ans)
        head.setOnClickListener {
            val open = ans.visibility == android.view.View.VISIBLE
            ans.visibility = if (open) android.view.View.GONE else android.view.View.VISIBLE
            sign.text = if (open) "+" else "–"
        }
        box.addView(c, lp(b = 8))
    }

    private fun help() {
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
        hero(
            R.drawable.ic_help,
            tr("সাহায্য ও সহায়তা"),
            tr("সংস্করণ: ") + bn(ver),
            tr("সাধারণ প্রশ্ন ও সমাধান এক জায়গায়।")
        )
        section(1, Sec(
            tr("শুরু করার গাইড"),
            bullets = listOf(
                tr("হোমে খরচ, বাকি, করণীয় ও নোটের শর্টকাট আছে। সব টুল দেখতে নিচের “সব টুল” ট্যাবে যান।"),
                tr("টুল খুঁজতে হোমের ওপরের সার্চ বোতাম চাপুন।"),
                tr("প্রিয় টুল বানাতে টুলের ওপর কিছুক্ষণ চেপে ধরুন।"),
                tr("চেহারা, ভাষা ও লেখার আকার Settings > Appearance-এ বদলানো যায়।")
            )
        ))
        box.addView(label(tr("সাধারণ প্রশ্ন"), 16f, Ui.TEXT, true), lp(l = 4, t = 6, b = 8))
        faq(tr("ডেটা হারিয়ে গেলে কী করব?"), tr("আগে ব্যাকআপ নেওয়া থাকলে Settings > Backup & Restore থেকে ব্যাকআপ ফাইল বেছে ফিরিয়ে আনুন। ব্যাকআপ না থাকলে ডেটা ফেরানোর উপায় নেই, তাই নিয়মিত ব্যাকআপ নিন।"))
        faq(tr("পিন বা প্যাটার্ন ভুলে গেলে কী হবে?"), tr("ফোনের Settings থেকে এই অ্যাপের ডেটা মুছে নতুন করে শুরু করতে হবে। এতে অ্যাপের সব ডেটা মুছে যায়, তাই আগে ব্যাকআপ রাখুন।"))
        faq(tr("রিমাইন্ডার বা নামাজের অ্যালার্ম বাজছে না কেন?"), tr("নোটিফিকেশনের অনুমতি দিন, ফোনের ব্যাটারি সেটিংসে এই অ্যাপকে “সীমাহীন” (No restrictions) করুন, আর অ্যাপটি অন্তত একবার খুলুন। Xiaomi ফোনে “Autostart”-ও চালু করুন।"))
        faq(tr("ব্যাকআপ কীভাবে নেব ও ফেরাব?"), tr("Settings > Backup & Restore-এ গিয়ে “ব্যাকআপ তৈরি করুন” চাপুন, চাইলে পাসওয়ার্ড দিন, আর ফাইলটি Drive বা ফোনের ফোল্ডারে রাখুন। ফেরাতে “ব্যাকআপ থেকে ফিরিয়ে আনুন” চাপুন। ফেরালে এখনকার ডেটা মুছে ব্যাকআপের ডেটা বসে।"))
        faq(tr("নতুন ফোনে ডেটা কীভাবে নেব?"), tr("পুরোনো ফোনে ব্যাকআপ নিয়ে ফাইলটি নতুন ফোনে পাঠান, তারপর নতুন ফোনের অ্যাপে ফিরিয়ে আনুন।"))
        faq(tr("PDF ফাইল সংরক্ষণ হচ্ছে না কেন?"), tr("ফাইল সংরক্ষণের পর্দায় এমন ফোল্ডার বাছুন যেখানে লেখা যায়, যেমন Download বা Documents। পাসওয়ার্ড দেওয়া PDF হলে আগে “PDF লক / আনলক” টুলে খুলে নিন।"))
        faq(tr("ভাষা, থিম ও লেখার আকার কোথায় বদলাব?"), tr("Settings > Appearance পেজে থিম, রং ও লেখার আকার আছে। একদম নিচে ভাষা ও সংখ্যার ধরন আছে।"))
        section(2, Sec(
            tr("সমস্যা সমাধান"),
            bullets = listOf(
                tr("অ্যাপ ধীর লাগলে Settings থেকে Clear Cache করুন।"),
                tr("আইকন বদলাতে কয়েক সেকেন্ড লাগতে পারে, একটু অপেক্ষা করুন।"),
                tr("অনলাইন টুল কাজ না করলে ইন্টারনেট সংযোগ দেখুন।")
            )
        ))
        val c = card(16)
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val num = label(bn("3"), 13f, Color.WHITE, true)
        num.gravity = Gravity.CENTER
        num.background = bg(Ui.PRIMARY, 12)
        head.addView(num, lp(width = dp(24), height = dp(24), r = 10))
        head.addView(label(tr("যোগাযোগ"), 16f, Ui.TEXT, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(head)
        c.addView(body(tr("যোগাযোগের ব্যবস্থা শীঘ্রই আসছে")), lp(t = 10))
        c.addView(body(tr("সমস্যা জানানোর সময় অ্যাপের সংস্করণ ও ফোনের মডেল সাথে রাখুন।"), Ui.MUTED), lp(t = 6))
        c.addView(pill(tr("তথ্য কপি করুন")) {
            copyText("Memoredia v" + ver + " · " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + " · Android " + android.os.Build.VERSION.RELEASE)
        }, lp(t = 12))
        box.addView(c, lp(b = 10))
    }

    private fun secTitle(s: String) {
        box.addView(label(s, 13f, Ui.PRIMARY, true), lp(l = 6, t = 14, b = 8))
    }

    private fun about() {
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val iconRes = AppIcon.res(this)
        val c = card(20)
        c.gravity = Gravity.CENTER_HORIZONTAL
        val iv = android.widget.ImageView(this)
        iv.setImageResource(iconRes)
        iv.clipToOutline = true
        iv.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: android.view.View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, v.width * 0.24f) }
        }
        c.addView(iv, lp(width = dp(76), height = dp(76)))
        val name = label(tr("Memoredia"), 22f, Ui.TEXT, true)
        name.gravity = Gravity.CENTER
        c.addView(name, lp(t = 12))
        val v = label(tr("সংস্করণ: ") + bn(ver), 13f, Ui.MUTED)
        v.gravity = Gravity.CENTER
        c.addView(v, lp(t = 4))
        val tag = body(tr("আপনার হিসাব, নোট ও দরকারি টুল, এক অ্যাপে।"))
        tag.gravity = Gravity.CENTER
        c.addView(tag, lp(t = 12))
        box.addView(c, lp())

        secTitle(tr("অ্যাপে কী আছে"))
        fun feat(icon: Int, title: String, sub: String) = listRow(icon, title, sub, Ui.TEXT, null, false)
        box.addView(groupCard(listOf(
            feat(R.drawable.ic_wallet, tr("খাতা ও বাকি"), tr("আয়-ব্যয়, বাকি ও আংশিক পরিশোধ, বাজেট")),
            feat(R.drawable.ic_text, tr("করণীয় ও নোট"), tr("রিমাইন্ডারসহ তালিকা, রঙিন নোট ও চেকলিস্ট")),
            feat(R.drawable.ic_clock, tr("নামাজের সময়"), tr("অফলাইনে সময়সূচি ও রিমাইন্ডার")),
            feat(R.drawable.ic_grid, tr("ড্যাশবোর্ড"), tr("মাস অনুযায়ী সারাংশ, চার্ট ও বাজেটের অবস্থা")),
            feat(R.drawable.ic_pdf, tr("PDF ও ছবির টুল"), tr("জোড়া, ভাগ, ছোট করা, সই, রূপান্তর ও আরও অনেক কিছু")),
            feat(R.drawable.ic_shield, tr("নিরাপত্তা ও ব্যাকআপ"), tr("অ্যাপ লক, এনক্রিপ্টেড ও স্বয়ংক্রিয় ব্যাকআপ"))
        )), lp())

        secTitle(tr("আপনার ডেটা"))
        val names = listOf(tr("খরচের খাতা"), tr("বাকির খাতা"), tr("করণীয় তালিকা"), tr("দ্রুত নোট"))
        val keys = listOf("ledger", "debts", "todos", "notes")
        val dc = card(16)
        var total = 0
        for (i in keys.indices) {
            val n = try { Store.read(this, keys[i]).length() } catch (e: Throwable) { 0 }
            total += n
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.addView(label(names[i], 14f, Ui.TEXT), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(label(bn(n.toString()), 14f, Ui.MUTED, true))
            dc.addView(r, lp(b = 8))
        }
        val size = try { java.io.File(applicationInfo.dataDir, "shared_prefs/appdata.xml").length() } catch (e: Throwable) { 0L }
        val tr1 = LinearLayout(this)
        tr1.orientation = LinearLayout.HORIZONTAL
        tr1.addView(label(tr("মোট এন্ট্রি"), 14f, Ui.TEXT, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tr1.addView(label(bn(total.toString()), 14f, Ui.PRIMARY, true))
        dc.addView(tr1, lp(t = 4, b = 8))
        val tr2 = LinearLayout(this)
        tr2.orientation = LinearLayout.HORIZONTAL
        tr2.addView(label(tr("জমানো ডেটার আকার"), 14f, Ui.TEXT, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        tr2.addView(label(Img.human(size), 14f, Ui.PRIMARY, true))
        dc.addView(tr2)
        dc.addView(body(tr("ডেটা শুধু এই ফোনেই থাকে। ব্যাকআপ না নিলে অ্যাপ মুছলে ডেটাও যাবে।"), Ui.MUTED), lp(t = 12))
        box.addView(dc, lp())

        secTitle(tr("আরও"))
        val pr = listRow(R.drawable.ic_doc, tr("গোপনীয়তা নীতি"))
        pr.setOnClickListener { startActivity(android.content.Intent(this, InfoPageActivity::class.java).putExtra("page", "privacy")) }
        val hp = listRow(R.drawable.ic_help, tr("সাহায্য ও সহায়তা"))
        hp.setOnClickListener { startActivity(android.content.Intent(this, InfoPageActivity::class.java).putExtra("page", "help")) }
        box.addView(groupCard(listOf(pr, hp)), lp())

        val foot = label(tr("© ২০২৬ Memoredia"), 12f, Ui.MUTED)
        foot.gravity = Gravity.CENTER
        box.addView(foot, lp(t = 16))
    }
}
