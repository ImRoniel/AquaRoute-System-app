package com.example.aquaroute_system.ui.viewmodel

import android.location.Location
import android.util.Log
import androidx.lifecycle.*
import com.example.aquaroute_system.data.models.Ferry
import com.example.aquaroute_system.data.models.FirestorePort
import com.example.aquaroute_system.data.models.Result
import com.example.aquaroute_system.data.models.WeatherCondition
import com.example.aquaroute_system.data.repository.FerryRepository
import com.example.aquaroute_system.data.repository.PortRepository
import com.example.aquaroute_system.data.repository.WeatherRefreshRepository
import com.example.aquaroute_system.data.repository.WeatherRepository
import com.example.aquaroute_system.util.GeoHashUtils
import com.example.aquaroute_system.util.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.*

class PortsViewModel(
    private val portRepository: PortRepository,
    private val ferryRepository: FerryRepository,
    private val sessionManager: SessionManager,
    private val weatherRepository: WeatherRepository,
    private val weatherRefreshRepository: WeatherRefreshRepository
) : ViewModel() {

    private val _ports = MutableLiveData<List<FirestorePort>>(emptyList())
    val ports: LiveData<List<FirestorePort>> = _ports

    private val _vesselCounts = MutableLiveData<Map<String, Int>>()
    val vesselCounts: LiveData<Map<String, Int>> = _vesselCounts

    private val _locationPermissionGranted = MutableLiveData<Boolean>(true)
    val locationPermissionGranted: LiveData<Boolean> = _locationPermissionGranted

    private val _userLocation = MutableLiveData<Location?>()
    val userLocation: LiveData<Location?> = _userLocation

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // ── Search state (exposed so Fragment can show a "Global Search" indicator) ──
    private val _isSearchMode = MutableLiveData<Boolean>(false)
    val isSearchMode: LiveData<Boolean> = _isSearchMode

    // Adjustable radius (default 10km)
    private val _radiusKm = MutableLiveData(10.0)
    val radiusKm: LiveData<Double> = _radiusKm

    // Real-time hour for dynamic port status
    private val _currentHour = MutableLiveData<Int>()
    val currentHourLive: LiveData<Int> = _currentHour

    // Weather data for the currently selected port
    private val _portWeather = MutableLiveData<Result<WeatherCondition>>()
    val portWeather: LiveData<Result<WeatherCondition>> = _portWeather

    private val allFerries = MutableLiveData<List<Ferry>>()

    // ── Jobs ─────────────────────────────────────────────────────────
    private var portJob: Job? = null
    private var searchJob: Job? = null
    private var timeJob: Job? = null
    private var weatherJob: Job? = null

    // ── Debounced search StateFlow ────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")

    init {
        observeFerries()
        startHourTimer()
        // Collect debounced queries ≥ 3 chars → global prefix search
        viewModelScope.launch {
            _searchQuery
                .debounce(400)
                .filter { it.length >= 3 }
                .collectLatest { q -> runGlobalSearch(q) }
        }
    }

    // ── Public: called from Fragment TextWatcher ─────────────────────
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        when {
            query.isBlank() -> {
                // Clear search → back to nearby mode
                searchJob?.cancel()
                _isSearchMode.value = false
                refreshPortsNearUser()
            }
            query.length < 3 -> {
                // Too short to search yet — show nothing (debounce will fire when ≥ 3)
                searchJob?.cancel()
            }
        }
    }

    // ── Global prefix search — reads ≤ 20 docs, zero bounding-box reads ──
    private fun runGlobalSearch(query: String) {
        portJob?.cancel()   // Stop the nearby real-time listener
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isLoading.postValue(true)
            _isSearchMode.postValue(true)
            val results = portRepository.searchPortsByNamePrefix(query)
            _ports.postValue(results)
            _isLoading.postValue(false)
            if (results.isEmpty()) {
                Log.d("PortsVM", "Global search for '$query' returned 0 results")
            }
        }
    }

    // ── Nearby radius fetch (default mode) ───────────────────────────
    fun refreshPortsNearUser() {
        val location = _userLocation.value
        val radius = _radiusKm.value ?: 50.0

        if (location == null) {
            _errorMessage.value = "Enable location to see nearby ports."
            return
        }

        portJob?.cancel()
        portJob = viewModelScope.launch {
            _isLoading.value = true
            portRepository.observePortsNearLocation(location.latitude, location.longitude, radius)
                .collect { result ->
                    when (result) {
                        is Result.Success -> {
                            _ports.value = result.data
                            calculateVesselActivity()
                        }
                        is Result.Error -> {
                            _errorMessage.value = result.exception.message
                        }
                        else -> {}
                    }
                    _isLoading.value = false
                }
        }
    }

    fun setUserLocation(location: Location) {
        val oldLocation = _userLocation.value
        _userLocation.value = location

        // Only refresh if first time or moved > 1km (avoid unnecessary re-fetches)
        if (oldLocation == null || GeoHashUtils.calculateDistance(
                oldLocation.latitude, oldLocation.longitude,
                location.latitude, location.longitude
            ) > 1.0
        ) {
            // Don't replace an active global search with a geo-refresh
            if (_isSearchMode.value != true) {
                refreshPortsNearUser()
            }
        }
    }

    fun setRadius(km: Double) {
        _radiusKm.value = km
        // Radius change exits search mode and goes back to nearby
        searchJob?.cancel()
        _isSearchMode.value = false
        _searchQuery.value = ""
        refreshPortsNearUser()
    }

    // ── Weather ───────────────────────────────────────────────────────
    fun fetchWeatherForPort(portId: String) {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            Log.d("WeatherTrace", "[PortsViewModel] fetchWeatherForPort called for portId=$portId")
            _portWeather.value = Result.Loading
            val refreshResult = weatherRefreshRepository.requestRefresh(listOf(portId))
            when (refreshResult) {
                is Result.Success -> Log.d("WeatherTrace", "Backend refresh OK")
                is Result.Error   -> Log.w("WeatherTrace", "Backend refresh failed: ${refreshResult.exception.message}")
                else -> {}
            }
            delay(2000)
            weatherRepository.getWeatherForLocation(portId).collect { result ->
                Log.d("WeatherTrace", "[PortsViewModel] portWeather result: $result")
                _portWeather.value = result
            }
        }
    }

    private fun observeFerries() {
        viewModelScope.launch {
            ferryRepository.observeAllFerries().collectLatest { result ->
                if (result is Result.Success) {
                    allFerries.value = result.data
                    calculateVesselActivity()
                }
            }
        }
    }

    private fun calculateVesselActivity() {
        val currentPorts = _ports.value ?: return
        val currentFerries = allFerries.value ?: return

        val counts = mutableMapOf<String, Int>()
        currentPorts.forEach { port ->
            val nearbyVessels = currentFerries.count { ferry ->
                GeoHashUtils.calculateDistance(port.lat, port.lng, ferry.lat, ferry.lon) <= 0.5
            }
            counts[port.id] = nearbyVessels
        }
        _vesselCounts.value = counts
    }

    private fun startHourTimer() {
        timeJob = viewModelScope.launch {
            while (isActive) {
                _currentHour.value = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                delay(60_000)
            }
        }
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        _locationPermissionGranted.value = granted
    }

    fun clearError() { _errorMessage.value = null }

    val currentHour: Int
        get() = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    override fun onCleared() {
        super.onCleared()
        timeJob?.cancel()
        portJob?.cancel()
        searchJob?.cancel()
        weatherJob?.cancel()
    }
}
