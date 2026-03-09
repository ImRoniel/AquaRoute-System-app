package com.example.aquaroute_system.data.repository

import android.util.Log
import com.example.aquaroute_system.data.models.Result
import com.example.aquaroute_system.data.models.WeatherCondition
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

class WeatherRepository(private val firestore: FirebaseFirestore) {

    companion object {
        private const val TAG = "WeatherRepository"
        private const val WEATHER_COLLECTION = "weather"
    }

    fun getAllWeatherConditions(): Flow<Result<List<WeatherCondition>>> = flow {
        try {
            emit(Result.Loading)

            val snapshot = firestore.collection(WEATHER_COLLECTION)
                .get()
                .await()

            val weatherList = snapshot.documents.mapNotNull { doc ->
                doc.toObject(WeatherCondition::class.java)?.copy(locationId = doc.id)
            }

            emit(Result.Success(weatherList))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting weather conditions", e)
            emit(Result.Error(e))
        }
    }

    fun getWeatherForLocation(locationId: String): Flow<Result<WeatherCondition>> = flow {
        try {
            val doc = firestore.collection(WEATHER_COLLECTION)
                .document(locationId)
                .get()
                .await()

            val weather = doc.toObject(WeatherCondition::class.java)?.copy(locationId = doc.id)
                ?: throw Exception("Weather data not found")

            emit(Result.Success(weather))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting weather", e)
            emit(Result.Error(e))
        }
    }
}