package com.example.aquaroute_system.data.repository

import android.util.Log
import com.example.aquaroute_system.data.models.Ferry
import com.example.aquaroute_system.data.models.Result
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing ferry data from Firebase
 */
class FerryRepository(private val firestore: FirebaseFirestore) {

    companion object {
        private const val TAG = "FerryRepository"
        private const val FERRIES_COLLECTION = "ferries"
    }

    /**
     * Get all ferries from Firebase
     */
    suspend fun getAllFerries(): List<Ferry> {
        return try {
            val snapshot = firestore.collection(FERRIES_COLLECTION)
                .get()
                .await()

            val ferries = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null

                    Ferry(
                        id = doc.id,
                        name = data["name"] as? String ?: "",
                        lat = (data["current_lat"] as? Number)?.toDouble() ?: 0.0,
                        lon = (data["current_lng"] as? Number)?.toDouble() ?: 0.0,
                        status = data["status"] as? String ?: "unknown",
                        eta = (data["eta"] as? Number)?.toInt() ?: 0,
                        route = data["route"] as? String ?: "",
                        speed_knots = (data["speed_knots"] as? Number)?.toInt() ?: 0,
                        pointA = (data["pointA"] as? List<*>)?.mapNotNull {
                            (it as? Number)?.toDouble()
                        },
                        pointB = (data["pointB"] as? List<*>)?.mapNotNull {
                            (it as? Number)?.toDouble()
                        },
                        source = data["source"] as? String ?: "",
                        created_at = data["created_at"] as? String,
                        last_updated = data["last_updated"] as? String
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing ferry document ${doc.id}", e)
                    null
                }
            }

            Log.d(TAG, "Loaded ${ferries.size} ferries from Firebase")
            ferries
        } catch (e: Exception) {
            Log.e(TAG, "Error loading ferries", e)
            emptyList()
        }
    }

    /**
     * Get ferries as Flow for real-time updates
     */
    fun observeAllFerries(): Flow<Result<List<Ferry>>> = flow {
        try {
            emit(Result.Loading)

            val snapshot = firestore.collection(FERRIES_COLLECTION)
                .get()
                .await()

            val ferries = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Ferry::class.java)?.copy(id = doc.id)
            }

            emit(Result.Success(ferries))
        } catch (e: Exception) {
            Log.e(TAG, "Error observing ferries", e)
            emit(Result.Error(e))
        }
    }

    /**
     * Get ferry by name
     */
    suspend fun getFerryByName(name: String): Ferry? {
        return getAllFerries().find { it.name == name }
    }

    /**
     * Get ferries near a location (within radius km)
     */
    suspend fun getFerriesNearLocation(lat: Double, lon: Double, radiusKm: Double = 100.0): List<Ferry> {
        val allFerries = getAllFerries()

        // Simple bounding box filter (for demo)
        // In production, use geoqueries
        return allFerries.filter { ferry ->
            val distance = calculateDistance(lat, lon, ferry.lat, ferry.lon)
            distance <= radiusKm
        }
    }

    /**
     * Calculate distance between two coordinates (km)
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371 // Earth's radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}