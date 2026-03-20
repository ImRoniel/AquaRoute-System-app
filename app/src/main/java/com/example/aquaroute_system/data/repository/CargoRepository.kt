package com.example.aquaroute_system.data.repository

import android.util.Log
import com.example.aquaroute_system.data.models.Cargo
import com.example.aquaroute_system.data.models.CargoStatus
import com.example.aquaroute_system.data.models.Result
import com.example.aquaroute_system.data.models.VoyageRecord
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

class CargoRepository(private val firestore: FirebaseFirestore) {

    companion object {
        private const val TAG = "CargoRepository"
        private const val CARGO_COLLECTION = "cargo"
        private const val CARGO_STATUS_COLLECTION = "cargo_status"
    }

    // Get user's cargo with REAL-TIME updates - FILTERED BY USER ID
    fun observeUserCargo(userId: String): Flow<Result<List<Cargo>>> = callbackFlow {
        var listener: ListenerRegistration? = null

        try {
            trySend(Result.Loading)

            listener = firestore.collection(CARGO_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error observing cargo", error)
                        trySend(Result.Error(error))
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val cargoList = snapshot.documents.mapNotNull { doc ->
                            try {
                                doc.toObject(Cargo::class.java)?.copy(id = doc.id)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing cargo: ${e.message}")
                                null
                            }
                        }
                        trySend(Result.Success(cargoList))
                        Log.d(TAG, "Found ${cargoList.size} cargo items for user $userId")
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

    // Get user's ACTIVE cargo with REAL-TIME updates - FILTERED BY USER ID
    fun observeUserActiveCargo(userId: String): Flow<Result<List<Cargo>>> = callbackFlow {
        var listener: ListenerRegistration? = null

        try {
            trySend(Result.Loading)

            listener = firestore.collection(CARGO_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereIn("status", listOf("processing", "in_transit"))
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error observing active cargo", error)
                        trySend(Result.Error(error))
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val cargoList = snapshot.documents.mapNotNull { doc ->
                            try {
                                doc.toObject(Cargo::class.java)?.copy(id = doc.id)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing active cargo: ${e.message}")
                                null
                            }
                        }
                        trySend(Result.Success(cargoList))
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

    // Get user's cargo statistics - FILTERED BY USER ID
    suspend fun getUserCargoStatistics(userId: String): Result<Map<String, Int>> {
        return try {
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
                when (status.lowercase()) {
                    "processing", "pending" -> stats["processing"] = stats["processing"]!! + 1
                    "in_transit" -> stats["in_transit"] = stats["in_transit"]!! + 1
                    "delivered" -> stats["delivered"] = stats["delivered"]!! + 1
                    "delayed" -> stats["delayed"] = stats["delayed"]!! + 1
                }
            }

            Result.Success(stats)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cargo statistics", e)
            Result.Error(e)
        }
    }

    // Observe FLEET-WIDE active cargo (no userId filter) — for dashboard Cargo Pulse
    // NOTE: Firestore may require a composite index for this query.
    //       Follow the URL in the logcat error to create it.
    fun observeFleetActiveCargo(limit: Int = 5): Flow<Result<List<Cargo>>> = callbackFlow {
        var listener: ListenerRegistration? = null

        try {
            trySend(Result.Loading)

            listener = firestore.collection(CARGO_COLLECTION)
                .whereIn("status", listOf(
                    "in_transit", "processing", "pending",
                    "In Transit", "Processing", "Pending"
                ))
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20) // QUOTA FIX: was unbounded — every write re-read all cargo docs
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error observing fleet cargo", error)
                        trySend(Result.Error(error))
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val cargoList = snapshot.documents.mapNotNull { doc ->
                            try {
                                doc.toObject(Cargo::class.java)?.copy(id = doc.id)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error parsing fleet cargo: ${e.message}")
                                null
                            }
                        }
                        trySend(Result.Success(cargoList))
                        Log.d(TAG, "Fleet cargo pulse: ${cargoList.size} active items")
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up fleet cargo listener", e)
            trySend(Result.Error(e))
        }

        awaitClose {
            listener?.remove()
        }
    }

    // Track cargo by reference number - PUBLIC ACCESS (no userId needed)
    suspend fun getCargoByReference(referenceNumber: String): Result<Cargo> {
        return try {
            Log.d(TAG, "Searching for cargo with reference: $referenceNumber")

            val snapshot = firestore.collection(CARGO_COLLECTION)
                .whereEqualTo("reference", referenceNumber)
                .limit(1)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                val document = snapshot.documents[0]
                val cargo = document.toObject(Cargo::class.java)?.copy(id = document.id)

                if (cargo != null) {
                    Log.d(TAG, "Cargo found: ${cargo.reference}")
                    Result.Success(cargo)
                } else {
                    Log.d(TAG, "Cargo found but couldn't parse")
                    Result.Error(Exception("Cargo data is corrupted"))
                }
            } else {
                Log.d(TAG, "No cargo found with reference: $referenceNumber")
                Result.Error(Exception("No cargo found with reference: $referenceNumber"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cargo by reference", e)
            Result.Error(e)
        }
    }

    // Create cargo - MUST set userId from current session
    suspend fun createCargo(cargo: Cargo, userId: String): Result<String> {
        return try {
            val cargoWithUserId = cargo.copy(userId = userId)
            val docRef = firestore.collection(CARGO_COLLECTION).document()
            val cargoWithId = cargoWithUserId.copy(id = docRef.id)
            docRef.set(cargoWithId).await()
            Result.Success(docRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating cargo", e)
            Result.Error(e)
        }
    }

    // Get cargo by ID - MUST verify ownership
    suspend fun getCargoById(cargoId: String, userId: String): Result<Cargo> {
        return try {
            val doc = firestore.collection(CARGO_COLLECTION)
                .document(cargoId)
                .get()
                .await()

            if (doc.exists()) {
                val cargo = doc.toObject(Cargo::class.java)?.copy(id = doc.id)
                if (cargo != null && cargo.userId == userId) {
                    Result.Success(cargo)
                } else {
                    Result.Error(Exception("Unauthorized access to cargo"))
                }
            } else {
                Result.Error(Exception("Cargo not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting cargo", e)
            Result.Error(e)
        }
    }

    // Update cargo status - MUST verify ownership
    suspend fun updateCargoStatus(cargoId: String, userId: String, status: CargoStatus): Result<Boolean> {
        return try {
            val cargoResult = getCargoById(cargoId, userId)
            if (cargoResult is Result.Success) {
                firestore.collection(CARGO_COLLECTION)
                    .document(cargoId)
                    .update(
                        mapOf(
                            "status" to status.status,
                            "updatedAt" to System.currentTimeMillis()
                        )
                    )
                    .await()

                firestore.collection(CARGO_STATUS_COLLECTION)
                    .add(status)
                    .await()

                Result.Success(true)
            } else {
                Result.Error(Exception("Unauthorized or cargo not found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating cargo status", e)
            Result.Error(e)
        }
    }

    // Get user's voyage history - FILTERED BY USER ID
    suspend fun getUserVoyageHistory(userId: String): Result<List<VoyageRecord>> {
        return try {
            val snapshot = firestore.collection(CARGO_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereIn("status", listOf("delivered", "delayed"))
                .orderBy("deliveryDate", Query.Direction.DESCENDING)
                .get()
                .await()

            val voyages = snapshot.documents.mapNotNull { doc ->
                try {
                    val cargo = doc.toObject(Cargo::class.java)?.copy(id = doc.id)
                    cargo?.let {
                        VoyageRecord(
                            id = it.id,
                            trackingNumber = it.getDisplayReference(),
                            origin = it.origin,
                            destination = it.destination,
                            cargoType = it.cargoType,
                            weight = "${it.weight} ${if (it.weight > 0) "kg" else "units"}",
                            status = it.status,
                            statusIcon = getStatusIcon(it.status),
                            deliveryDate = formatDate(it.deliveryDate ?: 0),
                            deliveryTime = formatTime(it.deliveryDate ?: 0),
                            isOnTime = it.isOnTime ?: true,
                            delayMinutes = it.delayMinutes ?: 0
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing voyage record: ${e.message}")
                    null
                }
            }

            Result.Success(voyages)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private fun getStatusIcon(status: String): String {
        return when (status.lowercase()) {
            "delivered" -> "✅"
            "delayed" -> "⚠️"
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

    // Test Firebase connection
    suspend fun testFirebaseConnection(): Result<String> {
        return try {
            val snapshot = firestore.collection(CARGO_COLLECTION).limit(1).get().await()
            if (!snapshot.isEmpty) {
                val doc = snapshot.documents[0]
                val data = doc.data
                val fieldNames = data?.keys?.joinToString(", ") ?: "no fields"
                Result.Success("Connected! Found document with fields: $fieldNames")
            } else {
                Result.Success("Connected but no documents found in 'cargo' collection")
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}