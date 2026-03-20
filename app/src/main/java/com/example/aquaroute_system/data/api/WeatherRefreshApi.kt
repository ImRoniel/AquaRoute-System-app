package com.example.aquaroute_system.data.api

import retrofit2.http.Body
import retrofit2.http.POST

data class RefreshRequest(val portIds: List<String>)
data class RefreshByCoordsRequest(
    val locationId: String,
    val lat: Double,
    val lon: Double,
    val name: String
)

interface WeatherRefreshApi {
    @POST("weather/refresh")
    suspend fun refreshWeather(@Body request: RefreshRequest): retrofit2.Response<Unit>

    @POST("weather/refresh-by-coords")
    suspend fun refreshWeatherByCoords(@Body request: RefreshByCoordsRequest): retrofit2.Response<Unit>
}