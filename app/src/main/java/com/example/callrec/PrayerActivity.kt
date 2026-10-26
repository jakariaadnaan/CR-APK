package com.example.callrec

import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.util.Calendar

/** নামাজের সময়: offline prayer times for a chosen city, optional reminders. */
class PrayerActivity : GuardedActivity() {

    private lateinit var box: LinearLayout
    private var dayOffset = 0
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (dayOffset == 0) render()
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(8), dp(16), dp(20))
        scroll.addView(box)
        setScreen(tr("নামাজের সময়"), scroll, null, true,
            bottomBar(LinearLayout(this).also { it.addView(primaryButton(tr("মাসের সময়সূচি")) { showMonth() }, lp(height = dp(48))) }))
        render()
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(tick, 30_000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun pname(i: Int) = tr(PRAYER_NAMES[i])

    private fun render() {
        box.removeAllViews()
        val day = Calendar.getInstance()
        day.add(Calendar.DAY_OF_YEAR, dayOffset)
        val times = PrayerCalc.forDay(this, day)
        val loc = PrayerCalc.location(this)

        // date switcher
        val nav = LinearLayout(this)
        nav.gravity = Gravity.CENTER_VERTICAL
        val prev = label("‹", 26f, Ui.PRIMARY, true)
        prev.gravity = Gravity.CENTER
        prev.setOnClickListener { dayOffset--; render() }
        val next = label("›", 26f, Ui.PRIMARY, true)
        next.gravity = Gravity.CENTER
        next.setOnClickListener { dayOffset++; render() }
        val title = label(dateText(day.timeInMillis) + (if (dayOffset == 0) " · " + tr("আজ") else ""), 16f, Ui.TEXT, true)
        title.gravity = Gravity.CENTER
        nav.addView(prev, LinearLayout.LayoutParams(dp(48), dp(44)))
        nav.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(next, LinearLayout.LayoutParams(dp(48), dp(44)))
        box.addView(nav, lp(b = 6))

        // next prayer card (today only)
        var nextIdx = -1
        if (dayOffset == 0) {
            val now = System.currentTimeMillis()
            for (i in listOf(0, 2, 3, 4, 5)) {
                if (PrayerCalc.toMillis(day, times.at(i)) > now) { nextIdx = i; break }
            }
            if (nextIdx >= 0) {
                val ms = PrayerCalc.toMillis(day, times.at(nextIdx)) - now
                val mins = (ms / 60000).toInt()
                val left = tr("বাকি সময় ") + (if (mins >= 60) bn((mins / 60).toString()) + tr(" ঘণ্টা ") else "") + bn((mins % 60).toString()) + tr(" মিনিট")
                box.addView(resultBox(tr("পরবর্তী নামাজ"), pname(nextIdx) + " · " + PrayerCalc.format(times.at(nextIdx)), left, 28f), lp(b = 12))
            } else {
                box.addView(resultBox(tr("পরবর্তী নামাজ"), tr("আজকের সব নামাজের সময় শেষ"), tr("আগামীকালের সময় দেখতে › চাপুন"), 18f), lp(b = 12))
            }
        }

        // location row
        val locRow = LinearLayout(this)
        locRow.gravity = Gravity.CENTER_VERTICAL
        locRow.addView(label(tr(loc.first), 14f, Ui.TEXT, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val chg = label(tr("বদলান"), 13f, Ui.PRIMARY, true)
        chg.setPadding(dp(10), dp(6), dp(2), dp(6))
        chg.setOnClickListener { pickCity() }
        locRow.addView(chg)
        box.addView(locRow, lp(l = 4, b = 4))

        // schedule
        box.addView(sectionLabel(tr("আজকের সময়সূচি"), 6))
        val list = card(4)
        for (i in 0..5) {
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(12), dp(12), dp(8), dp(12))
            val hi = i == nextIdx
            if (hi) r.background = bg(Ui.KEY_OP, 10, Ui.mix(Ui.PRIMARY, Ui.CARD, 0.5f), 1)
            val dot = View(this)
            dot.background = bg(if (hi) Ui.PRIMARY else Ui.GREY, 4)
            r.addView(dot, lp(width = dp(8), height = dp(8), r = 12))
            r.addView(label(pname(i), 15f, if (hi) Ui.PRIMARY else Ui.TEXT, hi), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(label(PrayerCalc.format(times.at(i)), 15f, if (hi) Ui.PRIMARY else Ui.TEXT, true))
            if (i != 1) {
                val on = PrayerCalc.notify(this, i)
                val bell = label(if (on) "🔔" else "🔕", 16f, if (on) Ui.PRIMARY else Ui.MUTED)
                bell.gravity = Gravity.CENTER
                bell.setOnClickListener {
                    PrayerCalc.setNotify(this, i, !on)
                    if (!on && android.os.Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 10)
                    PrayerReminder.scheduleAll(this)
                    render()
                }
                r.addView(bell, LinearLayout.LayoutParams(dp(44), dp(32)).also { it.leftMargin = dp(4) })
            } else {
                r.addView(View(this), LinearLayout.LayoutParams(dp(44), dp(32)).also { it.leftMargin = dp(4) })
            }
            list.addView(r)
        }
        box.addView(list, lp(b = 14))

        // settings
        val city = tr(loc.first)
        box.addView(label(tr("সেটিং"), 13f, Ui.MUTED, true), lp(l = 4, b = 6))
        val set = ArrayList<android.view.View>()
        set.add(setRow(tr("শহর"), city) { pickCity() })
        set.add(setRow(tr("হিসাবের পদ্ধতি"), tr(PrayerCalc.METHODS[PrayerCalc.methodIndex(this)].name)) { pickMethod() })
        set.add(setRow(tr("আসরের হিসাব"), if (PrayerCalc.hanafi(this)) tr("হানাফি") else tr("শাফেয়ী, মালেকী, হাম্বলি")) { pickAsr() })
        set.add(setRow(tr("সময় সমন্বয়"), tr("মসজিদের সময়ের সঙ্গে মেলান")) { pickAdjust() })
        box.addView(groupCard(set), lp(b = 10))
        box.addView(label(tr("হিসাব ফোনেই হয়, ইন্টারনেট লাগে না। মসজিদের সময়ের সঙ্গে ১-২ মিনিট তফাত হতে পারে, দরকারে সময় সমন্বয় করুন। 🔔 চাপলে সেই নামাজের সময়ে নোটিফিকেশন আসবে।"), 12f, Ui.MUTED), lp(l = 4))
    }

    private fun showMonth() {
        val day = Calendar.getInstance()
        val n = day.getActualMaximum(Calendar.DAY_OF_MONTH)
        val lines = ArrayList<String>()
        for (d in 1..n) {
            day.set(Calendar.DAY_OF_MONTH, d)
            val t = PrayerCalc.forDay(this, day)
            lines.add(bn(d.toString()) + "  " + listOf(0, 2, 3, 4, 5).joinToString(" · ") { pname(it) + " " + PrayerCalc.format(t.at(it)) })
        }
        AlertDialog.Builder(this).setTitle(BN_MONTHS[Calendar.getInstance().get(Calendar.MONTH)])
            .setItems(lines.toTypedArray(), null).setPositiveButton(tr("বন্ধ"), null).show()
    }

    private fun setRow(name: String, value: String, onClick: () -> Unit): LinearLayout {
        val r = LinearLayout(this)
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(18), dp(15), dp(18), dp(15))
        r.addView(label(name, 15f, Ui.TEXT), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(label(value, 14f, Ui.MUTED))
        r.setOnClickListener { onClick() }
        return r
    }

    private fun changed() {
        PrayerReminder.scheduleAll(this)
        render()
    }

    private fun pickCity() {
        val names = PrayerCalc.CITIES.map { tr(it.name) } + tr("নিজের স্থান (অক্ষাংশ, দ্রাঘিমাংশ)")
        AlertDialog.Builder(this).setTitle(tr("শহর")).setItems(names.toTypedArray()) { _, i ->
            if (i < PrayerCalc.CITIES.size) {
                PrayerCalc.set(this, "city", i)
                changed()
            } else customLocation()
        }.show()
    }

    private fun customLocation() {
        val cu = PrayerCalc.custom(this)
        val lat = EditText(this)
        lat.hint = tr("অক্ষাংশ, যেমন: 23.81")
        lat.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        lat.setText(cu.first.toString())
        val lng = EditText(this)
        lng.hint = tr("দ্রাঘিমাংশ, যেমন: 90.41")
        lng.inputType = lat.inputType
        lng.setText(cu.second.toString())
        val w = LinearLayout(this)
        w.orientation = LinearLayout.VERTICAL
        w.setPadding(dp(20), dp(10), dp(20), 0)
        w.addView(lat, lp())
        w.addView(lng, lp(t = 4))
        AlertDialog.Builder(this).setTitle(tr("নিজের স্থান")).setView(w)
            .setPositiveButton(tr("সংরক্ষণ")) { _, _ ->
                val a = asciiDigits(lat.text.toString()).toDoubleOrNull()
                val b = asciiDigits(lng.text.toString()).toDoubleOrNull()
                if (a == null || b == null || a < -66 || a > 66 || b < -180 || b > 180) {
                    Toast.makeText(this, tr("সঠিক মান লিখুন"), Toast.LENGTH_SHORT).show()
                } else {
                    PrayerCalc.setCustom(this, a, b)
                    PrayerCalc.set(this, "city", -1)
                    changed()
                }
            }
            .setNegativeButton(tr("বাতিল"), null).show()
    }

    private fun pickMethod() {
        val names = PrayerCalc.METHODS.map { tr(it.name) }.toTypedArray()
        AlertDialog.Builder(this).setTitle(tr("হিসাবের পদ্ধতি")).setItems(names) { _, i ->
            PrayerCalc.set(this, "method", i)
            changed()
        }.show()
    }

    private fun pickAsr() {
        val names = arrayOf(tr("হানাফি"), tr("শাফেয়ী, মালেকী, হাম্বলি"))
        AlertDialog.Builder(this).setTitle(tr("আসরের হিসাব")).setItems(names) { _, i ->
            PrayerCalc.setHanafi(this, i == 0)
            changed()
        }.show()
    }

    private fun pickAdjust() {
        val names = Array(6) { i -> pname(i) + ": " + signed(PrayerCalc.adjust(this, i)) }
        AlertDialog.Builder(this).setTitle(tr("সময় সমন্বয়")).setItems(names) { _, i -> adjustOne(i) }
            .setNegativeButton(tr("বন্ধ"), null).show()
    }

    private fun signed(v: Int): String = (if (v > 0) "+" else if (v < 0) "-" else "") + bn(Math.abs(v).toString()) + tr(" মিনিট")

    private fun adjustOne(i: Int) {
        val f = EditText(this)
        f.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        f.hint = tr("মিনিট, যেমন: 2 বা -2")
        f.setText(PrayerCalc.adjust(this, i).toString())
        val w = LinearLayout(this)
        w.setPadding(dp(20), dp(10), dp(20), 0)
        w.addView(f, lp())
        AlertDialog.Builder(this).setTitle(pname(i)).setView(w)
            .setPositiveButton(tr("সংরক্ষণ")) { _, _ ->
                val v = (asciiDigits(f.text.toString()).toIntOrNull() ?: 0).coerceIn(-30, 30)
                PrayerCalc.setAdjust(this, i, v)
                changed()
            }
            .setNegativeButton(tr("বাতিল"), null).show()
    }
}
