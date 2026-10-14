package com.example.callrec

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Bangladesh revised Bangla calendar: Boishakh 1 is always 14 April; Falgun has 31 days in a leap year. */
object BanglaCal {
    val MONTHS = listOf(
        "বৈশাখ", "জ্যৈষ্ঠ", "আষাঢ়", "শ্রাবণ", "ভাদ্র", "আশ্বিন",
        "কার্তিক", "অগ্রহায়ণ", "পৌষ", "মাঘ", "ফাল্গুন", "চৈত্র"
    )

    class BDate(val year: Int, val month: Int, val day: Int)

    private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

    /** [startYear] is the Gregorian year in which that Bangla year starts (in April). */
    private fun lengths(startYear: Int): IntArray {
        val a = intArrayOf(31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 30, 30)
        a[10] = if (isLeap(startYear + 1)) 31 else 30
        return a
    }

    fun fromGregorian(d: LocalDate): BDate {
        var sy = d.year
        if (d.isBefore(LocalDate.of(sy, 4, 14))) sy -= 1
        var off = ChronoUnit.DAYS.between(LocalDate.of(sy, 4, 14), d).toInt()
        val len = lengths(sy)
        var m = 0
        while (m < 11 && off >= len[m]) {
            off -= len[m]
            m++
        }
        return BDate(sy - 593, m, off + 1)
    }

    /** [month] is 0-based. Returns null when the day does not exist in that month. */
    fun toGregorian(year: Int, month: Int, day: Int): LocalDate? {
        val sy = year + 593
        if (sy < 1700 || sy > 2400) return null
        val len = lengths(sy)
        if (month < 0 || month > 11 || day < 1 || day > len[month]) return null
        var off = day - 1
        for (i in 0 until month) off += len[i]
        return LocalDate.of(sy, 4, 14).plusDays(off.toLong())
    }
}
