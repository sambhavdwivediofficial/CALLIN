package com.sambhavdwivedi.callin

import android.app.Application
import com.sambhavdwivedi.callin.core.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CallinApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // CRITICAL FIX: CallRepository MUST be collecting signaling
        // messages from the moment the process exists — not only
        // once some UI composable happens to run. Without this, a
        // cold process created purely to handle an FCM push or a
        // notification-button BroadcastReceiver has nobody listening
        // for the call.accept echo, webrtc offer/answer, etc., so
        // Accept/Decline from the notification silently did nothing
        // and the call never left "ringing" even though the message
        // was sent.
        container.callRepository.start()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (container.tokenStore.getAccessToken() != null &&
                container.tokenStore.getProfileCompleted()
            ) {
                container.signalingClient.start()
            }
        }
    }
}
