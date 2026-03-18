package com.example.aquaroute_system.ui.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aquaroute_system.R
import com.example.aquaroute_system.data.repository.FerryRepository
import com.example.aquaroute_system.data.repository.PortRepository
import com.example.aquaroute_system.databinding.FragmentPortsBinding
import com.example.aquaroute_system.ui.adapter.PortAdapter
import com.example.aquaroute_system.ui.viewmodel.PortsViewModel
import com.example.aquaroute_system.ui.viewmodel.PortsViewModelFactory
import com.example.aquaroute_system.ui.viewmodel.UserDashboardViewModel
import com.example.aquaroute_system.util.SessionManager
import com.google.firebase.firestore.FirebaseFirestore

class PortsFragment : Fragment() {

    private var _binding: FragmentPortsBinding? = null
    private val binding get() = _binding!!

    private val userViewModel: UserDashboardViewModel by activityViewModels()
    private lateinit var viewModel: PortsViewModel
    private lateinit var adapter: PortAdapter

    private val unitOptions = listOf("km", "mi")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPortsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViewModel()
        setupRecyclerView()
        setupRadiusSelector()
        setupListeners()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.setLocationPermissionGranted(false)
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
        } else {
            viewModel.setLocationPermissionGranted(true)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                viewModel.setLocationPermissionGranted(true)
                Toast.makeText(context, "Location permission granted", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.setLocationPermissionGranted(false)
                Toast.makeText(context, "Location permission denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initializeViewModel() {
        val firestore = FirebaseFirestore.getInstance()
        val portRepository = PortRepository()
        val ferryRepository = FerryRepository(firestore)
        val sessionManager = SessionManager(requireContext())

        val factory = PortsViewModelFactory(portRepository, ferryRepository, sessionManager)
        val vm: PortsViewModel by viewModels { factory }
        viewModel = vm
    }

    private fun setupRecyclerView() {
        adapter = PortAdapter(
            currentHour = viewModel.currentHour,
            vesselCounts = { viewModel.vesselCounts.value ?: emptyMap() },
            onPortClick = { port ->
                Toast.makeText(context, "Locating ${port.name} on map...", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.nav_map)
            }
        )

        binding.rvPorts.layoutManager = LinearLayoutManager(context)
        binding.rvPorts.adapter = adapter
    }

    private fun setupRadiusSelector() {
        val unitAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, unitOptions)
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerUnits.adapter = unitAdapter
        binding.spinnerUnits.setSelection(0)
        binding.etRadius.setText("10")

        binding.btnApplyRadius.setOnClickListener {
            applyRadius()
        }
    }

    private fun applyRadius() {
        val radiusStr = binding.etRadius.text.toString()
        if (radiusStr.isBlank()) {
            Toast.makeText(context, "Please enter a radius value", Toast.LENGTH_SHORT).show()
            return
        }

        val radiusValue = radiusStr.toDoubleOrNull()
        if (radiusValue == null || radiusValue <= 0) {
            Toast.makeText(context, "Please enter a valid positive number", Toast.LENGTH_SHORT).show()
            return
        }

        val unit = binding.spinnerUnits.selectedItem.toString()
        val radiusKm = if (unit == "mi") radiusValue * 1.60934 else radiusValue
        viewModel.setRadius(radiusKm)
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.refreshPortsNearUser()
        }

        binding.btnEnableLocation.setOnClickListener {
            checkLocationPermission()
        }

        binding.searchPorts.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterPorts(newText)
                return true
            }
        })
    }

    private fun observeViewModel() {
        viewModel.ports.observe(viewLifecycleOwner) { ports ->
            adapter.submitList(ports)
            updateEmptyState(ports.isEmpty())
        }

        viewModel.locationPermissionGranted.observe(viewLifecycleOwner) { granted ->
            if (!granted) {
                showLocationRequiredState()
            } else if (viewModel.ports.value.isNullOrEmpty()) {
                showNoPortsState()
            }
        }

        viewModel.vesselCounts.observe(viewLifecycleOwner) {
            adapter.notifyDataSetChanged()
        }

        // Real-time hour updates -> refresh status chips
        viewModel.currentHourLive.observe(viewLifecycleOwner) { hour ->
            adapter.updateCurrentHour(hour)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
            binding.progressBar.visibility = if (isLoading && !binding.swipeRefresh.isRefreshing) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        userViewModel.userLocation.observe(viewLifecycleOwner) { location ->
            location?.let { viewModel.setUserLocation(it) }
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            if (viewModel.locationPermissionGranted.value == false) {
                showLocationRequiredState()
            } else {
                showNoPortsState()
            }
        } else {
            binding.emptyState.visibility = View.GONE
        }
    }

    private fun showLocationRequiredState() {
        binding.emptyState.visibility = View.VISIBLE
        binding.tvEmptyTitle.text = "Location Required"
        binding.tvEmptyMessage.text = "Enable location to see nearby ports."
        binding.btnEnableLocation.visibility = View.VISIBLE
    }

    private fun showNoPortsState() {
        val radius = viewModel.radiusKm.value?.toInt() ?: 50
        binding.emptyState.visibility = View.VISIBLE
        binding.tvEmptyTitle.text = "No Ports Found"
        binding.tvEmptyMessage.text = "No terminals or piers found within $radius km of your location."
        binding.btnEnableLocation.visibility = View.GONE
    }

    private fun filterPorts(query: String?) {
        val allPorts = viewModel.ports.value ?: return
        if (query.isNullOrBlank()) {
            adapter.submitList(allPorts)
        } else {
            val filtered = allPorts.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.type.contains(query, ignoreCase = true)
            }
            adapter.submitList(filtered)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
