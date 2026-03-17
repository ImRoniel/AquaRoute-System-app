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

    private val unitOptions = listOf("km", "mi")

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            checkLocationThenObserve()
        } else {
            showEmptyState("Enable location to see nearby weather conditions.")
            disableRadiusInput()
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
        // Units Spinner
        val unitAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, unitOptions)
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerUnits.adapter = unitAdapter
        binding.spinnerUnits.setSelection(0) // Default "km"

        // Default value
        binding.etRadius.setText("50")

        binding.btnReloadFilter.setOnClickListener {
            val radiusStr = binding.etRadius.text.toString()
            if (radiusStr.isBlank()) {
                Toast.makeText(this, "Please enter a radius value", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val radiusValue = radiusStr.toDoubleOrNull()
            if (radiusValue == null || radiusValue <= 0) {
                Toast.makeText(this, "Please enter a valid positive number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val unit = binding.spinnerUnits.selectedItem.toString()
            viewModel.applyFilter(radiusValue, unit)
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
        binding.llEmptyError.visibility = android.view.View.GONE
        
        // Use high accuracy for better reliability as requested
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    viewModel.setUserLocation(location)
                    enableRadiusInput()
                } else {
                    binding.progressBar.visibility = android.view.View.GONE
                    showEmptyState("Could not determine your location. Please ensure GPS is enabled and try again.")
                    disableRadiusInput()
                }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = android.view.View.GONE
                showErrorState("Error getting location: ${it.message}")
            }
    }

    private fun enableRadiusInput() {
        binding.cvRadiusSelector.alpha = 1.0f
        binding.etRadius.isEnabled = true
        binding.spinnerUnits.isEnabled = true
        binding.btnReloadFilter.isEnabled = true
    }

    private fun disableRadiusInput() {
        binding.cvRadiusSelector.alpha = 0.5f
        binding.etRadius.isEnabled = false
        binding.spinnerUnits.isEnabled = false
        binding.btnReloadFilter.isEnabled = false
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
                        val radiusInfo = viewModel.getDisplayRadiusInfo()
                        
                        if (viewModel.userLocation.value == null) {
                            showEmptyState("Location needed to filter nearby weather.")
                        } else {
                            showEmptyState("No weather advisories found within $radiusInfo of your location.")
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
