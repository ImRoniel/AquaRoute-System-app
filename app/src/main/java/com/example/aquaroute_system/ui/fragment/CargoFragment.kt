package com.example.aquaroute_system.ui.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.TranslateAnimation
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.aquaroute_system.R
import com.example.aquaroute_system.data.models.Cargo
import com.example.aquaroute_system.data.models.Ferry
import com.example.aquaroute_system.data.repository.CargoRepository
import com.example.aquaroute_system.data.repository.FerryRepository
import com.example.aquaroute_system.databinding.ActivityCargoTrackerBinding
import com.example.aquaroute_system.ui.viewmodel.CargoTrackerViewModel
import com.example.aquaroute_system.ui.viewmodel.CargoTrackerViewModelFactory
import com.example.aquaroute_system.util.SessionManager
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.*

class CargoFragment : Fragment() {

    private var _binding: ActivityCargoTrackerBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: CargoTrackerViewModel
    private lateinit var sessionManager: SessionManager
    private lateinit var cargoRepository: CargoRepository
    private lateinit var ferryRepository: FerryRepository
    
    private var mapInitialized = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ActivityCargoTrackerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        sessionManager = SessionManager(requireContext())
        val firestore = FirebaseFirestore.getInstance()
        cargoRepository = CargoRepository(firestore)
        ferryRepository = FerryRepository(firestore)

        initializeViewModel()
        setupViews()
        setupObservers()
        setupClickListeners()
        
        viewModel.testConnection()
    }

    override fun onResume() {
        super.onResume()
        if (mapInitialized) {
            binding.cargoMiniMapView.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (mapInitialized) {
            binding.cargoMiniMapView.onPause()
        }
    }

    private fun initializeViewModel() {
        val factory = CargoTrackerViewModelFactory(cargoRepository, ferryRepository, sessionManager)
        val vm: CargoTrackerViewModel by viewModels { factory }
        viewModel = vm
    }

    private fun setupViews() {
        binding.btnBack.visibility = View.GONE
        binding.searchInstruction.visibility = View.VISIBLE
        binding.resultCard.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.cargoMapPreviewPanel.visibility = View.GONE

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text.clear()
        }
    }

    private fun setupObservers() {
        viewModel.searchResult.observe(viewLifecycleOwner) { cargo ->
            if (cargo != null) {
                showCargoDetails(cargo)
                updateViewMapButtonState(cargo, viewModel.assignedFerry.value)
                binding.emptyState.visibility = View.GONE
                binding.cargoMapPreviewPanel.visibility = View.GONE
                binding.resultCard.visibility = View.VISIBLE
            } else {
                binding.resultCard.visibility = View.GONE
                binding.cargoMapPreviewPanel.visibility = View.GONE
            }
        }

        viewModel.assignedFerry.observe(viewLifecycleOwner) { ferry ->
            updateViewMapButtonState(viewModel.searchResult.value, ferry)
            if (ferry != null) {
                binding.tvFerryNameValue.text = ferry.name
            } else {
                val rawId = viewModel.searchResult.value?.ferryId
                binding.tvFerryNameValue.text = if (rawId.isNullOrEmpty()) "Not assigned" else "Unknown Ferry"
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.loadingProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnTrack.isEnabled = !isLoading
            
            if (isLoading) {
                binding.cargoMapPreviewPanel.visibility = View.GONE
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
                binding.emptyState.visibility = View.VISIBLE
                binding.resultCard.visibility = View.GONE
                binding.cargoMapPreviewPanel.visibility = View.GONE
            }
        }

        viewModel.notFound.observe(viewLifecycleOwner) { notFound ->
            if (notFound) {
                binding.emptyState.visibility = View.VISIBLE
                binding.resultCard.visibility = View.GONE
                viewModel.setMapExpanded(false)
            }
        }

        viewModel.isMapExpanded.observe(viewLifecycleOwner) { isExpanded ->
            val ferry = viewModel.assignedFerry.value
            val cargo = viewModel.searchResult.value
            if (isExpanded && (ferry != null || (cargo?.current_lat != null && cargo.current_lng != null) || cargo?.currentLocation != null)) {
                showMapPreview(cargo, ferry)
            } else {
                hideMapPreview()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnTrack.setOnClickListener {
            val referenceNumber = binding.etSearch.text.toString().trim()
            if (referenceNumber.isNotEmpty()) {
                viewModel.setMapExpanded(false)
                viewModel.searchCargoByReference(referenceNumber)
            } else {
                binding.etSearch.error = "Enter reference number"
            }
        }

        binding.btnViewOnMap.setOnClickListener {
            val currentlyExpanded = viewModel.isMapExpanded.value ?: false
            viewModel.setMapExpanded(!currentlyExpanded)
        }

        binding.btnCloseCargoMap.setOnClickListener {
            viewModel.setMapExpanded(false)
        }

        binding.btnNewSearch.setOnClickListener {
            binding.etSearch.text.clear()
            binding.resultCard.visibility = View.GONE
            binding.emptyState.visibility = View.GONE
            viewModel.setMapExpanded(false)
            binding.etSearch.requestFocus()
        }
    }

    // --- Inline Map Preview Logic ---

    private fun showMapPreview(cargo: Cargo?, ferry: Ferry?) {
        if (!mapInitialized) {
            val mapView = binding.cargoMiniMapView
            mapView.setTileSource(TileSourceFactory.MAPNIK)
            mapView.setMultiTouchControls(true)
            mapView.controller.setZoom(15.0)
            mapInitialized = true
            mapView.onResume()
        }

        val mapView = binding.cargoMiniMapView
        mapView.overlays.clear()
        
        val pointsToZoom = mutableListOf<GeoPoint>()

        // 1. Determine Ferry/Cargo Current Location
        val currentPoint = when {
            ferry != null && ferry.lat != 0.0 -> GeoPoint(ferry.lat, ferry.lon)
            cargo?.current_lat != null && cargo.current_lng != null -> GeoPoint(cargo.current_lat, cargo.current_lng)
            cargo?.currentLocation != null -> GeoPoint(cargo.currentLocation.latitude, cargo.currentLocation.longitude)
            else -> null
        }

        currentPoint?.let {
            pointsToZoom.add(it)
            val marker = Marker(mapView).apply {
                position = it
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = ferry?.name ?: "Current Location"
                icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_ferry_marker)
            }
            mapView.overlays.add(marker)
        }

        // 2. Add Port Markers (Origin/Destination)
        if (ferry != null) {
            val pointA = ferry.pointA?.toGeoPoint()
            val pointB = ferry.pointB?.toGeoPoint()

            pointA?.let {
                pointsToZoom.add(it)
                val marker = Marker(mapView).apply {
                    position = it
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Origin: ${ferry.getOrigin()}"
                    icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_port_marker)
                }
                mapView.overlays.add(marker)
            }

            pointB?.let {
                pointsToZoom.add(it)
                val marker = Marker(mapView).apply {
                    position = it
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Destination: ${ferry.getDestination()}"
                    icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_port_marker)
                }
                mapView.overlays.add(marker)
            }
        }

        // 4. Auto-Zoom / Centering
        if (pointsToZoom.isNotEmpty()) {
            if (pointsToZoom.size == 1) {
                mapView.controller.animateTo(pointsToZoom[0])
                mapView.controller.setZoom(15.0)
            } else {
                mapView.post {
                    try {
                        val boundingBox = BoundingBox.fromGeoPoints(pointsToZoom)
                        mapView.zoomToBoundingBox(boundingBox.increaseByScale(1.3f), true)
                    } catch (e: Exception) {
                        mapView.controller.animateTo(pointsToZoom[0])
                    }
                }
            }
        }

        mapView.invalidate()
        binding.tvPreviewFerryName.text = ferry?.name ?: "Cargo Tracking"

        val panel = binding.cargoMapPreviewPanel
        if (panel.visibility != View.VISIBLE) {
            panel.visibility = View.VISIBLE
            panel.post {
                val anim = TranslateAnimation(0f, 0f, panel.height.toFloat(), 0f)
                anim.duration = 250
                panel.startAnimation(anim)
            }
        }
    }

    private fun List<Double>?.toGeoPoint(): GeoPoint? {
        return if (this != null && size >= 2) GeoPoint(this[0], this[1]) else null
    }

    private fun updateViewMapButtonState(cargo: Cargo?, ferry: Ferry?) {
        val hasFerryLocation = ferry != null && (ferry.lat != 0.0 || ferry.lon != 0.0)
        val hasCargoLocation = cargo != null && (cargo.current_lat != null || cargo.currentLocation != null)
        
        val isEnabled = hasFerryLocation || hasCargoLocation
        binding.btnViewOnMap.isEnabled = isEnabled
        binding.btnViewOnMap.alpha = if (isEnabled) 1.0f else 0.5f
    }

    private fun hideMapPreview() {
        val panel = binding.cargoMapPreviewPanel
        if (panel.visibility == View.VISIBLE) {
            val anim = TranslateAnimation(0f, 0f, 0f, panel.height.toFloat())
            anim.duration = 200
            anim.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(a: android.view.animation.Animation?) {}
                override fun onAnimationRepeat(a: android.view.animation.Animation?) {}
                override fun onAnimationEnd(a: android.view.animation.Animation?) {
                    panel.visibility = View.GONE
                    if (mapInitialized) {
                         binding.cargoMiniMapView.overlays.clear()
                    }
                }
            })
            panel.startAnimation(anim)
        }
    }

    // --- Details ---

    private fun showCargoDetails(cargo: Cargo) {
        binding.tvTrackingNumberValue.text = cargo.reference
        binding.tvCargoTypeValue.text = cargo.cargoType.ifEmpty { "General Cargo" }
        binding.tvWeightValue.text = String.format(Locale.US, "%.1f kg", cargo.weight)
        
        // Status Display & Vectorized Dot Mapping
        val statusText = cargo.getStatusDisplay()
        binding.tvFerryStatusValue.text = statusText
        
        val (dotRes, colorRes) = when (cargo.status.lowercase()) {
            "in_transit" -> Pair(R.drawable.ic_status_dot_green, R.color.success_green)
            "delivered" -> Pair(R.drawable.ic_status_dot_blue, R.color.deep_ocean_blue)
            "delayed" -> Pair(R.drawable.ic_status_dot_red, R.color.error_red)
            "processing", "pending" -> Pair(R.drawable.ic_status_dot_amber, R.color.warning_amber)
            else -> Pair(R.drawable.ic_status_dot_gray, R.color.medium_text)
        }
        
        binding.ivCargoStatusDot.setImageResource(dotRes)
        binding.tvFerryStatusValue.setTextColor(ContextCompat.getColor(requireContext(), colorRes))

        // Route Information
        binding.tvOriginValue.text = cargo.origin.ifEmpty { "Manila" }
        binding.tvDestinationValue.text = cargo.destination.ifEmpty { "Cebu" }

        // ETA Formatting
        binding.tvEtaValue.text = cargo.eta?.let {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.US)
            dateFormat.format(Date(it))
        } ?: "TBD"

        // Dynamic Progress Visibility
        if (cargo.status.lowercase() == "in_transit" && cargo.progress in 1..99) {
            binding.progressLayout.visibility = View.VISIBLE
            binding.progressBar.progress = cargo.progress
            binding.tvProgressValue.text = "${cargo.progress}%"
        } else {
            binding.progressLayout.visibility = View.GONE
        }
        
        // Hide unused layouts from previous version
        // (Note: These IDs were removed in the layout refactor, so we don't need to touch them here if they're gone)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (mapInitialized) {
            binding.cargoMiniMapView.onDetach()
        }
        _binding = null
    }
}
