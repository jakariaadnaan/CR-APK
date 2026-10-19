package com.example.callrec

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
}
