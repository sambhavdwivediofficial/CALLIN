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
        container.callRepository.start()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (container.tokenStore.getAccessToken() != null &&
                container.tokenStore.getProfileCompleted()
            ) {
                container.signalingClient.start()
            }
        }

        // Warms up WebRTC's native library load on a background
        // thread the instant the process starts, instead of the
        // first time it's actually needed (inside startCall/
        // acceptCall). This native load is the single biggest
        // contributor to the 3-5s delay on a cold-started call —
        // doing it here means it's almost always already finished by
        // the time a real call happens.
        CoroutineScope(Dispatchers.Default).launch {
            runCatching {
                org.webrtc.PeerConnectionFactory.initialize(
                    org.webrtc.PeerConnectionFactory.InitializationOptions
                        .builder(this@CallinApplication)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                )
            }
        }
    }
}
