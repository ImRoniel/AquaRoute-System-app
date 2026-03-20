package com.example.aquaroute_system.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.aquaroute_system.R
import com.example.aquaroute_system.View.MainDashboard
import com.example.aquaroute_system.data.models.Cargo
import com.example.aquaroute_system.data.models.Ferry
import com.example.aquaroute_system.data.models.PortStatus
import com.example.aquaroute_system.data.models.User
import com.example.aquaroute_system.data.models.WeatherCondition
import com.example.aquaroute_system.databinding.FragmentHomeBinding
import com.example.aquaroute_system.databinding.ItemCargoSnapshotRowBinding
import com.example.aquaroute_system.ui.viewmodel.UserDashboardViewModel
import java.util.Calendar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: UserDashboardViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers()
        setupClickListeners()
        setupSwipeRefresh()
    }

    // -------------------------------------------------------------------------
    // Observers
    // -------------------------------------------------------------------------

    private fun setupObservers() {

        // Hero Header — user greeting
        viewModel.currentUser.observe(viewLifecycleOwner) { user ->
            user?.let { updateGreeting(it) }
        }

        // Hero Header — cargo stat chips (individual counts)
        viewModel.inTransitCount.observe(viewLifecycleOwner) { count ->
            binding.tvInTransitCount.text = count.toString()
            updateCargoSummarySubtitle()
        }
        viewModel.processingCount.observe(viewLifecycleOwner) { count ->
            binding.tvProcessingCount.text = count.toString()
        }
        viewModel.deliveredCount.observe(viewLifecycleOwner) { count ->
            binding.tvDeliveredCount.text = count.toString()
        }
        viewModel.delayedCount.observe(viewLifecycleOwner) { count ->
            binding.tvDelayedCount.text = count.toString()
        }

        // Fleet Status Pulse
        viewModel.totalActiveFerryCount.observe(viewLifecycleOwner) { count ->
            updateFleetCount(count)
        }
        viewModel.nearestFerry.observe(viewLifecycleOwner) { ferry ->
            updateNearestFerrySpotlight(ferry)
        }
        viewModel.nearestFerryMessage.observe(viewLifecycleOwner) { message ->
            binding.tvFleetEmpty.text = message
        }

        // Fleet Cargo Pulse — fleet-wide active cargo rows + count badge
        viewModel.fleetActiveCargo.observe(viewLifecycleOwner) { cargoList ->
            updateCargoSnapshot(cargoList)
        }
        viewModel.fleetActiveCargoCount.observe(viewLifecycleOwner) { count ->
            binding.tvFleetCargoCount.text = "$count ACTIVE"
        }
        // Ferry name map for enriching cargo rows with ferry names
        viewModel.ferryNameMap.observe(viewLifecycleOwner) { _ ->
            // Re-render rows when ferry names arrive/update
            viewModel.fleetActiveCargo.value?.let { updateCargoSnapshot(it) }
        }

        // Port & Weather Ticker
        viewModel.portStatuses.observe(viewLifecycleOwner) { ports ->
            updatePortStatus(ports)
        }
        viewModel.weatherConditions.observe(viewLifecycleOwner) { weatherList ->
            updateAdvisoryBanner(weatherList)
        }

        // Swipe-refresh loading indicator
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefreshLayout.isRefreshing = isLoading
        }
    }

    // -------------------------------------------------------------------------
    // Card 1: Personalized Hero Header
    // -------------------------------------------------------------------------

    private fun updateGreeting(user: User) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 0..11  -> "GOOD MORNING"
            in 12..16 -> "GOOD AFTERNOON"
            else      -> "GOOD EVENING"
        }
        val name = (user.displayName ?: "CAPTAIN").uppercase()
        binding.tvWelcome.text = "$greeting, $name!"
    }

    private fun updateCargoSummarySubtitle() {
        val inTransit = viewModel.inTransitCount.value ?: 0
        binding.tvCargoSummary.text = when {
            inTransit > 0 -> "The fleet currently has $inTransit shipment${if (inTransit > 1) "s" else ""} in transit."
            else          -> "No active fleet shipments right now. Stay on course!"
        }
    }

    // -------------------------------------------------------------------------
    // Card 2: Fleet Status Pulse
    // -------------------------------------------------------------------------

    private fun updateFleetCount(count: Int) {
        binding.tvActiveFerryCount.text = "$count ACTIVE"
    }

    private fun updateNearestFerrySpotlight(ferry: Ferry?) {
        if (ferry == null) {
            binding.layoutNearestFerry.visibility = View.GONE
            binding.tvFleetEmpty.visibility = View.VISIBLE
        } else {
            binding.tvFleetEmpty.visibility = View.GONE
            binding.layoutNearestFerry.visibility = View.VISIBLE

            binding.tvNearestFerryName.text = ferry.name
            binding.tvNearestFerryRoute.text = "${ferry.getOrigin()} → ${ferry.getDestination()}"

            val etaText = if (ferry.eta > 0) "${ferry.eta} min" else "— min"
            binding.tvNearestFerryEta.text = etaText

            val (statusText, statusColor) = when (ferry.status.lowercase()) {
                "en_route", "active", "sailing" -> "ON TIME" to R.color.safety_green
                "arrived", "docked"             -> "DOCKED" to R.color.sky_cyan
                "delayed"                       -> "DELAYED" to R.color.error_red
                else                            -> ferry.status.uppercase() to R.color.warning_amber
            }
            binding.tvNearestFerryStatus.text = statusText
            binding.tvNearestFerryStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(
                requireContext().getColor(statusColor)
            )

            val progress = calculateFerryProgress(ferry)
            binding.pbNearestFerry.progress = progress
            binding.tvNearestFerryProgress.text = "$progress%"
        }
    }

    private fun calculateFerryProgress(ferry: Ferry): Int {
        // Time-based (most accurate)
        if (ferry.startTime != null && ferry.endTime != null && ferry.endTime > ferry.startTime) {
            val now = System.currentTimeMillis()
            val total = ferry.endTime - ferry.startTime
            val elapsed = now - ferry.startTime
            if (total > 0) return ((elapsed.toFloat() / total) * 100).toInt().coerceIn(0, 100)
        }
        // GPS-coordinate based
        if (ferry.pointA != null && ferry.pointB != null &&
            ferry.pointA.size == 2 && ferry.pointB.size == 2) {
            val totalDist = haversine(ferry.pointA[0], ferry.pointA[1], ferry.pointB[0], ferry.pointB[1])
            val travelledDist = haversine(ferry.pointA[0], ferry.pointA[1], ferry.lat, ferry.lon)
            if (totalDist > 0.1) return ((travelledDist / totalDist) * 100).toInt().coerceIn(0, 100)
        }
        // Status fallback
        if (ferry.status.lowercase() in listOf("arrived", "docked")) return 100
        // ETA fallback
        val maxEta = 120
        return if (ferry.eta > 0) {
            if (ferry.eta >= maxEta) 5
            else ((1 - ferry.eta.toFloat() / maxEta) * 100).toInt().coerceIn(0, 100)
        } else 0
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }

    // -------------------------------------------------------------------------
    // Card 3: Fleet Cargo Pulse
    // -------------------------------------------------------------------------

    private fun updateCargoSnapshot(cargoList: List<Cargo>) {
        val container = binding.llCargoRows
        container.removeAllViews()

        // Hide loading text once data arrives
        binding.tvCargoLoading.visibility = View.GONE

        val activeCargo = cargoList.take(3) // Show at most 3 rows

        if (activeCargo.isEmpty()) {
            binding.layoutCargoEmpty.visibility = View.VISIBLE
        } else {
            binding.layoutCargoEmpty.visibility = View.GONE

            val ferryMap = viewModel.ferryNameMap.value ?: emptyMap()

            activeCargo.forEach { cargo ->
                val rowBinding = ItemCargoSnapshotRowBinding.inflate(
                    layoutInflater, container, false
                )

                val (dotRes, labelColor) = when (cargo.status.lowercase()) {
                    "in_transit"             -> R.drawable.ic_status_dot_green to R.color.safety_green
                    "delivered"              -> R.drawable.ic_status_dot_blue to R.color.sky_cyan
                    "delayed"                -> R.drawable.ic_status_dot_red to R.color.error_red
                    "processing", "pending"  -> R.drawable.ic_status_dot_amber to R.color.warning_amber
                    else                     -> R.drawable.ic_status_dot_amber to R.color.medium_text
                }

                rowBinding.ivCargoStatusDot.setImageResource(dotRes)
                rowBinding.tvCargoReference.text = cargo.getDisplayReference().ifEmpty { cargo.id.take(8) }

                // Enrich route with ferry name when available
                val ferryId = cargo.assignedFerryId ?: cargo.ferryId
                val ferryName = ferryId?.let { ferryMap[it] }
                val routeText = when {
                    !cargo.origin.isNullOrBlank() && !cargo.destination.isNullOrBlank() ->
                        "${cargo.origin} → ${cargo.destination}"
                    ferryName != null -> "Ferry: $ferryName"
                    !cargo.description.isNullOrBlank() -> cargo.description
                    else -> "Shipment ${cargo.id.take(6)}"
                }
                rowBinding.tvCargoRoute.text = routeText

                rowBinding.tvCargoStatusLabel.text = cargo.getStatusDisplay()
                rowBinding.tvCargoStatusLabel.setTextColor(
                    requireContext().getColor(labelColor)
                )

                container.addView(rowBinding.root)

                // Divider between rows (not after last)
                if (cargo != activeCargo.last()) {
                    val divider = View(requireContext())
                    val params = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                    divider.layoutParams = params
                    divider.setBackgroundColor(requireContext().getColor(R.color.light_gray))
                    container.addView(divider)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Card 4: Port & Weather Advisory Ticker
    // -------------------------------------------------------------------------

    private fun updatePortStatus(ports: List<PortStatus>) {
        val open    = ports.count { it.status.lowercase() == "open" }
        val limited = ports.count { it.status.lowercase() == "limited" }
        val closed  = ports.count { it.status.lowercase() == "closed" }

        binding.tvPortsOpen.text    = "$open Open"
        binding.tvPortsLimited.text = "$limited Limited"
        binding.tvPortsClosed.text  = "$closed Closed"

        binding.tvPortsEmpty.visibility = if (ports.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateAdvisoryBanner(weatherList: List<WeatherCondition>) {
        val advisories = weatherList.filter { it.hasAdvisory && !it.advisoryMessage.isNullOrBlank() }
        if (advisories.isEmpty()) {
            binding.layoutAdvisoryBanner.visibility = View.GONE
        } else {
            binding.layoutAdvisoryBanner.visibility = View.VISIBLE
            val text = advisories.joinToString(" • ") { "${it.location}: ${it.advisoryMessage}" }
            binding.tvAdvisoryText.text = text
        }
    }

    // -------------------------------------------------------------------------
    // Click Listeners
    // -------------------------------------------------------------------------

    private fun setupClickListeners() {
        binding.btnViewAllOnMap.setOnClickListener {
            (activity as? MainDashboard)?.navigateToTab(R.id.nav_map)
        }
        binding.btnTrackAllCargo.setOnClickListener {
            (activity as? MainDashboard)?.navigateToTab(R.id.nav_cargo)
        }
        binding.btnViewWeather.setOnClickListener {
            (activity as? MainDashboard)?.navigateToTab(R.id.nav_ferries)
        }
    }

    // -------------------------------------------------------------------------
    // Swipe Refresh
    // -------------------------------------------------------------------------

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshData()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
