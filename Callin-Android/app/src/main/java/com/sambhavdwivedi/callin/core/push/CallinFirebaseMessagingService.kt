package com.sambhavdwivedi.callin.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sambhavdwivedi.callin.CallinApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallinFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val container = (applicationContext as CallinApplication).container
        scope.launch {
            if (container.tokenStore.getAccessToken() != null) {
                container.userRepository.registerDevice(token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
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
    }
}
