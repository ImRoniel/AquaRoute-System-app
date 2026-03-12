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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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

    // Active cargo
    private val _activeCargo = MutableLiveData<List<Cargo>>()
    val activeCargo: LiveData<List<Cargo>> = _activeCargo

    // NEARBY FERRIES for live map preview
    private val _nearbyFerries = MutableLiveData<List<Ferry>>()
    val nearbyFerries: LiveData<List<Ferry>> = _nearbyFerries

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
    init {
        loadCurrentUser()
        startRealTimeUpdates()
    }

    private fun loadCurrentUser() {
        _currentUser.value = sessionManager.getUserData()
    }

    private fun startRealTimeUpdates() {
        val userId = sessionManager.getUserId() ?: return

        viewModelScope.launch {
            dashboardRepository.observeUserStats(userId)
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

        viewModelScope.launch {
            portStatusRepository.observeAllPortStatuses()
                .catch { e ->
                    Log.e(TAG, "Error observing ports", e)
                    _errorMessage.value = "Failed to load ports: ${e.message}"
                }
                .collectLatest { result ->
                    when (result) {
                        is Result.Success -> {
                            _portStatuses.value = result.data
                        }
                        is Result.Error -> {
                            _errorMessage.value = "Ports error: ${result.exception.message}"
                        }
                        else -> {}
                    }
                }
        }

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

        viewModelScope.launch {
            cargoRepository.observeUserActiveCargo(userId)
                .catch { e ->
                    Log.e(TAG, "Error observing cargo", e)
                    _errorMessage.value = "Failed to load cargo: ${e.message}"
                }
                .collectLatest { result ->
                    when (result) {
                        is Result.Success -> {
                            _activeCargo.value = result.data
                            updateStatsFromCargo(result.data)
                        }
                        is Result.Error -> {
                            _errorMessage.value = "Cargo error: ${result.exception.message}"
                        }
                        else -> {}
                    }
                }
        }

        // Load nearby ferries periodically
        loadNearbyFerries()
    }

    fun loadNearbyFerries() {
        viewModelScope.launch {
            val userLoc = _userLocation.value
            if (userLoc != null) {
                val allFerries = ferryRepository.getAllFerries()
                // Filter to nearby (within 100km)
                val nearby = allFerries.filter { ferry ->
                    calculateDistance(
                        userLoc.latitude, userLoc.longitude,
                        ferry.lat, ferry.lon
                    ) <= 100
                }
                _nearbyFerries.value = nearby
            } else {
                // If no location, show all or top few
                val allFerries = ferryRepository.getAllFerries()
                _nearbyFerries.value = allFerries.take(3)
            }
        }
    }

    fun setUserLocation(location: Location) {
        _userLocation.value = location
        loadNearbyFerries()
        checkNearbyWeather(location)
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
        val currentStats = _dashboardStats.value ?: DashboardStats()
        val updatedStats = currentStats.copy(
            totalShipments = cargoList.size,
            inTransit = cargoList.count { it.status == "in_transit" },
            delivered = cargoList.count { it.status == "delivered" },
            delayed = cargoList.count { it.status == "delayed" }
        )
        _dashboardStats.value = updatedStats
    }

    fun refreshData() {
        _isLoading.value = true
        loadCurrentUser()
        loadNearbyFerries()
        _isLoading.value = false
    }
    // New function to check nearby weather
    private fun checkNearbyWeather(location: Location) {
        viewModelScope.launch {
            val lat = location.latitude
            val lon = location.longitude
            val range = 5.0 // ~500km radius

            val ports = portRepository.getPortsInBounds(
                lat - range, lat + range,
                lon - range, lon + range
            )
            if (ports.isEmpty()) return@launch

            val stalePortIds = mutableListOf<String>()
            for (port in ports) {
                val lastUpdated = weatherRepository.getWeatherLastUpdated(port.id)
                if (lastUpdated == null || System.currentTimeMillis() - lastUpdated > 60 * 60 * 1000) {
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