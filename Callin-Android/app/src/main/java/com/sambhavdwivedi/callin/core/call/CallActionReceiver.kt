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
 * Accept deliberately does NOT launch MainActivity. Accepting from
 * the notification is the "answer without opening the app" path —
 * the call connects and keeps running through the foreground
 * service + this same notification (now a live timer + Hang up),
 * on the earpiece, exactly like answering a real phone call from
 * the lock screen without unlocking into an app. Opening the actual
 * Call screen only happens if the user separately taps the
 * notification body itself.
 */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = (context.applicationContext as CallinApplication).container.callRepository
        when (intent.action) {
            CallNotifier.ACTION_ACCEPT -> repo.acceptCall()
            CallNotifier.ACTION_REJECT -> repo.rejectCall()
            CallNotifier.ACTION_HANGUP -> repo.endCall()
        }
    }
}
