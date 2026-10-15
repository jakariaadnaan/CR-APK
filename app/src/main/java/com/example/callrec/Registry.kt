package com.example.callrec

import android.content.Context
import android.graphics.Color

class Cat(val id: String, val name: String, val icon: Int, val tint: Int, val fg: Int)

/** [target] is null while a tool is not built yet. */
class Tool(
    val id: String,
    val title: String,
    val sub: String,
    val cat: String,
    val target: Class<*>?,
    val online: Boolean = false,
    val keywords: String = ""
)

/** The list of every tool, grouped into categories. One place to change when a new tool is added. */
object Registry {
    val cats = listOf(
        Cat("khata", "খাতা ও হিসাব", R.drawable.ic_wallet, Color.parseColor("#E3F1EA"), Color.parseColor("#1E6B52")),
        Cat("time", "সময় ও নোট", R.drawable.ic_clock, Color.parseColor("#ECE7F6"), Color.parseColor("#553C93")),
        Cat("image", "ছবি", R.drawable.ic_image, Color.parseColor("#E4EEF9"), Color.parseColor("#1F5A99")),
        Cat("pdf", "PDF", R.drawable.ic_pdf, Color.parseColor("#F9E6E3"), Color.parseColor("#9A3028")),
        Cat("media", "অডিও ও ভিডিও", R.drawable.ic_media, Color.parseColor("#FBEFD9"), Color.parseColor("#8A5200")),
        Cat("text", "টেক্সট ও ভাষা", R.drawable.ic_text, Color.parseColor("#DFF1F2"), Color.parseColor("#0B6870")),
        Cat("qr", "QR ও বারকোড", R.drawable.ic_qr, Color.parseColor("#E6E9E7"), Color.parseColor("#2F3A34")),
        Cat("net", "নেটওয়ার্ক ও ডিভাইস", R.drawable.ic_globe, Color.parseColor("#F1E4F6"), Color.parseColor("#6E2F82"))
    )

    val tools = listOf(
        // খাতা ও হিসাব
        Tool("ledger", "খরচের খাতা", "আয়-ব্যয় ও মাসিক হিসাব", "khata", LedgerActivity::class.java, keywords = "expense income খরচ আয় হিসাব"),
        Tool("debt", "বাকির খাতা", "কে কত দেবে / পাবে", "khata", DebtActivity::class.java, keywords = "baki due বাকি ধার"),
        Tool("calc", "ক্যালকুলেটর", "হিসাব ও ইউনিট রূপান্তর", "khata", CalcActivity::class.java, keywords = "calculator unit converter length weight"),
        Tool("land", "জমির মাপ", "শতাংশ, কাঠা, বিঘা", "khata", LandActivity::class.java, keywords = "land katha bigha decimal জমি"),
        Tool("words", "টাকা কথায়", "চেক ও রশিদের জন্য", "khata", WordsActivity::class.java, keywords = "taka in words cheque amount"),
        Tool("currency", "কারেন্সি কনভার্টার", "টাকা, ডলার ও অন্যান্য মুদ্রা", "khata", null, true, "currency exchange dollar rate"),
        // সময় ও নোট
        Tool("todo", "করণীয়", "দিনের কাজের তালিকা", "time", TodoActivity::class.java, keywords = "todo task কাজ"),
        Tool("notes", "দ্রুত নোট", "নোট, নম্বর, আইডিয়া", "time", NotesActivity::class.java, keywords = "note memo"),
        Tool("date", "তারিখ ও বয়স", "বাংলা, হিজরি, বয়স", "time", DateActivity::class.java, keywords = "date age calculator hijri bangla birthday"),
        Tool("timer", "টাইমার", "কাউন্টডাউন, স্টপওয়াচ, পমোডোরো", "time", TimerActivity::class.java, keywords = "timer stopwatch pomodoro countdown"),
        Tool("pass", "পাসওয়ার্ড জেনারেটর", "শক্তিশালী পাসওয়ার্ড", "time", PasswordActivity::class.java, keywords = "password generator"),
        // ছবি
        Tool("imgfmt", "ফরম্যাট কনভার্টার", "PNG, JPG, WebP", "image", ImageFormatActivity::class.java, false, "image format converter png jpg webp"),
        Tool("imgcomp", "ফাইল সাইজ কমানো", "ছবির আকার ছোট করুন", "image", ImageCompressActivity::class.java, false, "compress file size reduce"),
        Tool("upscale", "ইমেজ আপস্কেলার", "ছবি বড় করুন", "image", UpscaleActivity::class.java, false, "image upscaler enlarge resize"),
        Tool("transp", "ট্রান্সপারেন্ট PNG মেকার", "ব্যাকগ্রাউন্ড মুছুন", "image", TransparentActivity::class.java, false, "transparent png background remove"),
        Tool("color", "কালার পিকার", "ছবি থেকে রঙের কোড", "image", ColorPickerActivity::class.java, false, "color picker hex rgb"),
        // PDF
        Tool("img2pdf", "ছবি → PDF", "একাধিক ছবি একটি PDF-এ", "pdf", ImagesToPdfActivity::class.java, false, "image to pdf images to pdf"),
        Tool("pdfsplit", "PDF ভাগ", "পেজ রেঞ্জ ধরে আলাদা ফাইল", "pdf", PdfSplitActivity::class.java, false, "pdf splitter split"),
        Tool("pdfextract", "পেজ এক্সট্র্যাক্টর", "দরকারি পেজ বেছে নতুন PDF", "pdf", PdfExtractActivity::class.java, false, "pdf page extractor"),
        Tool("pdf2img", "PDF → ছবি", "প্রতিটি পেজ ছবি হিসেবে", "pdf", PdfToImagesActivity::class.java, false, "pdf to images"),
        Tool("pdflock", "PDF লক / আনলক", "পাসওয়ার্ড দিয়ে সুরক্ষা", "pdf", PdfLockActivity::class.java, false, "pdf lock unlock password protect"),
        // অডিও ও ভিডিও
        Tool("aconv", "অডিও কনভার্টার", "MP3, WAV", "media", null, false, "audio converter mp3 wav"),
        Tool("v2mp3", "ভিডিও → MP3", "ভিডিও থেকে অডিও", "media", null, false, "video to mp3 extract audio"),
        Tool("gif", "GIF মেকার", "ছবি বা ভিডিও থেকে GIF", "media", null, false, "gif maker"),
        Tool("vmeta", "ভিডিও মেটাডেটা", "ভিডিওর খুঁটিনাটি তথ্য", "media", null, false, "video metadata viewer info"),
        Tool("rec", "কল রেকর্ডিং", "টেস্ট স্ক্রিন", "media", MainActivity::class.java, keywords = "call recording record"),
        // টেক্সট ও ভাষা
        Tool("textcase", "টেক্সট কেস কনভার্টার", "বড়-ছোট হাতের, camelCase, snake_case", "text", TextCaseActivity::class.java, keywords = "text case converter uppercase lowercase title"),
        Tool("counter", "শব্দ ও অক্ষর গণনা", "শব্দ, অক্ষর, লাইন, পড়ার সময়", "text", CounterActivity::class.java, keywords = "word character counter count"),
        Tool("unicode", "ইউনিকোড কনভার্টার", "টেক্সট ↔ কোড পয়েন্ট, UTF-8, HTML", "text", UnicodeActivity::class.java, keywords = "unicode utf-8 code point html entity"),
        Tool("dict", "অভিধান", "শব্দের অর্থ খুঁজুন", "text", null, true, "dictionary meaning"),
        // QR ও বারকোড
        Tool("qrgen", "QR কোড তৈরি", "লেখা বা লিংক থেকে QR", "qr", null, false, "qr code generator"),
        Tool("barcode", "বারকোড তৈরি", "বিভিন্ন ধরনের বারকোড", "qr", null, false, "barcode generator"),
        Tool("scan", "স্ক্যানার", "ক্যামেরা দিয়ে QR ও বারকোড পড়ুন", "qr", null, false, "qr scanner barcode scanner"),
        // নেটওয়ার্ক ও ডিভাইস
        Tool("speed", "ইন্টারনেট স্পিড টেস্ট", "ডাউনলোড ও আপলোড গতি", "net", null, true, "internet speed test"),
        Tool("ip", "আমার IP ও IP তথ্য", "পাবলিক IP ও অবস্থান", "net", null, true, "ip address checker public ip information"),
        Tool("whois", "WHOIS লুকআপ", "ডোমেইনের মালিকানা তথ্য", "net", null, true, "whois domain lookup"),
        Tool("webcam", "ওয়েবক্যাম টেস্টার", "ক্যামেরা ঠিকমতো চলছে কি না", "net", null, false, "webcam camera tester")
    )

    fun cat(id: String): Cat = cats.first { it.id == id }
    fun inCat(id: String): List<Tool> = tools.filter { it.cat == id }

    private fun sp(c: Context) = c.getSharedPreferences("home", Context.MODE_PRIVATE)

    fun favIds(c: Context): List<String> =
        (sp(c).getString("favs", "ledger,calc,todo,notes") ?: "").split(",").filter { it.isNotEmpty() }

    fun isFav(c: Context, id: String): Boolean = favIds(c).contains(id)

    /** Returns true when the tool is a favourite after the change. */
    fun toggleFav(c: Context, id: String): Boolean {
        val list = favIds(c).toMutableList()
        val now: Boolean
        if (list.contains(id)) {
            list.remove(id)
            now = false
        } else {
            list.add(id)
            now = true
        }
        sp(c).edit().putString("favs", list.joinToString(",")).apply()
        return now
    }

    fun matches(t: Tool, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        val hay = (t.title + " " + t.sub + " " + t.keywords + " " + cat(t.cat).name).lowercase()
        return q.split(Regex("\\s+")).all { hay.contains(it) }
    }
}
