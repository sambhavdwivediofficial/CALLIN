package com.sambhavdwivedi.callin.core.network

import com.sambhavdwivedi.callin.data.remote.AuthApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitProvider {

    // coerceInputValues: when the backend sends `null` for a field
    // that has a default (e.g. an empty list), fall back to that
    // default instead of crashing. Go's json.Marshal emits `null`
    // for a nil slice, not `[]` — this is what makes
    // ConnectionsListResponse/PendingRequestsResponse safe to parse
    // even when the list is empty.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun create(tokenStore: TokenStore): Retrofit {
        lateinit var retrofit: Retrofit
        val lazyAuthApi = lazy { retrofit.create(AuthApi::class.java) }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore, lazyAuthApi))
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit
    }
}
