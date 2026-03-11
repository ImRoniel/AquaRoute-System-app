package com.example.aquaroute_system.data.repository

import android.util.Log
import com.example.aquaroute_system.data.models.PortStatus
import com.example.aquaroute_system.data.models.Result
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class PortStatusRepository(private val firestore: FirebaseFirestore) {

    companion object {
        private const val TAG = "PortStatusRepository"
        private const val PORT_STATUS_COLLECTION = "port_status"
    }

    // Real-time listener for all port statuses
    fun observeAllPortStatuses(): Flow<Result<List<PortStatus>>> = callbackFlow {
        var listener: ListenerRegistration? = null

        try {
            trySend(Result.Loading)

            listener = firestore.collection(PORT_STATUS_COLLECTION)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error observing ports", error)
                        trySend(Result.Error(error))
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val ports = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(PortStatus::class.java)?.copy(portId = doc.id)
                        }
                        trySend(Result.Success(ports))
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

    // Get single port status with real-time updates
    fun observePortStatus(portId: String): Flow<Result<PortStatus>> = callbackFlow {
        var listener: ListenerRegistration? = null

        try {
            trySend(Result.Loading)

            listener = firestore.collection(PORT_STATUS_COLLECTION)
                .document(portId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error observing port", error)
                        trySend(Result.Error(error))
                        return@addSnapshotListener
                    }

                    if (snapshot != null && snapshot.exists()) {
                        val port = snapshot.toObject(PortStatus::class.java)?.copy(portId = snapshot.id)
                        if (port != null) {
                            trySend(Result.Success(port))
                        }
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
}