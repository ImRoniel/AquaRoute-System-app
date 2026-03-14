package com.example.aquaroute_system.data.repository

import com.example.aquaroute_system.data.api.FerryRefreshApi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.example.aquaroute_system.data.models.Result
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class FerryRefreshRepository(private val baseUrl: String) {
    private val api: FerryRefreshApi by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(100, TimeUnit.SECONDS)
            .readTimeout(100, TimeUnit.SECONDS)
            .writeTimeout(100, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FerryRefreshApi::class.java)
    }

    suspend fun refresh(): Result<Int> {
        return try {
            val response = api.refreshFerries()
            if (response.isSuccessful) {
                Result.Success(response.body()?.count ?: 0)
            } else {
                Result.Error(Exception("Refresh failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}