package com.sambhavdwivedi.callin.core.call

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Keeps the app's process alive — and the WebSocket/WebRTC session
 * with it — for as long as a call is ringing or active, regardless
 * of whether the screen is locked, another app is on top, or the
 * task is swiped from Recents (stopWithTask="false" in the manifest).
 *
 * foregroundServiceType is "microphone", NOT "phoneCall" — the
 * latter requires Telecom integration (default dialer / self-managed
 * ConnectionService) that this app doesn't have, and attempting it
 * threw immediately on start, crashing the entire process right
 * after the incoming-call notification/ringtone fired. "microphone"
 * has no such requirement, matching what this service actually does
 * (keep an audio call alive).
 *
 * onStartCommand itself is wrapped defensively: even a successful
 * manifest declaration can still fail to start on some OEM skins
 * under aggressive battery restrictions — in that case we log rather
 * than let the exception propagate and kill the process, since a
 * degraded call (foreground service failed, but WebRTC/signaling
 * still running) is strictly better than no call at all.
 */
class CallForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val notification = NotificationCompat.Builder(this, CallNotifier.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.sym_call_outgoing)
                .setContentTitle("CALLIN")
                .setContentText("Call in progress")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .build()
            startForeground(CallNotifier.NOTIFICATION_ID, notification)
        } catch (t: Throwable) {
            android.util.Log.e("CallForegroundService", "startForeground failed", t)
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }
}
