package com.example.aquaroute_system.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aquaroute_system.data.models.Ferry
import com.example.aquaroute_system.data.models.Result
import com.example.aquaroute_system.data.models.WeatherCondition
import com.example.aquaroute_system.data.repository.FerryRepository
import com.example.aquaroute_system.data.repository.WeatherRefreshRepository
import com.example.aquaroute_system.data.repository.WeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FerriesViewModel(
    private val ferryRepository: FerryRepository,
    private val weatherRepository: WeatherRepository,
    private val weatherRefreshRepository: WeatherRefreshRepository
) : ViewModel() {

    private val _ferries = MutableLiveData<List<Ferry>>(emptyList())
    val ferries: LiveData<List<Ferry>> = _ferries

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // Weather for the currently selected ferry's destination port
    private val _destinationWeather = MutableLiveData<Result<WeatherCondition>>()
    val destinationWeather: LiveData<Result<WeatherCondition>> = _destinationWeather

    private var ferryListJob: Job? = null
    private var weatherJob: Job? = null

    init {
        observeFerries()
    }

    private fun observeFerries() {
        ferryListJob?.cancel()
        ferryListJob = viewModelScope.launch {
            _isLoading.value = true
            ferryRepository.observeAllFerries().collect { result ->
                when (result) {
                    is Result.Success -> {
                        _isLoading.value = false
                        _errorMessage.value = null
                        // Show all ferries — filter to "active" statuses if available
                        val active = result.data.filter { ferry ->
                            ferry.status.lowercase() in listOf("at sea", "active", "en route", "underway", "docked")
                        }
                        Log.d("FerriesVM", "Ferries: ${result.data.size} total, ${active.size} filtered")
                        _ferries.value = active.ifEmpty { result.data }
                    }
                    is Result.Error -> {
                        _isLoading.value = false
                        _errorMessage.value = "Failed to load ferries: ${result.exception.message}"
                        Log.e("FerriesVM", "Error: ${result.exception.message}")
                    }
                    is Result.Loading -> _isLoading.value = true
                }
            }
        }
    }

    /**
     * Calls the Render production backend to refresh weather for the destination port,
     * then reads the updated Firestore weather doc.
     * @param destinationPortId  Firestore document ID in the `weather` collection (same as the port doc ID)
     */
    fun fetchWeatherForDestination(destinationPortId: String) {
        if (destinationPortId.isBlank()) {
            _destinationWeather.value = Result.Error(Exception("No destination port ID available"))
            return
        }
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            Log.d("WeatherTrace", "[FerriesVM] fetchWeatherForDestination: $destinationPortId")
            _destinationWeather.value = Result.Loading

            // 1. Trigger backend refresh (production Render URL)
            val refreshResult = weatherRefreshRepository.requestRefresh(listOf(destinationPortId))
            when (refreshResult) {
                is Result.Success -> Log.d("WeatherTrace", "[FerriesVM] Refresh OK")
                is Result.Error  -> Log.w("WeatherTrace", "[FerriesVM] Refresh failed: ${refreshResult.exception.message}")
                else -> {}
            }

            // 2. Brief wait for Firestore write
            delay(2000)

            // 3. Read weather doc from Firestore
            weatherRepository.getWeatherForLocation(destinationPortId).collect { result ->
                Log.d("WeatherTrace", "[FerriesVM] destinationWeather: $result")
                _destinationWeather.value = result
            }
        }
    }

    fun clearError() { _errorMessage.value = null }

    fun refresh() { observeFerries() }

    override fun onCleared() {
        super.onCleared()
        ferryListJob?.cancel()
        weatherJob?.cancel()
    }
}
