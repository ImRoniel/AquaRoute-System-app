package com.example.aquaroute_system.View

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aquaroute_system.data.repository.CargoRepository
import com.example.aquaroute_system.databinding.ActivityVoyageHistoryBinding
import com.example.aquaroute_system.ui.adapter.VoyageHistoryAdapter
import com.example.aquaroute_system.ui.viewmodel.VoyageHistoryViewModel
import com.example.aquaroute_system.ui.viewmodel.VoyageHistoryViewModelFactory
import com.example.aquaroute_system.util.SessionManager
import com.google.firebase.firestore.FirebaseFirestore

class VoyageHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVoyageHistoryBinding
    private lateinit var adapter: VoyageHistoryAdapter
    private lateinit var sessionManager: SessionManager
    private lateinit var cargoRepository: CargoRepository

    private val viewModel: VoyageHistoryViewModel by viewModels {
        VoyageHistoryViewModelFactory(cargoRepository, sessionManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVoyageHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeDependencies()
        setupViews()
        setupObservers()

        // LOAD REAL DATA FROM FIREBASE
        viewModel.loadVoyageHistory()
    }

    private fun initializeDependencies() {
        sessionManager = SessionManager(this)
        cargoRepository = CargoRepository(FirebaseFirestore.getInstance())
    }

    private fun setupViews() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Filter tabs
        binding.btnAll.setOnClickListener { viewModel.filterVoyages("all") }
        binding.btnActive.setOnClickListener { viewModel.filterVoyages("active") }
        binding.btnCompleted.setOnClickListener { viewModel.filterVoyages("completed") }
        binding.btnDelayed.setOnClickListener { viewModel.filterVoyages("delayed") }

        // Setup RecyclerView
        adapter = VoyageHistoryAdapter { voyage ->
            Toast.makeText(this, "Viewing voyage ${voyage.trackingNumber}", Toast.LENGTH_SHORT).show()
            // Navigate to voyage details
        }

        binding.rvVoyageHistory.apply {
            layoutManager = LinearLayoutManager(this@VoyageHistoryActivity)
            adapter = this@VoyageHistoryActivity.adapter
        }
    }

    private fun setupObservers() {
        // Observe voyages list
        viewModel.voyages.observe(this) { voyages ->
            adapter.submitList(voyages)
            updateFilterUI(viewModel.currentFilter)
        }

        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            // Show/hide loading indicator
        }

        // Observe errors
        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun updateFilterUI(filter: String) {
        binding.btnAll.isSelected = filter == "all"
        binding.btnActive.isSelected = filter == "active"
        binding.btnCompleted.isSelected = filter == "completed"
        binding.btnDelayed.isSelected = filter == "delayed"
    }
}