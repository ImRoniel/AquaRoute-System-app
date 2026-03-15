package com.example.aquaroute_system.data.repository

import android.util.Log
import com.example.aquaroute_system.data.models.FirestorePort
import com.example.aquaroute_system.data.models.Result
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await
import java.lang.Math.toRadians
import kotlin.math.cos

/**
 * Repository for handling Firestore port data operations
 */
class PortRepository {

    companion object {
        private const val TAG = "PortRepository"
        private const val PORTS_COLLECTION = "ports"

        // Counters to track how many times each method is called
        var getPortsInBoundsCallCount = 0
        var getPortsNearLocationCallCount = 0
        var getPortsByNamesCallCount = 0
        var loadPortsCallCount = 0
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val portsCollection = firestore.collection(PORTS_COLLECTION)

    suspend fun loadPorts(): Result<List<FirestorePort>> {
        loadPortsCallCount++
        Log.d(TAG, "loadPorts() called #$loadPortsCallCount")
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

    suspend fun getPortsInBounds(
        minLat: Double, maxLat: Double,
        minLon: Double, maxLon: Double
    ): List<FirestorePort> {
        getPortsInBoundsCallCount++
        Log.d(TAG, "getPortsInBounds() called #$getPortsInBoundsCallCount with bounds: lat[$minLat, $maxLat], lon[$minLon, $maxLon]")
        return try {
            val latQuery = firestore.collection("ports")
                .whereGreaterThanOrEqualTo("lat", minLat)
                .whereLessThanOrEqualTo("lat", maxLat)
                .get()
                .await()

            val ports = latQuery.documents.mapNotNull { doc ->
                val port = doc.toObject(FirestorePort::class.java)?.copy(id = doc.id)
                if (port != null && port.lng in minLon..maxLon) {
                    port
                } else {
                    null
                }
            }

            Log.d(TAG, "Found ${ports.size} ports in bounds after manual filtering")
            ports
        } catch (e: Exception) {
            Log.e(TAG, "Error getting ports in bounds", e)
            emptyList()
        }
    }

    suspend fun getPortsNearLocation(lat: Double, lon: Double, radiusKm: Double): List<FirestorePort> {
        getPortsNearLocationCallCount++
        Log.d(TAG, "getPortsNearLocation() called #$getPortsNearLocationCallCount at ($lat, $lon) radius $radiusKm km")
        val latDelta = radiusKm / 111.0
        val lonDelta = radiusKm / (111.0 * cos(toRadians(lat)))

        val minLat = lat - latDelta
        val maxLat = lat + latDelta
        val minLon = lon - lonDelta
        val maxLon = lon + lonDelta

        return getPortsInBounds(minLat, maxLat, minLon, maxLon)
    }

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

    private fun generateOperatingHours(name: String, type: String): Pair<Int?, Int?> {
        return when {
            type.contains("Ferry", ignoreCase = true) -> 5 to 20
            name.contains("Banton", ignoreCase = true) -> 6 to 19
            else -> {
                val hash = abs(name.hashCode())
                val open = 5 + (hash % 4)
                val close = 17 + (hash % 5)
                open to close
            }
        }
    }

    private fun abs(int: Int): Int = if (int < 0) -int else int

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

    suspend fun getPortsByNames(names: List<String>): List<FirestorePort> {
        getPortsByNamesCallCount++
        Log.d(TAG, "getPortsByNames() called #$getPortsByNamesCallCount with ${names.size} names")
        if (names.isEmpty()) return emptyList()
        return try {
            val snapshot = firestore.collection(PORTS_COLLECTION)
                .whereIn("name", names)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                doc.toObject(FirestorePort::class.java)?.copy(id = doc.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting ports by names", e)
            emptyList()
        }
    }
}