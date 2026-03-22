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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class FerriesViewModel(
    private val ferryRepository: FerryRepository,
    private val weatherRepository: WeatherRepository,
    private val weatherRefreshRepository: WeatherRefreshRepository
) : ViewModel() {

    // ── Raw unfiltered ferry list (kept in memory for free local search) ──
    private val _allFerries = mutableListOf<Ferry>()

    // ── Search query StateFlow (updated on every keystroke, zero Firebase reads) ──
    private val _searchQuery = MutableStateFlow("")

    // ── Filtered list exposed to the Fragment ──────────────────────────
    private val _ferries = MutableLiveData<List<Ferry>>(emptyList())
    val ferries: LiveData<List<Ferry>> = _ferries

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _ferryWeather = MutableLiveData<Result<WeatherCondition>>()
    val ferryWeather: LiveData<Result<WeatherCondition>> = _ferryWeather

    private var ferryListJob: Job? = null
    private var weatherJob: Job? = null

    // ── Accepted active statuses (matches actual Firestore values) ─────
    companion object {
        private const val TAG = "FerriesVM"
        private val ACTIVE_STATUSES = setOf(
            "on_time",      // ← confirmed live Firestore value
            "delayed",
            "docked",
            "at sea",
            "active",
            "en route",
            "underway",
            "suspended",    // Admin updated
            "cancelled",
            "maintenance"
        )
    }

    init {
        observeFerries()
        // Whenever the search query changes → re-filter the in-memory list (no Firebase read)
        viewModelScope.launch {
            _searchQuery.collect { query ->
                _ferries.postValue(applySearch(_allFerries.toList(), query))
            }
        }
    }

    // ── Public API: called from Fragment on every search keystroke ─────
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // ── Real-time Firestore listener ───────────────────────────────────
    private fun observeFerries() {
        ferryListJob?.cancel()
        ferryListJob = viewModelScope.launch {
            _isLoading.value = true
            ferryRepository.observeAllFerries().collect { result ->
                when (result) {
                    is Result.Success -> {
                        _isLoading.value = false
                        _errorMessage.value = null

                        // Filter to active statuses; fall back to full list if none match
                        val active = result.data.filter { it.status.lowercase() in ACTIVE_STATUSES }
                        val toShow = active.ifEmpty { result.data }

                        Log.d(TAG, "Ferries: ${result.data.size} total, ${toShow.size} shown")

                        // Update master list → re-apply current search filter
                        _allFerries.clear()
                        _allFerries.addAll(toShow)
                        _ferries.postValue(applySearch(toShow, _searchQuery.value))
                    }
                    is Result.Error -> {
                        _isLoading.value = false
                        _errorMessage.value = "Failed to load ferries: ${result.exception.message}"
                        Log.e(TAG, "Error: ${result.exception.message}")
                    }
                    is Result.Loading -> _isLoading.value = true
                }
            }
        }
    }

    // ── Local search filter — pure in-memory, zero Firestore reads ─────
    private fun applySearch(ferries: List<Ferry>, query: String): List<Ferry> {
        if (query.isBlank()) return ferries
        val q = query.trim()
        return ferries.filter { ferry ->
            ferry.name.contains(q, ignoreCase = true) ||
            ferry.route.contains(q, ignoreCase = true)
        }
    }

    // ── Weather for the tapped ferry ──────────────────────────────────
    fun fetchWeatherForFerry(ferry: Ferry) {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            val ferryId = ferry.id
            Log.d("WeatherTrace", "[FerriesVM] fetchWeatherForFerry: $ferryId")

            _ferryWeather.value = Result.Loading

            // Step 1: Check Firestore cache (15-min TTL)
            var cachedAndFresh = false
            weatherRepository.getWeatherForLocation(ferryId).collect { result ->
                if (result is Result.Success) {
                    val ageMs = System.currentTimeMillis() - result.data.updatedAt
                    if (ageMs < 15 * 60 * 1000L) {
                        _ferryWeather.value = result
                        cachedAndFresh = true
                    }
                    return@collect
                }
            }
            if (cachedAndFresh) return@launch

            // Step 2: Backend refresh via live coordinates
            weatherRefreshRepository.requestRefreshByCoords(
                locationId = ferryId,
                lat = ferry.lat,
                lon = ferry.lon,
                name = ferry.name.ifBlank { ferryId }
            )

            // Step 3: Wait for write propagation, then read updated doc
            delay(2000)
            weatherRepository.getWeatherForLocation(ferryId).collect { result ->
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
