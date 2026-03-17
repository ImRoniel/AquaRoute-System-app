package com.example.aquaroute_system.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aquaroute_system.data.repository.PortRepository
import com.example.aquaroute_system.data.repository.WeatherRepository

class WeatherAdvisoriesViewModelFactory(
    private val weatherRepository: WeatherRepository,
    private val portRepository: PortRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherAdvisoriesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WeatherAdvisoriesViewModel(weatherRepository, portRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
