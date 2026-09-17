package com.sambhavdwivedi.callin.data.repository

import com.sambhavdwivedi.callin.data.remote.UserApi
import com.sambhavdwivedi.callin.data.remote.dto.CompleteProfileRequest
import com.sambhavdwivedi.callin.data.remote.dto.MeDto

class UserRepository(private val api: UserApi) {
    suspend fun getMe(): Result<MeDto> = runCatching { api.me() }

    suspend fun completeProfile(username: String, firstName: String, lastName: String): Result<MeDto> =
        runCatching { api.completeProfile(CompleteProfileRequest(username, firstName, lastName)) }
}
