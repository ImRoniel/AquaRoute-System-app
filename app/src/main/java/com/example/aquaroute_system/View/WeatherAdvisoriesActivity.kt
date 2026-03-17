package com.example.aquaroute_system.View

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.aquaroute_system.R
import com.example.aquaroute_system.data.models.Result
import com.example.aquaroute_system.data.repository.PortRepository
import com.example.aquaroute_system.data.repository.WeatherRepository
import com.example.aquaroute_system.databinding.ActivityWeatherAdvisoriesBinding
import com.example.aquaroute_system.ui.adapter.WeatherAdvisoryAdapter
import com.example.aquaroute_system.ui.viewmodel.WeatherAdvisoriesViewModel
import com.example.aquaroute_system.ui.viewmodel.WeatherAdvisoriesViewModelFactory
import com.example.aquaroute_system.util.SessionManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.FirebaseFirestore

class WeatherAdvisoriesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWeatherAdvisoriesBinding
    private lateinit var viewModel: WeatherAdvisoriesViewModel
    private lateinit var adapter: WeatherAdvisoryAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val radiusOptions = listOf("10 km", "25 km", "50 km", "100 km", "All")
    private val radiusValues = listOf(10, 25, 50, 100, 5000)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            checkLocationThenObserve()
        } else {
            showEmptyState("Enable location to see nearby weather conditions.")
            binding.cvRadiusSelector.alpha = 0.5f
            binding.spinnerRadius.isEnabled = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeatherAdvisoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupToolbar()
        setupViewModel()
        setupRadiusSelector()
        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()
        
        checkLocationPermission()
    }

    private fun setupRadiusSelector() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, radiusOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRadius.adapter = adapter

        // Set default selection to 50 km (index 2)
        binding.spinnerRadius.setSelection(2)

        binding.spinnerRadius.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val radius = radiusValues[position]
                viewModel.setSelectedRadius(radius)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupViewModel() {
        val firestore = FirebaseFirestore.getInstance()
        val weatherRepository = WeatherRepository(firestore)
        val portRepository = PortRepository()
        val factory = WeatherAdvisoriesViewModelFactory(weatherRepository, portRepository)
        viewModel = ViewModelProvider(this, factory).get(WeatherAdvisoriesViewModel::class.java)
    }

    private fun setupRecyclerView() {
        adapter = WeatherAdvisoryAdapter()
        binding.rvWeatherAdvisories.adapter = adapter
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            checkLocationThenObserve()
        }
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                checkLocationThenObserve()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkLocationThenObserve() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    viewModel.setUserLocation(location)
                    binding.cvRadiusSelector.alpha = 1.0f
                    binding.spinnerRadius.isEnabled = true
                } else {
                    binding.progressBar.visibility = android.view.View.GONE
                    showEmptyState("Could not determine your location. Please ensure GPS is enabled and try again.")
                    binding.cvRadiusSelector.alpha = 0.5f
                    binding.spinnerRadius.isEnabled = false
                }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = android.view.View.GONE
                showErrorState("Error getting location: ${it.message}")
            }
    }

    private fun observeViewModel() {
        viewModel.weatherState.observe(this) { result ->
            when (result) {
                is Result.Loading -> {
                    if (!binding.swipeRefreshLayout.isRefreshing) {
                        binding.progressBar.visibility = android.view.View.VISIBLE
                    }
                    binding.llEmptyError.visibility = android.view.View.GONE
                }
                is Result.Success -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                    
                    if (result.data.isEmpty()) {
                        val radiusVal = viewModel.selectedRadius.value ?: 50
                        val radiusText = if (radiusVal >= 5000) "any distance" else "within $radiusVal km"
                        
                        if (viewModel.userLocation.value == null) {
                            showEmptyState("Location needed to filter nearby weather.")
                        } else {
                            showEmptyState("No weather advisories found $radiusText of your location.")
                        }
                    } else {
                        binding.llEmptyError.visibility = android.view.View.GONE
                        adapter.submitList(result.data)
                    }
                }
                is Result.Error -> {
                    binding.progressBar.visibility = android.view.View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                    showErrorState(result.exception.message ?: "An unknown error occurred")
                }
            }
        }

        binding.btnRetry.setOnClickListener {
            checkLocationThenObserve()
        }
    }

    private fun showEmptyState(message: String) {
        binding.llEmptyError.visibility = android.view.View.VISIBLE
        binding.tvEmptyError.text = message
        binding.btnRetry.visibility = android.view.View.VISIBLE
        binding.btnRetry.text = "Retry / Refresh Location"
        adapter.submitList(emptyList())
    }

    private fun showErrorState(message: String) {
        binding.llEmptyError.visibility = android.view.View.VISIBLE
        binding.tvEmptyError.text = "Error: $message"
        binding.btnRetry.visibility = android.view.View.VISIBLE
        binding.btnRetry.text = "Retry"
    }
}
