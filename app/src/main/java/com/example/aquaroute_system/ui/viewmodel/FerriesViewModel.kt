package com.example.aquaroute_system.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aquaroute_system.data.models.Ferry
import com.example.aquaroute_system.data.repository.FerryRepository
import kotlinx.coroutines.launch

class FerriesViewModel(private val repository: FerryRepository) : ViewModel() {

    private val _ferries = MutableLiveData<List<Ferry>>()
    val ferries: LiveData<List<Ferry>> = _ferries

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    init {
        loadFerries()
    }

    fun loadFerries() {
        _isLoading.value = true
        _errorMessage.value = null
        
        viewModelScope.launch {
            try {
                val result = repository.getAllFerries()
                _ferries.value = result
                if (result.isEmpty()) {
                    _errorMessage.value = "No ferries available at the moment."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load ferries: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
