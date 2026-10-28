package com.memoredia.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/**
 * Logs call state changes. When "auto-record" is on it tries to start and stop recording with the call.
 * Newer Android versions often refuse to start a microphone service from the background; if that happens
 * the reason is written to the log, which is exactly what this test is meant to find out.
 */
class CallReceiver : BroadcastReceiver() {

    companion object {
        private var last = ""
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        if (state == last) return
        last = state

        val label = when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> "Call ringing"
            TelephonyManager.EXTRA_STATE_OFFHOOK -> "Call active"
            TelephonyManager.EXTRA_STATE_IDLE -> "Call ended"
            else -> state
        }
        EventLog.add(ctx, label)

        val auto = ctx.getSharedPreferences("callrec_prefs", Context.MODE_PRIVATE).getBoolean("auto", false)
        if (!auto) return

        if (state == TelephonyManager.EXTRA_STATE_OFFHOOK && !RecorderService.running) {
            val idx = ctx.getSharedPreferences("callrec_prefs", Context.MODE_PRIVATE).getInt("source", 0)
            try {
                ctx.startForegroundService(
                    Intent(ctx, RecorderService::class.java)
                        .setAction(RecorderService.ACTION_START)
                        .putExtra(RecorderService.EXTRA_SOURCE, idx)
                )
                EventLog.add(ctx, "Auto-record: start requested")
            } catch (t: Throwable) {
                EventLog.add(ctx, "Auto-record blocked: ${t.javaClass.simpleName}: ${t.message}")
            }
        } else if (state == TelephonyManager.EXTRA_STATE_IDLE && RecorderService.running) {
            try {
                ctx.startService(Intent(ctx, RecorderService::class.java).setAction(RecorderService.ACTION_STOP))
            } catch (t: Throwable) {
                EventLog.add(ctx, "Auto-stop failed: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }
}
