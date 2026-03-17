package com.example.aquaroute_system.data.repository

import android.util.Log
import com.example.aquaroute_system.data.models.Result
import com.example.aquaroute_system.data.models.WeatherCondition
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

import com.google.firebase.firestore.Query

class WeatherRepository(private val firestore: FirebaseFirestore) {

    companion object {
        private const val TAG = "WeatherRepository"
        private const val WEATHER_COLLECTION = "weather"
    }

    // Single fetch (non-real-time)
    fun getAllWeatherConditions(): Flow<Result<List<WeatherCondition>>> = flow {
        try {
            emit(Result.Loading)

            val snapshot = firestore.collection(WEATHER_COLLECTION)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(50)
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

    // REAL-TIME observer for weather conditions
    fun observeAllWeatherConditions(): Flow<Result<List<WeatherCondition>>> = callbackFlow {
        var listener: ListenerRegistration? = null

        try {
            trySend(Result.Loading)

            listener = firestore.collection(WEATHER_COLLECTION)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error observing weather", error)
                        trySend(Result.Error(error))
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val weatherList = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(WeatherCondition::class.java)?.copy(locationId = doc.id)
                        }
                        trySend(Result.Success(weatherList))
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up listener", e)
            trySend(Result.Error(e))
        }

        awaitClose {
            listener?.remove()
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
    suspend fun getWeatherLastUpdated(portId: String): Long? {
        return try {
            val doc = firestore.collection(WEATHER_COLLECTION).document(portId).get().await()
            doc.getLong("updatedAt")
        } catch (e: Exception) {
            null
        }
    }
}