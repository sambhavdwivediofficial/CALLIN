package com.sambhavdwivedi.callin.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sambhavdwivedi.callin.CallinApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives Firebase Cloud Messaging data messages. This is the ONLY
 * way an incoming call can reach the user when the app process is
 * fully killed or has no live WebSocket: Android starts this service
 * on its own the instant a high-priority FCM message arrives — see
 * push.FCMClient.NotifyIncomingCall on the backend, which fires
 * exactly when the signaling hub finds the callee offline. The
 * original call.invite that went out over the (now-dead) socket is
 * unrecoverable, so this push's data payload (call_id, caller_id) is
 * the sole source of truth for that call until the user accepts, at
 * which point a freshly (re)started signaling connection carries the
 * accept/offer/answer/ICE exchange completely normally.
 */
class CallinFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val container = (applicationContext as CallinApplication).container
        scope.launch {
            // If the user isn't logged in yet, there's nothing to
            // register against — the login path registers the
            // current token itself once they sign in.
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
        container.callRepository.onPushIncomingCall(callId, callerId)
    }
}
