package com.example.aquaroute_system.data.repository

import android.util.Log
import com.example.aquaroute_system.data.models.Ferry
import com.example.aquaroute_system.data.models.Result
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import org.osmdroid.util.GeoPoint as OsmGeoPoint

class FerryRepository(private val firestore: FirebaseFirestore) {

    companion object {
        private const val TAG = "FerryRepository"
        private const val FERRIES_COLLECTION = "ferries"
    }

    suspend fun getAllFerries(): List<Ferry> {
        return try {
            val snapshot = firestore.collection(FERRIES_COLLECTION)
                .get()
                .await()

            val ferries = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null

                    // Parse routePoints - handle both array of maps and array of GeoPoints
                    val routePoints = when (val rp = data["routePoints"]) {
                        is List<*> -> {
                            rp.mapNotNull { point ->
                                when (point) {
                                    is Map<*, *> -> {
                                        val lat = (point["lat"] as? Number)?.toDouble()
                                        val lng = (point["lng"] as? Number)?.toDouble()
                                        if (lat != null && lng != null) OsmGeoPoint(lat, lng) else null
                                    }
                                    is GeoPoint -> OsmGeoPoint(point.latitude, point.longitude)
                                    else -> null
                                }
                            }.takeIf { it.isNotEmpty() }
                        }
                        else -> null
                    }

                    // Parse startTime - handle Long, Number, or String
                    val startTime = when (val st = data["startTime"]) {
                        is Long -> st
                        is Number -> st.toLong()
                        is String -> st.toLongOrNull()
                        else -> null
                    }

                    // Parse endTime
                    val endTime = when (val et = data["endTime"]) {
                        is Long -> et
                        is Number -> et.toLong()
                        is String -> et.toLongOrNull()
                        else -> null
                    }

                    val ferry = Ferry(
                        id = doc.id,
                        name = data["name"] as? String ?: "",
                        lat = (data["current_lat"] as? Number)?.toDouble() ?: 0.0,
                        lon = (data["current_lng"] as? Number)?.toDouble() ?: 0.0,
                        status = data["status"] as? String ?: "unknown",
                        eta = when (val etaVal = data["eta"]) {
                            is Number -> {
                                val num = etaVal.toDouble()
                                if (num.isNaN() || num.isInfinite()) 0 else etaVal.toInt()
                            }
                            else -> 0
                        },
                         route = data["route"] as? String ?: "",
                        speed_knots = (data["speed_knots"] as? Number)?.toInt() ?: 0,
                        pointA = parsePoint(data["pointA"]),
                        pointB = parsePoint(data["pointB"]),
                        source = data["source"] as? String ?: "",
                        created_at = data["created_at"] as? String,
                        last_updated = data["last_updated"] as? String,
                        routePoints = routePoints,
                        startTime = startTime,
                        endTime = endTime
                    )
                    ferry
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

    private fun parsePoint(obj: Any?): List<Double>? {
        return when (obj) {
            is List<*> -> {
                val list = obj.mapNotNull { (it as? Number)?.toDouble() }
                if (list.size == 2) list else null
            }
            is GeoPoint -> listOf(obj.latitude, obj.longitude)
            is Map<*, *> -> {
                val lat = (obj["lat"] as? Number)?.toDouble()
                val lng = (obj["lng"] as? Number)?.toDouble()
                if (lat != null && lng != null) listOf(lat, lng) else null
            }
            else -> null
        }
    }

    suspend fun getFerriesNearLocation(lat: Double, lon: Double, radiusKm: Double = 100.0): List<Ferry> {
        val allFerries = getAllFerries()
        return allFerries.filter { ferry ->
            val distance = calculateDistance(lat, lon, ferry.lat, ferry.lon)
            distance <= radiusKm
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}