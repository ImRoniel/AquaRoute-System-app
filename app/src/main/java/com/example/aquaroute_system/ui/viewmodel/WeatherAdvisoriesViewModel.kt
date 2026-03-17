package com.example.aquaroute_system.ui.viewmodel

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aquaroute_system.data.models.FirestorePort
import com.example.aquaroute_system.data.models.Result
import com.example.aquaroute_system.data.models.WeatherCondition
import com.example.aquaroute_system.data.repository.PortRepository
import com.example.aquaroute_system.data.repository.WeatherRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WeatherAdvisoriesViewModel(
    private val weatherRepository: WeatherRepository,
    private val portRepository: PortRepository
) : ViewModel() {

    private val _weatherState = MutableLiveData<Result<List<WeatherCondition>>>()
    val weatherState: LiveData<Result<List<WeatherCondition>>> = _weatherState

    private val _userLocation = MutableLiveData<Location?>()
    val userLocation: LiveData<Location?> = _userLocation

    private val _selectedRadius = MutableLiveData<Int>(50) // Default 50km
    val selectedRadius: LiveData<Int> = _selectedRadius

    private var currentRadiusKm: Double = 50.0
    private var displayRadius: Double = 50.0
    private var currentUnit: String = "km"

    private var allPorts: List<FirestorePort> = emptyList()

    fun setUserLocation(location: Location) {
        _userLocation.value = location
        refreshData()
    }

    fun applyFilter(radiusValue: Double, unit: String) {
        currentUnit = unit
        displayRadius = radiusValue
        currentRadiusKm = if (unit.equals("mi", ignoreCase = true)) {
            radiusValue * 1.60934
        } else {
            radiusValue
        }
        // Use the Integer LiveData just to trigger UI updates if necessary, 
        // but we'll use the precise double for internal logic
        _selectedRadius.value = currentRadiusKm.toInt()
        refreshData()
    }

    fun getDisplayRadiusInfo(): String {
        return if (displayRadius % 1.0 == 0.0) {
            "${displayRadius.toInt()} $currentUnit"
        } else {
            "%.1f $currentUnit".format(displayRadius)
        }
    }

    init {
        loadPortsAndObserveWeather()
    }

    private fun loadPortsAndObserveWeather() {
        viewModelScope.launch {
            // Load ports once to get coordinates
            val portResult = portRepository.loadPorts()
            if (portResult is Result.Success) {
                allPorts = portResult.data
                observeWeather()
            } else if (portResult is Result.Error) {
                _weatherState.value = Result.Error(portResult.exception)
            }
        }
    }

    fun observeWeather() {
        viewModelScope.launch {
            // Ensure we are in a loading state while waiting for data and location
            _weatherState.value = Result.Loading
            
            weatherRepository.observeAllWeatherConditions()
                .collectLatest { result ->
                    if (result is Result.Success) {
                        val location = _userLocation.value
                        val radius = currentRadiusKm
                        
                        if (location == null) {
                            // Instead of showing empty results immediately, we wait for location.
                            // The activity should handle the "Location Needed" UI if it stays null too long.
                            _weatherState.value = Result.Loading
                        } else {
                            val filteredList = filterWeatherByDistance(result.data, location, radius)
                            _weatherState.value = Result.Success(filteredList)
                        }
                    } else {
                        _weatherState.value = result
                    }
                }
        }
    }

    private fun filterWeatherByDistance(
        weatherList: List<WeatherCondition>,
        userLoc: Location,
        radius: Double
    ): List<WeatherCondition> {
        // If radius is very large (e.g. All), don't filter but still enrich with port types
        val sourceList = if (radius >= 5000) weatherList else weatherList.filter { weather ->
            val port = allPorts.find { it.name.trim().equals(weather.location.trim(), ignoreCase = true) }
            if (port != null) {
                val portLoc = Location("").apply {
                    latitude = port.lat
                    longitude = port.lng
                }
                val distanceInMeters = userLoc.distanceTo(portLoc)
                distanceInMeters <= (radius * 1000)
            } else {
                false
            }
        }

        // Enrich with port types
        return sourceList.map { weather ->
            val port = allPorts.find { it.name.trim().equals(weather.location.trim(), ignoreCase = true) }
            weather.copy(portType = port?.type)
        }
    }

    fun refreshData() {
        loadPortsAndObserveWeather()
    }
}
