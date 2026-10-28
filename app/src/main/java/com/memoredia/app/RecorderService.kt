package com.memoredia.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records audio in a foreground service, so it keeps going with the screen off.
 * The audio source can be changed from the app, because which source (if any) can hear
 * the other person on a call depends on the phone and the Android version.
 */
class RecorderService : Service() {

    companion object {
        const val ACTION_START = "com.memoredia.app.START"
        const val ACTION_STOP = "com.memoredia.app.STOP"
        const val EXTRA_SOURCE = "source"
        private const val CHANNEL = "recording"
        private const val NOTIF_ID = 1

        @Volatile
        var running = false

        // VOICE_CALL normally needs a system permission, so it is expected to fail on most phones.
        val SOURCES: List<Pair<String, Int>> = listOf(
            "MIC" to MediaRecorder.AudioSource.MIC,
            "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
            "VOICE_COMMUNICATION" to MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            "UNPROCESSED" to MediaRecorder.AudioSource.UNPROCESSED,
            "VOICE_CALL" to MediaRecorder.AudioSource.VOICE_CALL
        )

        fun recDir(ctx: Context): File =
            ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: ctx.filesDir
    }

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private val handler = Handler(Looper.getMainLooper())
    private var peak = 0
    private var loud = 0
    private var samples = 0
    private var startedAt = 0L

    private val sampler = object : Runnable {
        override fun run() {
            val r = recorder
            if (r != null) {
                try {
                    val a = r.maxAmplitude
                    if (a > peak) peak = a
                    if (a > 2000) loud++
                    samples++
                } catch (_: Throwable) {
                }
                handler.postDelayed(this, 250)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_START -> {
                if (running) return START_NOT_STICKY
                try {
                    val n = buildNotification()
                    if (Build.VERSION.SDK_INT >= 29) {
                        startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                    } else {
                        startForeground(NOTIF_ID, n)
                    }
                } catch (t: Throwable) {
                    EventLog.add(this, "Could not start foreground service: ${t.javaClass.simpleName}: ${t.message}")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startRecording(intent?.getIntExtra(EXTRA_SOURCE, 0) ?: 0)
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(index: Int) {
        val (name, source) = SOURCES[index.coerceIn(0, SOURCES.size - 1)]
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val f = File(recDir(this), "rec_${stamp}_$name.m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(source)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64000)
            r.setAudioSamplingRate(44100)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
        } catch (t: Throwable) {
            EventLog.add(this, "Start failed ($name): ${t.javaClass.simpleName}: ${t.message}")
            try { r.release() } catch (_: Throwable) {}
            f.delete()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        recorder = r
        file = f
        peak = 0; loud = 0; samples = 0
        startedAt = SystemClock.elapsedRealtime()
        running = true
        handler.postDelayed(sampler, 250)
        EventLog.add(this, "Recording started, source=$name")
    }

    private fun stopRecording() {
        handler.removeCallbacks(sampler)
        val r = recorder ?: run { running = false; return }
        recorder = null
        var ok = true
        try { r.stop() } catch (t: Throwable) {
            ok = false
            EventLog.add(this, "Stop error: ${t.javaClass.simpleName}: ${t.message}")
        }
        try { r.release() } catch (_: Throwable) {}
        running = false
        val f = file
        file = null
        if (f != null) {
            val secs = (SystemClock.elapsedRealtime() - startedAt) / 1000
            if (ok && f.exists() && f.length() > 0) {
                EventLog.add(this, "Saved ${f.name}: ${f.length() / 1024} KB, ${secs}s, peak level $peak of 32767, loud samples $loud of $samples")
            } else {
                f.delete()
                EventLog.add(this, "Nothing was saved")
            }
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Recording", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, RecorderService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Recording")
            .setContentText("Tap to open. Use Stop to finish.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Stop", stop).build())
            .build()
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }
}
