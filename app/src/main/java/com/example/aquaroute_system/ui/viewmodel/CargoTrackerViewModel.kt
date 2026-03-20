package com.example.aquaroute_system.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aquaroute_system.data.models.Cargo
import com.example.aquaroute_system.data.models.Ferry
import com.example.aquaroute_system.data.models.Result
import com.example.aquaroute_system.data.models.WeatherCondition
import com.example.aquaroute_system.data.repository.CargoRepository
import com.example.aquaroute_system.data.repository.FerryRepository
import com.example.aquaroute_system.data.repository.WeatherRefreshRepository
import com.example.aquaroute_system.data.repository.WeatherRepository
import com.example.aquaroute_system.util.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CargoTrackerViewModel(
    private val cargoRepository: CargoRepository,
    private val ferryRepository: FerryRepository,
    private val sessionManager: SessionManager,
    private val weatherRepository: WeatherRepository,
    private val weatherRefreshRepository: WeatherRefreshRepository
) : ViewModel() {

    private val _searchResult = MutableLiveData<Cargo?>()
    val searchResult: LiveData<Cargo?> = _searchResult

    private val _assignedFerry = MutableLiveData<Ferry?>()
    val assignedFerry: LiveData<Ferry?> = _assignedFerry

    private val _isMapExpanded = MutableLiveData(false)
    val isMapExpanded: LiveData<Boolean> = _isMapExpanded

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _notFound = MutableLiveData(false)
    val notFound: LiveData<Boolean> = _notFound

    /* Weather for the ferry carrying the cargo */
    private val _cargoFerryWeather = MutableLiveData<Result<WeatherCondition>>()
    val cargoFerryWeather: LiveData<Result<WeatherCondition>> = _cargoFerryWeather

    private var weatherJob: Job? = null

    fun searchCargoByReference(referenceNumber: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _notFound.value = false
            _searchResult.value = null
            _cargoFerryWeather.value = Result.Error(Exception("no_data")) // reset weather

            val result = cargoRepository.getCargoByReference(referenceNumber)

            when (result) {
                is Result.Success -> {
                    val cargo = result.data
                    _searchResult.value = cargo
                    Log.d("CARGO_VM", "Found cargo: ${cargo.reference}")

                    if (!cargo.ferryId.isNullOrEmpty()) {
                        try {
                            val ferries = ferryRepository.getAllFerries()
                            val ferry = ferries.find { it.id == cargo.ferryId }
                            _assignedFerry.value = ferry
                        } catch (e: Exception) {
                            Log.e("CARGO_VM", "Failed to fetch ferry details", e)
                            _assignedFerry.value = null
                        }
                    } else {
                        _assignedFerry.value = null
                    }
                }
                is Result.Error -> {
                    _errorMessage.value = "Cargo not found: ${result.exception.message}"
                    _notFound.value = true
                    Log.e("CARGO_VM", "Error: ${result.exception.message}")
                }
                else -> {}
            }

            _isLoading.value = false
        }
    }

    /**
     * Fetches weather for the ferry that is carrying this cargo.
     *
     * Edge-case guard: if the cargo isn't in transit (no assigned ferry), emit a sentinel
     * Result.Error("not_in_transit") so the fragment can show the appropriate message.
     *
     * Cache policy (15-min Firebase quota guard):
     *   1. Check Firestore weather/<ferry.id> — if updatedAt < 15 min old → emit cached data.
     *   2. Otherwise → POST /weather/refresh-by-coords → delay 2 s → re-read Firestore.
     */
    fun fetchWeatherForCargoFerry(ferry: Ferry) {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            val ferryId = ferry.id
            Log.d("WeatherTrace", "[CargoVM] fetchWeatherForCargoFerry: $ferryId (${ferry.lat},${ferry.lon})")

            _cargoFerryWeather.value = Result.Loading

            // ── Step 1: Check Firestore cache ─────────────────────────────────────────
            var cachedAndFresh = false
            weatherRepository.getWeatherForLocation(ferryId).collect { result ->
                if (result is Result.Success) {
                    val ageMs = System.currentTimeMillis() - result.data.updatedAt
                    val FIFTEEN_MIN_MS = 15 * 60 * 1000L
                    if (ageMs < FIFTEEN_MIN_MS) {
                        Log.d("WeatherTrace", "[CargoVM] Cache HIT for $ferryId (age=${ageMs / 1000}s)")
                        _cargoFerryWeather.value = result
                        cachedAndFresh = true
                    } else {
                        Log.d("WeatherTrace", "[CargoVM] Cache STALE for $ferryId (age=${ageMs / 1000}s)")
                    }
                    return@collect
                }
            }
            if (cachedAndFresh) return@launch

            // ── Step 2: Refresh via backend coords endpoint ───────────────────────────
            Log.d("WeatherTrace", "[CargoVM] Calling refresh-by-coords for $ferryId")
            val refreshResult = weatherRefreshRepository.requestRefreshByCoords(
                locationId = ferryId,
                lat = ferry.lat,
                lon = ferry.lon,
                name = ferry.name.ifBlank { ferryId }
            )
            when (refreshResult) {
                is Result.Success -> Log.d("WeatherTrace", "[CargoVM] Coords refresh OK")
                is Result.Error   -> Log.w("WeatherTrace", "[CargoVM] Coords refresh failed: ${refreshResult.exception.message}")
                else -> {}
            }

            // ── Step 3: Wait for Firestore propagation, then re-read ──────────────────
            delay(2000)
            weatherRepository.getWeatherForLocation(ferryId).collect { result ->
                Log.d("WeatherTrace", "[CargoVM] cargoFerryWeather after refresh: $result")
                _cargoFerryWeather.value = result
                return@collect
            }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            val result = cargoRepository.testFirebaseConnection()
            when (result) {
                is Result.Success -> Log.d("FIREBASE_TEST", "SUCCESS: ${result.data}")
                is Result.Error   -> Log.e("FIREBASE_TEST", "FAILED: ${result.exception.message}")
                else -> {}
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
        _notFound.value = false
    }

    fun setMapExpanded(expanded: Boolean) {
        if (_isMapExpanded.value != expanded) {
            _isMapExpanded.value = expanded
        }
    }
}