package com.sambhavdwivedi.callin.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sambhavdwivedi.callin.CallinApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Every handler here is wrapped in try/catch. A cold process launched
 * purely to handle this FCM message is the single most fragile
 * moment in the whole app — AppContainer/CallRepository might still
 * be mid-construction, DataStore reads might not have resolved yet,
 * etc. An uncaught exception anywhere in this class previously
 * crashed the entire process, which is what silently turned
 * "notification shown" into "ringtone cuts out, buttons do nothing,
 * tapping the notification gets stuck" — the process was already
 * dead by the time any of those were attempted.
 */
class CallinFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        try {
            val container = (applicationContext as CallinApplication).container
            scope.launch {
                try {
                    if (container.tokenStore.getAccessToken() != null) {
                        container.userRepository.registerDevice(token)
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("CallinFCM", "registerDevice failed", t)
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("CallinFCM", "onNewToken failed", t)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        try {
            val data = message.data
            if (data["type"] != "incoming_call") return

            val callId = data["call_id"] ?: return
            val callerId = data["caller_id"] ?: return

            val container = (applicationContext as CallinApplication).container
            container.callRepository.onPushIncomingCall(
                callId = callId,
                callerId = callerId,
                callerUsername = data["caller_username"],
                callerDisplayName = data["caller_display_name"]?.takeIf { it.isNotBlank() },
                callerAvatarUrl = data["caller_avatar_url"]?.takeIf { it.isNotBlank() },
            )
        } catch (t: Throwable) {
            android.util.Log.e("CallinFCM", "onMessageReceived failed", t)
        }
    }
}
