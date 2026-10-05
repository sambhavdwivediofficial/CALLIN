package com.sambhavdwivedi.callin.core.di

import android.content.Context
import com.sambhavdwivedi.callin.core.network.RetrofitProvider
import com.sambhavdwivedi.callin.core.network.SignalingClient
import com.sambhavdwivedi.callin.core.network.TokenStore
import com.sambhavdwivedi.callin.core.storage.NotificationHistoryStore
import com.sambhavdwivedi.callin.core.storage.RecentCallsStore
import com.sambhavdwivedi.callin.core.storage.RingtoneStore
import com.sambhavdwivedi.callin.data.remote.AuthApi
import com.sambhavdwivedi.callin.data.remote.ConnectionApi
import com.sambhavdwivedi.callin.data.remote.UserApi
import com.sambhavdwivedi.callin.data.repository.AuthRepository
import com.sambhavdwivedi.callin.data.repository.CallRepository
import com.sambhavdwivedi.callin.data.repository.ConnectionRepository
import com.sambhavdwivedi.callin.data.repository.UserRepository

class AppContainer(context: Context) {
    val tokenStore = TokenStore(context.applicationContext)

    private val retrofit = RetrofitProvider.create(tokenStore)
    private val authApi = retrofit.create(AuthApi::class.java)
    private val userApi = retrofit.create(UserApi::class.java)
    private val connectionApi = retrofit.create(ConnectionApi::class.java)

    private val notificationHistoryStore = NotificationHistoryStore(context.applicationContext)
    val ringtoneStore = RingtoneStore(context.applicationContext)
    val recentCallsStore = RecentCallsStore(context.applicationContext)

    val authRepository = AuthRepository(authApi, tokenStore)
    val userRepository = UserRepository(userApi)
    val connectionRepository = ConnectionRepository(connectionApi, notificationHistoryStore)

    val signalingClient = SignalingClient(context.applicationContext, tokenStore)

    val callRepository = CallRepository(
        context = context.applicationContext,
        signalingClient = signalingClient,
        ringtoneStore = ringtoneStore,
        recentCallsStore = recentCallsStore,
        myUserId = { userRepository.me.value?.id },
        lookupPeer = { peerId ->
            connectionRepository.connections.value
                ?.find { it.user_id == peerId }
                ?.let { Triple(it.username, it.display_name, it.avatar_url) }
        }
    )

    /** Full, permanent wipe on sign-out: every on-device store this
     * app writes to, not just the auth tokens — recents, notification
     * history, ringtone choice, the permission-recheck timestamp. */
    suspend fun fullLogout() {
        signalingClient.stop()
        tokenStore.clear()
        recentCallsStore.clearAll()
        connectionRepository.clearHistory()
        // In-memory caches reset too, so the UI reflects "logged out"
        // instantly without needing a process restart.
    }
}
