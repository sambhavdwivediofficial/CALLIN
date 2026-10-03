package com.sambhavdwivedi.callin.core.call

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Keeps the app's process alive — and the WebSocket/WebRTC session
 * with it — for as long as a call is ringing or active, regardless
 * of whether the screen is locked, another app is on top, or the
 * user swipes CALLIN away from Recents.
 *
 * This MUST be declared in AndroidManifest.xml with
 * android:stopWithTask="false" — without that manifest entry,
 * Android can't find the component and startForegroundService()
 * throws, which was silently killing call reliability project-wide:
 * every attempt to start this service crashed right after the
 * ringtone/notification fired, explaining the ringtone cutting out
 * and the notification buttons appearing to do nothing.
 *
 * [onTaskRemoved] is overridden with an explicit empty body as a
 * guard against ever adding a stopSelf() here by accident — with
 * stopWithTask="false" properly declared, Android already keeps this
 * running when the task is swiped from Recents; this is just
 * documentation-as-code for that intent.
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Deliberately empty — see class doc comment.
        super.onTaskRemoved(rootIntent)
    }
}
