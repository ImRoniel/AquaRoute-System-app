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
        val factory = CargoTrackerViewModelFactory(cargoRepository, com.example.aquaroute_system.data.repository.FerryRepository(com.google.firebase.firestore.FirebaseFirestore.getInstance()), sessionManager)
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

        // Description
        binding.tvDescriptionValue.text = cargo.description.ifEmpty { "No description provided" }

        // Weight
        binding.tvWeightValue.text = String.format(Locale.US, "%.1f kg", cargo.weight)

        // Status
        binding.tvFerryStatusValue.text = cargo.getStatusDisplay()

        // Set status color
        val statusColor = when (cargo.status.lowercase()) {
            "in_transit" -> getColor(R.color.success_green)
            "delivered" -> getColor(R.color.deep_ocean_blue)
            "delayed" -> getColor(R.color.error_red)
            "processing", "pending" -> getColor(R.color.warning_orange)
            else -> getColor(R.color.medium_text)
        }
        binding.tvFerryStatusValue.setTextColor(statusColor)

        // Ferry information
        binding.tvFerryNameValue.text = cargo.ferryId?.let {
            "Ferry ID: $it"
        } ?: "Not assigned"

        // Origin & Destination (if available)
        binding.tvOriginValue.text = cargo.origin.ifEmpty {
            when (cargo.ferryId) {
                "D893D5IGv3JW1m8ilZer" -> "Manila"
                else -> "Unknown"
            }
        }

        binding.tvDestinationValue.text = cargo.destination.ifEmpty {
            when (cargo.ferryId) {
                "D893D5IGv3JW1m8ilZer" -> "Cebu"
                else -> "Unknown"
            }
        }

        // Cargo Type (use description as fallback)
        binding.tvCargoTypeValue.text = cargo.cargoType.ifEmpty {
            cargo.description.take(20) + if (cargo.description.length > 20) "..." else ""
        }

        // ETA (if available)
        binding.tvEtaValue.text = cargo.eta?.let {
            val etaDate = Date(it)
            val dateFormat = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.US)
            dateFormat.format(etaDate)
        } ?: "TBD"

        // Progress (hide by default)
        binding.progressLayout.visibility = View.GONE

        // Delay reason (hide by default)
        binding.delayReasonLayout.visibility = View.GONE

        // Recipient (hide by default)
        binding.recipientLayout.visibility = View.GONE
    }
}