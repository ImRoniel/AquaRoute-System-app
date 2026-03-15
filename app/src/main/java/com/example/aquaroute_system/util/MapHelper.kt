package com.example.aquaroute_system.util

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.aquaroute_system.R
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

            // Use emoji for ferry (most reliable across devices)
            setTextIcon("⛴️")
            setTextLabelFontSize(18)

            // Color based on status
            val color = when (ferry.status.lowercase()) {
                "on_time" -> Color.parseColor("#4CAF50") // Green
                "delayed" -> Color.parseColor("#FF9800") // Orange
                "cancelled" -> Color.parseColor("#F44336") // Red
                else -> Color.parseColor("#666666") // Gray
            }

            setTextLabelForegroundColor(color)
            setTextLabelBackgroundColor(Color.TRANSPARENT)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

            title = ferry.name
            subDescription = "${ferry.route} • ${ferry.speed_knots} knots"

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

            // Use P for port
            setTextIcon("⚓")
            setTextLabelFontSize(18)

            val color = if (port.isPrimary) {
                Color.parseColor("#F44336") // Red for primary ports
            } else {
                Color.parseColor("#666666") // Gray for others
            }

            setTextLabelForegroundColor(color)
            setTextLabelBackgroundColor(Color.TRANSPARENT)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

            title = port.name
            subDescription = if (port.isPrimary) "Main Terminal" else "Port"

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
        currentHour: Int,
        onMarkerClick: (FirestorePort) -> Unit
    ): Marker {
        val dynamicStatus = port.getCurrentStatus(currentHour)
        val displayPort = port.copy(status = dynamicStatus)

        return Marker(mapView).apply {
            position = GeoPoint(port.lat, port.lng)
            title = port.name

            // Use emoji based on port type (most reliable)
            val iconText = when {
                port.type.contains("ferry", ignoreCase = true) -> "⛴️"
                port.type.contains("pier", ignoreCase = true) -> "⚓"
                else -> "📍"
            }
            setTextIcon(iconText)
            setTextLabelFontSize(18)

            // Color based on status
            val color = when {
                dynamicStatus.contains("open", ignoreCase = true) -> Color.parseColor("#4CAF50") // Green
                dynamicStatus.contains("close", ignoreCase = true) -> Color.parseColor("#F44336") // Red
                dynamicStatus.contains("soon", ignoreCase = true) -> Color.parseColor("#FF9800") // Orange
                else -> Color.parseColor("#2196F3") // Blue
            }
            setTextLabelForegroundColor(color)

            // Background based on type
            val bgColor = when {
                port.type.contains("ferry", ignoreCase = true) -> Color.parseColor("#80E3F2FD") // Light blue
                port.type.contains("pier", ignoreCase = true) -> Color.parseColor("#80E8F5E9") // Light green
                else -> Color.TRANSPARENT
            }
            setTextLabelBackgroundColor(bgColor)

            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

            // Build detailed description
            subDescription = buildString {
                append("Type: ${port.type.replaceFirstChar { it.uppercase() }}")
                append(" • Status: $dynamicStatus")
                if (port.openHour != null && port.closeHour != null) {
                    append(" • ${port.openHour}:00-${port.closeHour}:00")
                }
            }

            setOnMarkerClickListener { _, _ ->
                onMarkerClick(displayPort)
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