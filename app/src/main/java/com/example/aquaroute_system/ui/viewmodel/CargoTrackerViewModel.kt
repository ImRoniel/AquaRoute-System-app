package com.example.aquaroute_system.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aquaroute_system.data.models.Cargo
import com.example.aquaroute_system.data.models.Location
import com.example.aquaroute_system.data.models.Result
import com.example.aquaroute_system.data.repository.CargoRepository
import com.example.aquaroute_system.util.SessionManager
import kotlinx.coroutines.launch

class CargoTrackerViewModel(
    private val cargoRepository: CargoRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _cargoDetails = MutableLiveData<Cargo?>()
    val cargoDetails: LiveData<Cargo?> = _cargoDetails

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _recentSearches = MutableLiveData<List<String>>()
    val recentSearches: LiveData<List<String>> = _recentSearches

    fun trackCargo(referenceNumber: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            // For demo, create mock data
            val result = getMockCargo(referenceNumber)

            // FIXED: Use when instead of onSuccess/onFailure
            when (result) {
                is Result.Success -> {
                    _cargoDetails.value = result.data
                }
                is Result.Error -> {
                    _errorMessage.value = "Cargo not found: ${result.exception.message}"
                    _cargoDetails.value = null
                }
                else -> {} // Handle Loading if needed
            }

            _isLoading.value = false
        }
    }

    fun saveRecentSearch(referenceNumber: String) {
        val searches = _recentSearches.value?.toMutableList() ?: mutableListOf()

        // Remove if already exists
        searches.remove(referenceNumber)

        // Add to front
        searches.add(0, referenceNumber)

        // Keep only last 5
        if (searches.size > 5) {
            searches.removeAt(searches.lastIndex)
        }

        _recentSearches.value = searches

        // Save to SharedPreferences
        sessionManager.saveRecentSearches(searches)
    }

    fun loadRecentSearches() {
        val searches = sessionManager.getRecentSearches()
        _recentSearches.value = searches
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun getMockCargo(referenceNumber: String): Result<Cargo> {
        return try {
            // Mock data for demonstration
            val cargo = Cargo(
                id = "cargo_001",
                userId = "user_123",
                trackingNumber = referenceNumber,
                origin = "Manila",
                destination = "Cebu",
                status = "in_transit",
                eta = System.currentTimeMillis() + (2 * 60 * 60 * 1000), // 2 hours from now
                departureTime = System.currentTimeMillis() - (1 * 60 * 60 * 1000), // 1 hour ago
                cargoType = "Perishable Goods",
                weight = 500.0,
                volume = 2.5,
                units = 1,
                value = 150000.0,
                assignedFerryId = "ferry_001",
                assignedFerryName = "MV OceanJet 8",
                currentLocation = Location(14.5, 120.9),
                progress = 45,
                updatedAt = System.currentTimeMillis() - (2 * 60 * 1000) // 2 minutes ago
            )
            Result.Success(cargo)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}