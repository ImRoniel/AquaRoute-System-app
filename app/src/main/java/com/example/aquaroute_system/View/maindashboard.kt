package com.example.aquaroute_system.View

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.aquaroute_system.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.*

class MainDashboard : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var btnMenu: ImageButton
    private lateinit var btnLayers: ImageButton
    private lateinit var btnCompass: ImageButton
    private lateinit var btnSearch: ImageButton
    private lateinit var btnZoomIn: ImageButton
    private lateinit var btnZoomOut: ImageButton
    private lateinit var tvLiveStatus: TextView
    private lateinit var tvLastUpdate: TextView
    private lateinit var tvConnection: TextView
    private lateinit var bottomSheet: View
    private lateinit var ivSelectedIcon: ImageView
    private lateinit var tvSelectedTitle: TextView
    private lateinit var tvSelectedStatus: TextView
    private lateinit var tvSelectedETA: TextView
    private lateinit var tvSelectedRoute: TextView
    private lateinit var tvSelectedDetails: TextView
    private lateinit var btnCloseSheet: ImageButton

    private val ferryMarkers = mutableMapOf<String, Marker>()
    private val portMarkers = mutableMapOf<String, Marker>()
    private val routeLines = mutableListOf<Polyline>()

    private val ferryData = listOf(
        Ferry("MV Star Express", "Manila-Cavite", 14.55, 120.96, "on_time", 15),
        Ferry("MV Sea Princess", "Manila-Cavite", 14.50, 120.93, "delayed", 25),
        Ferry("MV Ocean King", "Manila-Batangas", 14.20, 120.98, "on_time", 40)
    )

    private val portData = listOf(
        Port("Dagupan Ferry Terminal", 16.0431, 120.3339, true),
        Port("Manila Port", 14.594, 120.970, false),
        Port("Cavite Port", 14.475, 120.915, false)
    )

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 3000L
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // MUST BE CALLED BEFORE setContentView
        Configuration.getInstance().apply {
            userAgentValue = packageName
            load(this@MainDashboard, getSharedPreferences("osmdroid", MODE_PRIVATE))
        }

        setContentView(R.layout.maindashboard)

        supportActionBar?.hide()

        initializeViews()
        setupMap()
        setupEventListeners()
        startLiveUpdates()
    }

    private fun initializeViews() {
        mapView = findViewById(R.id.mapView)
        btnMenu = findViewById(R.id.btnMenu)
        btnLayers = findViewById(R.id.btnLayers)
        btnCompass = findViewById(R.id.btnCompass)
        btnSearch = findViewById(R.id.btnSearch)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        btnZoomOut = findViewById(R.id.btnZoomOut)
        tvLiveStatus = findViewById(R.id.tvLiveStatus)
        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        tvConnection = findViewById(R.id.tvConnection)
        bottomSheet = findViewById(R.id.bottomSheet)
        ivSelectedIcon = findViewById(R.id.ivSelectedIcon)
        tvSelectedTitle = findViewById(R.id.tvSelectedTitle)
        tvSelectedStatus = findViewById(R.id.tvSelectedStatus)
        tvSelectedETA = findViewById(R.id.tvSelectedETA)
        tvSelectedRoute = findViewById(R.id.tvSelectedRoute)
        tvSelectedDetails = findViewById(R.id.tvSelectedDetails)
        btnCloseSheet = findViewById(R.id.btnCloseSheet)
    }

    private fun setupMap() {
        try {
            mapView.setTileSource(TileSourceFactory.MAPNIK)
            mapView.setMultiTouchControls(true)
            mapView.minZoomLevel = 5.0
            mapView.maxZoomLevel = 19.0

            // Set initial view
            val dagupan = GeoPoint(16.0431, 120.3339)
            mapView.controller.setZoom(13.0)
            mapView.controller.setCenter(dagupan)

            addPortMarkers()
            addFerryMarkers()
            drawRoutes()

        } catch (e: Exception) {
            Toast.makeText(this, "Map setup error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun addPortMarkers() {
        portData.forEach { port ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(port.lat, port.lon)

                // Set text icon for port
                setTextIcon("P")

                // Color based on primary status
                if (port.isPrimary) {
                    setTextLabelForegroundColor(Color.parseColor("#F44336")) // Red
                } else {
                    setTextLabelForegroundColor(Color.parseColor("#666666")) // Gray
                }

                setTextLabelBackgroundColor(Color.TRANSPARENT)
                setTextLabelFontSize(16)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                // Show title on click
                title = port.name

                setOnMarkerClickListener { _, _ ->
                    showBottomSheetForPort(port)
                    true
                }
            }
            mapView.overlays.add(marker)
            portMarkers[port.name] = marker
        }
    }

    private fun addFerryMarkers() {
        ferryData.forEach { ferry ->
            val marker = Marker(mapView).apply {
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

                // Show title on click
                title = ferry.name

                setOnMarkerClickListener { _, _ ->
                    showBottomSheetForFerry(ferry)
                    true
                }
            }
            mapView.overlays.add(marker)
            ferryMarkers[ferry.name] = marker
        }
    }

    private fun drawRoutes() {
        val northRoute = listOf(
            GeoPoint(16.0431, 120.3339), // Dagupan
            GeoPoint(15.8797, 119.7740), // Lingayen
            GeoPoint(14.594, 120.970)    // Manila
        )

        val polyline = Polyline().apply {
            setPoints(northRoute)
            color = Color.parseColor("#5500A3E0")
            width = 3.0f
        }
        mapView.overlays.add(polyline)
        routeLines.add(polyline)
    }

    private fun showBottomSheetForFerry(ferry: Ferry) {
        tvSelectedTitle.text = ferry.name
        tvSelectedStatus.text = ferry.status.replace("_", " ").uppercase()
        tvSelectedETA.text = "${ferry.eta} min"
        tvSelectedRoute.text = ferry.route
        tvSelectedDetails.text = "Location: ${ferry.lat}, ${ferry.lon}"

        bottomSheet.visibility = View.VISIBLE

        // Center map on ferry
        mapView.controller.animateTo(GeoPoint(ferry.lat, ferry.lon))
    }

    private fun showBottomSheetForPort(port: Port) {
        tvSelectedTitle.text = port.name
        tvSelectedStatus.text = if (port.isPrimary) "MAIN TERMINAL" else "TERMINAL"
        tvSelectedETA.text = "N/A"
        tvSelectedRoute.text = "Hub"
        tvSelectedDetails.text = "Location: ${port.lat}, ${port.lon}"

        bottomSheet.visibility = View.VISIBLE

        // Center map on port
        mapView.controller.animateTo(GeoPoint(port.lat, port.lon))
    }

    private fun setupEventListeners() {
        btnMenu.setOnClickListener {
            Toast.makeText(this, "Menu", Toast.LENGTH_SHORT).show()
        }

        btnLayers.setOnClickListener {
            toggleLayers()
        }

        btnCompass.setOnClickListener {
            // Reset map rotation
            mapView.mapOrientation = 0f
            mapView.invalidate()
            Toast.makeText(this, "North reset", Toast.LENGTH_SHORT).show()
        }

        btnSearch.setOnClickListener {
            Toast.makeText(this, "Search", Toast.LENGTH_SHORT).show()
        }

        btnZoomIn.setOnClickListener {
            mapView.controller.zoomIn()
        }

        btnZoomOut.setOnClickListener {
            mapView.controller.zoomOut()
        }

        btnCloseSheet.setOnClickListener {
            bottomSheet.visibility = View.GONE
        }

        // Close bottom sheet when clicking map
        mapView.setOnClickListener {
            if (bottomSheet.visibility == View.VISIBLE) {
                bottomSheet.visibility = View.GONE
            }
        }
    }

    private fun toggleLayers() {
        val areRoutesVisible = routeLines.any { it.isEnabled }
        val areFerriesVisible = ferryMarkers.values.any { it.isEnabled }

        routeLines.forEach { it.isEnabled = !areRoutesVisible }
        ferryMarkers.values.forEach { it.isEnabled = !areFerriesVisible }

        mapView.invalidate()

        val state = if (!areRoutesVisible && !areFerriesVisible) "shown" else "hidden"
        Toast.makeText(this, "Layers $state", Toast.LENGTH_SHORT).show()
    }

    private fun startLiveUpdates() {
        handler.post(object : Runnable {
            override fun run() {
                updateFerryPositions()
                updateStatusOverlay()
                handler.postDelayed(this, updateInterval)
            }
        })
    }

    private fun updateFerryPositions() {
        // Simulate movement
        ferryData.forEach { ferry ->
            ferry.lat += (Math.random() - 0.5) * 0.001
            ferry.lon += (Math.random() - 0.5) * 0.001

            ferryMarkers[ferry.name]?.position = GeoPoint(ferry.lat, ferry.lon)
        }

        mapView.invalidate()
    }

    private fun updateStatusOverlay() {
        tvLastUpdate.text = timeFormat.format(Date())

        // Blink LIVE indicator
        tvLiveStatus.alpha = if (tvLiveStatus.alpha == 1f) 0.5f else 1f
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        handler.removeCallbacksAndMessages(null)
    }

    data class Ferry(
        val name: String,
        val route: String,
        var lat: Double,
        var lon: Double,
        val status: String,
        val eta: Int
    )

    data class Port(
        val name: String,
        val lat: Double,
        val lon: Double,
        val isPrimary: Boolean
    )
}