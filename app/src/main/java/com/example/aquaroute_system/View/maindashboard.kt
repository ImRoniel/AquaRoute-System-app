package com.example.aquaroute_system.View

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.example.aquaroute_system.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Log

class MainDashboard : AppCompatActivity() {

    companion object {
        private const val TAG = "MainDashboard"
    }

    // Firebase
    private val firestore = FirebaseFirestore.getInstance()
    private val portsCollection = firestore.collection("ports")
    private val firestorePortMarkers = mutableMapOf<String, Marker>()

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

    // Data
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

    // Handler and formatters, (the time and in the right button of the app )
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 3000L
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    // SharedPreferences
    private val PREFS_NAME = "MainDashboardPrefs"
    private val KEY_LAST_SEARCH = "last_search_query"
    private lateinit var sharedPreferences: SharedPreferences

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
        setupSharedPreferences()
        setupMap()
        setupEventListeners()
        startLiveUpdates()
    }

    //this function is to call the id from the xml file
    private fun initializeViews() {
        mapView = findViewById(R.id.mapView)
        btnMenu = findViewById(R.id.btnMenu)
        btnLayers = findViewById(R.id.btnLayers)
        btnCompass = findViewById(R.id.btnCompass)
        btnSearch = findViewById(R.id.btnSearch)
        btnZoomIn = findViewById(   R.id.btnZoomIn)
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
            loadPortsFromFirestore()

        } catch (e: Exception) {
            Toast.makeText(this, "Map setup error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    // ==================== FIREBASE FUNCTIONS ====================

    private fun loadPortsFromFirestore() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                portsCollection.get()
                    .addOnSuccessListener { querySnapshot ->
                        if (querySnapshot.isEmpty) {
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainDashboard,
                                    "No ports found in database",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            Log.d(TAG, "No ports found in Firestore")
                            return@addOnSuccessListener
                        }

                        // Clear existing Firestore markers
                        clearFirestoreMarkers()

                        var successCount = 0
                        var errorCount = 0

                        for (document in querySnapshot.documents) {
                            try {
                                if (addFirestoreMarker(document)) {
                                    successCount++
                                } else {
                                    errorCount++
                                }
                            } catch (e: Exception) {
                                errorCount++
                                Log.e(TAG, "Error processing document ${document.id}: ${e.message}")
                            }
                        }

                        // Update the map on UI thread
                        runOnUiThread {
                            mapView.invalidate()
                            if (errorCount > 0) {
                                Toast.makeText(
                                    this@MainDashboard,
                                    "Loaded $successCount ports ($errorCount errors)",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    this@MainDashboard,
                                    "Loaded $successCount ports from Firestore",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        Log.d(TAG, "Firestore load complete: $successCount successful, $errorCount errors")

                    }
                    .addOnFailureListener { exception ->
                        runOnUiThread {
                            Toast.makeText(
                                this@MainDashboard,
                                "Failed to load ports: ${exception.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        Log.e(TAG, "Error loading ports from Firestore", exception)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in loadPortsFromFirestore: ${e.message}", e)
            }
        }
    }

    private fun addFirestoreMarker(document: DocumentSnapshot): Boolean {
        val data = document.data ?: run {
            Log.w(TAG, "Document ${document.id} has no data")
            return false
        }

        // Extract required fields with safe null checks
        val name = data["name"] as? String ?: run {
            Log.w(TAG, "Document ${document.id} missing 'name' field")
            return false
        }

        // Handle lat field - it could be Double, Float, Long, or Int
        val latValue = data["lat"]
        val lat = when (latValue) {
            is Double -> latValue
            is Float -> latValue.toDouble()
            is Number -> latValue.toDouble()
            is String -> try {
                latValue.toDouble()
            } catch (e: NumberFormatException) {
                Log.w(TAG, "Document ${document.id} has invalid 'lat' format: $latValue")
                return false
            }
            else -> {
                Log.w(TAG, "Document ${document.id} missing or invalid 'lat' field: $latValue")
                return false
            }
        }

        // Handle lng field - it could be Double, Float, Long, or Int
        val lngValue = data["lng"]
        val lng = when (lngValue) {
            is Double -> lngValue
            is Float -> lngValue.toDouble()
            is Number -> lngValue.toDouble()
            is String -> try {
                lngValue.toDouble()
            } catch (e: NumberFormatException) {
                Log.w(TAG, "Document ${document.id} has invalid 'lng' format: $lngValue")
                return false
            }
            else -> {
                Log.w(TAG, "Document ${document.id} missing or invalid 'lng' field: $lngValue")
                return false
            }
        }

        // Optional fields with defaults
        val type = (data["type"] as? String)?.trim()?.lowercase() ?: "unknown"
        val status = (data["status"] as? String)?.trim() ?: "Unknown"
        val source = (data["source"] as? String)?.trim() ?: "Unknown"
        val createdAt = data["createdAt"] as? Timestamp ?: Timestamp.now()

        // Validate coordinates
        if (lat < -90 || lat > 90) {
            Log.w(TAG, "Document ${document.id} has invalid latitude: $lat")
            return false
        }
        if (lng < -180 || lng > 180) {
            Log.w(TAG, "Document ${document.id} has invalid longitude: $lng")
            return false
        }

        // Create GeoPoint
        val geoPoint = GeoPoint(lat, lng)

        // Create marker on UI thread
        runOnUiThread {
            try {
                val marker = Marker(mapView).apply {
                    position = geoPoint
                    title = name

                    // Set text icon based on type
                    val iconText = when (type) {
                        "ferry_terminal" -> "⛴"  // Ferry emoji
                        "pier" -> "⚓"            // Anchor emoji
                        else -> "📍"              // Default location pin
                    }
                    setTextIcon(iconText)

                    // Set color based on type
                    val color = when (type) {
                        "ferry_terminal" -> Color.parseColor("#2196F3") // Blue
                        "pier" -> Color.parseColor("#4CAF50")          // Green
                        else -> Color.parseColor("#FF9800")            // Orange
                    }
                    setTextLabelForegroundColor(color)

                    // Set background color based on status
                    when (status.lowercase()) {
                        "open" -> setTextLabelBackgroundColor(Color.parseColor("#E8F5E8")) // Light green
                        "closed" -> setTextLabelBackgroundColor(Color.parseColor("#FFEBEE")) // Light red
                        else -> setTextLabelBackgroundColor(Color.TRANSPARENT)
                    }

                    setTextLabelFontSize(14)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                    // Set subtitle with additional info
                    subDescription = "Type: ${type.uppercase()} | Status: $status"

                    // Set OnMarkerClickListener
                    setOnMarkerClickListener { _, _ ->
                        showFirestorePortDetails(name, type, status, source, createdAt, lat, lng)
                        true
                    }
                }

                mapView.overlays.add(marker)
                firestorePortMarkers[document.id] = marker

                Log.d(TAG, "Added marker for: $name at ($lat, $lng)")

            } catch (e: Exception) {
                Log.e(TAG, "Error creating marker for document ${document.id}: ${e.message}", e)
            }
        }

        return true
    }

    private fun showFirestorePortDetails(
        name: String,
        type: String,
        status: String,
        source: String,
        createdAt: Timestamp,
        lat: Double,
        lng: Double
    ) {
        runOnUiThread {
            tvSelectedTitle.text = name

            // Format type for display (ferry_terminal -> Ferry Terminal)
            val displayType = type.split("_")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

            tvSelectedStatus.text = status
            tvSelectedETA.text = displayType
            tvSelectedRoute.text = "Source: $source"

            // Format the date
            val dateString = dateFormat.format(createdAt.toDate())
            tvSelectedDetails.text = "Created: $dateString\n" +
                    "Location: ${String.format("%.6f", lat)}, ${String.format("%.6f", lng)}"

            bottomSheet.visibility = View.VISIBLE

            // Center map on the port
            mapView.controller.animateTo(GeoPoint(lat, lng))
        }
    }

    // Function to clear all Firestore markers
    private fun clearFirestoreMarkers() {
        runOnUiThread {
            // Remove markers from map
            firestorePortMarkers.values.forEach { marker ->
                mapView.overlays.remove(marker)
            }

            // Clear the map
            firestorePortMarkers.clear()
            Log.d(TAG, "Cleared all Firestore markers")
        }
    }

    // Function to reload Firestore data
    fun reloadFirestoreData() {
        Log.d(TAG, "Reloading Firestore data...")
        loadPortsFromFirestore()
    }

    // Add real-time listener for Firestore updates (optional)
    private fun setupFirestoreRealtimeListener() {
        portsCollection.addSnapshotListener { querySnapshot, error ->
            if (error != null) {
                Log.e(TAG, "Firestore real-time listen failed", error)
                return@addSnapshotListener
            }

            if (querySnapshot != null && !querySnapshot.metadata.hasPendingWrites()) {
                Log.d(TAG, "Firestore real-time update received: ${querySnapshot.size()} documents")

                // Clear existing markers
                clearFirestoreMarkers()

                var successCount = 0
                var errorCount = 0

                // Add updated markers
                for (document in querySnapshot.documents) {
                    try {
                        if (addFirestoreMarker(document)) {
                            successCount++
                        } else {
                            errorCount++
                        }
                    } catch (e: Exception) {
                        errorCount++
                        Log.e(TAG, "Error updating document ${document.id}: ${e.message}")
                    }
                }

                // Update the map
                runOnUiThread {
                    mapView.invalidate()
                    if (successCount > 0) {
                        Log.d(TAG, "Real-time update applied: $successCount successful, $errorCount errors")
                    }
                }
            }
        }
    }

    // ==================== ORIGINAL MARKER FUNCTIONS ====================

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

    // ==================== SEARCH & PREFERENCES ====================

    private fun setupSharedPreferences() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        restoreSearchText()
    }

    private fun restoreSearchText() {
        val savedQuery = sharedPreferences.getString(KEY_LAST_SEARCH, "") ?: ""
        if (savedQuery.isNotBlank()) {
            etSearch.setText(savedQuery)
            etSearch.setSelection(savedQuery.length)
        }
    }

    private fun saveSearchText() {
        val query = etSearch.text.toString().trim()
        sharedPreferences.edit().apply {
            putString(KEY_LAST_SEARCH, query)
            apply()
        }
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
            // Reset map rotation
            mapView.mapOrientation = 0f
            mapView.invalidate()
            Toast.makeText(this, "North reset", Toast.LENGTH_SHORT).show()
        }

        // Search functionality
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
            bottomSheet.visibility = View.GONE
            // Clear any selection
            tvSelectedTitle.text = ""
            tvSelectedStatus.text = ""
            tvSelectedETA.text = ""
            tvSelectedRoute.text = ""
            tvSelectedDetails.text = ""
        }

        // Close bottom sheet when clicking map
        mapView.setOnClickListener {
            if (bottomSheet.visibility == View.VISIBLE) {
                bottomSheet.visibility = View.GONE
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
        etSearch.setOnEditorActionListener { v, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = etSearch.text.toString()
                if (query.isNotBlank()) {
                    performSearch(query)
                }
                hideKeyboard()
                return@setOnEditorActionListener true
            }
            false
        }
    }

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

        // Show keyboard
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)

        // Update clear button
        btnClearSearch.visibility = if (etSearch.text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    private fun hideSearchBar() {
        searchCard.visibility = View.GONE
        hideKeyboard()
        saveSearchText()  // Save when hiding
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
    }

    private fun performSearch(query: String) {
        if (query.isNotBlank()) {
            // Search in Firestore markers
            var firestoreFound = false

            firestorePortMarkers.values.forEach { marker ->
                if (marker.title?.contains(query, ignoreCase = true) == true) {
                    // Center map on found marker
                    mapView.controller.animateTo(marker.position)

                    // Try to find the document to show details
                    val documentId = firestorePortMarkers.entries.find { it.value == marker }?.key
                    if (documentId != null) {
                        portsCollection.document(documentId).get()
                            .addOnSuccessListener { document ->
                                if (document.exists()) {
                                    val data = document.data
                                    val name = data?.get("name") as? String ?: ""
                                    val type = (data?.get("type") as? String)?.lowercase() ?: "unknown"
                                    val status = data?.get("status") as? String ?: "Unknown"
                                    val source = data?.get("source") as? String ?: "Unknown"
                                    val createdAt = data?.get("createdAt") as? Timestamp ?: Timestamp.now()
                                    val lat = (data?.get("lat") as? Double) ?: marker.position.latitude
                                    val lng = (data?.get("lng") as? Double) ?: marker.position.longitude

                                    showFirestorePortDetails(name, type, status, source, createdAt, lat, lng)
                                }
                            }
                    }

                    firestoreFound = true
                }
            }

            // Also search in local port data
            val foundPorts = portData.filter { it.name.contains(query, ignoreCase = true) }
            val foundFerries = ferryData.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.route.contains(query, ignoreCase = true)
            }

            if (firestoreFound || foundPorts.isNotEmpty() || foundFerries.isNotEmpty()) {
                val totalFound = (if (firestoreFound) 1 else 0) + foundPorts.size + foundFerries.size
                Toast.makeText(this, "Found $totalFound results", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No results found", Toast.LENGTH_SHORT).show()
            }
        }
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

    // ==================== LIVE UPDATES ====================

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

    // ==================== UTILITY FUNCTIONS ====================

    // Add this function to clear all markers from the map
    private fun clearAllMarkers() {
        // Clear local port markers
        portMarkers.values.forEach { marker ->
            mapView.overlays.remove(marker)
        }
        portMarkers.clear()

        // Clear local ferry markers
        ferryMarkers.values.forEach { marker ->
            mapView.overlays.remove(marker)
        }
        ferryMarkers.clear()

        // Clear Firestore markers
        clearFirestoreMarkers()

        // Invalidate map
        mapView.invalidate()

        Log.d(TAG, "Cleared all markers from map")
    }

    // ==================== LIFECYCLE ====================

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        // Optional: Start real-time listener when activity resumes
        // setupFirestoreRealtimeListener()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        handler.removeCallbacksAndMessages(null)
        saveSearchText()
    }

    override fun onStop() {
        super.onStop()
        saveSearchText()
    }

    // ==================== DATA CLASSES ====================

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