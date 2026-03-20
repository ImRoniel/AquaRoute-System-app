package com.example.aquaroute_system.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.TranslateAnimation
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aquaroute_system.data.models.Ferry
import com.example.aquaroute_system.data.models.Result
import com.example.aquaroute_system.data.repository.FerryRepository
import com.example.aquaroute_system.data.repository.WeatherRefreshRepository
import com.example.aquaroute_system.data.repository.WeatherRepository
import com.example.aquaroute_system.databinding.FragmentFerriesBinding
import com.example.aquaroute_system.ui.adapter.FerryAdapter
import com.example.aquaroute_system.ui.viewmodel.FerriesViewModel
import com.example.aquaroute_system.ui.viewmodel.FerriesViewModelFactory
import com.google.firebase.firestore.FirebaseFirestore
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker

class FerriesFragment : Fragment() {

    private var _binding: FragmentFerriesBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: FerriesViewModel
    private lateinit var adapter: FerryAdapter

    private var mapInitialized = false
    private var selectedFerryId: String? = null

    // Production backend URL (Render)
    private val BACKEND_BASE_URL = "https://aquaroute-system-web.onrender.com/"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFerriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViewModel()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun initializeViewModel() {
        val firestore = FirebaseFirestore.getInstance()
        val ferryRepository = FerryRepository(firestore)
        val weatherRepository = WeatherRepository(firestore)
        val weatherRefreshRepository = WeatherRefreshRepository(BACKEND_BASE_URL)

        val factory = FerriesViewModelFactory(ferryRepository, weatherRepository, weatherRefreshRepository)
        val vm: FerriesViewModel by viewModels { factory }
        viewModel = vm
    }

    private fun setupRecyclerView() {
        adapter = FerryAdapter { ferry -> onFerryTapped(ferry) }
        binding.rvFerries.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFerries.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnCloseFerryPreview.setOnClickListener { hideFerryPreview() }
    }

    // ─── Ferry tap handler ───────────────────────────────────────────

    private fun onFerryTapped(ferry: Ferry) {
        if (selectedFerryId == ferry.id) {
            hideFerryPreview()
        } else {
            showFerryPreview(ferry)
        }
    }

    private fun showFerryPreview(ferry: Ferry) {
        selectedFerryId = ferry.id

        // Initialize OSM map on first use
        if (!mapInitialized) {
            binding.ferryMiniMapView.setTileSource(TileSourceFactory.MAPNIK)
            binding.ferryMiniMapView.setMultiTouchControls(true)
            binding.ferryMiniMapView.controller.setZoom(13.0)
            mapInitialized = true
            binding.ferryMiniMapView.onResume()
        }

        // Centre map on ferry position
        val point = GeoPoint(ferry.lat, ferry.lon)
        binding.ferryMiniMapView.controller.animateTo(point)
        binding.ferryMiniMapView.controller.setZoom(13.0)

        // Drop ferry marker
        binding.ferryMiniMapView.overlays.clear()
        val marker = Marker(binding.ferryMiniMapView).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = ferry.name
        }
        binding.ferryMiniMapView.overlays.add(marker)
        binding.ferryMiniMapView.invalidate()

        // Populate header
        binding.tvPreviewFerryName.text = ferry.name
        binding.tvPreviewFerryRoute.text = ferry.route

        // Reset weather section to loading state
        resetWeatherUI()

        // Derive destination port ID: use the last segment of the route as the portId key
        // e.g. route "Bataan - Manila" → destinationPortId used for weather lookup
        val destinationPortId = ferry.getDestination().trim()
        Log.d("FerriesFragment", "Ferry tapped: ${ferry.name}, destinationPortId=$destinationPortId")
        viewModel.fetchWeatherForDestination(destinationPortId)

        // Slide up the panel
        val panel = binding.ferryPreviewPanel
        if (panel.visibility != View.VISIBLE) {
            panel.visibility = View.VISIBLE
            panel.post {
                val anim = TranslateAnimation(0f, 0f, panel.height.toFloat(), 0f)
                anim.duration = 250
                panel.startAnimation(anim)
            }
        }
    }

    private fun hideFerryPreview() {
        selectedFerryId = null
        val panel = binding.ferryPreviewPanel
        if (panel.visibility == View.VISIBLE) {
            val anim = TranslateAnimation(0f, 0f, 0f, panel.height.toFloat())
            anim.duration = 200
            anim.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(a: android.view.animation.Animation?) {}
                override fun onAnimationRepeat(a: android.view.animation.Animation?) {}
                override fun onAnimationEnd(a: android.view.animation.Animation?) {
                    panel.visibility = View.GONE
                    binding.ferryMiniMapView.overlays.clear()
                }
            })
            panel.startAnimation(anim)
        }
    }

    private fun resetWeatherUI() {
        binding.tvDestWeatherIcon.text = "☀️"
        binding.tvDestWeatherTemp.text = "--°C"
        binding.tvDestWeatherDesc.text = "Loading..."
        binding.tvDestWeatherWind.text = "💨 -- m/s"
        binding.tvDestWeatherWaves.text = "🌊 --"
        binding.destWeatherAdvisoryBanner.visibility = View.GONE
        binding.tvDestWeatherNoData.visibility = View.GONE
    }

    // ─── Observers ───────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.ferries.observe(viewLifecycleOwner) { ferries ->
            adapter.submitList(ferries)
            val count = ferries.size
            binding.tvFerryCount.text = if (count == 1) "1 active ferry" else "$count active ferries"
            binding.emptyState.visibility = if (ferries.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.destinationWeather.observe(viewLifecycleOwner) { result ->
            Log.d("WeatherTrace", "[FerriesFragment] destinationWeather: $result")
            when (result) {
                is Result.Loading -> {
                    binding.tvDestWeatherDesc.text = "Loading..."
                    binding.tvDestWeatherNoData.visibility = View.GONE
                }
                is Result.Success -> {
                    val w = result.data
                    Log.d("WeatherTrace", "[FerriesFragment] SUCCESS temp=${w.temperature} cond=${w.condition}")
                    binding.tvDestWeatherNoData.visibility = View.GONE
                    binding.tvDestWeatherIcon.text = w.icon
                    binding.tvDestWeatherTemp.text = "${w.temperature.toInt()}°C"
                    binding.tvDestWeatherDesc.text = w.condition.uppercase()
                    binding.tvDestWeatherWind.text = "💨 ${w.windSpeed}"
                    binding.tvDestWeatherWaves.text = "🌊 ${w.waves}"
                    if (w.hasAdvisory && w.advisoryMessage != null) {
                        binding.destWeatherAdvisoryBanner.visibility = View.VISIBLE
                        binding.tvDestAdvisoryMessage.text = w.advisoryMessage
                    } else {
                        binding.destWeatherAdvisoryBanner.visibility = View.GONE
                    }
                }
                is Result.Error -> {
                    Log.e("WeatherTrace", "[FerriesFragment] ERROR ${result.exception.message}")
                    binding.tvDestWeatherIcon.text = "❓"
                    binding.tvDestWeatherTemp.text = "N/A"
                    binding.tvDestWeatherDesc.text = ""
                    binding.tvDestWeatherWind.text = ""
                    binding.tvDestWeatherWaves.text = ""
                    binding.destWeatherAdvisoryBanner.visibility = View.GONE
                    binding.tvDestWeatherNoData.visibility = View.VISIBLE
                }
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrBlank()) {
                Log.e("FerriesFragment", "Error: $msg")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (mapInitialized) binding.ferryMiniMapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (mapInitialized) binding.ferryMiniMapView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
