package com.example.aquaroute_system.ui.fragment

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import com.example.aquaroute_system.R
import com.example.aquaroute_system.data.models.FirestorePort
import com.example.aquaroute_system.data.models.MarkerDetail
import com.example.aquaroute_system.data.repository.FerryRefreshRepository
import com.example.aquaroute_system.data.repository.FerryRepository
import com.example.aquaroute_system.data.repository.PortRepository
import com.example.aquaroute_system.databinding.ActivityLivemapBinding
import com.example.aquaroute_system.ui.viewmodel.LiveMapViewModel
import com.example.aquaroute_system.ui.viewmodel.LiveMapViewModelFactory
import com.example.aquaroute_system.util.MapHelper
import com.example.aquaroute_system.util.SessionManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.*

class LiveMapFragment : Fragment() {

    private var _binding: ActivityLivemapBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: LiveMapViewModel
    private lateinit var sessionManager: SessionManager

    private val ferryMarkers = mutableMapOf<String, Marker>()
    private val portMarkers = mutableMapOf<String, Marker>()
    private val firestorePortMarkers = mutableMapOf<String, Marker>()
    private val routeLines = mutableListOf<Polyline>()

    private var animationJob: Job? = null
    private var portsVisible = true

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.requestUserLocation(requireActivity())
        } else {
            Toast.makeText(context, "Location permission needed", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Configuration.getInstance().apply {
            userAgentValue = requireActivity().packageName
            load(requireContext(), requireActivity().getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
        }
        _binding = ActivityLivemapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeViewModel()
        setupMap()
        setupEventListeners()
        setupViewModelObservers()
        checkLocationPermission()
        
        viewModel.startLiveUpdates()
        viewModel.refreshFerries()
    }

    private fun initializeViewModel() {
        sessionManager = SessionManager(requireContext())
        val firestore = FirebaseFirestore.getInstance()
        val baseUrl = "https://aquaroute-system-web.onrender.com/"

        val portRepository = PortRepository()
        val ferryRepository = FerryRepository(firestore)
        val ferryRefreshRepository = FerryRefreshRepository(baseUrl)

        val factory = LiveMapViewModelFactory(
            sessionManager,
            portRepository,
            ferryRepository,
            ferryRefreshRepository
        )

        val vm: LiveMapViewModel by viewModels { factory }
        viewModel = vm
    }

    private fun setupMap() {
        try {
            binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
            binding.mapView.setMultiTouchControls(true)
            binding.mapView.minZoomLevel = 5.0
            binding.mapView.maxZoomLevel = 19.0

            val phCenter = GeoPoint(12.8797, 121.7740)
            binding.mapView.controller.setZoom(6.0)
            binding.mapView.controller.setCenter(phCenter)
        } catch (e: Exception) {
            Toast.makeText(context, "Map setup error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun setupViewModelObservers() {
        viewModel.visiblePorts.observe(viewLifecycleOwner) { ports ->
            updateFirestorePortMarkers(ports)
        }

        viewModel.currentHour.observe(viewLifecycleOwner) { _ ->
            viewModel.visiblePorts.value?.let { ports ->
                updateFirestorePortMarkers(ports)
            }
        }

        viewModel.ferries.observe(viewLifecycleOwner) { ferries ->
            if (ferries.isNotEmpty()) {
                updateFerryMarkers(ferries)
                startFerryAnimation()
            }
        }

        viewModel.ports.observe(viewLifecycleOwner) { ports ->
            updatePortMarkers(ports)
        }

        viewModel.selectedMarkerDetail.observe(viewLifecycleOwner) { detail ->
            if (detail != null) {
                showBottomSheetForMarkerDetail(detail)
            } else {
                hideBottomSheet()
            }
        }

        viewModel.lastUpdateTime.observe(viewLifecycleOwner) { time ->
            binding.tvLastUpdate.text = "Last update: $time"
        }

        viewModel.liveIndicatorAlpha.observe(viewLifecycleOwner) { alpha ->
            binding.tvLiveStatus.alpha = alpha
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.userLocation.observe(viewLifecycleOwner) { location ->
            if (location != null) {
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                binding.mapView.controller.animateTo(geoPoint)
                binding.mapView.controller.setZoom(15.0)
                addUserLocationMarker(geoPoint)
                viewModel.loadPortsNearUserOnce(location.latitude, location.longitude)
            }
        }

        viewModel.isLocationLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                Toast.makeText(context, "Getting your location...", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.locationError.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(context, "Location error: $error", Toast.LENGTH_SHORT).show()
                viewModel.clearLocationError()
            }
        }
    }

    private fun updateFerryMarkers(ferries: List<com.example.aquaroute_system.data.models.Ferry>) {
        ferryMarkers.values.forEach { binding.mapView.overlays.remove(it) }
        ferryMarkers.clear()

        ferries.forEach { ferry ->
            val marker = MapHelper.createFerryMarker(requireContext(), binding.mapView, ferry) { selectedFerry ->
                viewModel.onFerryMarkerClick(selectedFerry)
            }
            binding.mapView.overlays.add(marker)
            ferryMarkers[ferry.name] = marker
        }
        binding.mapView.invalidate()
    }

    private fun updatePortMarkers(ports: List<com.example.aquaroute_system.data.models.Port>) {
        portMarkers.values.forEach { binding.mapView.overlays.remove(it) }
        portMarkers.clear()

        ports.forEach { port ->
            val marker = MapHelper.createPortMarker(requireContext(), binding.mapView, port) { selectedPort ->
                viewModel.onPortMarkerClick(selectedPort)
            }
            binding.mapView.overlays.add(marker)
            portMarkers[port.name] = marker
        }
        binding.mapView.invalidate()
    }

    private fun updateFirestorePortMarkers(ports: List<FirestorePort>) {
        firestorePortMarkers.values.forEach { binding.mapView.overlays.remove(it) }
        firestorePortMarkers.clear()

        val currentHour = viewModel.currentHour.value ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        ports.forEach { port ->
            val portWithStatus = viewModel.getPortWithDynamicStatus(port)
            val marker = MapHelper.createFirestorePortMarker(
                context = requireContext(),
                mapView = binding.mapView,
                port = portWithStatus,
                currentHour = currentHour,
                onMarkerClick = { clickedPort ->
                    viewModel.onFirestorePortMarkerClick(clickedPort)
                }
            )
            binding.mapView.overlays.add(marker)
            firestorePortMarkers[port.id] = marker
        }
        binding.mapView.invalidate()
    }

    private fun startFerryAnimation() {
        animationJob?.cancel()
        animationJob = viewModel.viewModelScope.launch {
            while (isActive) {
                updateFerryPositions()
                delay(30_000)
            }
        }
    }

    private fun updateFerryPositions() {
        val ferries = viewModel.ferries.value ?: return
        val now = System.currentTimeMillis()
        ferries.forEach { ferry ->
            if (ferry.startTime == null || ferry.endTime == null || ferry.routePoints == null) return@forEach
            val start = ferry.startTime
            val end = ferry.endTime
            val route = ferry.routePoints
            if (route.size < 2) return@forEach

            val fraction = ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
            val startPoint = route.first()
            val endPoint = route.last()
            val lat = startPoint.latitude + (endPoint.latitude - startPoint.latitude) * fraction
            val lng = startPoint.longitude + (endPoint.longitude - startPoint.longitude) * fraction

            val marker = ferryMarkers[ferry.name]
            marker?.position = GeoPoint(lat, lng)
        }
        binding.mapView.invalidate()
    }

    private fun showBottomSheetForMarkerDetail(detail: MarkerDetail) {
        when (detail) {
            is MarkerDetail.FerryDetail -> showBottomSheetForFerry(detail.ferry)
            is MarkerDetail.PortDetail -> showBottomSheetForPort(detail.port)
            is MarkerDetail.FirestorePortDetail -> showBottomSheetForFirestorePort(detail.port)
        }
    }

    private fun showBottomSheetForFerry(ferry: com.example.aquaroute_system.data.models.Ferry) {
        binding.tvVesselName.text = "🚢 ${ferry.name}"
        binding.tvVesselEta.text = "⏱️ ${ferry.eta} mins"
        binding.tvVesselRoute.text = "Route: ${ferry.route}"
        binding.tvVesselLocation.text = "📍 At sea"

        val statusText = when (ferry.status.lowercase(Locale.getDefault())) {
            "on_time" -> "🟢 ON TIME"
            "delayed" -> "🟡 DELAYED"
            "cancelled" -> "🔴 CANCELLED"
            else -> "⚪ UNKNOWN"
        }
        binding.tvVesselStatus.text = statusText
        binding.tvVesselSpeed.text = "⚡ ${ferry.speed_knots} knots"

        binding.bottomSheet.visibility = View.VISIBLE
        binding.mapView.controller.animateTo(GeoPoint(ferry.lat, ferry.lon))
    }

    private fun showBottomSheetForPort(port: com.example.aquaroute_system.data.models.Port) {
        binding.tvVesselName.text = "🏢 ${port.name}"
        binding.tvVesselEta.text = "⏱️ --"
        binding.tvVesselRoute.text = "Port"
        binding.tvVesselLocation.text = "📍 Port Area"
        binding.tvVesselStatus.text = if (port.isPrimary) "MAIN TERMINAL" else "TERMINAL"
        binding.tvVesselSpeed.text = ""

        binding.bottomSheet.visibility = View.VISIBLE
        binding.mapView.controller.animateTo(GeoPoint(port.lat, port.lon))
    }

    private fun showBottomSheetForFirestorePort(port: com.example.aquaroute_system.data.models.FirestorePort) {
        binding.tvVesselName.text = "🏢 ${port.name}"
        binding.tvVesselEta.text = "⏱️ --"
        binding.tvVesselRoute.text = "Port"
        binding.tvVesselLocation.text = "📍 Port Area"
        binding.tvVesselStatus.text = port.status
        binding.tvVesselSpeed.text = ""

        binding.bottomSheet.visibility = View.VISIBLE
        binding.mapView.controller.animateTo(GeoPoint(port.lat, port.lng))
    }

    private fun hideBottomSheet() {
        binding.bottomSheet.visibility = View.GONE
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                viewModel.requestUserLocation(requireActivity())
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun addUserLocationMarker(geoPoint: GeoPoint) {
        val oldMarkers = binding.mapView.overlays.filter {
            it is Marker && it.title == "You are here"
        }
        binding.mapView.overlays.removeAll(oldMarkers)

        val marker = Marker(binding.mapView).apply {
            position = geoPoint
            title = "You are here"
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_my_location)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        }
        binding.mapView.overlays.add(marker)
        binding.mapView.invalidate()
    }

    private fun setupEventListeners() {
        binding.btnBack.visibility = View.GONE // Handled by bottom nav
        
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        binding.btnZoomIn.setOnClickListener {
            binding.mapView.controller.zoomIn()
        }

        binding.btnZoomOut.setOnClickListener {
            binding.mapView.controller.zoomOut()
        }

        binding.btnMyLocation.setOnClickListener {
            viewModel.userLocation.value?.let { location ->
                binding.mapView.controller.animateTo(GeoPoint(location.latitude, location.longitude))
                binding.mapView.controller.setZoom(15.0)
            } ?: checkLocationPermission()
        }

        binding.btnSatellite.setOnClickListener {
            toggleMapLayer()
        }

        binding.btnCloseSheet.setOnClickListener {
            viewModel.clearSelectedMarkerDetail()
        }

        binding.btnTogglePorts.setOnClickListener {
            portsVisible = !portsVisible
            if (portsVisible) {
                viewModel.visiblePorts.value?.let { ports -> updateFirestorePortMarkers(ports) }
                binding.btnTogglePorts.setColorFilter(Color.WHITE)
            } else {
                firestorePortMarkers.values.forEach { binding.mapView.overlays.remove(it) }
                firestorePortMarkers.clear()
                binding.mapView.invalidate()
                binding.btnTogglePorts.setColorFilter(Color.GRAY)
            }
        }
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Settings")
            .setItems(arrayOf("Reload Ferries", "Refresh Ports")) { _, which ->
                when (which) {
                    0 -> viewModel.refreshFerries()
                    1 -> {
                        viewModel.resetPortsLoaded()
                        viewModel.userLocation.value?.let { loc ->
                            viewModel.loadPortsNearUserOnce(loc.latitude, loc.longitude)
                        }
                    }
                }
            }
            .show()
    }

    private fun toggleMapLayer() {
        if (binding.mapView.tileProvider.tileSource == TileSourceFactory.MAPNIK) {
            binding.mapView.setTileSource(TileSourceFactory.HIKEBIKEMAP)
            Toast.makeText(context, "Terrain view", Toast.LENGTH_SHORT).show()
        } else {
            binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
            Toast.makeText(context, "Map view", Toast.LENGTH_SHORT).show()
        }
        binding.mapView.invalidate()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        viewModel.stopLiveUpdates()
        animationJob?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.stopLiveUpdates()
        animationJob?.cancel()
        _binding = null
    }
}
