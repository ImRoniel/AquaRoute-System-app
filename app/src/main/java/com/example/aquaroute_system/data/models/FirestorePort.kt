package com.example.aquaroute_system.data.models

import com.google.firebase.Timestamp
import java.util.*

data class FirestorePort(
    val id: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val type: String = "",
    val status: String = "",
    val source: String = "",
    val createdAt: Timestamp = Timestamp.now(),  // default will be overridden by actual data
    val openHour: Int? = null,
    val closeHour: Int? = null,
    val timezone: String = "Asia/Manila"
) {
    // NEW: Get dynamic status based on current time
    fun getCurrentStatus(currentHour: Int): String {
        // If no hours defined, return original status
        if (openHour == null || closeHour == null) {
            return status
        }

        return when {
            // Before opening
            currentHour < openHour -> "Opens at ${formatHour(openHour)}"

            // During operating hours
            currentHour in openHour until closeHour -> {
                // Closing soon? (within 1 hour)
                if (closeHour - currentHour <= 1) {
                    "Closing Soon (until ${formatHour(closeHour)})"
                } else {
                    "Open"
                }
            }

            // After closing
            else -> "Closed (opens at ${formatHour(openHour)} tomorrow)"
        }
    }

    // Helper to format hour nicely
    private fun formatHour(hour: Int): String {
        return when {
            hour == 0 -> "12 AM"
            hour == 12 -> "12 PM"
            hour < 12 -> "${hour} AM"
            else -> "${hour - 12} PM"
        }
    }

    // For bottom sheet display - ONLY what we want to show
    fun getDisplayFields(): Map<String, String> {
        return mapOf(
            "name" to name,
            "status" to status,  // We'll update this dynamically
            "type" to type
            // NOTE: No createdAt, lat, lng
        )
    }
}