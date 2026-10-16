package com.sambhavdwivedi.callin.core.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sambhavdwivedi.callin.CallinApplication

/**
 * Handles the Accept/Decline/Hang up buttons on the call
 * notification. goAsync() + try/catch is what stops a cold-process
 * edge case here from silently doing nothing and leaving the
 * notification looking "stuck".
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
