package com.example.aquaroute_system.View

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private lateinit var recentSearchesAdapter: RecentSearchesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCargoTrackerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeDependencies()
        setupViews()
        setupObservers()
        loadRecentSearches()
    }

    private fun initializeDependencies() {
        sessionManager = SessionManager(this)
        cargoRepository = CargoRepository(FirebaseFirestore.getInstance())

        val factory = CargoTrackerViewModelFactory(cargoRepository, sessionManager)
        viewModel = ViewModelProvider(this, factory)[CargoTrackerViewModel::class.java]
    }

    private fun setupViews() {
        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Track button
        binding.btnTrack.setOnClickListener {
            val referenceNumber = binding.etReferenceNumber.text.toString().trim()
            if (referenceNumber.isNotEmpty()) {
                viewModel.trackCargo(referenceNumber)
                saveRecentSearch(referenceNumber)
            } else {
                binding.etReferenceNumber.error = "Enter reference number"
            }
        }

        // Clear button visibility based on text
        binding.etReferenceNumber.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.btnClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Search on enter
        binding.etReferenceNumber.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                binding.btnTrack.performClick()
                true
            } else {
                false
            }
        }

        // Clear button
        binding.btnClear.setOnClickListener {
            binding.etReferenceNumber.text.clear()
        }

        // Setup recent searches RecyclerView
        recentSearchesAdapter = RecentSearchesAdapter { referenceNumber ->
            binding.etReferenceNumber.setText(referenceNumber)
            viewModel.trackCargo(referenceNumber)
        }

        binding.rvRecentSearches.apply {
            layoutManager = LinearLayoutManager(this@CargoTrackerActivity)
            adapter = recentSearchesAdapter
        }
    }

    private fun setupObservers() {
        viewModel.cargoDetails.observe(this) { cargo ->
            if (cargo != null) {
                showCargoDetails(cargo)
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
            }
        }

        viewModel.recentSearches.observe(this) { searches ->
            recentSearchesAdapter.submitList(searches)
            binding.tvRecentSearches.visibility = if (searches.isEmpty()) View.GONE else View.VISIBLE
            binding.rvRecentSearches.visibility = if (searches.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun showCargoDetails(cargo: Cargo) {
        binding.cargoDetailsCard.visibility = View.VISIBLE

        // Cargo Information
        binding.tvCargoTypeValue.text = cargo.cargoType
        binding.tvWeightValue.text = "${cargo.weight} kg"
        binding.tvVolumeValue.text = "${cargo.volume} m³"
        binding.tvValueValue.text = "₱${cargo.value?.toString() ?: "N/A"}"

        // Vessel Information
        binding.tvFerryValue.text = cargo.assignedFerryName ?: "Not assigned"
        binding.tvRouteValue.text = "${cargo.origin} → ${cargo.destination}"

        val dateFormat = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())
        binding.tvDepartureValue.text = cargo.departureTime?.let { dateFormat.format(Date(it)) } ?: "TBD"
        binding.tvEtaValue.text = cargo.eta?.let { dateFormat.format(Date(it)) } ?: "TBD"

        // Current Location
        binding.tvPositionValue.text = cargo.currentLocation?.let {
            String.format("%.1f°N, %.1f°E", it.latitude, it.longitude)
        } ?: "Unknown"

        binding.tvStatusValue.text = cargo.status.replace("_", " ").uppercase()
        binding.tvStatusValue.setTextColor(
            when (cargo.status) {
                "in_transit" -> getColor(android.R.color.holo_green_dark)
                "delivered" -> getColor(android.R.color.holo_blue_dark)
                "delayed" -> getColor(android.R.color.holo_orange_dark)
                else -> getColor(android.R.color.darker_gray)
            }
        )

        binding.tvProgressValue.text = "${cargo.progress}%"
        binding.cargoProgressBar.progress = cargo.progress
        binding.tvLastUpdateValue.text = "Last update: ${getTimeAgo(cargo.updatedAt)}"

        // Button listeners
        binding.btnViewOnMap.setOnClickListener {
            val intent = Intent(this, LiveMapView::class.java)
            intent.putExtra("CARGO_ID", cargo.id)
            intent.putExtra("FOCUS_ON_CARGO", true)
            startActivity(intent)
        }

        binding.btnFullDetails.setOnClickListener {
            val intent = Intent(this, CargoDetailsActivity::class.java)
            intent.putExtra("CARGO_ID", cargo.id)
            startActivity(intent)
        }

        binding.btnNotifyMe.setOnClickListener {
            Toast.makeText(this, "Notifications enabled for ${cargo.trackingNumber}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveRecentSearch(referenceNumber: String) {
        viewModel.saveRecentSearch(referenceNumber)
    }

    private fun loadRecentSearches() {
        viewModel.loadRecentSearches()
    }

    private fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000} minutes ago"
            diff < 86400000 -> "${diff / 3600000} hours ago"
            else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
        }
    }
}

// Adapter for recent searches
class RecentSearchesAdapter(
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<RecentSearchesAdapter.ViewHolder>() {

    private var searches = listOf<String>()

    fun submitList(list: List<String>) {
        searches = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val search = searches[position]
        holder.bind(search)
    }

    override fun getItemCount() = searches.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text1 = itemView.findViewById<TextView>(android.R.id.text1)
        private val text2 = itemView.findViewById<TextView>(android.R.id.text2)

        init {
            itemView.setOnClickListener {
                onItemClick(searches[adapterPosition])
            }
        }

        fun bind(search: String) {
            text1.text = search
            text2.text = "Tap to track"
        }
    }
}