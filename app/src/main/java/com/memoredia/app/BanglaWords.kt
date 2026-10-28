package com.memoredia.app

/** Bangla number words using lakh / crore grouping, e.g. 12500 -> বারো হাজার পাঁচশত. */
object BanglaWords {
    private val W = arrayOf(
        "শূন্য", "এক", "দুই", "তিন", "চার", "পাঁচ", "ছয়", "সাত", "আট", "নয়",
        "দশ", "এগারো", "বারো", "তেরো", "চৌদ্দ", "পনেরো", "ষোলো", "সতেরো", "আঠারো", "উনিশ",
        "বিশ", "একুশ", "বাইশ", "তেইশ", "চব্বিশ", "পঁচিশ", "ছাব্বিশ", "সাতাশ", "আঠাশ", "ঊনত্রিশ",
        "ত্রিশ", "একত্রিশ", "বত্রিশ", "তেত্রিশ", "চৌত্রিশ", "পঁয়ত্রিশ", "ছত্রিশ", "সাঁইত্রিশ", "আটত্রিশ", "ঊনচল্লিশ",
        "চল্লিশ", "একচল্লিশ", "বিয়াল্লিশ", "তেতাল্লিশ", "চুয়াল্লিশ", "পঁয়তাল্লিশ", "ছেচল্লিশ", "সাতচল্লিশ", "আটচল্লিশ", "ঊনপঞ্চাশ",
        "পঞ্চাশ", "একান্ন", "বায়ান্ন", "তিপ্পান্ন", "চুয়ান্ন", "পঞ্চান্ন", "ছাপ্পান্ন", "সাতান্ন", "আটান্ন", "ঊনষাট",
        "ষাট", "একষট্টি", "বাষট্টি", "তেষট্টি", "চৌষট্টি", "পঁয়ষট্টি", "ছেষট্টি", "সাতষট্টি", "আটষট্টি", "ঊনসত্তর",
        "সত্তর", "একাত্তর", "বাহাত্তর", "তিয়াত্তর", "চুয়াত্তর", "পঁচাত্তর", "ছিয়াত্তর", "সাতাত্তর", "আটাত্তর", "ঊনআশি",
        "আশি", "একাশি", "বিরাশি", "তিরাশি", "চুরাশি", "পঁচাশি", "ছিয়াশি", "সাতাশি", "অষ্টাশি", "ঊননব্বই",
        "নব্বই", "একানব্বই", "বিরানব্বই", "তিরানব্বই", "চুরানব্বই", "পঁচানব্বই", "ছিয়ানব্বই", "সাতানব্বই", "আটানব্বই", "নিরানব্বই"
    )

    private fun below1000(n: Int): String {
        val parts = ArrayList<String>()
        val h = n / 100
        val r = n % 100
        if (h > 0) parts.add(W[h] + "শত")
        if (r > 0) parts.add(W[r])
        return parts.joinToString(" ")
    }

    fun number(n: Long): String {
        if (n == 0L) return W[0]
        var rest = n
        val crore = rest / 10000000L
        rest %= 10000000L
        val lakh = (rest / 100000L).toInt()
        rest %= 100000L
        val thousand = (rest / 1000L).toInt()
        rest %= 1000L
        val parts = ArrayList<String>()
        if (crore > 0) parts.add(number(crore) + " কোটি")
        if (lakh > 0) parts.add(W[lakh] + " লাখ")
        if (thousand > 0) parts.add(W[thousand] + " হাজার")
        if (rest > 0) parts.add(below1000(rest.toInt()))
        return parts.joinToString(" ")
    }

    /** [totalPaisa] is the amount multiplied by 100. */
    fun amount(totalPaisa: Long): String {
        val taka = totalPaisa / 100
        val paisa = (totalPaisa % 100).toInt()
        val sb = StringBuilder()
        if (taka > 0 || paisa == 0) sb.append(number(taka)).append(" টাকা")
        if (paisa > 0) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(W[paisa]).append(" পয়সা")
        }
        sb.append(" মাত্র")
        return sb.toString()
    }
}
