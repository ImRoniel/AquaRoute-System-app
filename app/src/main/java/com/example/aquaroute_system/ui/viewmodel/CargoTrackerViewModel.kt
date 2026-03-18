package com.example.aquaroute_system.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aquaroute_system.data.models.Cargo
import com.example.aquaroute_system.data.models.Result
import com.example.aquaroute_system.data.repository.CargoRepository
import com.example.aquaroute_system.data.repository.FerryRepository
import com.example.aquaroute_system.data.models.Ferry
import com.example.aquaroute_system.util.SessionManager
import kotlinx.coroutines.launch

class CargoTrackerViewModel(
    private val cargoRepository: CargoRepository,
    private val ferryRepository: FerryRepository,
    private val sessionManager: SessionManager
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

    fun searchCargoByReference(referenceNumber: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _notFound.value = false
            _searchResult.value = null

            val result = cargoRepository.getCargoByReference(referenceNumber)

            when (result) {
                is Result.Success -> {
                    val cargo = result.data
                    _searchResult.value = cargo
                    Log.d("CARGO_VM", "Found cargo: ${cargo.reference}")
                    
                    if (!cargo.ferryId.isNullOrEmpty()) {
                        // Fetch the actual ferry information
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

    fun testConnection() {
        viewModelScope.launch {
            val result = cargoRepository.testFirebaseConnection()
            when (result) {
                is Result.Success -> {
                    Log.d("FIREBASE_TEST", "SUCCESS: ${result.data}")
                }
                is Result.Error -> {
                    Log.e("FIREBASE_TEST", "FAILED: ${result.exception.message}")
                }
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