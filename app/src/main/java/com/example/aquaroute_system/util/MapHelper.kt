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
        onMarkerClick: (FirestorePort) -> Unit
    ): Marker {
        return Marker(mapView).apply {
            position = GeoPoint(port.lat, port.lng)
            title = port.name

            val iconText = when (port.type) {
                "ferry_terminal" -> "⛴"  // Ferry emoji
                "pier" -> "⚓"            // Anchor emoji
                else -> "📍"              // Default location pin
            }
            setTextIcon(iconText)

            val color = when (port.type) {
                "ferry_terminal" -> Color.parseColor("#2196F3") // Blue
                "pier" -> Color.parseColor("#4CAF50")          // Green
                else -> Color.parseColor("#FF9800")            // Orange
            }
            setTextLabelForegroundColor(color)

            when (port.status.lowercase()) {
                "open" -> setTextLabelBackgroundColor(Color.parseColor("#E8F5E8")) // Light green
                "closed" -> setTextLabelBackgroundColor(Color.parseColor("#FFEBEE")) // Light red
                else -> setTextLabelBackgroundColor(Color.TRANSPARENT)
            }

            setTextLabelFontSize(14)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            subDescription = "Type: ${port.type.uppercase()} | Status: ${port.status}"

            setOnMarkerClickListener { _, _ ->
                onMarkerClick(port)
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
