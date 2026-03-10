//C:\Users\Roniel Cuaresma\AndroidStudioProjects\AquaRouteSystem\app\src\main\java\com\example\aquaroute_system\ui\viewmodel\MainDashboardViewModel.kt
package com.example.aquaroute_system.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aquaroute_system.data.models.*
import com.example.aquaroute_system.data.repository.*
import com.example.aquaroute_system.util.SessionManager
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainDashboardViewModel(
    private val sessionManager: SessionManager,
    private val dashboardRepository: DashboardRepository,
    private val portStatusRepository: PortStatusRepository,
    private val weatherRepository: WeatherRepository,
    private val cargoRepository: CargoRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MainDashboardViewModel"
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

    // Loading states
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isRefreshing = MutableLiveData(false)
    val isRefreshing: LiveData<Boolean> = _isRefreshing

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // Last update time
    private val _lastUpdated = MutableLiveData<Long>()
    val lastUpdated: LiveData<Long> = _lastUpdated

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
            // Observe dashboard stats in real-time
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
                        is Result.Loading -> {
                            // Optional: handle loading state
                        }
                    }
                }
        }

        viewModelScope.launch {
            // Observe port statuses in real-time
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
            // Observe weather conditions in real-time
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
            // Observe active cargo in real-time
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
        _isRefreshing.value = true
        // Force refresh by reloading current user
        loadCurrentUser()
        _isRefreshing.value = false
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

    override fun onCleared() {
        super.onCleared()
        // Clean up resources if needed
    }
}