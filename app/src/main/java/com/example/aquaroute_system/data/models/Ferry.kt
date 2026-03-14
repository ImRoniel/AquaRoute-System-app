package com.example.aquaroute_system.data.models

import android.os.Parcelable

import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.IgnoredOnParcel
import org.osmdroid.util.GeoPoint

/**
 * Data class representing a ferry matching Firebase structure
 */
@Parcelize
data class Ferry(
    val id: String = "",
    val name: String = "",
    var lat: Double = 0.0,
    var lon: Double = 0.0,
    val status: String = "",
    val eta: Int = 0,
    val route: String = "",
    val speed_knots: Int = 0,
    val pointA: List<Double>? = null,
    val pointB: List<Double>? = null,
    val source: String = "",
    val created_at: String? = null,
    val last_updated: String? = null,
    @IgnoredOnParcel
    val routePoints: List<GeoPoint>? = null,
    val startTime: Long? = null,
    val endTime: Long? = null         // Timestamp when journey ends (ms)// NEW: from Firebase
) : Parcelable {

    // Helper to get destination from route
    fun getDestination(): String {
        return if (route.contains("-")) {
            route.split("-").lastOrNull()?.trim() ?: "Unknown"
        } else {
            "Unknown"
        }
    }

    // Helper to get origin from route
    fun getOrigin(): String {
        return if (route.contains("-")) {
            route.split("-").firstOrNull()?.trim() ?: "Unknown"
        } else {
            "Unknown"
        }
    }


}