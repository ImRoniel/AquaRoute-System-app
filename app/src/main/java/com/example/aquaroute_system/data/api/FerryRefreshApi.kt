package com.example.aquaroute_system.data.api

import retrofit2.Response
import retrofit2.http.POST

interface FerryRefreshApi {
    @POST("api/ferries/refresh")
    suspend fun refreshFerries(): Response<RefreshResponse>
}

data class RefreshResponse(val success: Boolean, val count: Int)