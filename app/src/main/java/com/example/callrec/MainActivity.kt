package com.example.callrec

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** Step 1 test screen: record, watch call events, and see what each audio source captures. */
class MainActivity : GuardedActivity() {

    private lateinit var logView: TextView
    private lateinit var listBox: LinearLayout
    private lateinit var srcBtn: Button
    private lateinit var autoBtn: Button
    private lateinit var recBtn: Button
    private lateinit var permView: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var playingName: String? = null
    private var lastRunning: Boolean? = null
    private var lastLog = ""

    private val prefs by lazy { getSharedPreferences("callrec_prefs", MODE_PRIVATE) }

    private val ticker = object : Runnable {
        override fun run() {
            refreshLog()
            updateButtons()
            handler.postDelayed(this, 1000)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun neededPermissions(): List<String> {
        val list = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        return list
    }

    private fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun deviceInfo() =
        "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    private fun text(s: String, sp: Float = 14f, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = sp
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), dp(24))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)

        root.addView(text("Call Recorder Test", 24f, true))
        root.addView(text(deviceInfo(), 13f))
        root.addView(text("Make a real phone call, start recording, then play the file back to hear if the other person was recorded. Try each source.", 13f))

        permView = text("", 13f)
        root.addView(permView)
        root.addView(button("1. Allow permissions") {
            requestPermissions(neededPermissions().toTypedArray(), 1)
        })

        srcBtn = button("") { cycleSource() }
        root.addView(srcBtn)

        recBtn = button("") { toggleRecording() }
        root.addView(recBtn)

        autoBtn = button("") {
            prefs.edit().putBoolean("auto", !prefs.getBoolean("auto", false)).apply()
            updateButtons()
        }
        root.addView(autoBtn)

        root.addView(button("Copy report (device + log)") { copyReport() })
        root.addView(button("Clear log") { EventLog.clear(this); lastLog = "\u0000"; refreshLog() })

        root.addView(text("Log", 16f, true))
        logView = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
        }
        root.addView(logView)

        root.addView(text("Recordings (tap = play or stop, long-press = delete)", 16f, true))
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listBox)

        updateButtons()
        refreshList()
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        stopPlayer()
        super.onPause()
    }

    private fun cycleSource() {
        if (RecorderService.running) { toast("Stop recording first"); return }
        val next = (prefs.getInt("source", 0) + 1) % RecorderService.SOURCES.size
        prefs.edit().putInt("source", next).apply()
        updateButtons()
    }

    private fun toggleRecording() {
        if (RecorderService.running) {
            startService(Intent(this, RecorderService::class.java).setAction(RecorderService.ACTION_STOP))
            return
        }
        if (!granted(Manifest.permission.RECORD_AUDIO)) {
            toast("Allow permissions first")
            return
        }
        try {
            startForegroundService(
                Intent(this, RecorderService::class.java)
                    .setAction(RecorderService.ACTION_START)
                    .putExtra(RecorderService.EXTRA_SOURCE, prefs.getInt("source", 0))
            )
        } catch (t: Throwable) {
            EventLog.add(this, "Could not start: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun updateButtons() {
        val running = RecorderService.running
        srcBtn.text = "2. Source: ${RecorderService.SOURCES[prefs.getInt("source", 0)].first} (tap to change)"
        recBtn.text = if (running) "Stop recording" else "3. Start recording"
        autoBtn.text = "Auto-record on calls: " + if (prefs.getBoolean("auto", false)) "ON" else "OFF"
        val missing = neededPermissions().filter { !granted(it) }
        permView.text = if (missing.isEmpty()) "Permissions: all allowed"
        else "Missing: " + missing.joinToString { it.substringAfterLast('.') }
        if (lastRunning != running) {
            lastRunning = running
            refreshList()
        }
    }

    private fun refreshLog() {
        val s = EventLog.read(this)
        if (s != lastLog) {
            lastLog = s
            logView.text = if (s.isEmpty()) "(empty)" else s
        }
    }

    private fun refreshList() {
        listBox.removeAllViews()
        val files = RecorderService.recDir(this).listFiles { f -> f.extension == "m4a" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (files.isEmpty()) {
            listBox.addView(text("No recordings yet", 13f))
            return
        }
        for (f in files) {
            val row = text("${f.name}\n${f.length() / 1024} KB", 13f)
            row.setPadding(0, dp(10), 0, dp(10))
            row.setOnClickListener { togglePlay(f) }
            row.setOnLongClickListener { f.delete(); stopPlayer(); refreshList(); true }
            if (f.name == playingName) row.setBackgroundColor(0x2233AA33)
            listBox.addView(row)
        }
    }

    private fun togglePlay(f: File) {
        val wasPlaying = playingName == f.name
        stopPlayer()
        if (wasPlaying) { refreshList(); return }
        try {
            player = MediaPlayer().apply {
                setDataSource(f.absolutePath)
                setOnCompletionListener { stopPlayer(); refreshList() }
                prepare()
                start()
            }
            playingName = f.name
        } catch (t: Throwable) {
            toast("Cannot play: ${t.message}")
            stopPlayer()
        }
        refreshList()
    }

    private fun stopPlayer() {
        try { player?.release() } catch (_: Throwable) {}
        player = null
        playingName = null
    }

    private fun copyReport() {
        val report = deviceInfo() + "\n" + (permView.text) + "\n\n" + EventLog.read(this)
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("report", report))
        toast("Copied. Paste it into the chat.")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateButtons()
    }
}
