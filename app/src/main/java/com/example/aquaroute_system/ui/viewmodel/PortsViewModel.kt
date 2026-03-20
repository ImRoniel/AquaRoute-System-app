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
import kotlinx.coroutines.flow.collectLatest
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

    private val _ports = MutableLiveData<List<FirestorePort>>()
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

    // Adjustable radius (default 10km)
    private val _radiusKm = MutableLiveData(10.0)
    val radiusKm: LiveData<Double> = _radiusKm

    // Real-time hour for dynamic port status
    private val _currentHour = MutableLiveData<Int>()
    val currentHourLive: LiveData<Int> = _currentHour

    private val allFerries = MutableLiveData<List<Ferry>>()
    private var portJob: Job? = null
    private var timeJob: Job? = null
    private var weatherJob: Job? = null

    // Weather data for the currently selected port
    private val _portWeather = MutableLiveData<Result<WeatherCondition>>()
    val portWeather: LiveData<Result<WeatherCondition>> = _portWeather

    fun fetchWeatherForPort(portId: String) {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            Log.d("WeatherTrace", "[PortsViewModel] fetchWeatherForPort called for portId=$portId")

            // Step 1: Tell the backend to refresh weather from OpenWeather API into Firestore
            Log.d("WeatherTrace", "[PortsViewModel] Triggering backend weather refresh...")
            val refreshResult = weatherRefreshRepository.requestRefresh(listOf(portId))
            when (refreshResult) {
                is Result.Success -> Log.d("WeatherTrace", "[PortsViewModel] Backend refresh succeeded.")
                is Result.Error  -> Log.w("WeatherTrace", "[PortsViewModel] Backend refresh failed: ${refreshResult.exception.message}. Proceeding with cached Firestore data.")
                else -> {}
            }

            // Step 2: Small delay to allow backend to write to Firestore
            delay(2000)

            // Step 3: Read the freshly written (or existing) weather doc from Firestore
            Log.d("WeatherTrace", "[PortsViewModel] Reading weather from Firestore for portId=$portId")
            weatherRepository.getWeatherForLocation(portId).collect { result ->
                Log.d("WeatherTrace", "[PortsViewModel] portWeather result: $result")
                _portWeather.value = result
            }
        }
    }

    init {
        observeFerries()
        startHourTimer()
    }

    /**
     * Updates current hour every 60 seconds for real-time status chip refresh
     */
    private fun startHourTimer() {
        timeJob = viewModelScope.launch {
            while (isActive) {
                _currentHour.value = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                delay(60_000)
            }
        }
    }

    fun setUserLocation(location: Location) {
        val oldLocation = _userLocation.value
        _userLocation.value = location

        // Refresh if first time or moved more than 1km
        if (oldLocation == null || GeoHashUtils.calculateDistance(
                oldLocation.latitude, oldLocation.longitude,
                location.latitude, location.longitude
            ) > 1.0
        ) {
            refreshPortsNearUser()
        }
    }

    fun setRadius(km: Double) {
        _radiusKm.value = km
        refreshPortsNearUser()
    }

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

    fun setLocationPermissionGranted(granted: Boolean) {
        _locationPermissionGranted.value = granted
    }

    fun clearError() {
        _errorMessage.value = null
    }

    val currentHour: Int
        get() = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    override fun onCleared() {
        super.onCleared()
        timeJob?.cancel()
        portJob?.cancel()
        weatherJob?.cancel()
    }
}
