package com.example.aquaroute_system.util

import android.graphics.Color
import android.util.Log
import com.example.aquaroute_system.data.models.Ferry
import com.example.aquaroute_system.data.models.FirestorePort
import com.example.aquaroute_system.data.models.Port
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.util.GeoPoint

/**
 * Utility class for creating and managing map markers
 */
object MapHelper {

    private const val TAG = "MapHelper"

    /**
     * Create a marker for a ferry
     */
    fun createFerryMarker(
        mapView: MapView,
        ferry: Ferry,
        onMarkerClick: (Ferry) -> Unit
    ): Marker {
        return Marker(mapView).apply {
            position = GeoPoint(ferry.lat, ferry.lon)

            // Use ship emoji
            setTextIcon("⛴")

            // Color based on status
            val color = when (ferry.status) {
                "on_time" -> Color.parseColor("#4CAF50") // Green
                "delayed" -> Color.parseColor("#FF9800") // Orange
                else -> Color.parseColor("#666666") // Gray
            }

            setTextLabelForegroundColor(color)
            setTextLabelBackgroundColor(Color.TRANSPARENT)
            setTextLabelFontSize(14)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

            title = ferry.name

            setOnMarkerClickListener { _, _ ->
                onMarkerClick(ferry)
                true
            }
        }
    }

    /**
     * Create a marker for a local port
     */
    fun createPortMarker(
        mapView: MapView,
        port: Port,
        onMarkerClick: (Port) -> Unit
    ): Marker {
        return Marker(mapView).apply {
            position = GeoPoint(port.lat, port.lon)

            setTextIcon("P")

            if (port.isPrimary) {
                setTextLabelForegroundColor(Color.parseColor("#F44336")) // Red
            } else {
                setTextLabelForegroundColor(Color.parseColor("#666666")) // Gray
            }

            setTextLabelBackgroundColor(Color.TRANSPARENT)
            setTextLabelFontSize(16)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

            title = port.name

            setOnMarkerClickListener { _, _ ->
                onMarkerClick(port)
                true
            }
        }
    }

    /**
     * Create a marker for a Firestore port
     */
    fun createFirestorePortMarker(
        mapView: MapView,
        port: FirestorePort,
        currentHour: Int,  // NEW: Pass current hour
        onMarkerClick: (FirestorePort) -> Unit
    ): Marker {
        // Calculate dynamic status based on time
        val dynamicStatus = port.getCurrentStatus(currentHour)

        // Create a copy of port with dynamic status for display
        val displayPort = port.copy(status = dynamicStatus)

        return Marker(mapView).apply {
            position = GeoPoint(port.lat, port.lng)
            title = port.name  // Keep original name

            // Icon based on port type (unchanged)
            val iconText = when (port.type) {
                "ferry_terminal" -> "⛴"  // Ferry emoji
                "pier" -> "⚓"            // Anchor emoji
                else -> "📍"              // Default location pin
            }
            setTextIcon(iconText)

            // Color based on port type (unchanged)
            val color = when (port.type) {
                "ferry_terminal" -> Color.parseColor("#2196F3") // Blue
                "pier" -> Color.parseColor("#4CAF50")          // Green
                else -> Color.parseColor("#FF9800")            // Orange
            }
            setTextLabelForegroundColor(color)

            // MODIFIED: Background based on DYNAMIC status
            when (dynamicStatus.lowercase()) {
                "open" -> setTextLabelBackgroundColor(Color.parseColor("#E8F5E8")) // Light green
                "closed" -> setTextLabelBackgroundColor(Color.parseColor("#FFEBEE")) // Light red
                "closing soon" -> setTextLabelBackgroundColor(Color.parseColor("#FFF3E0")) // Light orange
                else -> setTextLabelBackgroundColor(Color.TRANSPARENT)
            }

            setTextLabelFontSize(14)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

            // MODIFIED: Show dynamic status in description
            subDescription = buildString {
                append("Type: ${port.type.uppercase()}")
                append(" | Status: $dynamicStatus")

                // Show hours if available
                if (port.openHour != null && port.closeHour != null) {
                    append(" | ${port.openHour}:00-${port.closeHour}:00")
                }
            }

            // IMPORTANT: Pass the displayPort (with dynamic status) to click handler
            setOnMarkerClickListener { _, _ ->
                onMarkerClick(displayPort)  // Pass port with current status
                true
            }
        }
    }

    /**
     * Update marker position
     */
    fun updateMarkerPosition(marker: Marker, lat: Double, lon: Double) {
        marker.position = GeoPoint(lat, lon)
    }
}
