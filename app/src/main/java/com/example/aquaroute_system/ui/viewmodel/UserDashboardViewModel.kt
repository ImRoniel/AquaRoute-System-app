package com.example.aquaroute_system.ui.viewmodel

import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aquaroute_system.data.models.*
import com.example.aquaroute_system.data.repository.*
import com.example.aquaroute_system.util.LocationHelper
import com.example.aquaroute_system.util.SessionManager
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.example.aquaroute_system.util.SpatialIndex

class UserDashboardViewModel(
    private val sessionManager: SessionManager,
    private val dashboardRepository: DashboardRepository,
    private val portStatusRepository: PortStatusRepository,
    private val weatherRepository: WeatherRepository,
    private val cargoRepository: CargoRepository,
    private val ferryRepository: FerryRepository,
    private val portRepository: PortRepository,               // ADDED
    private val weatherRefreshRepository: WeatherRefreshRepository  // ADDED
) : ViewModel() {

    companion object {
        private const val TAG = "UserDashboardViewModel"
    }

    // User data
    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    // Dashboard stats
    private val _dashboardStats = MutableLiveData<DashboardStats?>()
    val dashboardStats: LiveData<DashboardStats?> = _dashboardStats

    // Port statuses
    private val _portStatuses = MutableLiveData<List<PortStatus>>()
    val portStatuses: LiveData<List<PortStatus>> = _portStatuses

    // Weather conditions
    private val _weatherConditions = MutableLiveData<List<WeatherCondition>>()
    val weatherConditions: LiveData<List<WeatherCondition>> = _weatherConditions

    // Active cargo (user-specific — kept for Cargo screen)
    private val _activeCargo = MutableLiveData<List<Cargo>>()
    val activeCargo: LiveData<List<Cargo>> = _activeCargo

    // Fleet-wide active cargo for dashboard Cargo Pulse card
    private val _fleetActiveCargo = MutableLiveData<List<Cargo>>()
    val fleetActiveCargo: LiveData<List<Cargo>> = _fleetActiveCargo

    private val _fleetActiveCargoCount = MutableLiveData(0)
    val fleetActiveCargoCount: LiveData<Int> = _fleetActiveCargoCount

    // Ferry name map (ferryId → ferry name) for enriching cargo rows
    private val _ferryNameMap = MutableLiveData<Map<String, String>>(emptyMap())
    val ferryNameMap: LiveData<Map<String, String>> = _ferryNameMap

    // Nearby ferries list for dynamic cards & Live Map preview
    private val _nearbyFerries = MutableLiveData<List<Ferry>>()
    val nearbyFerries: LiveData<List<Ferry>> = _nearbyFerries

    // Total fleet size for accurate FLEET STATUS badge
    private val _totalActiveFerryCount = MutableLiveData(0)
    val totalActiveFerryCount: LiveData<Int> = _totalActiveFerryCount

    private val _nearestFerry = MutableLiveData<Ferry?>()
    val nearestFerry: LiveData<Ferry?> = _nearestFerry

    // Message for empty or loading states on the Nearest Ferry card
    private val _nearestFerryMessage = MutableLiveData<String>()
    val nearestFerryMessage: LiveData<String> = _nearestFerryMessage

    // Per-status cargo counts for hero header chips
    private val _inTransitCount = MutableLiveData(0)
    val inTransitCount: LiveData<Int> = _inTransitCount

    private val _processingCount = MutableLiveData(0)
    val processingCount: LiveData<Int> = _processingCount

    private val _deliveredCount = MutableLiveData(0)
    val deliveredCount: LiveData<Int> = _deliveredCount

    private val _delayedCount = MutableLiveData(0)
    val delayedCount: LiveData<Int> = _delayedCount

    // Location Permission Status
    private val _locationPermissionGranted = MutableLiveData<Boolean>(true)
    val locationPermissionGranted: LiveData<Boolean> = _locationPermissionGranted

    // User location
    private val _userLocation = MutableLiveData<Location?>()
    val userLocation: LiveData<Location?> = _userLocation

    // Loading states
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // Last update time
    private val _lastUpdated = MutableLiveData<Long>()
    val lastUpdated: LiveData<Long> = _lastUpdated

    //live data for refreshing state
    private val _isRefreshingWeather = MutableLiveData(false)
    val isRefreshingWeather: LiveData<Boolean> = _isRefreshingWeather
    private var _cachedAllFerries = listOf<Ferry>()
    private val spatialIndex = SpatialIndex()

    // QUOTA FIX: throttle weather checks to at most once per hour
    private var lastWeatherCheckTime = 0L
    private val WEATHER_CHECK_INTERVAL_MS = 60 * 60 * 1000L

    // Job to track the active nearby-ports observation (cancelled & relaunched on location change)
    private var nearbyPortsJob: Job? = null

    // Location debounce states
    private var lastSearchLocation: Location? = null
    private var lastSearchTime: Long = 0L
    private val MIN_DISTANCE_M = 1000f  // QUOTA FIX: was 100f — 1 km before re-querying Firestore
    private val MIN_TIME_MS = 30_000L   // QUOTA FIX: was 500ms — 30 s minimum between listener re-attachments

    init {
        loadCurrentUser()
        startRealTimeUpdates()
        startUiTicker()
    }

    private fun loadCurrentUser() {
        _currentUser.value = sessionManager.getUserData()
    }

    private fun startRealTimeUpdates() {
        val userId = sessionManager.getUserId() ?: return

        // Observe Ferries in real-time
        viewModelScope.launch {
            ferryRepository.observeAllFerries()
                .catch { e ->
                    Log.e(TAG, "Error observing ferries", e)
                    _errorMessage.value = "Failed to load ferries: ${e.message}"
                }
                .collectLatest { result ->
                    when (result) {
                        is Result.Success -> {
                            _cachedAllFerries = result.data
                            spatialIndex.build(result.data)
                            _totalActiveFerryCount.value = result.data.size
                            updateNearbyFerries()
                            // Build ferryId → ferryName map for cargo row enrichment
                            _ferryNameMap.value = result.data.associate { it.id to it.name }
                        }
                        is Result.Error -> {
                            _errorMessage.value = "Ferry error: ${result.exception.message}"
                        }
                        else -> {}
                    }
                }
        }

        viewModelScope.launch {
            dashboardRepository.observeUserStats(userId)
// ... (rest of the stats/port/weather observation logic remains similar)
                .catch { e ->
                    Log.e(TAG, "Error observing stats", e)
                    _errorMessage.value = "Failed to load stats: ${e.message}"
                }
                .collectLatest { result ->
                    when (result) {
                        is Result.Success -> {
                            _dashboardStats.value = result.data
                            _lastUpdated.value = System.currentTimeMillis()
                        }
                        is Result.Error -> {
                            _errorMessage.value = "Stats error: ${result.exception.message}"
                        }
                        else -> {}
                    }
                }
        }

        // Port status observation is now location-driven.
        // See updateNearbyPorts() — called from setUserLocation().

        viewModelScope.launch {
            weatherRepository.observeAllWeatherConditions()
                .catch { e ->
                    Log.e(TAG, "Error observing weather", e)
                    _errorMessage.value = "Failed to load weather: ${e.message}"
                }
                .collectLatest { result ->
                    when (result) {
                        is Result.Success -> {
                            _weatherConditions.value = result.data
                        }
                        is Result.Error -> {
                            _errorMessage.value = "Weather error: ${result.exception.message}"
                        }
                        else -> {}
                    }
                }
        }

        // Removed old observeUserActiveCargo tracker since UI is entirely fleet-oriented now

        // Fleet-wide ALL-ACTIVE cargo — used for Hero Header stat chips only
        viewModelScope.launch {
            cargoRepository.observeFleetActiveCargo()
                .catch { e ->
                    Log.e(TAG, "Error observing fleet cargo", e)
                    updateStatsFromCargo(emptyList())
                }
                .collectLatest { result ->
                    when (result) {
                        is Result.Success -> updateStatsFromCargo(result.data)
                        is Result.Error   -> Log.e(TAG, "Fleet cargo error: ${result.exception.message}")
                        else              -> {}
                    }
                }
        }

        // Fleet-wide IN-TRANSIT only cargo — drives Cargo Pulse card rows + active badge
        viewModelScope.launch {
            cargoRepository.observeFleetInTransitCargo()
                .catch { e ->
                    Log.e(TAG, "Error observing in-transit cargo", e)
                    _fleetActiveCargo.value = emptyList()
                    _fleetActiveCargoCount.value = 0
                }
                .collectLatest { result ->
                    when (result) {
                        is Result.Success -> {
                            _fleetActiveCargo.value      = result.data
                            _fleetActiveCargoCount.value = result.data.size
                        }
                        is Result.Error -> {
                            Log.e(TAG, "In-transit cargo error: ${result.exception.message}")
                            // Keep existing UI state — don't blank screen on transient errors
                        }
                        else -> {}
                    }
                }
        }

        // Load nearby ferries periodically (Managed by startUiTicker)
    }

    private fun startUiTicker() {
        viewModelScope.launch {
            while (true) {
                delay(30_000) // 30 seconds
                updateNearbyFerries()
            }
        }
    }

    private fun updateNearbyFerries() {
        val allFerries = _cachedAllFerries
        if (allFerries.isEmpty()) {
            _nearestFerryMessage.value = "All ferries are docked"
            _nearestFerry.value = null
            return
        }

        val userLoc = _userLocation.value
        if (userLoc != null) {
            // Trigger radius search for Nearest Ferry spotlight
            updateNearestFerryWithRadiusSearch(userLoc)

            // Legacy simple 100km filter for nearbyFerries LiveData
            val ferriesWithDistance = allFerries.map { ferry ->
                ferry to calculateDistance(
                    userLoc.latitude, userLoc.longitude,
                    ferry.lat, ferry.lon
                )
            }.sortedBy { it.second }

            val nearby = ferriesWithDistance.filter { it.second <= 100 }.map { it.first }

            val newList = if (nearby.isNotEmpty()) {
                nearby
            } else {
                ferriesWithDistance.take(2).map { it.first }
            }
            _nearbyFerries.value = newList
        } else {
            // No location: fallback to first few ferries
            val fallback = allFerries.take(3)
            _nearbyFerries.value = fallback
            _nearestFerryMessage.value = if (_locationPermissionGranted.value == false) {
                "Location permission denied"
            } else {
                "Waiting for location..."
            }
            _nearestFerry.value = null
        }
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        _locationPermissionGranted.value = granted
        if (!granted) {
            _nearestFerryMessage.value = "Location permission denied"
            _nearestFerry.value = null
        } else {
            // If just granted, we might still be waiting for the first location fix
            if (_userLocation.value == null) {
                _nearestFerryMessage.value = "Waiting for location..."
            }
        }
    }

    private fun updateNearestFerryWithRadiusSearch(location: Location) {
        if (_cachedAllFerries.isEmpty()) {
            _nearestFerryMessage.postValue("All ferries are docked")
            _nearestFerry.postValue(null)
            return
        }

        _nearestFerryMessage.postValue("Finding nearest ferry...")

        viewModelScope.launch(Dispatchers.Default) {
            val (nearest, _) = spatialIndex.findNearestFerryExpandingRadius(
                location.latitude, location.longitude
            )

            withContext(Dispatchers.Main) {
                if (nearest != null) {
                    _nearestFerry.value = nearest
                } else {
                    _nearestFerry.value = null
                    _nearestFerryMessage.value = "No ferries within 200 km"
                }
            }
        }
    }

    fun setUserLocation(location: Location) {
        _userLocation.value = location
        checkNearbyWeather(location)

        val timeSinceLast = System.currentTimeMillis() - lastSearchTime
        val distChange = lastSearchLocation?.distanceTo(location) ?: Float.MAX_VALUE

        if (timeSinceLast > MIN_TIME_MS && distChange > MIN_DISTANCE_M) {
            lastSearchTime = System.currentTimeMillis()
            lastSearchLocation = location
            updateNearestFerryWithRadiusSearch(location)
            updateNearbyPorts(location)
            
            // Also update legacy nearby list
            updateNearbyFerries()
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371 // Earth's radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    private fun updateStatsFromCargo(cargoList: List<Cargo>) {
        // Normalize to uppercase for robust matching regardless of how the web admin wrote the status
        val inTransit  = cargoList.count {
            val s = it.status.uppercase()
            s == "IN_TRANSIT" || s == "IN TRANSIT"
        }
        val delivered  = cargoList.count { it.status.equals("delivered",  ignoreCase = true) }
        val delayed    = cargoList.count { it.status.equals("delayed",    ignoreCase = true) }
        val processing = cargoList.count {
            val s = it.status.uppercase()
            s == "PROCESSING" || s == "PENDING"
        }

        val currentStats = _dashboardStats.value ?: DashboardStats()
        _dashboardStats.value = currentStats.copy(
            totalShipments = cargoList.size,
            inTransit  = inTransit,
            delivered  = delivered,
            delayed    = delayed
        )

        _inTransitCount.value  = inTransit
        _deliveredCount.value  = delivered
        _delayedCount.value    = delayed
        _processingCount.value = processing
    }

    fun refreshData() {
        _isLoading.value = true
        loadCurrentUser()
        updateNearbyFerries()
        _userLocation.value?.let { updateNearbyPorts(it) }
        _isLoading.value = false
    }

    // -----------------------------------------------------------------
    // Location-aware port status (5 km radius)
    // -----------------------------------------------------------------

    /**
     * Cancel the previous port listener and start a new one scoped to
     * the user's current position.  Maps FirestorePort → PortStatus
     * so HomeFragment can display Open / Limited / Closed counts.
     */
    private fun updateNearbyPorts(location: Location) {
        nearbyPortsJob?.cancel()
        nearbyPortsJob = viewModelScope.launch {
            portRepository.observePortsNearLocation(
                location.latitude, location.longitude, 5.0  // 10 km (matches Ports Tab default)
            )
                .catch { e ->
                    Log.e(TAG, "Error observing nearby ports", e)
                    _portStatuses.value = emptyList()
                }
                .collectLatest { result ->
                    when (result) {
                        is Result.Success -> {
                            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                            _portStatuses.value = result.data.map { mapToPortStatus(it, hour) }
                        }
                        is Result.Error -> {
                            Log.e(TAG, "Nearby ports error: ${result.exception.message}")
                            _portStatuses.value = emptyList()
                        }
                        else -> {}
                    }
                }
        }
    }

    /** Convert a FirestorePort into a PortStatus using time-aware status. */
    private fun mapToPortStatus(port: FirestorePort, hour: Int): PortStatus {
        val dynamicStatus = port.getCurrentStatus(hour)
        val normalizedStatus = when {
            dynamicStatus.startsWith("Closed", ignoreCase = true)      -> "closed"
            dynamicStatus.startsWith("Opens at", ignoreCase = true)    -> "closed"
            dynamicStatus.startsWith("Closing", ignoreCase = true)     -> "limited"
            dynamicStatus.contains("Limited", ignoreCase = true)       -> "limited"
            dynamicStatus.startsWith("Open", ignoreCase = true)        -> "open"
            else -> port.status.lowercase()
        }
        return PortStatus(
            portId   = port.id,
            portName = port.name,
            status   = normalizedStatus
        )
    }
    // QUOTA FIX: throttled weather check — runs at most once per hour, smaller bounds, capped port count
    private fun checkNearbyWeather(location: Location) {
        val now = System.currentTimeMillis()
        if (now - lastWeatherCheckTime < WEATHER_CHECK_INTERVAL_MS) return // Skip if checked recently
        lastWeatherCheckTime = now

        viewModelScope.launch {
            val lat = location.latitude
            val lon = location.longitude
            val range = 1.0 // QUOTA FIX: was 5.0 (~500 km) — reduced to ~111 km

            val ports = portRepository.getPortsInBounds(
                lat - range, lat + range,
                lon - range, lon + range
            )
            if (ports.isEmpty()) return@launch

            // QUOTA FIX: cap to 10 ports to prevent runaway individual-document reads
            val portsToCheck = ports.take(10)
            val stalePortIds = mutableListOf<String>()
            for (port in portsToCheck) {
                val lastUpdated = weatherRepository.getWeatherLastUpdated(port.id)
                if (lastUpdated == null || now - lastUpdated > WEATHER_CHECK_INTERVAL_MS) {
                    stalePortIds.add(port.id)
                }
            }

            if (stalePortIds.isNotEmpty()) {
                _isRefreshingWeather.value = true
                val result = weatherRefreshRepository.requestRefresh(stalePortIds)
                if (result is Result.Error) {
                    _errorMessage.value = "Weather refresh failed: ${result.exception.message}"
                }
                _isRefreshingWeather.value = false
            }
        }
    }

    fun getPortStatus(portName: String): PortStatus? {
        return _portStatuses.value?.find { it.portName.contains(portName, ignoreCase = true) }
    }

    fun getWeatherForLocation(location: String): WeatherCondition? {
        return _weatherConditions.value?.find { it.location.contains(location, ignoreCase = true) }
    }

    fun getActiveCargoCount(): Int {
        return _activeCargo.value?.size ?: 0
    }

    fun clearError() {
        _errorMessage.value = null
    }
}