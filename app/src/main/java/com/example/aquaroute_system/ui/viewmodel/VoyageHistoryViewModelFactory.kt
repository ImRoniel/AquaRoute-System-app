package com.example.aquaroute_system.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aquaroute_system.data.repository.CargoRepository
import com.example.aquaroute_system.util.SessionManager

class VoyageHistoryViewModelFactory(
    private val cargoRepository: CargoRepository,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VoyageHistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VoyageHistoryViewModel(cargoRepository, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}