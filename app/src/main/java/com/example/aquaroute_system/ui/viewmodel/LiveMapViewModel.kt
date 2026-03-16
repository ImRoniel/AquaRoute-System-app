package com.example.aquaroute_system.ui.viewmodel

import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import com.example.aquaroute_system.data.models.*
import com.example.aquaroute_system.data.repository.*
import com.example.aquaroute_system.util.LocationHelper
import com.example.aquaroute_system.util.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.*

class LiveMapViewModel(
    private val sessionManager: SessionManager,
    private val portRepository: PortRepository,
    private val ferryRepository: FerryRepository,
    private val ferryRefreshRepository: FerryRefreshRepository
) : ViewModel() {

    companion object {
        private const val TAG = "LiveMapViewModel"
        private const val LIVE_UPDATE_INTERVAL = 3000L
    }

    // Firestore ports (full list, unused now)
    private val _firestorePorts = MutableLiveData<Result<List<FirestorePort>>>()
    val firestorePorts: LiveData<Result<List<FirestorePort>>> = _firestorePorts

    // Real ferry data from Firebase
    private val _ferries = MutableLiveData<List<Ferry>>()
    val ferries: LiveData<List<Ferry>> = _ferries

    // Nearby ferries (filtered by location)
    private val _nearbyFerries = MutableLiveData<List<Ferry>>()
    val nearbyFerries: LiveData<List<Ferry>> = _nearbyFerries

    // Local port data (for demo)
    private val _ports = MutableLiveData<List<Port>>()
    val ports: LiveData<List<Port>> = _ports

    // Selected marker details for bottom sheet
    private val _selectedMarkerDetail = MutableLiveData<MarkerDetail?>()
    val selectedMarkerDetail: LiveData<MarkerDetail?> = _selectedMarkerDetail

    // Last update time string
    private val _lastUpdateTime = MutableLiveData<String>()
    val lastUpdateTime: LiveData<String> = _lastUpdateTime

    // Live status indicator
    private val _liveIndicatorAlpha = MutableLiveData<Float>(1f)
    val liveIndicatorAlpha: LiveData<Float> = _liveIndicatorAlpha

    // Last search query
    private val _lastSearchQuery = MutableLiveData<String>()
    val lastSearchQuery: LiveData<String> = _lastSearchQuery

    // Search results count
    private val _searchResultsCount = MutableLiveData<Int>()
    val searchResultsCount: LiveData<Int> = _searchResultsCount

    // Error messages
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // Current hour for dynamic status
    private val _currentHour = MutableLiveData<Int?>()
    val currentHour: LiveData<Int?> = _currentHour

    // User location
    private val _userLocation = MutableLiveData<Location?>()
    val userLocation: LiveData<Location?> = _userLocation

    private val _isLocationLoading = MutableLiveData(false)
    val isLocationLoading: LiveData<Boolean> = _isLocationLoading

    private val _locationError = MutableLiveData<String?>()
    val locationError: LiveData<String?> = _locationError

    // Loading state for ferry refresh
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Live update job
    private var liveUpdateJob: Job? = null

    // Visible ports (combined from ferries and user location)
    private val _visiblePorts = MutableLiveData<List<FirestorePort>>()
    val visiblePorts: LiveData<List<FirestorePort>> = _visiblePorts

    // Once‑per‑session port loading (user location)
    private var _portsLoadedFromLocation = false
    private var lastLoadLat = 0.0
    private var lastLoadLon = 0.0
    private val MOVE_THRESHOLD_KM = 50.0

    private val _portsLoadedFromFerries = MutableLiveData(false)
    val portsLoadedFromFerries: LiveData<Boolean> = _portsLoadedFromFerries

    // Static port data for demo (fallback)
    private val portData = listOf(
        Port("Dagupan Ferry Terminal", 16.0431, 120.3339, true),
        Port("Manila Port", 14.594, 120.970, false),
        Port("Cavite Port", 14.475, 120.915, false)
    )

    init {
        initializeMapData()
        startTimeUpdates()
        loadFerries()
    }

    private fun initializeMapData() {
        _ports.value = portData
    }

    fun loadFerries() {
        viewModelScope.launch {
            val ferries = ferryRepository.getAllFerries()
            _ferries.value = ferries
            filterNearbyFerries()
            loadPortsFromFerries(ferries)
        }
    }

    private fun filterNearbyFerries() {
        val userLoc = _userLocation.value ?: return
        val allFerries = _ferries.value ?: return  // Use cached ferries
        viewModelScope.launch {
            val nearby = allFerries.filter { ferry ->
                calculateDistance(userLoc.latitude, userLoc.longitude, ferry.lat, ferry.lon) <= 100.0
            }
            _nearbyFerries.value = nearby
        }
    }

    private fun startTimeUpdates() {
        viewModelScope.launch {
            while (true) {
                _currentHour.postValue(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
                delay(60000)
            }
        }
    }

    fun startLiveUpdates() {
        liveUpdateJob?.cancel()
        liveUpdateJob = viewModelScope.launch {
            while (isActive) {
                updateStatusOverlay()
                delay(LIVE_UPDATE_INTERVAL)
            }
        }
    }

    fun stopLiveUpdates() {
        liveUpdateJob?.cancel()
        liveUpdateJob = null
    }

    fun refreshFerries() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = ferryRefreshRepository.refresh()) {
                is Result.Success -> {
                    loadFerries()
                }
                is Result.Error -> {
                    _errorMessage.value = "Ferry refresh failed: ${result.exception.message}"
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun loadPortsInBounds(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double) {
        viewModelScope.launch {
            val ports = portRepository.getPortsInBounds(minLat, maxLat, minLon, maxLon)
            _visiblePorts.postValue(ports)
        }
    }

    fun getPortWithDynamicStatus(port: FirestorePort): FirestorePort {
        val currentHour = _currentHour.value ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val dynamicStatus = port.getCurrentStatus(currentHour)
        return port.copy(status = dynamicStatus)
    }

    fun onPortMarkerClick(port: Port) {
        _selectedMarkerDetail.value = MarkerDetail.PortDetail(port)
    }

    fun onFerryMarkerClick(ferry: Ferry) {
        _selectedMarkerDetail.value = MarkerDetail.FerryDetail(ferry)
    }

    fun onFirestorePortMarkerClick(port: FirestorePort) {
        val portWithStatus = getPortWithDynamicStatus(port)
        _selectedMarkerDetail.value = MarkerDetail.FirestorePortDetail(portWithStatus)
    }

    fun clearSelectedMarkerDetail() {
        _selectedMarkerDetail.value = null
    }

    fun performSearch(query: String) {
        if (query.isBlank()) {
            _searchResultsCount.value = 0
            return
        }

        _lastSearchQuery.value = query

        var count = 0
        val firestorePorts = (_firestorePorts.value as? Result.Success)?.data ?: emptyList()
        val foundFirestorePorts = firestorePorts.filter {
            it.name.contains(query, ignoreCase = true)
        }
        count += foundFirestorePorts.size

        val foundPorts = portData.filter {
            it.name.contains(query, ignoreCase = true)
        }
        count += foundPorts.size

        val ferries = _ferries.value ?: emptyList()
        val foundFerries = ferries.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.route.contains(query, ignoreCase = true)
        }
        count += foundFerries.size

        _searchResultsCount.value = count

        when {
            foundFerries.isNotEmpty() -> onFerryMarkerClick(foundFerries.first())
            foundPorts.isNotEmpty() -> onPortMarkerClick(foundPorts.first())
            foundFirestorePorts.isNotEmpty() -> onFirestorePortMarkerClick(foundFirestorePorts.first())
        }
    }

    fun requestUserLocation(context: android.content.Context) {
        viewModelScope.launch {
            _isLocationLoading.value = true
            try {
                val location = LocationHelper.getLastLocation(context)
                if (location != null) {
                    _userLocation.value = location
                    _locationError.value = null
                    filterNearbyFerries()
                } else {
                    _locationError.value = "Could not get location"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting location", e)
                _locationError.value = "Error: ${e.message}"
            } finally {
                _isLocationLoading.value = false
            }
        }
    }

    fun clearLocationError() {
        _locationError.value = null
    }

    private fun updateStatusOverlay() {
        val timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        _lastUpdateTime.value = timeString
        val currentAlpha = _liveIndicatorAlpha.value ?: 1f
        _liveIndicatorAlpha.value = if (currentAlpha == 1f) 0.5f else 1f
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun loadPortsNearUserOnce(lat: Double, lon: Double, radiusKm: Double = 100.0) {
        if (_portsLoadedFromLocation) {
            val distance = calculateDistance(lat, lon, lastLoadLat, lastLoadLon)
            if (distance < MOVE_THRESHOLD_KM) return
            // else reset and load new area
            _portsLoadedFromLocation = false
        }
        viewModelScope.launch {
            val ports = portRepository.getPortsNearLocation(lat, lon, radiusKm)
            mergePorts(ports)
            _portsLoadedFromLocation = true
            lastLoadLat = lat
            lastLoadLon = lon
        }
    }

    fun resetPortsLoaded() {
        _portsLoadedFromLocation = false
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    /**
     * Load ports that are origins or destinations of the given ferries.
     */
    private fun loadPortsFromFerries(ferries: List<Ferry>) {
        viewModelScope.launch {
            // Collect all unique port names from origins and destinations
            val portNames = ferries.flatMap { ferry ->
                listOf(ferry.getOrigin(), ferry.getDestination())
            }.filter { it != "Unknown" }.distinct()

            if (portNames.isEmpty()) {
                Log.d(TAG, "No port names extracted from ferries")
                return@launch
            }

            // Firestore whereIn has a limit of 10 items per query, so batch
            val batchSize = 10
            val portLists = portNames.chunked(batchSize).map { chunk ->
                async {
                    try {
                        portRepository.getPortsByNames(chunk)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading ports batch", e)
                        emptyList()
                    }
                }
            }
            val results = portLists.awaitAll().flatten()

            // Merge with existing visible ports
            mergePorts(results)
            _portsLoadedFromFerries.postValue(true)
        }
    }

    /**
     * Merge a new list of ports into _visiblePorts, avoiding duplicates.
     */
    private fun mergePorts(newPorts: List<FirestorePort>) {
        val current = _visiblePorts.value?.toMutableList() ?: mutableListOf()
        current.addAll(newPorts)
        _visiblePorts.postValue(current.distinctBy { it.id })
    }
}