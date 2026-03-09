package com.example.aquaroute_system.data.repository

import android.util.Log
import com.example.aquaroute_system.data.models.PortStatus
import com.example.aquaroute_system.data.models.Result
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

class PortStatusRepository(private val firestore: FirebaseFirestore) {

    companion object {
        private const val TAG = "PortStatusRepository"
        private const val PORT_STATUS_COLLECTION = "port_status"
    }

    fun getAllPortStatuses(): Flow<Result<List<PortStatus>>> = flow {
        try {
            emit(Result.Loading)

            val snapshot = firestore.collection(PORT_STATUS_COLLECTION)
                .get()
                .await()

            val ports = snapshot.documents.mapNotNull { doc ->
                doc.toObject(PortStatus::class.java)?.copy(portId = doc.id)
            }

            emit(Result.Success(ports))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting port statuses", e)
            emit(Result.Error(e))
        }
    }

    fun getPortStatus(portId: String): Flow<Result<PortStatus>> = flow {
        try {
            val doc = firestore.collection(PORT_STATUS_COLLECTION)
                .document(portId)
                .get()
                .await()

            val port = doc.toObject(PortStatus::class.java)?.copy(portId = doc.id)
                ?: throw Exception("Port not found")

            emit(Result.Success(port))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting port status", e)
            emit(Result.Error(e))
        }
    }

    // For real-time updates
    fun observePortStatus(portId: String): Flow<Result<PortStatus>> = flow {
        val listener = firestore.collection(PORT_STATUS_COLLECTION)
            .document(portId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Handle error
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val port = snapshot.toObject(PortStatus::class.java)?.copy(portId = snapshot.id)
                    port?.let {
                        // Emit on the flow
                    }
                }
            }

        // Keep flow alive
        try {
            emit(Result.Loading)
        } finally {
            listener.remove()
        }
    }
}