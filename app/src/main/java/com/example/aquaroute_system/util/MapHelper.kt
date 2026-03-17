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
        context: android.content.Context,
        mapView: MapView,
        ferry: Ferry,
        onMarkerClick: (Ferry) -> Unit
    ): Marker {
        return Marker(mapView).apply {
            position = GeoPoint(ferry.lat, ferry.lon)

            // Use ferry drawable
            icon = ContextCompat.getDrawable(context, R.drawable.ic_ferry_marker)

            // Color tint based on status
            val color = when (ferry.status.lowercase()) {
                "on_time" -> Color.parseColor("#4CAF50") // Green
                "delayed" -> Color.parseColor("#FF9800") // Orange
                "cancelled" -> Color.parseColor("#F44336") // Red
                else -> Color.parseColor("#666666") // Gray
            }
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

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
        context: android.content.Context,
        mapView: MapView,
        port: Port,
        onMarkerClick: (Port) -> Unit
    ): Marker {
        return Marker(mapView).apply {
            position = GeoPoint(port.lat, port.lon)

            // Use terminal icon for primary, pier for others
            val drawableRes = if (port.isPrimary) R.drawable.ic_port_terminal else R.drawable.ic_port_pier
            icon = ContextCompat.getDrawable(context, drawableRes)

            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

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
        context: android.content.Context,
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

            // Select icon based on port type
            val drawableRes = when {
                port.type.contains("terminal", ignoreCase = true) -> R.drawable.ic_port_terminal
                port.type.contains("pier", ignoreCase = true) -> R.drawable.ic_port_pier
                port.type.contains("boatyard", ignoreCase = true) -> R.drawable.ic_port_boatyard
                else -> R.drawable.ic_port_marker
            }
            icon = ContextCompat.getDrawable(context, drawableRes)

            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

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