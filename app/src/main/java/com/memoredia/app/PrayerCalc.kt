package com.memoredia.app

import android.content.Context
import java.util.Calendar
import java.util.TimeZone

/** Offline prayer times from the sun's position (same maths as the well-known PrayTimes method). */
object PrayerCalc {
    class Method(val name: String, val fajr: Double, val isha: Double, val ishaMin: Int = 0)
    class City(val name: String, val lat: Double, val lng: Double)
    /** Hours in local time; NaN when the sun never reaches that angle. */
    class Times(val fajr: Double, val sunrise: Double, val dhuhr: Double, val asr: Double, val maghrib: Double, val isha: Double) {
        fun at(i: Int): Double = when (i) { 0 -> fajr; 1 -> sunrise; 2 -> dhuhr; 3 -> asr; 4 -> maghrib; else -> isha }
    }

    val METHODS = listOf(
        Method("কারাচি (বাংলাদেশে প্রচলিত)", 18.0, 18.0),
        Method("মুসলিম ওয়ার্ল্ড লীগ", 18.0, 17.0),
        Method("মিশরীয় কর্তৃপক্ষ", 19.5, 17.5),
        Method("উম্মুল কুরা (মক্কা)", 18.5, 0.0, 90),
        Method("ISNA (উত্তর আমেরিকা)", 15.0, 15.0)
    )

    val CITIES = listOf(
        City("ঢাকা", 23.8103, 90.4125), City("চট্টগ্রাম", 22.3569, 91.7832), City("সিলেট", 24.8949, 91.8687),
        City("রাজশাহী", 24.3745, 88.6042), City("খুলনা", 22.8456, 89.5403), City("বরিশাল", 22.7010, 90.3535),
        City("রংপুর", 25.7439, 89.2752), City("ময়মনসিংহ", 24.7471, 90.4203), City("কুমিল্লা", 23.4607, 91.1809),
        City("গাজীপুর", 23.9999, 90.4203), City("নারায়ণগঞ্জ", 23.6238, 90.5000), City("কক্সবাজার", 21.4272, 92.0058),
        City("যশোর", 23.1664, 89.2081), City("বগুড়া", 24.8465, 89.3776), City("দিনাজপুর", 25.6217, 88.6354),
        City("পাবনা", 24.0064, 89.2372), City("টাঙ্গাইল", 24.2513, 89.9167), City("নোয়াখালী", 22.8696, 91.0995),
        City("ফেনী", 23.0159, 91.3976), City("কুষ্টিয়া", 23.9013, 89.1205)
    )

    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun sin(d: Double) = Math.sin(rad(d))
    private fun cos(d: Double) = Math.cos(rad(d))
    private fun tan(d: Double) = Math.tan(rad(d))
    private fun fix(a: Double, m: Double): Double { val r = a - m * Math.floor(a / m); return if (r < 0) r + m else r }

    private fun julian(y0: Int, m0: Int, d: Int): Double {
        var y = y0; var m = m0
        if (m <= 2) { y -= 1; m += 12 }
        val a = Math.floor(y / 100.0)
        val b = 2 - a + Math.floor(a / 4)
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    /** Returns (declination, equation of time in hours). */
    private fun sunPos(jd: Double): Pair<Double, Double> {
        val dd = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * dd, 360.0)
        val q = fix(280.459 + 0.98564736 * dd, 360.0)
        val l = fix(q + 1.915 * sin(g) + 0.020 * sin(2 * g), 360.0)
        val e = 23.439 - 0.00000036 * dd
        val ra = deg(Math.atan2(cos(e) * sin(l), cos(l))) / 15.0
        val eqt = q / 15.0 - fix(ra, 24.0)
        val decl = deg(Math.asin(sin(e) * sin(l)))
        return Pair(decl, eqt)
    }

    fun compute(y: Int, m: Int, d: Int, lat: Double, lng: Double, tz: Double, method: Method, hanafi: Boolean): Times {
        val jDate = julian(y, m, d) - lng / (15.0 * 24.0)
        fun midDay(t: Double) = fix(12 - sunPos(jDate + t).second, 24.0)
        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sunPos(jDate + t).first
            val x = (-sin(angle) - sin(decl) * sin(lat)) / (cos(decl) * cos(lat))
            if (x < -1 || x > 1) return Double.NaN
            val v = deg(Math.acos(x)) / 15.0
            return midDay(t) + if (ccw) -v else v
        }
        fun asr(factor: Double, t: Double): Double {
            val decl = sunPos(jDate + t).first
            val angle = -deg(Math.atan(1.0 / (factor + tan(Math.abs(lat - decl)))))
            return angleTime(angle, t, false)
        }
        val adj = tz - lng / 15.0
        val fajr = angleTime(method.fajr, 5.0 / 24, true) + adj
        val sunrise = angleTime(0.833, 6.0 / 24, true) + adj
        val dhuhr = midDay(12.0 / 24) + adj
        val asr = asr(if (hanafi) 2.0 else 1.0, 13.0 / 24) + adj
        val maghrib = angleTime(0.833, 18.0 / 24, false) + adj
        val isha = if (method.ishaMin > 0) maghrib + method.ishaMin / 60.0 else angleTime(method.isha, 18.0 / 24, false) + adj
        return Times(fajr, sunrise, dhuhr, asr, maghrib, isha)
    }

    // ---- saved choices
    private fun sp(c: Context) = c.getSharedPreferences("prayer", Context.MODE_PRIVATE)
    fun cityIndex(c: Context) = sp(c).getInt("city", 0)
    fun methodIndex(c: Context) = sp(c).getInt("method", 0).coerceIn(0, METHODS.size - 1)
    fun hanafi(c: Context) = sp(c).getBoolean("hanafi", true)
    fun adjust(c: Context, i: Int) = sp(c).getInt("adj$i", 0)
    fun setAdjust(c: Context, i: Int, v: Int) = sp(c).edit().putInt("adj$i", v).apply()
    fun notify(c: Context, i: Int) = sp(c).getBoolean("n$i", false)
    fun setNotify(c: Context, i: Int, on: Boolean) = sp(c).edit().putBoolean("n$i", on).apply()
    fun set(c: Context, key: String, v: Int) = sp(c).edit().putInt(key, v).apply()
    fun setHanafi(c: Context, v: Boolean) = sp(c).edit().putBoolean("hanafi", v).apply()
    fun custom(c: Context): Pair<Double, Double> = Pair(sp(c).getFloat("clat", 23.8103f).toDouble(), sp(c).getFloat("clng", 90.4125f).toDouble())
    fun setCustom(c: Context, lat: Double, lng: Double) = sp(c).edit().putFloat("clat", lat.toFloat()).putFloat("clng", lng.toFloat()).apply()

    fun location(c: Context): Triple<String, Double, Double> {
        val i = cityIndex(c)
        if (i in CITIES.indices) return Triple(CITIES[i].name, CITIES[i].lat, CITIES[i].lng)
        val cu = custom(c)
        return Triple("নিজের স্থান", cu.first, cu.second)
    }

    /** Times for a day, including the user's minute adjustments. */
    fun forDay(c: Context, cal: Calendar): Times {
        val loc = location(c)
        val noon = Calendar.getInstance()
        noon.timeInMillis = cal.timeInMillis
        noon.set(Calendar.HOUR_OF_DAY, 12)
        val tz = TimeZone.getDefault().getOffset(noon.timeInMillis) / 3600000.0
        val t = compute(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), loc.second, loc.third, tz, METHODS[methodIndex(c)], hanafi(c))
        return Times(t.fajr + adjust(c, 0) / 60.0, t.sunrise + adjust(c, 1) / 60.0, t.dhuhr + adjust(c, 2) / 60.0,
            t.asr + adjust(c, 3) / 60.0, t.maghrib + adjust(c, 4) / 60.0, t.isha + adjust(c, 5) / 60.0)
    }

    fun toMillis(day: Calendar, hours: Double): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = day.timeInMillis
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis + Math.round(hours * 3600000.0)
    }

    fun format(hours: Double): String {
        if (hours.isNaN()) return "--"
        val total = Math.round(fix(hours, 24.0) * 60.0).toInt() % 1440
        val h24 = total / 60
        val mm = total % 60
        val h12 = if (h24 % 12 == 0) 12 else h24 % 12
        return bn(String.format(java.util.Locale.US, "%d:%02d", h12, mm)) + " " + (if (h24 < 12) "AM" else "PM")
    }
}
