package com.sambhavdwivedi.callin.core.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sambhavdwivedi.callin.CallinApplication

/**
 * Handles the Accept/Decline/Hang up buttons on the call
 * notification. Everything runs in-process, so it reaches straight
 * into the app's single CallRepository instance — no IPC needed.
 *
 * Wrapped in goAsync() + try/catch: a BroadcastReceiver has a very
 * short execution budget before the OS treats it as an ANR, and if
 * CallinApplication/AppContainer hadn't finished constructing yet (a
 * genuinely cold process launched purely to handle this broadcast),
 * any uncaught exception here would crash silently with zero
 * feedback — which is exactly what made the notification buttons
 * feel like they "did nothing".
 *
 * Accept deliberately does NOT launch MainActivity — the call
 * connects and keeps running through the foreground service +
 * notification (now a live timer + Hang up), on the earpiece, like
 * answering a real phone call without unlocking into an app. The
 * Call screen only opens if the user separately taps the
 * notification body.
 */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        try {
            val repo = (context.applicationContext as CallinApplication).container.callRepository
            when (intent.action) {
                CallNotifier.ACTION_ACCEPT -> repo.acceptCall()
                CallNotifier.ACTION_REJECT -> repo.rejectCall()
                CallNotifier.ACTION_HANGUP -> repo.endCall()
            }
        } catch (t: Throwable) {
            android.util.Log.e("CallActionReceiver", "failed to handle ${intent.action}", t)
        } finally {
            pendingResult.finish()
        }
    }
}
