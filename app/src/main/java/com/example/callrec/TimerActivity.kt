package com.example.callrec

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** টাইমার: countdown, stopwatch with laps, and a pomodoro timer. Alerts even when the app is closed. */
class TimerActivity : GuardedActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val sp by lazy { getSharedPreferences("timers", MODE_PRIVATE) }
    private val cd by lazy { Countdown(this, "cd", 5 * 60_000L) }
    private val pomo by lazy { Countdown(this, "pomo", 25 * 60_000L) }

    private var tab = 0
    private var ticker: (() -> Unit)? = null
    private var ringtone: Ringtone? = null

    private lateinit var tabRow: LinearLayout
    private lateinit var body: FrameLayout

    private val loop = object : Runnable {
        override fun run() {
            checkFinish()
            ticker?.invoke()
            handler.postDelayed(this, 50)
        }
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        tabRow = LinearLayout(this)
        tabRow.orientation = LinearLayout.HORIZONTAL
        root.addView(tabRow, lp(l = 14, t = 12, r = 14, b = 6))
        body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setScreen(tr("টাইমার"), root)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        }
        showTab(0)
    }

    override fun onResume() {
        super.onResume()
        TimerState.visible = true
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(1000 + Alarms.CODE_COUNTDOWN)
        nm.cancel(1000 + Alarms.CODE_POMODORO)
        handler.post(loop)
    }

    override fun onPause() {
        super.onPause()
        TimerState.visible = false
        handler.removeCallbacks(loop)
        stopAlert()
    }

    // ------------------------------------------------------------------ helpers

    private fun hms(ms: Long): String {
        val s = (ms + 999) / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        val t = if (h > 0) String.format("%d:%02d:%02d", h, m, sec) else String.format("%02d:%02d", m, sec)
        return bn(t)
    }

    private fun alert() {
        stopAlert()
        try {
            val r = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            r.play()
            ringtone = r
            handler.postDelayed({ stopAlert() }, 8000)
        } catch (e: Exception) {
            // no ringtone available; vibration below still works
        }
        try {
            val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 400), -1))
        } catch (e: Exception) {
            // device without vibrator
        }
        Toast.makeText(this, tr("⏰ সময় শেষ!"), Toast.LENGTH_LONG).show()
    }

    private fun stopAlert() {
        try {
            ringtone?.stop()
        } catch (e: Exception) {
            // already stopped
        }
        ringtone = null
    }

    private fun bigTime(): TextView {
        val t = label("", 56f, Ui.TEXT, true)
        t.gravity = Gravity.CENTER
        t.typeface = Typeface.MONOSPACE
        t.setPadding(0, dp(18), 0, dp(18))
        return t
    }

    private fun showTab(t: Int) {
        tab = t
        ticker = null
        tabRow.removeAllViews()
        val names = listOf(tr("কাউন্টডাউন"), tr("স্টপওয়াচ"), tr("পমোডোরো"))
        for (i in names.indices) {
            val on = tab == i
            val chip = label(names[i], 14f, if (on) Color.WHITE else Ui.PRIMARY, true)
            chip.gravity = Gravity.CENTER
            chip.background = bg(if (on) Ui.PRIMARY else Ui.CARD, 20, Ui.PRIMARY, 1)
            chip.setPadding(dp(14), dp(8), dp(14), dp(8))
            chip.setOnClickListener { showTab(i) }
            tabRow.addView(chip, lp(r = 8))
        }
        body.removeAllViews()
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(14), dp(6), dp(14), dp(24))
        scroll.addView(col)
        body.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        when (tab) {
            0 -> buildCountdown(col)
            1 -> buildStopwatch(col)
            else -> buildPomodoro(col)
        }
    }

    private fun checkFinish() {
        val now = System.currentTimeMillis()
        if (cd.running && cd.endAt <= now) {
            val late = now - cd.endAt
            cd.reset()
            Alarms.cancel(this, Alarms.CODE_COUNTDOWN)
            if (late < 6000) alert()
        }
        if (pomo.running && pomo.endAt <= now) {
            val late = now - pomo.endAt
            advancePomo()
            Alarms.cancel(this, Alarms.CODE_POMODORO)
            if (late < 6000) alert()
        }
    }

    // ------------------------------------------------------------------ countdown

    private fun buildCountdown(col: LinearLayout) {
        val c = card(16)
        val time = bigTime()
        c.addView(time, lp())
        col.addView(c, lp(b = 12))

        val chipScroll = HorizontalScrollView(this)
        chipScroll.isHorizontalScrollBarEnabled = false
        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        for (m in listOf(1, 3, 5, 10, 15, 30, 60)) {
            val chip = label(bn(m.toString()) + tr(" মি"), 14f, Ui.PRIMARY, true)
            chip.background = bg(Ui.CARD, 20, Ui.PRIMARY, 1)
            chip.setPadding(dp(16), dp(8), dp(16), dp(8))
            chip.setOnClickListener {
                if (cd.running) {
                    Toast.makeText(this, tr("আগে থামান বা রিসেট করুন"), Toast.LENGTH_SHORT).show()
                } else {
                    cd.reset(m * 60_000L)
                }
            }
            chips.addView(chip, lp(r = 8))
        }
        chipScroll.addView(chips)
        col.addView(chipScroll, lp(b = 12))

        val custom = card(14)
        custom.addView(label(tr("নিজের সময় দিন"), 13f, Ui.MUTED))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val min = EditText(this)
        min.hint = tr("মিনিট")
        min.inputType = InputType.TYPE_CLASS_NUMBER
        val sec = EditText(this)
        sec.hint = tr("সেকেন্ড")
        sec.inputType = InputType.TYPE_CLASS_NUMBER
        row.addView(min, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(sec, lp(width = 0, l = 8, r = 8).also { it.weight = 1f })
        row.addView(pill(tr("সেট")) {
            val m = asciiDigits(min.text.toString()).trim().toLongOrNull() ?: 0L
            val s = asciiDigits(sec.text.toString()).trim().toLongOrNull() ?: 0L
            val total = (m * 60 + s) * 1000
            if (cd.running) {
                Toast.makeText(this, tr("আগে থামান বা রিসেট করুন"), Toast.LENGTH_SHORT).show()
            } else if (total <= 0 || total > 99L * 3600 * 1000) {
                Toast.makeText(this, tr("সঠিক সময় দিন"), Toast.LENGTH_SHORT).show()
            } else {
                cd.reset(total)
            }
        })
        custom.addView(row, lp(t = 4))
        col.addView(custom, lp(b = 12))

        val controls = LinearLayout(this)
        controls.orientation = LinearLayout.HORIZONTAL
        val startBtn = pill(tr("শুরু")) {
            if (cd.running) {
                cd.pause()
                Alarms.cancel(this, Alarms.CODE_COUNTDOWN)
            } else {
                cd.start()
                Alarms.schedule(this, Alarms.CODE_COUNTDOWN, cd.endAt, tr("টাইমার"), tr("সময় শেষ হয়েছে"))
            }
        }
        val resetBtn = pill(tr("রিসেট"), Ui.BG, Ui.RED) {
            cd.reset()
            Alarms.cancel(this, Alarms.CODE_COUNTDOWN)
        }
        controls.addView(startBtn, lp(width = 0, r = 8).also { it.weight = 1f })
        controls.addView(resetBtn, lp(width = 0, l = 8).also { it.weight = 1f })
        col.addView(controls, lp())

        var lastRunning: Boolean? = null
        ticker = {
            time.text = hms(cd.remaining())
            if (lastRunning != cd.running) {
                lastRunning = cd.running
                startBtn.text = if (cd.running) tr("থামান") else tr("শুরু")
                startBtn.background = bg(if (cd.running) Ui.RED else Ui.PRIMARY, 22)
            }
        }
    }

    // ------------------------------------------------------------------ stopwatch

    private fun swElapsed(): Long {
        val acc = sp.getLong("sw.acc", 0L)
        val start = sp.getLong("sw.start", 0L)
        return if (start > 0L) acc + (System.currentTimeMillis() - start) else acc
    }

    private fun swLaps(): List<Long> {
        val s = sp.getString("sw.laps", "") ?: ""
        return if (s.isEmpty()) emptyList() else s.split(",").mapNotNull { it.toLongOrNull() }
    }

    private fun swTime(ms: Long): String {
        val cs = (ms / 10) % 100
        val s = (ms / 1000) % 60
        val m = (ms / 60000) % 60
        val h = ms / 3600000
        val t = if (h > 0) String.format("%d:%02d:%02d.%02d", h, m, s, cs) else String.format("%02d:%02d.%02d", m, s, cs)
        return bn(t)
    }

    private fun buildStopwatch(col: LinearLayout) {
        val c = card(16)
        val time = bigTime()
        time.textSize = 46f
        c.addView(time, lp())
        col.addView(c, lp(b = 12))

        val lapBox = LinearLayout(this)
        lapBox.orientation = LinearLayout.VERTICAL

        fun renderLaps() {
            lapBox.removeAllViews()
            val laps = swLaps()
            if (laps.isEmpty()) return
            val lc = card(14)
            lc.addView(label(tr("ল্যাপ"), 14f, Ui.TEXT, true), lp(b = 4))
            for (i in laps.indices.reversed()) {
                val prev = if (i == 0) 0L else laps[i - 1]
                val r = LinearLayout(this)
                r.orientation = LinearLayout.HORIZONTAL
                r.addView(label("#" + bn((i + 1).toString()), 14f, Ui.MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f))
                r.addView(label("+" + swTime(laps[i] - prev), 14f, Ui.TEXT, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f))
                val tot = label(swTime(laps[i]), 14f, Ui.MUTED)
                tot.gravity = Gravity.END
                r.addView(tot, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f))
                lc.addView(r, lp(t = 4))
            }
            lapBox.addView(lc, lp(t = 12))
        }

        val controls = LinearLayout(this)
        controls.orientation = LinearLayout.HORIZONTAL
        val startBtn = pill(tr("শুরু")) {
            val running = sp.getLong("sw.start", 0L) > 0L
            if (running) {
                sp.edit().putLong("sw.acc", swElapsed()).putLong("sw.start", 0L).apply()
            } else {
                sp.edit().putLong("sw.start", System.currentTimeMillis()).apply()
            }
        }
        val lapBtn = pill(tr("ল্যাপ"), Ui.BG, Ui.PRIMARY) {
            if (sp.getLong("sw.start", 0L) > 0L) {
                val laps = swLaps().toMutableList()
                laps.add(swElapsed())
                sp.edit().putString("sw.laps", laps.joinToString(",")).apply()
                renderLaps()
            }
        }
        val resetBtn = pill(tr("রিসেট"), Ui.BG, Ui.RED) {
            sp.edit().putLong("sw.acc", 0L).putLong("sw.start", 0L).putString("sw.laps", "").apply()
            renderLaps()
        }
        controls.addView(startBtn, lp(width = 0, r = 6).also { it.weight = 1f })
        controls.addView(lapBtn, lp(width = 0, l = 6, r = 6).also { it.weight = 1f })
        controls.addView(resetBtn, lp(width = 0, l = 6).also { it.weight = 1f })
        col.addView(controls, lp())
        col.addView(lapBox, lp())
        renderLaps()

        var lastRunning: Boolean? = null
        ticker = {
            time.text = swTime(swElapsed())
            val running = sp.getLong("sw.start", 0L) > 0L
            if (lastRunning != running) {
                lastRunning = running
                startBtn.text = if (running) tr("থামান") else tr("শুরু")
                startBtn.background = bg(if (running) Ui.RED else Ui.PRIMARY, 22)
            }
        }
    }

    // ------------------------------------------------------------------ pomodoro

    private fun phaseMinutes(phase: Int): Int = when (phase) {
        0 -> sp.getInt("pomo.work", 25)
        1 -> sp.getInt("pomo.short", 5)
        else -> sp.getInt("pomo.long", 15)
    }

    private fun phaseName(phase: Int): String = when (phase) {
        0 -> tr("কাজের সময়")
        1 -> tr("ছোট বিরতি")
        else -> tr("বড় বিরতি")
    }

    private fun advancePomo() {
        val phase = sp.getInt("pomo.phase", 0)
        var count = sp.getInt("pomo.count", 0)
        val next: Int
        if (phase == 0) {
            count += 1
            next = if (count % 4 == 0) 2 else 1
        } else {
            next = 0
        }
        sp.edit().putInt("pomo.phase", next).putInt("pomo.count", count).apply()
        pomo.reset(phaseMinutes(next) * 60_000L)
    }

    private fun pomoAlarmText(): Pair<String, String> {
        val phase = sp.getInt("pomo.phase", 0)
        val count = sp.getInt("pomo.count", 0)
        val next = if (phase == 0) (if ((count + 1) % 4 == 0) 2 else 1) else 0
        val title = if (phase == 0) tr("পমোডোরো: কাজ শেষ") else tr("পমোডোরো: বিরতি শেষ")
        return Pair(title, tr("পরের ধাপ: ") + phaseName(next) + " (" + bn(phaseMinutes(next).toString()) + tr(" মিনিট)"))
    }

    private fun buildPomodoro(col: LinearLayout) {
        val c = card(16)
        val phaseLbl = label("", 18f, Ui.PRIMARY, true)
        phaseLbl.gravity = Gravity.CENTER
        c.addView(phaseLbl, lp())
        val time = bigTime()
        c.addView(time, lp())
        val countLbl = label("", 13f, Ui.MUTED)
        countLbl.gravity = Gravity.CENTER
        c.addView(countLbl, lp())
        col.addView(c, lp(b = 12))

        val controls = LinearLayout(this)
        controls.orientation = LinearLayout.HORIZONTAL
        val startBtn = pill(tr("শুরু")) {
            if (pomo.running) {
                pomo.pause()
                Alarms.cancel(this, Alarms.CODE_POMODORO)
            } else {
                // make sure a fresh phase starts from its full length
                if (pomo.remainingMs <= 0L) pomo.reset(phaseMinutes(sp.getInt("pomo.phase", 0)) * 60_000L)
                pomo.start()
                val m = pomoAlarmText()
                Alarms.schedule(this, Alarms.CODE_POMODORO, pomo.endAt, m.first, m.second)
            }
        }
        val skipBtn = pill(tr("পরের ধাপ"), Ui.BG, Ui.PRIMARY) {
            Alarms.cancel(this, Alarms.CODE_POMODORO)
            advancePomo()
        }
        val resetBtn = pill(tr("রিসেট"), Ui.BG, Ui.RED) {
            Alarms.cancel(this, Alarms.CODE_POMODORO)
            sp.edit().putInt("pomo.phase", 0).putInt("pomo.count", 0).apply()
            pomo.reset(phaseMinutes(0) * 60_000L)
        }
        controls.addView(startBtn, lp(width = 0, r = 6).also { it.weight = 1f })
        controls.addView(skipBtn, lp(width = 0, l = 6, r = 6).also { it.weight = 1f })
        controls.addView(resetBtn, lp(width = 0, l = 6).also { it.weight = 1f })
        col.addView(controls, lp(b = 12))
        col.addView(pill(tr("সময় সেটিংস"), Ui.BG, Ui.PRIMARY) { showPomoSettings() }, lp())

        var lastRunning: Boolean? = null
        ticker = {
            val phase = sp.getInt("pomo.phase", 0)
            phaseLbl.text = phaseName(phase)
            time.text = hms(pomo.remaining())
            countLbl.text = tr("সম্পন্ন পমোডোরো: ") + bn(sp.getInt("pomo.count", 0).toString()) + tr(" টি")
            if (lastRunning != pomo.running) {
                lastRunning = pomo.running
                startBtn.text = if (pomo.running) tr("থামান") else tr("শুরু")
                startBtn.background = bg(if (pomo.running) Ui.RED else Ui.PRIMARY, 22)
            }
        }
    }

    private fun showPomoSettings() {
        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(20), dp(10), dp(20), 0)

        fun field(title: String, key: String, def: Int): EditText {
            form.addView(label(title, 13f, Ui.MUTED), lp(t = 8))
            val e = EditText(this)
            e.inputType = InputType.TYPE_CLASS_NUMBER
            e.setText(sp.getInt(key, def).toString())
            form.addView(e, lp())
            return e
        }
        val w = field(tr("কাজ (মিনিট)"), "pomo.work", 25)
        val s = field(tr("ছোট বিরতি (মিনিট)"), "pomo.short", 5)
        val l = field(tr("বড় বিরতি (মিনিট)"), "pomo.long", 15)

        val dlg = AlertDialog.Builder(this)
            .setTitle(tr("পমোডোরো সময়"))
            .setView(form)
            .setPositiveButton(tr("সংরক্ষণ"), null)
            .setNegativeButton(tr("বাতিল"), null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val wv = asciiDigits(w.text.toString()).trim().toIntOrNull()
                val sv = asciiDigits(s.text.toString()).trim().toIntOrNull()
                val lv = asciiDigits(l.text.toString()).trim().toIntOrNull()
                if (wv == null || sv == null || lv == null || wv !in 1..180 || sv !in 1..60 || lv !in 1..90) {
                    Toast.makeText(this, tr("সঠিক মিনিট দিন"), Toast.LENGTH_SHORT).show()
                } else {
                    sp.edit().putInt("pomo.work", wv).putInt("pomo.short", sv).putInt("pomo.long", lv).apply()
                    if (!pomo.running) pomo.reset(phaseMinutes(sp.getInt("pomo.phase", 0)) * 60_000L)
                    dlg.dismiss()
                }
            }
        }
        dlg.show()
    }
}
