package com.sambhavdwivedi.callin.core.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sambhavdwivedi.callin.CallinApplication
import com.sambhavdwivedi.callin.MainActivity

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = (context.applicationContext as CallinApplication).container.callRepository
        when (intent.action) {
            CallNotifier.ACTION_ACCEPT -> {
                repo.acceptCall()
                val launch = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(launch)
            }
            CallNotifier.ACTION_REJECT -> repo.rejectCall()
            CallNotifier.ACTION_HANGUP -> repo.endCall()
        }
    }
}
