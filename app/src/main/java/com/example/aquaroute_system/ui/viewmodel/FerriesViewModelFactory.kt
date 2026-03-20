package com.example.aquaroute_system.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aquaroute_system.data.repository.FerryRepository
import com.example.aquaroute_system.data.repository.WeatherRefreshRepository
import com.example.aquaroute_system.data.repository.WeatherRepository

class FerriesViewModelFactory(
    private val ferryRepository: FerryRepository,
    private val weatherRepository: WeatherRepository,
    private val weatherRefreshRepository: WeatherRefreshRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FerriesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FerriesViewModel(ferryRepository, weatherRepository, weatherRefreshRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
