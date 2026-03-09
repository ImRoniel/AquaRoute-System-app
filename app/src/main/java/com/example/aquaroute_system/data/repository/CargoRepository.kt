package com.example.aquaroute_system.data.repository

import android.util.Log
import com.example.aquaroute_system.data.models.Cargo
import com.example.aquaroute_system.data.models.CargoStatus
import com.example.aquaroute_system.data.models.Result
import com.example.aquaroute_system.data.models.VoyageRecord
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

class CargoRepository(private val firestore: FirebaseFirestore) {

    companion object {
        private const val TAG = "CargoRepository"
        private const val CARGO_COLLECTION = "cargo"
        private const val CARGO_STATUS_COLLECTION = "cargo_status"
    }

    fun getUserCargo(userId: String): Flow<Result<List<Cargo>>> = flow {
        try {
            emit(Result.Loading)

            val snapshot = firestore.collection(CARGO_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val cargoList = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Cargo::class.java)?.copy(id = doc.id)
            }

            emit(Result.Success(cargoList))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user cargo", e)
            emit(Result.Error(e))
        }
    }

    fun getUserActiveCargo(userId: String): Flow<Result<List<Cargo>>> = flow {
        try {
            val snapshot = firestore.collection(CARGO_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereIn("status", listOf("processing", "in_transit"))
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val cargoList = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Cargo::class.java)?.copy(id = doc.id)
            }

            emit(Result.Success(cargoList))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active cargo", e)
            emit(Result.Error(e))
        }
    }

    fun getCargoStatistics(userId: String): Flow<Result<Map<String, Int>>> = flow {
        try {
            val snapshot = firestore.collection(CARGO_COLLECTION)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            val stats = mutableMapOf(
                "total" to snapshot.size(),
                "processing" to 0,
                "in_transit" to 0,
                "delivered" to 0,
                "delayed" to 0
            )

            snapshot.documents.forEach { doc ->
                val status = doc.getString("status") ?: ""
                when (status) {
                    "processing" -> stats["processing"] = stats["processing"]!! + 1
                    "in_transit" -> stats["in_transit"] = stats["in_transit"]!! + 1
                    "delivered" -> stats["delivered"] = stats["delivered"]!! + 1
                    "delayed" -> stats["delayed"] = stats["delayed"]!! + 1
                }
            }

            emit(Result.Success(stats))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cargo statistics", e)
            emit(Result.Error(e))
        }
    }

    suspend fun createCargo(cargo: Cargo): Result<String> {
        return try {
            val docRef = firestore.collection(CARGO_COLLECTION).document()
            val cargoWithId = cargo.copy(id = docRef.id)
            docRef.set(cargoWithId).await()
            Result.Success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating cargo", e)
            Result.Error(e)
        }
    }

    suspend fun updateCargoStatus(cargoId: String, status: CargoStatus): Result<Boolean> {
        return try {
            firestore.collection(CARGO_COLLECTION)
                .document(cargoId)
                .update(
                    mapOf(
                        "status" to status.status,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()

            // Add to status history
            firestore.collection(CARGO_STATUS_COLLECTION)
                .add(status)
                .await()

            Result.Success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating cargo status", e)
            Result.Error(e)
        }
    }
    suspend fun getUserVoyageHistory(userId: String): Result<List<VoyageRecord>> {
        return try {
            val snapshot = firestore.collection("cargo")
                .whereEqualTo("userId", userId)
                .orderBy("deliveryDate", Query.Direction.DESCENDING)
                .get()
                .await()

            val voyages = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Cargo::class.java)?.let { cargo ->
                    VoyageRecord(
                        id = cargo.id,
                        trackingNumber = cargo.trackingNumber,
                        origin = cargo.origin,
                        destination = cargo.destination,
                        cargoType = cargo.cargoType,
                        weight = "${cargo.weight} ${if (cargo.weight > 0) "kg" else "units"}",
                        status = cargo.status,
                        statusIcon = getStatusIcon(cargo.status),
                        deliveryDate = formatDate(cargo.deliveryDate ?: 0),
                        deliveryTime = formatTime(cargo.deliveryDate ?: 0),
                        isOnTime = cargo.isOnTime ?: true,
                        delayMinutes = cargo.delayMinutes ?: 0
                    )
                }
            }

            Result.Success(voyages)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private fun getStatusIcon(status: String): String {
        return when (status) {
            "completed" -> "🟢"
            "active", "in_transit" -> "🚢"
            "delayed" -> "🟡"
            else -> "📦"
        }
    }

    private fun formatDate(timestamp: Long): String {
        if (timestamp == 0L) return "TBD"
        val date = Date(timestamp)
        val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return format.format(date)
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp == 0L) return "TBD"
        val date = Date(timestamp)
        val format = SimpleDateFormat("h:mm a", Locale.getDefault())
        return format.format(date)
    }

    fun getCargoStatusHistory(cargoId: String): Flow<Result<List<CargoStatus>>> = flow {
        try {
            val snapshot = firestore.collection(CARGO_STATUS_COLLECTION)
                .whereEqualTo("cargoId", cargoId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()

            val history = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CargoStatus::class.java)
            }

            emit(Result.Success(history))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting status history", e)
            emit(Result.Error(e))
        }
    }
}