package com.example.aquaroute_system.data.repository

import android.util.Log
import com.example.aquaroute_system.data.models.DashboardStats
import com.example.aquaroute_system.data.models.Result
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class DashboardRepository(private val firestore: FirebaseFirestore) {

    companion object {
        private const val TAG = "DashboardRepository"
        private const val STATS_COLLECTION = "dashboard_stats"
    }

    // Real-time listener for dashboard stats
    fun observeUserStats(userId: String): Flow<Result<DashboardStats>> = callbackFlow {
        var listener: ListenerRegistration? = null

        try {
            // Try to send loading state
            trySend(Result.Loading)

            listener = firestore.collection(STATS_COLLECTION)
                .document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error observing stats", error)
                        trySend(Result.Error(error))
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val stats = snapshot.toObject(DashboardStats::class.java)
                        if (stats != null) {
                            trySend(Result.Success(stats))
                        } else {
                            // Create default stats if not exists
                            val defaultStats = DashboardStats(userId = userId)
                            trySend(Result.Success(defaultStats))
                        }
                    } else {
                        // Document doesn't exist, create default
                        val defaultStats = DashboardStats(userId = userId)
                        trySend(Result.Success(defaultStats))
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

    // Single fetch of stats
    suspend fun getUserStats(userId: String): Result<DashboardStats> {
        return try {
            val snapshot = firestore.collection(STATS_COLLECTION)
                .document(userId)
                .get()
                .await()

            if (snapshot.exists()) {
                val stats = snapshot.toObject(DashboardStats::class.java)
                Result.Success(stats ?: DashboardStats(userId = userId))
            } else {
                Result.Success(DashboardStats(userId = userId))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching stats", e)
            Result.Error(e)
        }
    }
}