package com.example.aquaroute_system.data.api

import retrofit2.http.Body
import retrofit2.http.POST

data class RefreshRequest(val portIds: List<String>)

interface WeatherRefreshApi {
    @POST("weather/refresh")
    suspend fun refreshWeather(@Body request: RefreshRequest): retrofit2.Response<Unit>
}