package com.sambhavdwivedi.callin.data.remote

import com.sambhavdwivedi.callin.data.remote.dto.ConnectionStatusResponse
import com.sambhavdwivedi.callin.data.remote.dto.ConnectionsListResponse
import com.sambhavdwivedi.callin.data.remote.dto.PendingRequestsResponse
import com.sambhavdwivedi.callin.data.remote.dto.RespondConnectionRequest
import com.sambhavdwivedi.callin.data.remote.dto.SendConnectionRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface ConnectionApi {
    @POST("api/v1/connections/request")
    suspend fun sendRequest(@Body request: SendConnectionRequest): ConnectionStatusResponse

    @GET("api/v1/connections/pending")
    suspend fun pending(): PendingRequestsResponse

    // The backend returns 204 No Content on success, so this is
    // declared as a bare Response<Unit> rather than a parsed body —
    // that avoids the JSON converter choking on an empty response.
    @POST("api/v1/connections/{id}/respond")
    suspend fun respond(
        @Path("id") requestId: String,
        @Body request: RespondConnectionRequest
    ): Response<Unit>

    @GET("api/v1/connections")
    suspend fun list(): ConnectionsListResponse
}
