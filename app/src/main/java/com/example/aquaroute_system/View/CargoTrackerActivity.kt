package com.example.aquaroute_system.View

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.aquaroute_system.R
import com.example.aquaroute_system.data.models.Cargo
import com.example.aquaroute_system.data.repository.CargoRepository
import com.example.aquaroute_system.data.repository.FerryRepository
import com.example.aquaroute_system.data.repository.WeatherRefreshRepository
import com.example.aquaroute_system.data.repository.WeatherRepository
import com.example.aquaroute_system.databinding.ActivityCargoTrackerBinding
import com.example.aquaroute_system.ui.viewmodel.CargoTrackerViewModel
import com.example.aquaroute_system.ui.viewmodel.CargoTrackerViewModelFactory
import com.example.aquaroute_system.util.SessionManager
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class CargoTrackerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCargoTrackerBinding
    private lateinit var viewModel: CargoTrackerViewModel
    private lateinit var sessionManager: SessionManager
    private lateinit var cargoRepository: CargoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCargoTrackerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        cargoRepository = CargoRepository(FirebaseFirestore.getInstance())

        initializeViewModel()

        // Test Firebase connection
        viewModel.testConnection()

        setupViews()
        setupObservers()
        setupClickListeners()
    }

    private fun initializeViewModel() {
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val ferryRepository = FerryRepository(firestore)
        val weatherRepository = WeatherRepository(firestore)
        val weatherRefreshRepository = WeatherRefreshRepository("https://aquaroute-system-web.onrender.com/")
        val factory = CargoTrackerViewModelFactory(
            cargoRepository, ferryRepository, sessionManager,
            weatherRepository, weatherRefreshRepository
        )
        viewModel = ViewModelProvider(this, factory)[CargoTrackerViewModel::class.java]
    }

    private fun setupViews() {
        // Show search-focused UI
        binding.searchInstruction.visibility = View.VISIBLE
        binding.resultCard.visibility = View.GONE
        binding.emptyState.visibility = View.GONE

        // Setup search text changes
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
        viewModel.searchResult.observe(this) { cargo ->
            if (cargo != null) {
                // Cargo found - show details
                showCargoDetails(cargo)
                binding.emptyState.visibility = View.GONE
                binding.resultCard.visibility = View.VISIBLE
                Log.d("CARGO_TRACKER", "Cargo found: ${cargo.reference}")
            } else {
                // No result or search not performed
                binding.resultCard.visibility = View.GONE
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.loadingProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnTrack.isEnabled = !isLoading
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
                binding.emptyState.visibility = View.VISIBLE
                binding.resultCard.visibility = View.GONE
                Log.e("CARGO_TRACKER", "Error: $it")
            }
        }

        viewModel.notFound.observe(this) { notFound ->
            if (notFound) {
                binding.emptyState.visibility = View.VISIBLE
                binding.resultCard.visibility = View.GONE
                Log.d("CARGO_TRACKER", "Cargo not found")
            }
        }

        viewModel.assignedFerry.observe(this) { ferry ->
            if (ferry != null) {
                binding.tvFerryNameValue.text = ferry.name
                
                // Add ETA mapping
                if (ferry.eta > 0) {
                    binding.tvEtaValue.text = "${ferry.eta} min${if (ferry.eta != 1) "s" else ""}"
                }
            } else {
                val rawId = viewModel.searchResult.value?.ferryId
                binding.tvFerryNameValue.text = if (rawId.isNullOrEmpty()) "Not assigned" else "Unknown Ferry"
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnTrack.setOnClickListener {
            val referenceNumber = binding.etSearch.text.toString().trim()
            if (referenceNumber.isNotEmpty()) {
                Log.d("CARGO_TRACKER", "Searching for: $referenceNumber")
                viewModel.searchCargoByReference(referenceNumber)
            } else {
                binding.etSearch.error = "Enter reference number"
            }
        }

        binding.btnViewOnMap.setOnClickListener {
            viewModel.searchResult.value?.let { cargo ->
                val intent = Intent(this, LiveMapView::class.java)
                intent.putExtra("FOCUS_ON_FERRY_ID", cargo.ferryId)
                intent.putExtra("CARGO_ID", cargo.id)
                startActivity(intent)
            }
        }

        // Removed btnFullDetails reference since it's deleted from layout

        binding.btnNewSearch.setOnClickListener {
            // Clear search and show search box
            binding.etSearch.text.clear()
            binding.resultCard.visibility = View.GONE
            binding.emptyState.visibility = View.GONE
            binding.etSearch.requestFocus()
        }
    }

    private fun showCargoDetails(cargo: Cargo) {
        // Display the reference number
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
        binding.tvFerryStatusValue.setTextColor(getColor(colorRes))

        // Route Information
        binding.tvOriginValue.text = cargo.origin.ifEmpty { "Manila" }
        binding.tvDestinationValue.text = cargo.destination.ifEmpty { "Cebu" }

        // Vessel Info
        binding.tvFerryNameValue.text = cargo.ferryId ?: "Not assigned"

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
    }
}