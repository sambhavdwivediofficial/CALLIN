package com.sambhavdwivedi.callin.core.di

import android.content.Context
import com.sambhavdwivedi.callin.core.network.RetrofitProvider
import com.sambhavdwivedi.callin.core.network.SignalingClient
import com.sambhavdwivedi.callin.core.network.TokenStore
import com.sambhavdwivedi.callin.core.storage.NotificationHistoryStore
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

    val authRepository = AuthRepository(authApi, tokenStore)
    val userRepository = UserRepository(userApi)
    val connectionRepository = ConnectionRepository(connectionApi, notificationHistoryStore)

    val signalingClient = SignalingClient(tokenStore)

    val callRepository = CallRepository(
        context = context.applicationContext,
        signalingClient = signalingClient,
        myUserId = { userRepository.me.value?.id },
        lookupPeer = { peerId ->
            connectionRepository.connections.value
                ?.find { it.user_id == peerId }
                ?.let { Triple(it.username, it.display_name, it.avatar_url) }
        }
    )
}
