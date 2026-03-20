package com.example.aquaroute_system.ui.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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

class WeatherFragment : Fragment() {

    private var _binding: ActivityWeatherAdvisoriesBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: WeatherAdvisoriesViewModel
    private lateinit var adapter: WeatherAdvisoryAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val unitOptions = listOf("km", "mi")

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkLocationThenObserve()
        } else {
            showEmptyState("Enable location to see nearby weather conditions.")
            disableRadiusInput()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ActivityWeatherAdvisoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        setupToolbar()
        setupViewModel()
        setupRadiusSelector()
        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()
        
        checkLocationPermission()
    }

    private fun setupToolbar() {
        binding.toolbar.visibility = View.GONE // Handled by activity
    }

    private fun setupViewModel() {
        val firestore = FirebaseFirestore.getInstance()
        val weatherRepository = WeatherRepository(firestore)
        val portRepository = PortRepository()
        val factory = WeatherAdvisoriesViewModelFactory(weatherRepository, portRepository)
        val vm: WeatherAdvisoriesViewModel by viewModels { factory }
        viewModel = vm
    }

    private fun setupRadiusSelector() {
        val unitAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, unitOptions)
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerUnits.adapter = unitAdapter
        binding.spinnerUnits.setSelection(0)
        binding.etRadius.setText("1")

        binding.btnReloadFilter.setOnClickListener {
            val radiusStr = binding.etRadius.text.toString()
            if (radiusStr.isBlank()) {
                Toast.makeText(context, "Please enter a radius value", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val radiusValue = radiusStr.toDoubleOrNull()
            if (radiusValue == null || radiusValue <= 0) {
                Toast.makeText(context, "Please enter a valid positive number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val unit = binding.spinnerUnits.selectedItem.toString()
            viewModel.applyFilter(radiusValue, unit)
        }
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
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                checkLocationThenObserve()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkLocationThenObserve() {
        binding.progressBar.visibility = View.VISIBLE
        binding.llEmptyError.visibility = View.GONE
        
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    viewModel.setUserLocation(location)
                    enableRadiusInput()
                } else {
                    binding.progressBar.visibility = View.GONE
                    showEmptyState("Could not determine your location. Please ensure GPS is enabled.")
                    disableRadiusInput()
                }
            }
            .addOnFailureListener {
                binding.progressBar.visibility = View.GONE
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
        viewModel.weatherState.observe(viewLifecycleOwner) { result ->
            when (result) {
                is Result.Loading -> {
                    if (!binding.swipeRefreshLayout.isRefreshing) {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    binding.llEmptyError.visibility = View.GONE
                }
                is Result.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefreshLayout.isRefreshing = false
                    
                    if (result.data.isEmpty()) {
                        val radiusInfo = viewModel.getDisplayRadiusInfo()
                        showEmptyState("No weather advisories found within $radiusInfo.")
                    } else {
                        binding.llEmptyError.visibility = View.GONE
                        adapter.submitList(result.data)
                    }
                }
                is Result.Error -> {
                    binding.progressBar.visibility = View.GONE
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
        binding.llEmptyError.visibility = View.VISIBLE
        binding.tvEmptyError.text = message
        binding.btnRetry.visibility = View.VISIBLE
        adapter.submitList(emptyList())
    }

    private fun showErrorState(message: String) {
        binding.llEmptyError.visibility = View.VISIBLE
        binding.tvEmptyError.text = "Error: $message"
        binding.btnRetry.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
