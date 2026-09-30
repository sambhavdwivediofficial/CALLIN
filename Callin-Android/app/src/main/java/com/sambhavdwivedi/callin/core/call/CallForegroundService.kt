package com.sambhavdwivedi.callin.core.call

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Keeps the app's process alive — and the WebSocket/WebRTC session
 * with it — for as long as a call is ringing or active, regardless
 * of whether the screen is locked or the app is in the background.
 * This is what makes a locked-screen or backgrounded incoming/
 * ongoing call keep ringing, keep receiving audio, and keep the
 * notification visible exactly like a real phone call — a normal
 * background process has no such guarantee and can be paused or
 * killed by the OS at any moment.
 *
 * CallRepository starts this the instant a call state becomes
 * non-Idle and stops it the instant it returns to Idle. The actual
 * notification content (name, Accept/Decline, Hang up) is owned by
 * CallNotifier, which posts to the same notification ID — so this
 * placeholder is only ever visible for a few milliseconds before
 * CallNotifier's real content replaces it.
 */
class CallForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CallNotifier.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentTitle("CALLIN")
            .setContentText("Call in progress")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
        startForeground(CallNotifier.NOTIFICATION_ID, notification)
        return START_STICKY
    }
}
