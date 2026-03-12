package com.example.aquaroute_system.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aquaroute_system.data.repository.*
import com.example.aquaroute_system.util.SessionManager

class UserDashboardViewModelFactory(
    private val sessionManager: SessionManager,
    private val dashboardRepository: DashboardRepository,
    private val portStatusRepository: PortStatusRepository,
    private val weatherRepository: WeatherRepository,
    private val cargoRepository: CargoRepository,
    private val ferryRepository: FerryRepository,
    private val portRepository: PortRepository,
    private val weatherRefreshRepository: WeatherRefreshRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserDashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UserDashboardViewModel(
                sessionManager,
                dashboardRepository,
                portStatusRepository,
                weatherRepository,
                cargoRepository,
                ferryRepository,
                portRepository,
                weatherRefreshRepository // ADD THIS
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}