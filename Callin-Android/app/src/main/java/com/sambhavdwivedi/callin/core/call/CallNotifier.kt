package com.sambhavdwivedi.callin.core.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.sambhavdwivedi.callin.MainActivity
import com.sambhavdwivedi.callin.data.repository.CallPeerInfo
import com.sambhavdwivedi.callin.data.repository.CallUiState

/**
 * Owns the one system notification CALLIN ever shows for a call.
 *
 * Incoming gets a full-screen intent (wakes the screen and shows
 * over the lock screen, just like a real incoming call) with
 * Accept/Decline actions. Outgoing/Active gets an ongoing
 * notification with a live chronometer once the call is Active, and
 * a Hang up action. Idle/Ended cancels it.
 *
 * Tapping the notification body opens MainActivity — CallinNavHost
 * routes to the Call screen reactively based on CallRepository's
 * state, and MainActivity skips its splash animation whenever a call
 * is already in progress (see AppRoot), so this always lands
 * directly on the Call screen, never the normal launch flow.
 */
class CallNotifier(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "callin_calls"
        const val NOTIFICATION_ID = 4201
        const val ACTION_ACCEPT = "com.sambhavdwivedi.callin.action.ACCEPT_CALL"
        const val ACTION_REJECT = "com.sambhavdwivedi.callin.action.REJECT_CALL"
        const val ACTION_HANGUP = "com.sambhavdwivedi.callin.action.HANGUP_CALL"
        const val EXTRA_OPEN_CALL = "com.sambhavdwivedi.callin.extra.OPEN_CALL"
    }

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    init {
        val channel = NotificationChannel(CHANNEL_ID, "Calls", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Incoming and ongoing CALLIN calls"
            setSound(null, null) // CallRepository plays the ringtone itself, looped and controllable
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun update(state: CallUiState) {
        when (state) {
            is CallUiState.Incoming -> showIncoming(state.info)
            is CallUiState.Outgoing -> showOngoing(
                state.info,
                statusText = if (state.ringing) "Ringing..." else "Connecting...",
                startedAtMillis = null
            )
            is CallUiState.Active -> showOngoing(
                state.info,
                statusText = "Ongoing call",
                startedAtMillis = state.startedAtMillis
            )
            else -> notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    private fun contentPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_CALL, true)
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(context, CallActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(context, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun peerName(info: CallPeerInfo) = info.peerDisplayName ?: info.peerUsername

    private fun showIncoming(info: CallPeerInfo) {
        val fullScreenIntent = contentPendingIntent()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Incoming call")
            .setContentText(peerName(info))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(fullScreenIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, "Decline", actionPendingIntent(ACTION_REJECT))
            .addAction(0, "Accept", actionPendingIntent(ACTION_ACCEPT))
            .build()
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /** [startedAtMillis] non-null (call is Active) switches the
     * notification to a live running chronometer — the exact
     * elapsed-time display asked for — instead of a static status
     * line. Outgoing (ringing/connecting) has no timer yet. */
    private fun showOngoing(info: CallPeerInfo, statusText: String, startedAtMillis: Long?) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentTitle(peerName(info))
            .setContentText(statusText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(contentPendingIntent())
            .setOngoing(true)
            .addAction(0, "Hang up", actionPendingIntent(ACTION_HANGUP))

        if (startedAtMillis != null) {
            builder.setUsesChronometer(true).setWhen(startedAtMillis)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }
}
