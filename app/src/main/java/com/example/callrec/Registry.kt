package com.example.callrec

import android.content.Context
import android.graphics.Color

class Cat(val id: String, private val rawName: String, val icon: Int, val tint: Int, val fg: Int) {
    val name: String get() = tr(rawName)
}

/** [target] is null while a tool is not built yet. */
class Tool(
    val id: String,
    private val rawTitle: String,
    private val rawSub: String,
    val cat: String,
    val target: Class<*>?,
    val online: Boolean = false,
    val keywords: String = ""
) {
    val title: String get() = tr(rawTitle)
    val sub: String get() = tr(rawSub)
}

/** The list of every tool, grouped into categories. One place to change when a new tool is added. */
object Registry {
    val cats = listOf(
        Cat("khata", "খাতা ও হিসাব", R.drawable.ic_wallet, Color.parseColor("#E3F1EA"), Color.parseColor("#1E6B52")),
        Cat("time", "সময় ও নোট", R.drawable.ic_clock, Color.parseColor("#ECE7F6"), Color.parseColor("#553C93")),
        Cat("image", "ছবি", R.drawable.ic_image, Color.parseColor("#E4EEF9"), Color.parseColor("#1F5A99")),
        Cat("pdf", "PDF", R.drawable.ic_pdf, Color.parseColor("#F9E6E3"), Color.parseColor("#9A3028")),
        Cat("media", "অডিও ও ভিডিও", R.drawable.ic_media, Color.parseColor("#FBEFD9"), Color.parseColor("#8A5200")),
        Cat("text", "টেক্সট ও ভাষা", R.drawable.ic_text, Color.parseColor("#DFF1F2"), Color.parseColor("#0B6870")),
        Cat("qr", "QR ও বারকোড", R.drawable.ic_qr, Color.parseColor("#E8EAEE"), Color.parseColor("#2F3A34")),
        Cat("net", "নেটওয়ার্ক ও ডিভাইস", R.drawable.ic_globe, Color.parseColor("#F1E4F6"), Color.parseColor("#6E2F82"))
    )

    val tools = listOf(
        // খাতা ও হিসাব
        Tool("ledger", "খরচ ও বাকির খাতা", "আয়-ব্যয়, মাসিক হিসাব, কে কত দেবে / পাবে", "khata", LedgerActivity::class.java, keywords = "expense income খরচ আয় হিসাব debt বাকি দেনা পাওনা ledger"),
        Tool("calc", "ক্যালকুলেটর", "সাধারণ ও সায়েন্টিফিক, ইউনিট রূপান্তর", "khata", CalcActivity::class.java, keywords = "calculator scientific sin cos log unit converter length weight ক্যালকুলেটর সায়েন্টিফিক"),
        Tool("land", "জমির মাপ", "শতাংশ, কাঠা, বিঘা", "khata", LandActivity::class.java, keywords = "land katha bigha decimal জমি"),
        Tool("words", "টাকা কথায়", "চেক ও রশিদের জন্য", "khata", WordsActivity::class.java, keywords = "taka in words cheque amount"),
        Tool("currency", "কারেন্সি কনভার্টার", "টাকা, ডলার ও অন্যান্য মুদ্রা", "khata", CurrencyActivity::class.java, true, "currency exchange dollar rate"),
        // সময় ও নোট
        Tool("prayer", "নামাজের সময়", "ঢাকা বা নিজের শহরের সময়", "time", PrayerActivity::class.java, keywords = "prayer namaz salat salah azan fajr zuhr asr maghrib isha নামাজ"),
        Tool("todo", "করণীয় ও নোট", "দিনের কাজ, নোট, নম্বর, আইডিয়া", "time", TodoActivity::class.java, keywords = "todo task notes কাজ নোট করণীয়"),
        Tool("date", "তারিখ ও বয়স", "বাংলা, হিজরি, বয়স", "time", DateActivity::class.java, keywords = "date age calculator hijri bangla birthday"),
        Tool("timer", "টাইমার", "কাউন্টডাউন, স্টপওয়াচ, পমোডোরো", "time", TimerActivity::class.java, keywords = "timer stopwatch pomodoro countdown"),
        Tool("pass", "পাসওয়ার্ড জেনারেটর", "শক্তিশালী পাসওয়ার্ড", "time", PasswordActivity::class.java, keywords = "password generator"),
        // ছবি
        Tool("imgfmt", "ছবি কনভার্টার", "ফরম্যাট বদল, সাইজ কমানো, ছবি বড় করা", "image", ImageFormatActivity::class.java, false, "image format converter png jpg webp compress reduce size upscale enlarge ফরম্যাট সাইজ কমানো বড়"),
        Tool("transp", "ট্রান্সপারেন্ট PNG মেকার", "ব্যাকগ্রাউন্ড মুছুন", "image", TransparentActivity::class.java, false, "transparent png background remove"),
        Tool("color", "কালার পিকার", "ছবি থেকে রঙের কোড", "image", ColorPickerActivity::class.java, false, "color picker hex rgb"),
        // PDF
        Tool("pdfedit", "PDF Edit", "স্ক্যান, এডিট, জোড়া, ভাগ, লক, OCR — সব এক জায়গায়", "pdf", PdfEditorActivity::class.java, keywords = "pdf edit editor scan ocr merge split compress lock unlock protect sign signature watermark page number organize rotate crop resize grayscale image to pdf pdf to image text extract word জোড়া ভাগ ছোট লক সই স্ক্যান পেজ"),
        Tool("txt2pdf", "PDF বানান", "লেখা, ওয়েবপেজ বা ইনভয়েস থেকে PDF", "pdf", TextToPdfActivity::class.java, keywords = "text to pdf write document webpage web invoice bill লেখা ওয়েবপেজ ইনভয়েস বিল"),
        Tool("pdfimgs", "PDF থেকে ছবি", "ভেতরের ছবিগুলো আলাদা করুন", "pdf", PdfImagesActivity::class.java, keywords = "extract images pdf pictures"),
        Tool("pdfnup", "এক শিটে একাধিক পেজ", "২/৪/৬/৯ পেজ বা বুকলেট", "pdf", PdfNupActivity::class.java, keywords = "pages per sheet n-up booklet print pdf"),
        Tool("collage", "ছবির কোলাজ", "একাধিক ছবি এক ছবিতে", "pdf", CollageActivity::class.java, keywords = "photo collage grid combine images"),
        // অডিও ও ভিডিও
        Tool("aconv", "অডিও কনভার্টার", "অডিও বা ভিডিও থেকে WAV, M4A", "media", AudioConvertActivity::class.java, false, "audio converter mp3 wav video to audio extract ভিডিও অডিও"),
        Tool("gif", "GIF মেকার", "ছবি বা ভিডিও থেকে GIF", "media", GifMakerActivity::class.java, false, "gif maker"),
        Tool("vmeta", "ভিডিও মেটাডেটা", "ভিডিওর খুঁটিনাটি তথ্য", "media", VideoInfoActivity::class.java, false, "video metadata viewer info"),
        Tool("rec", "কল রেকর্ডিং", "টেস্ট স্ক্রিন", "media", MainActivity::class.java, keywords = "call recording record"),
        // টেক্সট ও ভাষা
        Tool("textcase", "টেক্সট টুলস", "কেস বদল, শব্দ গণনা, ইউনিকোড", "text", TextCaseActivity::class.java, keywords = "text case converter uppercase lowercase title word counter character count unicode utf-8 শব্দ অক্ষর গণনা"),
        Tool("dict", "অভিধান", "শব্দের অর্থ খুঁজুন", "text", DictionaryActivity::class.java, true, "dictionary meaning"),
        // QR ও বারকোড
        Tool("qrgen", "QR ও বারকোড", "তৈরি করুন ও ক্যামেরায় পড়ুন", "qr", QrGenActivity::class.java, false, "qr code generator barcode scanner scan read কিউআর বারকোড স্ক্যান"),
        // নেটওয়ার্ক ও ডিভাইস
        Tool("speed", "ইন্টারনেট স্পিড টেস্ট", "ডাউনলোড ও আপলোড গতি", "net", SpeedTestActivity::class.java, true, "internet speed test"),
        Tool("ip", "IP ও WHOIS", "আমার IP, ডোমেইনের মালিকানা তথ্য", "net", IpActivity::class.java, true, "ip address checker public ip information whois domain lookup"),
        Tool("webcam", "ওয়েবক্যাম টেস্টার", "ক্যামেরা ঠিকমতো চলছে কি না", "net", WebcamActivity::class.java, false, "webcam camera tester")
    )

    fun cat(id: String): Cat = cats.first { it.id == id }
    /** Tools the person hid from the home screen and lists. */
    @Volatile var hidden: Set<String> = emptySet()

    fun loadHidden(c: Context) {
        hidden = (c.getSharedPreferences("home", Context.MODE_PRIVATE).getString("hidden", "") ?: "")
            .split(",").filter { it.isNotEmpty() }.toSet()
    }

    fun setHidden(c: Context, id: String, hide: Boolean) {
        val s = hidden.toMutableSet()
        if (hide) s.add(id) else s.remove(id)
        hidden = s
        c.getSharedPreferences("home", Context.MODE_PRIVATE).edit().putString("hidden", s.joinToString(",")).apply()
    }

    fun inCat(id: String): List<Tool> = tools.filter { it.cat == id && it.id !in hidden }

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
        if (t.id in hidden) return false
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        val hay = (t.title + " " + t.sub + " " + t.keywords + " " + cat(t.cat).name).lowercase()
        return q.split(Regex("\\s+")).all { hay.contains(it) }
    }
}
