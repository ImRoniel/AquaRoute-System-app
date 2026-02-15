package com.example.aquaroute_system.View

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.ViewModelProvider
import com.example.aquaroute_system.R
import com.example.aquaroute_system.data.models.MarkerDetail
import com.example.aquaroute_system.data.models.Result
import com.example.aquaroute_system.ui.viewmodel.MainDashboardViewModel
import com.example.aquaroute_system.ui.viewmodel.MainDashboardViewModelFactory
import com.example.aquaroute_system.util.DateFormatter
import com.example.aquaroute_system.util.MapHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import android.util.Log

class MainDashboard : AppCompatActivity() {

    companion object {
        private const val TAG = "MainDashboard"
    }

    // ViewModel
    private lateinit var viewModel: MainDashboardViewModel

    // UI Components
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

    // Search views
    private lateinit var searchCard: CardView
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageButton

    // Marker collections
    private val ferryMarkers = mutableMapOf<String, Marker>()
    private val portMarkers = mutableMapOf<String, Marker>()
    private val firestorePortMarkers = mutableMapOf<String, Marker>()
    private val routeLines = mutableListOf<Polyline>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // MUST BE CALLED BEFORE setContentView
        Configuration.getInstance().apply {
            userAgentValue = packageName
            load(this@MainDashboard, getSharedPreferences("osmdroid", MODE_PRIVATE))
        }

        setContentView(R.layout.maindashboard)
        supportActionBar?.hide()

        // Initialize ViewModel
        val factory = MainDashboardViewModelFactory(this)
        viewModel = ViewModelProvider(this, factory).get(MainDashboardViewModel::class.java)

        initializeViews()
        setupMap()
        setupEventListeners()
        setupViewModelObservers()
        viewModel.startLiveUpdates()
        viewModel.loadFirestorePorts()
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

        // Search views
        searchCard = findViewById(R.id.searchCard)
        etSearch = findViewById(R.id.etSearch)
        btnClearSearch = findViewById(R.id.btnClearSearch)
    }

    private fun setupMap() {
        try {
            mapView.setTileSource(TileSourceFactory.MAPNIK)
            mapView.setMultiTouchControls(true)
            mapView.minZoomLevel = 5.0
            mapView.maxZoomLevel = 19.0

            // Set initial view to Philippines
            val phCenter = GeoPoint(12.8797, 121.7740)
            mapView.controller.setZoom(6.0)
            mapView.controller.setCenter(phCenter)

            addPortMarkers()
            addFerryMarkers()
            drawRoutes()

        } catch (e: Exception) {
            Toast.makeText(this, "Map setup error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    // ==================== VIEWMODEL OBSERVERS ====================

    private fun setupViewModelObservers() {
        // Observe Firestore ports
        viewModel.firestorePorts.observe(this) { result ->
            when (result) {
                is Result.Loading -> {
                    // Show loading state if needed
                    Toast.makeText(this, "Loading ports...", Toast.LENGTH_SHORT).show()
                }
                is Result.Success -> {
                    clearFirestoreMarkers()
                    result.data.forEach { port ->
                        val marker = MapHelper.createFirestorePortMarker(mapView, port) { firestorePort ->
                            viewModel.onFirestorePortMarkerClick(firestorePort)
                        }
                        mapView.overlays.add(marker)
                        firestorePortMarkers[port.id] = marker
                    }
                    mapView.invalidate()
                    Toast.makeText(this, "Loaded ${result.data.size} ports from Firestore", Toast.LENGTH_SHORT).show()
                }
                is Result.Error -> {
                    Toast.makeText(this, "Failed to load ports: ${result.exception.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Observe ferries
        viewModel.ferries.observe(this) { ferries ->
            // Update ferry marker positions
            ferries.forEach { ferry ->
                ferryMarkers[ferry.name]?.let { marker ->
                    MapHelper.updateMarkerPosition(marker, ferry.lat, ferry.lon)
                }
            }
            mapView.invalidate()
        }

        // Observe selected marker detail
        viewModel.selectedMarkerDetail.observe(this) { detail ->
            if (detail != null) {
                showBottomSheetForMarkerDetail(detail)
            } else {
                hideBottomSheet()
            }
        }

        // Observe last update time
        viewModel.lastUpdateTime.observe(this) { time ->
            tvLastUpdate.text = time
        }

        // Observe live indicator alpha
        viewModel.liveIndicatorAlpha.observe(this) { alpha ->
            tvLiveStatus.alpha = alpha
        }

        // Observe error messages
        viewModel.errorMessage.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }

        // Observe search results
        viewModel.searchResultsCount.observe(this) { count ->
            if (count == 0) {
                Toast.makeText(this, "No results found", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Found $count results", Toast.LENGTH_SHORT).show()
            }
        }

        // Restore search query
        viewModel.lastSearchQuery.observe(this) { query ->
            if (query.isNotBlank() && etSearch.text.toString() != query) {
                etSearch.setText(query)
                etSearch.setSelection(query.length)
            }
        }
    }

    // ==================== MARKER SETUP ====================

    private fun addPortMarkers() {
        viewModel.ports.value?.forEach { port ->
            val marker = MapHelper.createPortMarker(mapView, port) { selectedPort ->
                viewModel.onPortMarkerClick(selectedPort)
            }
            mapView.overlays.add(marker)
            portMarkers[port.name] = marker
        }
    }

    private fun addFerryMarkers() {
        viewModel.ferries.value?.forEach { ferry ->
            val marker = MapHelper.createFerryMarker(mapView, ferry) { selectedFerry ->
                viewModel.onFerryMarkerClick(selectedFerry)
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

    private fun clearFirestoreMarkers() {
        firestorePortMarkers.values.forEach { marker ->
            mapView.overlays.remove(marker)
        }
        firestorePortMarkers.clear()
    }

    // ==================== BOTTOM SHEET ====================

    private fun showBottomSheetForMarkerDetail(detail: MarkerDetail) {
        when (detail) {
            is MarkerDetail.FerryDetail -> showBottomSheetForFerry(detail.ferry)
            is MarkerDetail.PortDetail -> showBottomSheetForPort(detail.port)
            is MarkerDetail.FirestorePortDetail -> showBottomSheetForFirestorePort(detail.port)
        }
    }

    private fun showBottomSheetForFerry(ferry: com.example.aquaroute_system.data.models.Ferry) {
        tvSelectedTitle.text = ferry.name
        tvSelectedStatus.text = ferry.status.replace("_", " ").uppercase()
        tvSelectedETA.text = "${ferry.eta} min"
        tvSelectedRoute.text = ferry.route
        tvSelectedDetails.text = "Location: ${ferry.lat}, ${ferry.lon}"
        bottomSheet.visibility = View.VISIBLE
        mapView.controller.animateTo(GeoPoint(ferry.lat, ferry.lon))
    }

    private fun showBottomSheetForPort(port: com.example.aquaroute_system.data.models.Port) {
        tvSelectedTitle.text = port.name
        tvSelectedStatus.text = if (port.isPrimary) "MAIN TERMINAL" else "TERMINAL"
//        tvSelectedETA.text = "N/A"
        tvSelectedRoute.text = "Hub"
        tvSelectedDetails.text = "Location: ${port.lat}, ${port.lon}"
        bottomSheet.visibility = View.VISIBLE
        mapView.controller.animateTo(GeoPoint(port.lat, port.lon))
    }

    private fun showBottomSheetForFirestorePort(port: com.example.aquaroute_system.data.models.FirestorePort) {
        tvSelectedTitle.text = port.name

        val displayType = port.type.split("_")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

        tvSelectedStatus.text = port.status
        tvSelectedETA.text = displayType
        tvSelectedRoute.text = "Source: ${port.source}"

        val dateString = DateFormatter.formatDateTime(port.createdAt.toDate())
        tvSelectedDetails.text = "Created: $dateString\n" +
                "Location: ${String.format("%.6f", port.lat)}, ${String.format("%.6f", port.lng)}"

        bottomSheet.visibility = View.VISIBLE
        mapView.controller.animateTo(GeoPoint(port.lat, port.lng))
    }

    private fun hideBottomSheet() {
        bottomSheet.visibility = View.GONE
        tvSelectedTitle.text = ""
        tvSelectedStatus.text = ""
        tvSelectedETA.text = ""
        tvSelectedRoute.text = ""
        tvSelectedDetails.text = ""
    }

    // ==================== EVENT LISTENERS ====================

    private fun setupEventListeners() {
        btnMenu.setOnClickListener {
            Toast.makeText(this, "You clicked menu", Toast.LENGTH_SHORT).show()
        }

        btnLayers.setOnClickListener {
            toggleLayers()
        }

        btnCompass.setOnClickListener {
            mapView.mapOrientation = 0f
            mapView.invalidate()
            Toast.makeText(this, "North reset", Toast.LENGTH_SHORT).show()
        }

        btnSearch.setOnClickListener {
            toggleSearchBar()
        }

        btnClearSearch.setOnClickListener {
            etSearch.text.clear()
            hideKeyboard()
            btnClearSearch.visibility = View.GONE
        }

        btnZoomIn.setOnClickListener {
            mapView.controller.zoomIn()
        }

        btnZoomOut.setOnClickListener {
            mapView.controller.zoomOut()
        }

        btnCloseSheet.setOnClickListener {
            viewModel.clearSelectedMarkerDetail()
        }

        mapView.setOnClickListener {
            if (bottomSheet.visibility == View.VISIBLE) {
                viewModel.clearSelectedMarkerDetail()
            }
        }

        // Search text change listener
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Handle search action on keyboard
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = etSearch.text.toString()
                if (query.isNotBlank()) {
                    viewModel.performSearch(query)
                }
                hideKeyboard()
                return@setOnEditorActionListener true
            }
            false
        }
    }

    // ==================== SEARCH FUNCTIONS ====================

    private fun toggleSearchBar() {
        if (searchCard.visibility == View.VISIBLE) {
            hideSearchBar()
        } else {
            showSearchBar()
        }
    }

    private fun showSearchBar() {
        searchCard.visibility = View.VISIBLE
        etSearch.requestFocus()

        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)

        btnClearSearch.visibility = if (etSearch.text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun hideSearchBar() {
        searchCard.visibility = View.GONE
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
    }

    // ==================== MAP FUNCTIONS ====================

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

    // ==================== LIFECYCLE ====================

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        viewModel.stopLiveUpdates()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopLiveUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopLiveUpdates()
    }
}
