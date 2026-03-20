package com.example.aquaroute_system.data.repository

import android.util.Log
import com.example.aquaroute_system.data.api.RefreshByCoordsRequest
import com.example.aquaroute_system.data.api.RefreshRequest
import com.example.aquaroute_system.data.api.WeatherRefreshApi
import com.example.aquaroute_system.data.models.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class WeatherRefreshRepository(private val baseUrl: String) {

    private val api: WeatherRefreshApi by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherRefreshApi::class.java)
    }

    suspend fun requestRefresh(portIds: List<String>): Result<Unit> {
        Log.d("WeatherRefreshRepo", "Sending request for ports: $portIds")
        return try {
            withContext(Dispatchers.IO) {
                val response = api.refreshWeather(RefreshRequest(portIds))
                Log.d("WeatherRefreshRepo", "Response code: ${response.code()}")
                if (response.isSuccessful) Result.Success(Unit)
                else Result.Error(Exception("Refresh failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("WeatherRefreshRepo", "Exception: ${e.message}", e)
            Result.Error(e)
        }
    }

    /** Refresh weather for any entity (including ferries) using raw coordinates. */
    suspend fun requestRefreshByCoords(
        locationId: String,
        lat: Double,
        lon: Double,
        name: String
    ): Result<Unit> {
        Log.d("WeatherRefreshRepo", "Sending coords refresh for $locationId ($lat,$lon)")
        return try {
            withContext(Dispatchers.IO) {
                val response = api.refreshWeatherByCoords(
                    RefreshByCoordsRequest(locationId, lat, lon, name)
                )
                Log.d("WeatherRefreshRepo", "Coords refresh response: ${response.code()}")
                if (response.isSuccessful) Result.Success(Unit)
                else Result.Error(Exception("Coords refresh failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e("WeatherRefreshRepo", "Coords refresh exception: ${e.message}", e)
            Result.Error(e)
        }
    }
}