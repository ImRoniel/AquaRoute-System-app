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
        private val ferryRefreshRepository: FerryRefreshRepository  // Added
    ) : ViewModel() {

        companion object {
            private const val TAG = "LiveMapViewModel"
            private const val LIVE_UPDATE_INTERVAL = 3000L
        }

        // Firestore ports
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

        private val _visiblePorts = MutableLiveData<List<FirestorePort>>()
        val visiblePorts: LiveData<List<FirestorePort>> = _visiblePorts
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
                _ferries.value = ferryRepository.getAllFerries()
                filterNearbyFerries()
            }
        }

        private fun filterNearbyFerries() {
            val userLoc = _userLocation.value
            if (userLoc != null) {
                viewModelScope.launch {
                    val nearby = ferryRepository.getFerriesNearLocation(
                        userLoc.latitude,
                        userLoc.longitude
                    )
                    _nearbyFerries.value = nearby
                }
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
                    // Only update the status overlay (live indicator) – no expensive refresh
                    updateStatusOverlay()
                    delay(LIVE_UPDATE_INTERVAL) // 3 seconds is fine for the blinking dot
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
                        // After refresh, reload ferries from Firestore
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

        fun loadFirestorePorts() {
//            _firestorePorts.value = Result.Loading
//            viewModelScope.launch {
//                val result = portRepository.loadPorts()
//                _firestorePorts.value = result
//            }
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
    }