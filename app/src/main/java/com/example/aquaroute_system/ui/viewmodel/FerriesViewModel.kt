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

    // Weather for the currently selected ferry's CURRENT LOCATION
    private val _ferryWeather = MutableLiveData<Result<WeatherCondition>>()
    val ferryWeather: LiveData<Result<WeatherCondition>> = _ferryWeather

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
     * Fetches current-location weather for the given ferry.
     *
     * Cache policy (Firebase quota guard):
     *   - If a valid weather doc exists in Firestore and is < 15 min old → emit cached data, skip backend.
     *   - Otherwise → POST /weather/refresh-by-coords with ferry.lat/lon → delay 2 s → re-read Firestore.
     */
    fun fetchWeatherForFerry(ferry: Ferry) {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            val ferryId = ferry.id
            Log.d("WeatherTrace", "[FerriesVM] fetchWeatherForFerry: $ferryId (${ferry.lat},${ferry.lon})")

            _ferryWeather.value = Result.Loading

            // ── Step 1: Check Firestore cache ──────────────────────────────────────
            var cachedAndFresh = false
            weatherRepository.getWeatherForLocation(ferryId).collect { result ->
                if (result is Result.Success) {
                    val ageMs = System.currentTimeMillis() - result.data.updatedAt
                    val FIFTEEN_MIN_MS = 15 * 60 * 1000L
                    if (ageMs < FIFTEEN_MIN_MS) {
                        Log.d("WeatherTrace", "[FerriesVM] Cache HIT for $ferryId (age=${ageMs/1000}s) — skipping backend")
                        _ferryWeather.value = result
                        cachedAndFresh = true
                    } else {
                        Log.d("WeatherTrace", "[FerriesVM] Cache STALE for $ferryId (age=${ageMs/1000}s)")
                    }
                    return@collect // stop the flow after first emission
                }
            }
            if (cachedAndFresh) return@launch

            // ── Step 2: Refresh from backend using live coordinates ────────────────
            Log.d("WeatherTrace", "[FerriesVM] Calling backend refresh-by-coords for $ferryId")
            val refreshResult = weatherRefreshRepository.requestRefreshByCoords(
                locationId = ferryId,
                lat = ferry.lat,
                lon = ferry.lon,
                name = ferry.name.ifBlank { ferryId }
            )
            when (refreshResult) {
                is Result.Success -> Log.d("WeatherTrace", "[FerriesVM] Coords refresh OK")
                is Result.Error  -> Log.w("WeatherTrace", "[FerriesVM] Coords refresh failed: ${refreshResult.exception.message}")
                else -> {}
            }

            // ── Step 3: Wait for Firestore write propagation, then read ───────────
            delay(2000)
            weatherRepository.getWeatherForLocation(ferryId).collect { result ->
                Log.d("WeatherTrace", "[FerriesVM] ferryWeather after refresh: $result")
                _ferryWeather.value = result
                return@collect
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
