package com.example.aquaroute_system.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
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

class CargoFragment : Fragment() {

    private var _binding: ActivityCargoTrackerBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: CargoTrackerViewModel
    private lateinit var sessionManager: SessionManager
    private lateinit var cargoRepository: CargoRepository

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ActivityCargoTrackerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        sessionManager = SessionManager(requireContext())
        cargoRepository = CargoRepository(FirebaseFirestore.getInstance())

        initializeViewModel()
        setupViews()
        setupObservers()
        setupClickListeners()
        
        viewModel.testConnection()
    }

    private fun initializeViewModel() {
        val factory = CargoTrackerViewModelFactory(cargoRepository, sessionManager)
        val vm: CargoTrackerViewModel by viewModels { factory }
        viewModel = vm
    }

    private fun setupViews() {
        binding.btnBack.visibility = View.GONE
        binding.searchInstruction.visibility = View.VISIBLE
        binding.resultCard.visibility = View.GONE
        binding.emptyState.visibility = View.GONE

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
                binding.emptyState.visibility = View.GONE
                binding.resultCard.visibility = View.VISIBLE
            } else {
                binding.resultCard.visibility = View.GONE
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.loadingProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnTrack.isEnabled = !isLoading
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
                binding.emptyState.visibility = View.VISIBLE
                binding.resultCard.visibility = View.GONE
            }
        }

        viewModel.notFound.observe(viewLifecycleOwner) { notFound ->
            if (notFound) {
                binding.emptyState.visibility = View.VISIBLE
                binding.resultCard.visibility = View.GONE
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnTrack.setOnClickListener {
            val referenceNumber = binding.etSearch.text.toString().trim()
            if (referenceNumber.isNotEmpty()) {
                viewModel.searchCargoByReference(referenceNumber)
            } else {
                binding.etSearch.error = "Enter reference number"
            }
        }

        binding.btnViewOnMap.setOnClickListener {
            viewModel.searchResult.value?.let { cargo ->
                // In a real app, we might navigate to the Map tab and focus on a specific ferry
                // For now, we'll keep the intent if LiveMapView is still an activity, 
                // but ideally we should navigate via NavController.
                Toast.makeText(context, "Navigating to Map for ferry: ${cargo.ferryId}", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnNewSearch.setOnClickListener {
            binding.etSearch.text.clear()
            binding.resultCard.visibility = View.GONE
            binding.emptyState.visibility = View.GONE
            binding.etSearch.requestFocus()
        }
    }

    private fun showCargoDetails(cargo: Cargo) {
        binding.tvTrackingNumberValue.text = cargo.reference
        binding.tvDescriptionValue.text = cargo.description.ifEmpty { "No description provided" }
        binding.tvWeightValue.text = String.format(Locale.US, "%.1f kg", cargo.weight)
        binding.tvFerryStatusValue.text = cargo.getStatusDisplay()

        val colorRes = when (cargo.status.lowercase()) {
            "in_transit" -> R.color.success_green
            "delivered" -> R.color.deep_ocean_blue
            "delayed" -> R.color.error_red
            "processing", "pending" -> R.color.warning_orange
            else -> R.color.medium_text
        }
        binding.tvFerryStatusValue.setTextColor(ContextCompat.getColor(requireContext(), colorRes))

        binding.tvFerryNameValue.text = cargo.ferryId?.let { "Ferry ID: $it" } ?: "Not assigned"
        binding.tvOriginValue.text = cargo.origin.ifEmpty { "Manila" }
        binding.tvDestinationValue.text = cargo.destination.ifEmpty { "Cebu" }
        binding.tvCargoTypeValue.text = cargo.cargoType.ifEmpty { "General Cargo" }

        binding.tvEtaValue.text = cargo.eta?.let {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.US)
            dateFormat.format(Date(it))
        } ?: "TBD"

        binding.progressLayout.visibility = View.GONE
        binding.delayReasonLayout.visibility = View.GONE
        binding.recipientLayout.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
