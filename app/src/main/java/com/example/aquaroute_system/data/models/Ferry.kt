package com.example.aquaroute_system.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Data class representing a ferry matching Firebase structure
 */
@Parcelize
data class Ferry(
    val id: String = "",
    val name: String = "",
    var lat: Double = 0.0,           // Maps to current_lat in Firebase
    var lon: Double = 0.0,           // Maps to current_lng in Firebase
    val status: String = "",          // Maps to status in Firebase (on_time, delayed, etc.)
    val eta: Int = 0,                 // Maps to eta in Firebase (minutes)
    val route: String = "",           // Maps to route in Firebase (e.g., "Batangas - Cebu")
    val speed_knots: Int = 0,         // NEW: from Firebase
    val pointA: List<Double>? = null, // NEW: [lat, lng] from Firebase
    val pointB: List<Double>? = null, // NEW: [lat, lng] from Firebase
    val source: String = "",          // NEW: from Firebase
    val created_at: String? = null,   // NEW: from Firebase
    val last_updated: String? = null  // NEW: from Firebase
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