package com.example.aquaroute_system.data.repository

import android.util.Log
import com.example.aquaroute_system.data.models.FirestorePort
import com.example.aquaroute_system.data.models.Result
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
//import kotlin.math.abs

/**
 * Repository for handling Firestore port data operations
 */
class PortRepository {

    companion object {
        private const val TAG = "PortRepository"
        private const val PORTS_COLLECTION = "ports"
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val portsCollection = firestore.collection(PORTS_COLLECTION)

    /**
     * Load all ports from Firestore
     */
    suspend fun loadPorts(): Result<List<FirestorePort>> {
        return try {
            val querySnapshot = portsCollection.get().await()

            if (querySnapshot.isEmpty) {
                Log.d(TAG, "No ports found in Firestore")
                Result.Success(emptyList())
            } else {
                val ports = mutableListOf<FirestorePort>()
                for (document in querySnapshot.documents) {
                    val port = parseFirestorePort(document)
                    if (port != null) {
                        ports.add(port)
                    }
                }

                Log.d(TAG, "Successfully loaded ${ports.size} ports from Firestore")
                Result.Success(ports)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading ports from Firestore", e)
            Result.Error(e)
        }
    }

    /**
     * Parse a Firestore document snapshot into a FirestorePort object
     */
    private fun parseFirestorePort(document: DocumentSnapshot): FirestorePort? {
        return try {
            val data = document.data ?: run {
                Log.w(TAG, "Document ${document.id} has no data")
                return null
            }

            val name = data["name"] as? String ?: run {
                Log.w(TAG, "Document ${document.id} missing 'name' field")
                return null
            }

            val lat = parseDouble(data["lat"], document.id, "lat") ?: return null
            val lng = parseDouble(data["lng"], document.id, "lng") ?: return null

            // Validate coordinates
            if (lat < -90 || lat > 90) {
                Log.w(TAG, "Document ${document.id} has invalid latitude: $lat")
                return null
            }
            if (lng < -180 || lng > 180) {
                Log.w(TAG, "Document ${document.id} has invalid longitude: $lng")
                return null
            }

            val type = (data["type"] as? String)?.trim()?.lowercase() ?: "unknown"
            val status = (data["status"] as? String)?.trim() ?: "Unknown"
            val source = (data["source"] as? String)?.trim() ?: "Unknown"
            val createdAt = data["createdAt"] as? Timestamp ?: Timestamp.now()

            // Generate realistic operating hours based on port name/type
            val (openHour, closeHour) = generateOperatingHours(name, type)

            FirestorePort(
                id = document.id,
                name = name,
                lat = lat,
                lng = lng,
                type = type,
                status = status,
                source = source,
                createdAt = createdAt,
                openHour = openHour,
                closeHour = closeHour
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing document ${document.id}: ${e.message}")
            null
        }
    }

    // Generate fake but realistic operating hours
    private fun generateOperatingHours(name: String, type: String): Pair<Int?, Int?> {
        return when {
            // Ferry terminals: 5 AM - 8 PM
            type.contains("Ferry", ignoreCase = true) -> {
                5 to 20
            }
            // Ports with "Banton" in name: 6 AM - 7 PM (matches your image)
            name.contains("Banton", ignoreCase = true) -> {
                6 to 19
            }
            // Generate based on name hash for consistency
            else -> {
                val hash = abs(name.hashCode())
                val open = 5 + (hash % 4)  // 5-8 AM
                val close = 17 + (hash % 5)  // 5-10 PM
                open to close
            }
        }
    }

    private fun abs(int: Int): Int = if (int < 0) -int else int
    /**
     * Helper function to safely parse numeric values from Firestore
     */
    private fun parseDouble(value: Any?, documentId: String, fieldName: String): Double? {
        return when (value) {
            is Double -> value
            is Float -> value.toDouble()
            is Number -> value.toDouble()
            is String -> try {
                value.toDouble()
            } catch (e: NumberFormatException) {
                Log.w(TAG, "Document $documentId has invalid '$fieldName' format: $value")
                null
            }
            else -> {
                Log.w(TAG, "Document $documentId missing or invalid '$fieldName' field: $value")
                null
            }
        }
    }
}
