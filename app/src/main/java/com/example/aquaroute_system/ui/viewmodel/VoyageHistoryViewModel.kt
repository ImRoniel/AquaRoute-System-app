package com.example.aquaroute_system.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aquaroute_system.data.models.VoyageRecord
import com.example.aquaroute_system.data.models.Result
import com.example.aquaroute_system.data.repository.CargoRepository
import com.example.aquaroute_system.util.SessionManager
import kotlinx.coroutines.launch

class VoyageHistoryViewModel(
    private val cargoRepository: CargoRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _voyages = MutableLiveData<List<VoyageRecord>>()
    val voyages: LiveData<List<VoyageRecord>> = _voyages

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private var allVoyages = listOf<VoyageRecord>()
    var currentFilter = "all"
    private set

    fun loadVoyageHistory() {
        viewModelScope.launch {
            _isLoading.value = true

            // Get current user ID from session
            val userId = sessionManager.getUserId()
            if (userId == null) {
                _errorMessage.value = "User not logged in"
                _isLoading.value = false
                return@launch
            }

            // Fetch from repository (you'll need to add this method to CargoRepository)
            when (val result = cargoRepository.getUserVoyageHistory(userId)) {
                is Result.Success -> {
                    allVoyages = result.data
                    applyFilter(currentFilter)
                }
                is Result.Error -> {
                    _errorMessage.value = result.exception.message
                }
                else -> {}
            }

            _isLoading.value = false
        }
    }

    fun filterVoyages(filter: String) {
        currentFilter = filter
        applyFilter(filter)
    }

    private fun applyFilter(filter: String) {
        _voyages.value = when (filter) {
            "active" -> allVoyages.filter { it.status == "active" || it.status == "in_transit" }
            "completed" -> allVoyages.filter { it.status == "completed" }
            "delayed" -> allVoyages.filter { it.status == "delayed" }
            else -> allVoyages // "all"
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}