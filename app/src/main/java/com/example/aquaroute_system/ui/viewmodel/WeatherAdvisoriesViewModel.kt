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

    private var allPorts: List<FirestorePort> = emptyList()

    fun setUserLocation(location: Location) {
        _userLocation.value = location
        refreshData()
    }

    fun setSelectedRadius(radius: Int) {
        if (_selectedRadius.value != radius) {
            _selectedRadius.value = radius
            // Re-trigger observation or just re-filter if we have cached results.
            // Since observeWeather is collectLatest, it might be better to just re-emit or re-fetch.
            refreshData()
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
            weatherRepository.observeAllWeatherConditions()
                .collectLatest { result ->
                    if (result is Result.Success) {
                        val location = _userLocation.value
                        val radius = _selectedRadius.value ?: 50
                        
                        if (location == null) {
                            _weatherState.value = Result.Success(emptyList())
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
        radius: Int
    ): List<WeatherCondition> {
        // If radius is very large (e.g. All), don't filter
        if (radius >= 5000) return weatherList

        return weatherList.filter { weather ->
            val port = allPorts.find { it.name.trim().equals(weather.location.trim(), ignoreCase = true) }
            if (port != null) {
                val portLoc = Location("").apply {
                    latitude = port.lat
                    longitude = port.lng
                }
                val distanceInMeters = userLoc.distanceTo(portLoc)
                distanceInMeters <= (radius * 1000) // Convert km to meters
            } else {
                false
            }
        }
    }

    fun refreshData() {
        loadPortsAndObserveWeather()
    }
}
