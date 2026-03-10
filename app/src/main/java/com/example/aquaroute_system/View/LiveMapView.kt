package com.example.aquaroute_system.View

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.aquaroute_system.R
import com.example.aquaroute_system.data.models.FirestorePort
import com.example.aquaroute_system.data.models.MarkerDetail
import com.example.aquaroute_system.data.models.Result
import com.example.aquaroute_system.data.repository.FerryRepository
import com.example.aquaroute_system.data.repository.PortRepository
import com.example.aquaroute_system.ui.viewmodel.LiveMapViewModel
import com.example.aquaroute_system.ui.viewmodel.LiveMapViewModelFactory
import com.example.aquaroute_system.util.MapHelper
import com.example.aquaroute_system.util.SessionManager
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.*

class LiveMapView : AppCompatActivity() {

    companion object {
        private const val TAG = "LiveMapView"
    }

    // ViewModel
    private lateinit var viewModel: LiveMapViewModel
    private lateinit var sessionManager: SessionManager

    // UI Components
    private lateinit var mapView: MapView
    private lateinit var btnBack: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnZoomIn: ImageButton
    private lateinit var btnZoomOut: ImageButton
    private lateinit var btnMyLocation: ImageButton
    private lateinit var btnSatellite: ImageButton
    private lateinit var tvLiveStatus: TextView
    private lateinit var tvLastUpdate: TextView
    private lateinit var tvConnection: TextView
    private lateinit var bottomSheet: View
    private lateinit var tvVesselName: TextView
    private lateinit var tvVesselEta: TextView
    private lateinit var tvVesselRoute: TextView
    private lateinit var tvVesselLocation: TextView
    private lateinit var tvVesselStatus: TextView
    private lateinit var tvVesselSpeed: TextView
    private lateinit var btnCloseSheet: ImageButton
    private lateinit var btnPassengerView: TextView
    private lateinit var btnCargoView: TextView
    private lateinit var btnAlerts: TextView
    private lateinit var etSearch: EditText

    // Marker collections
    private val ferryMarkers = mutableMapOf<String, Marker>()
    private val portMarkers = mutableMapOf<String, Marker>()
    private val firestorePortMarkers = mutableMapOf<String, Marker>()
    private val routeLines = mutableListOf<Polyline>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.requestUserLocation(this)
        } else {
            Toast.makeText(this, "Location permission needed", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().apply {
            userAgentValue = packageName
            load(this@LiveMapView, getSharedPreferences("osmdroid", MODE_PRIVATE))
        }

        setContentView(R.layout.activity_livemap)
        supportActionBar?.hide()

        initializeViews()
        initializeViewModel()
        setupMap()
        setupEventListeners()
        setupViewModelObservers()
        checkLocationPermission()
        viewModel.startLiveUpdates()
        viewModel.loadFirestorePorts()
    }

    private fun initializeViews() {
        mapView = findViewById(R.id.mapView)
        btnBack = findViewById(R.id.btnBack)
        btnSettings = findViewById(R.id.btnSettings)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        btnZoomOut = findViewById(R.id.btnZoomOut)
        btnMyLocation = findViewById(R.id.btnMyLocation)
        btnSatellite = findViewById(R.id.btnSatellite)
        tvLiveStatus = findViewById(R.id.tvLiveStatus)
        tvLastUpdate = findViewById(R.id.tvLastUpdate)
        tvConnection = findViewById(R.id.tvConnection)
        bottomSheet = findViewById(R.id.bottomSheet)
        tvVesselName = findViewById(R.id.tvVesselName)
        tvVesselEta = findViewById(R.id.tvVesselEta)
        tvVesselRoute = findViewById(R.id.tvVesselRoute)
        tvVesselLocation = findViewById(R.id.tvVesselLocation)
        tvVesselStatus = findViewById(R.id.tvVesselStatus)
        tvVesselSpeed = findViewById(R.id.tvVesselSpeed)
        btnCloseSheet = findViewById(R.id.btnCloseSheet)
        btnPassengerView = findViewById(R.id.btnPassengerView)
        btnCargoView = findViewById(R.id.btnCargoView)
        btnAlerts = findViewById(R.id.btnAlerts)
        etSearch = findViewById(R.id.etSearch)
    }

    private fun initializeViewModel() {
        sessionManager = SessionManager(this)
        val firestore = FirebaseFirestore.getInstance()

        val portRepository = PortRepository()
        val ferryRepository = FerryRepository(firestore)
        val factory = LiveMapViewModelFactory(
            sessionManager,
            portRepository,
            ferryRepository
        )
        viewModel = ViewModelProvider(this, factory).get(LiveMapViewModel::class.java)
    }

    private fun setupMap() {
        try {
            mapView.setTileSource(TileSourceFactory.MAPNIK)
            mapView.setMultiTouchControls(true)
            mapView.minZoomLevel = 5.0
            mapView.maxZoomLevel = 19.0

            val phCenter = GeoPoint(12.8797, 121.7740)
            mapView.controller.setZoom(7.0)
            mapView.controller.setCenter(phCenter)

            // We'll add markers when data arrives from ViewModel
        } catch (e: Exception) {
            Toast.makeText(this, "Map setup error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun setupViewModelObservers() {
        viewModel.firestorePorts.observe(this) { result ->
            when (result) {
                is Result.Success -> {
                    updateFirestorePortMarkers(result.data)
                }
                is Result.Error -> {
                    Toast.makeText(this, "Failed to load ports: ${result.exception.message}", Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }

        viewModel.currentHour.observe(this) { hour ->
            Log.d(TAG, "Hour updated to: $hour - Refreshing port markers")
            val currentResult = viewModel.firestorePorts.value
            if (currentResult is Result.Success) {
                updateFirestorePortMarkers(currentResult.data)
            }
        }

        viewModel.ferries.observe(this) { ferries ->
            updateFerryMarkers(ferries)
        }

        viewModel.ports.observe(this) { ports ->
            updatePortMarkers(ports)
        }

        viewModel.selectedMarkerDetail.observe(this) { detail ->
            if (detail != null) {
                showBottomSheetForMarkerDetail(detail)
            } else {
                hideBottomSheet()
            }
        }

        viewModel.lastUpdateTime.observe(this) { time ->
            tvLastUpdate.text = "Last update: $time"
        }

        viewModel.liveIndicatorAlpha.observe(this) { alpha ->
            tvLiveStatus.alpha = alpha
        }

        viewModel.errorMessage.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.userLocation.observe(this) { location ->
            if (location != null) {
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                mapView.controller.animateTo(geoPoint)
                mapView.controller.setZoom(15.0)
                addUserLocationMarker(geoPoint)
            }
        }

        viewModel.isLocationLoading.observe(this) { isLoading ->
            if (isLoading) {
                Toast.makeText(this, "Getting your location...", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.locationError.observe(this) { error ->
            if (error != null) {
                Toast.makeText(this, "Location error: $error", Toast.LENGTH_SHORT).show()
                viewModel.clearLocationError()
            }
        }
    }

    private fun updateFerryMarkers(ferries: List<com.example.aquaroute_system.data.models.Ferry>) {
        // Clear existing markers
        ferryMarkers.values.forEach { mapView.overlays.remove(it) }
        ferryMarkers.clear()

        // Add new markers
        ferries.forEach { ferry ->
            val marker = MapHelper.createFerryMarker(mapView, ferry) { selectedFerry ->
                viewModel.onFerryMarkerClick(selectedFerry)
            }
            mapView.overlays.add(marker)
            ferryMarkers[ferry.name] = marker
        }
        mapView.invalidate()
    }

    private fun updatePortMarkers(ports: List<com.example.aquaroute_system.data.models.Port>) {
        // Clear existing markers
        portMarkers.values.forEach { mapView.overlays.remove(it) }
        portMarkers.clear()

        // Add new markers
        ports.forEach { port ->
            val marker = MapHelper.createPortMarker(mapView, port) { selectedPort ->
                viewModel.onPortMarkerClick(selectedPort)
            }
            mapView.overlays.add(marker)
            portMarkers[port.name] = marker
        }
        mapView.invalidate()
    }

    private fun updateFirestorePortMarkers(ports: List<FirestorePort>) {
        // Clear existing markers
        firestorePortMarkers.values.forEach { mapView.overlays.remove(it) }
        firestorePortMarkers.clear()

        val currentHour = viewModel.currentHour.value ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        ports.forEach { port ->
            val portWithStatus = viewModel.getPortWithDynamicStatus(port)
            val marker = MapHelper.createFirestorePortMarker(
                mapView = mapView,
                port = portWithStatus,
                currentHour = currentHour,
                onMarkerClick = { clickedPort ->
                    viewModel.onFirestorePortMarkerClick(clickedPort)
                }
            )
            mapView.overlays.add(marker)
            firestorePortMarkers[port.id] = marker
        }
        mapView.invalidate()
    }

    private fun drawRoutes() {
        val northRoute = listOf(
            GeoPoint(16.0431, 120.3339),
            GeoPoint(15.8797, 119.7740),
            GeoPoint(14.594, 120.970)
        )

        val polyline = Polyline().apply {
            setPoints(northRoute)
            color = Color.parseColor("#5500A3E0")
            width = 3.0f
        }
        mapView.overlays.add(polyline)
        routeLines.add(polyline)
    }

    private fun showBottomSheetForMarkerDetail(detail: MarkerDetail) {
        when (detail) {
            is MarkerDetail.FerryDetail -> showBottomSheetForFerry(detail.ferry)
            is MarkerDetail.PortDetail -> showBottomSheetForPort(detail.port)
            is MarkerDetail.FirestorePortDetail -> showBottomSheetForFirestorePort(detail.port)
        }
    }

    private fun showBottomSheetForFerry(ferry: com.example.aquaroute_system.data.models.Ferry) {
        tvVesselName.text = "🚢 ${ferry.name}"
        tvVesselEta.text = "⏱️ ${ferry.eta} mins"
        tvVesselRoute.text = "Route: ${ferry.route}"
        tvVesselLocation.text = "📍 ${getLocationDescription(ferry.lat, ferry.lon)}"

        val statusText = when (ferry.status.lowercase(Locale.getDefault())) {
            "on_time" -> "🟢 ON TIME"
            "delayed" -> "🟡 DELAYED"
            "cancelled" -> "🔴 CANCELLED"
            else -> "⚪ UNKNOWN"
        }
        tvVesselStatus.text = statusText
        tvVesselSpeed.text = "⚡ ${(20..30).random()} knots"

        bottomSheet.visibility = View.VISIBLE
        mapView.controller.animateTo(GeoPoint(ferry.lat, ferry.lon))
    }

    private fun showBottomSheetForPort(port: com.example.aquaroute_system.data.models.Port) {
        tvVesselName.text = "🏢 ${port.name}"
        tvVesselEta.text = "⏱️ --"
        tvVesselRoute.text = "Port"
        tvVesselLocation.text = "📍 ${getLocationDescription(port.lat, port.lon)}"
        tvVesselStatus.text = if (port.isPrimary) "MAIN TERMINAL" else "TERMINAL"
        tvVesselSpeed.text = ""

        bottomSheet.visibility = View.VISIBLE
        mapView.controller.animateTo(GeoPoint(port.lat, port.lon))
    }

    private fun showBottomSheetForFirestorePort(port: com.example.aquaroute_system.data.models.FirestorePort) {
        tvVesselName.text = "🏢 ${port.name}"
        tvVesselEta.text = "⏱️ --"
        tvVesselRoute.text = "Port"
        tvVesselLocation.text = "📍 ${getLocationDescription(port.lat, port.lng)}"
        tvVesselStatus.text = port.status
        tvVesselSpeed.text = ""

        bottomSheet.visibility = View.VISIBLE
        mapView.controller.animateTo(GeoPoint(port.lat, port.lng))
    }

    private fun getLocationDescription(lat: Double, lon: Double): String {
        return when {
            lat > 14.0 && lon > 120.0 -> "Off Nasugbu"
            lat > 13.0 && lon > 120.0 -> "Near Batangas"
            lat > 10.0 && lon > 123.0 -> "Off Cebu"
            else -> "At sea"
        }
    }

    private fun hideBottomSheet() {
        bottomSheet.visibility = View.GONE
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.requestUserLocation(this)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun addUserLocationMarker(geoPoint: GeoPoint) {
        val oldMarkers = mapView.overlays.filter {
            it is Marker && it.title == "You are here"
        }
        mapView.overlays.removeAll(oldMarkers)

        val marker = Marker(mapView).apply {
            position = geoPoint
            title = "You are here"
            setTextIcon("📍")
            setTextLabelForegroundColor(Color.parseColor("#2196F3"))
            setTextLabelBackgroundColor(Color.parseColor("#E3F2FD"))
            setTextLabelFontSize(14)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
        mapView.overlays.add(marker)
        mapView.invalidate()
    }

    private fun centerOnUserLocation() {
        viewModel.userLocation.value?.let { location ->
            mapView.controller.animateTo(GeoPoint(location.latitude, location.longitude))
            mapView.controller.setZoom(15.0)
        } ?: run {
            checkLocationPermission()
        }
    }

    private fun toggleLayers() {
        val areRoutesVisible = routeLines.any { it.isEnabled }
        val areFerriesVisible = ferryMarkers.values.any { it.isEnabled }
        val areFirestorePortsVisible = firestorePortMarkers.values.any { it.isEnabled }

        routeLines.forEach { it.isEnabled = !areRoutesVisible }
        ferryMarkers.values.forEach { it.isEnabled = !areFerriesVisible }
        firestorePortMarkers.values.forEach { it.isEnabled = !areFirestorePortsVisible }
        mapView.invalidate()

        val state = if (!areRoutesVisible && !areFerriesVisible && !areFirestorePortsVisible) "shown" else "hidden"
        Toast.makeText(this, "Layers $state", Toast.LENGTH_SHORT).show()
    }

    private fun setupEventListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnSettings.setOnClickListener {
            Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
        }

        btnZoomIn.setOnClickListener {
            mapView.controller.zoomIn()
        }

        btnZoomOut.setOnClickListener {
            mapView.controller.zoomOut()
        }

        btnMyLocation.setOnClickListener {
            centerOnUserLocation()
        }

        btnSatellite.setOnClickListener {
            toggleMapLayer()
        }

        btnCloseSheet.setOnClickListener {
            viewModel.clearSelectedMarkerDetail()
        }

        btnPassengerView.setOnClickListener {
            Toast.makeText(this, "Passenger view coming soon", Toast.LENGTH_SHORT).show()
        }

        btnCargoView.setOnClickListener {
            Toast.makeText(this, "Cargo view coming soon", Toast.LENGTH_SHORT).show()
        }

        btnAlerts.setOnClickListener {
            Toast.makeText(this, "Alerts coming soon", Toast.LENGTH_SHORT).show()
        }

        // Map click listener to hide bottom sheet
        mapView.setOnClickListener {
            if (bottomSheet.visibility == View.VISIBLE) {
                viewModel.clearSelectedMarkerDetail()
            }
        }
    }

    private fun toggleMapLayer() {
        // Check current tile source
        val currentSource = mapView.tileProvider.tileSource

        if (currentSource == TileSourceFactory.MAPNIK) {
            // Switch to another map style (using a different available tile source)
            mapView.setTileSource(TileSourceFactory.HIKEBIKEMAP)
            btnSatellite.setImageResource(R.drawable.ic_map_placeholder)
            Toast.makeText(this, "Terrain view", Toast.LENGTH_SHORT).show()
        } else {
            // Switch back to MAPNIK
            mapView.setTileSource(TileSourceFactory.MAPNIK)
            btnSatellite.setImageResource(R.drawable.ic_layers)
            Toast.makeText(this, "Map view", Toast.LENGTH_SHORT).show()
        }
        mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        viewModel.stopLiveUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopLiveUpdates()
    }
}