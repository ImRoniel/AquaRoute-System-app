package com.example.aquaroute_system.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aquaroute_system.data.repository.CargoRepository
import com.example.aquaroute_system.data.repository.FerryRepository
import com.example.aquaroute_system.util.SessionManager

class CargoTrackerViewModelFactory(
    private val cargoRepository: CargoRepository,
    private val ferryRepository: FerryRepository,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CargoTrackerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CargoTrackerViewModel(cargoRepository, ferryRepository, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}