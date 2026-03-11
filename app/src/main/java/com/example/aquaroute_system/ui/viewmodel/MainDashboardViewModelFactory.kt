package com.example.aquaroute_system.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aquaroute_system.data.repository.*
import com.example.aquaroute_system.util.SessionManager

class MainDashboardViewModelFactory(
    private val sessionManager: SessionManager,
    private val dashboardRepository: DashboardRepository,
    private val portStatusRepository: PortStatusRepository,
    private val weatherRepository: WeatherRepository,
    private val cargoRepository: CargoRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainDashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainDashboardViewModel(
                sessionManager,
                dashboardRepository,
                portStatusRepository,
                weatherRepository,
                cargoRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}