package com.sambhavdwivedi.callin.core.di

import android.content.Context
import com.sambhavdwivedi.callin.core.network.RetrofitProvider
import com.sambhavdwivedi.callin.core.network.TokenStore
import com.sambhavdwivedi.callin.data.remote.AuthApi
import com.sambhavdwivedi.callin.data.remote.UserApi
import com.sambhavdwivedi.callin.data.repository.AuthRepository
import com.sambhavdwivedi.callin.data.repository.UserRepository

/**
 * A small hand-rolled dependency container. CALLIN is not big enough
 * yet to need Hilt/Dagger — this keeps things simple and explicit.
 */
class AppContainer(context: Context) {
    val tokenStore = TokenStore(context.applicationContext)

    private val retrofit = RetrofitProvider.create(tokenStore)
    private val authApi = retrofit.create(AuthApi::class.java)
    private val userApi = retrofit.create(UserApi::class.java)

    val authRepository = AuthRepository(authApi, tokenStore)
    val userRepository = UserRepository(userApi)
}
