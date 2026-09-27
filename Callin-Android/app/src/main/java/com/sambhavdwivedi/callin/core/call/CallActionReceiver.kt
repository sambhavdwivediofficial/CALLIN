package com.sambhavdwivedi.callin.core.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sambhavdwivedi.callin.CallinApplication

/** Handles the Accept/Decline/Hang up buttons on the call
 * notification. Everything runs in-process, so it reaches straight
 * into the app's single CallRepository instance — no IPC needed. */
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
