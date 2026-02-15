package com.example.aquaroute_system.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aquaroute_system.data.models.Ferry
import com.example.aquaroute_system.data.models.FirestorePort
import com.example.aquaroute_system.data.models.MarkerDetail
import com.example.aquaroute_system.data.models.Port
import com.example.aquaroute_system.data.models.Result
import com.example.aquaroute_system.data.repository.FerryRepository
import com.example.aquaroute_system.data.repository.PortRepository
import com.example.aquaroute_system.data.repository.SearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/**
 * ViewModel for MainDashboard activity
 * Manages all UI state and business logic
 */
class MainDashboardViewModel(
    private val portRepository: PortRepository,
    private val ferryRepository: FerryRepository,
    private val searchRepository: SearchRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MainDashboardViewModel"
        private const val LIVE_UPDATE_INTERVAL = 3000L
    }

    // Firestore ports data
    private val _firestorePorts = MutableLiveData<Result<List<FirestorePort>>>()
    val firestorePorts: LiveData<Result<List<FirestorePort>>> = _firestorePorts

    // Local ferry data
    private val _ferries = MutableLiveData<List<Ferry>>()
    val ferries: LiveData<List<Ferry>> = _ferries

    // Local port data
    private val _ports = MutableLiveData<List<Port>>()
    val ports: LiveData<List<Port>> = _ports

    // Selected marker details for bottom sheet
    private val _selectedMarkerDetail = MutableLiveData<MarkerDetail?>()
    val selectedMarkerDetail: LiveData<MarkerDetail?> = _selectedMarkerDetail

    // Last update time
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

    // Live update job
    private var liveUpdateJob: Job? = null

    //current hour dynamic status
    private val _currentHour = MutableLiveData<Int?>()
    val currentHour: LiveData<Int?> = _currentHour

    // Static port data
    private val portData = listOf(
        Port("Dagupan Ferry Terminal", 16.0431, 120.3339, true),
        Port("Manila Port", 14.594, 120.970, false),
        Port("Cavite Port", 14.475, 120.915, false)
    )

    init {
        initializeData()
        startTimeUpdates()
    }

    private fun startTimeUpdates() {
        viewModelScope.launch {
            while (true) {
                _currentHour.postValue(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
                delay(60000) // Update every minute
            }
        }
    }

    fun getPortWithDynamicStatus(port: FirestorePort): FirestorePort {
        val currentHour = _currentHour.value ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val dynamicStatus = port.getCurrentStatus(currentHour)

        // Return a copy with updated status
        return port.copy(status = dynamicStatus)
    }
    /**
     * Initialize data on ViewModel creation
     */
    private fun initializeData() {
        // Load ferries
        _ferries.value = ferryRepository.getAllFerries()

        // Load ports
        _ports.value = portData

        // Restore last search query
        val lastQuery = searchRepository.getLastSearchQuery()
        _lastSearchQuery.value  = lastQuery
    }

    /**
     * Load all Firestore ports
     */
    fun loadFirestorePorts() {
        _firestorePorts.value = Result.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val result = portRepository.loadPorts()
            _firestorePorts.postValue(result)

            if (result is Result.Error) {
                _errorMessage.postValue("Failed to load ports: ${result.exception.message}")
            }
        }
    }

    /**
     * Start live updates (3-second interval)
     */
    fun startLiveUpdates() {
        // Cancel any existing job
        liveUpdateJob?.cancel()

        liveUpdateJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                updateFerryPositions()
                updateStatusOverlay()
                delay(LIVE_UPDATE_INTERVAL)
            }
        }
    }

    /**
     * Stop live updates
     */
    fun stopLiveUpdates() {
        liveUpdateJob?.cancel()
        liveUpdateJob = null
    }

    /**
     * Update ferry positions (simulation)
     */
    private fun updateFerryPositions() {
        val ferries = _ferries.value ?: return
        ferries.forEach { ferry ->
            ferry.lat += (Math.random() - 0.5) * 0.001
            ferry.lon += (Math.random() - 0.5) * 0.001
            ferryRepository.updateFerryPosition(ferry.name, ferry.lat, ferry.lon)
        }
        _ferries.value = ferries
    }

    /**
     * Update status overlay (time and live indicator)
     */
    private fun updateStatusOverlay() {
        val timeString = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(Date())
        _lastUpdateTime.value = timeString

        // Toggle alpha for blinking effect
        val currentAlpha = _liveIndicatorAlpha.value ?: 1f
        _liveIndicatorAlpha.value = if (currentAlpha == 1f) 0.5f else 1f
    }

    /**
     * Handle ferry marker click
     */
    fun onFerryMarkerClick(ferry: Ferry) {
        _selectedMarkerDetail.value = MarkerDetail.FerryDetail(ferry)
    }

    /**
     * Handle port marker click
     */
    fun onPortMarkerClick(port: Port) {
        _selectedMarkerDetail.value = MarkerDetail.PortDetail(port)
    }

    /**
     * Handle Firestore port marker click
     */
    fun onFirestorePortMarkerClick(port: FirestorePort) {
        val portWithDynamicStatus = getPortWithDynamicStatus(port)
        _selectedMarkerDetail.postValue(
            MarkerDetail.FirestorePortDetail(portWithDynamicStatus)
        )
    }

    /**
     * Clear selected marker detail
     */
    fun clearSelectedMarkerDetail() {
        _selectedMarkerDetail.value = null
    }

    /**
     * Perform search for ferries and ports
     */
    fun performSearch(query: String) {
        if (query.isBlank()) {
            _searchResultsCount.value = 0
            return
        }

        // Save search query
        searchRepository.saveSearchQuery(query)
        _lastSearchQuery.value = query

        var count = 0

        // Search in Firestore ports
        val firestorePorts = (_firestorePorts.value as? Result.Success)?.data ?: emptyList()
        val foundFirestorePorts = firestorePorts.filter {
            it.name.contains(query, ignoreCase = true)
        }
        count += foundFirestorePorts.size

        // Search in local ports
        val foundPorts = portData.filter {
            it.name.contains(query, ignoreCase = true)
        }
        count += foundPorts.size

        // Search in ferries
        val ferries = _ferries.value ?: emptyList()
        val foundFerries = ferries.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.route.contains(query, ignoreCase = true)
        }
        count += foundFerries.size

        _searchResultsCount.value = count

        // Navigate to first result if available
        if (foundFerries.isNotEmpty()) {
            onFerryMarkerClick(foundFerries.first())
        } else if (foundPorts.isNotEmpty()) {
            onPortMarkerClick(foundPorts.first())
        } else if (foundFirestorePorts.isNotEmpty()) {
            onFirestorePortMarkerClick(foundFirestorePorts.first())
        }
    }

    /**
     * Clear search query
     */
    fun clearSearch() {
        _lastSearchQuery.value = ""
        searchRepository.clearSearchHistory()
    }

    override fun onCleared() {
        super.onCleared()
        stopLiveUpdates()
    }
}
