package com.example.aquaroute_system.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aquaroute_system.data.models.Result
import com.example.aquaroute_system.data.models.WeatherCondition
import com.example.aquaroute_system.data.repository.WeatherRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WeatherAdvisoriesViewModel(
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    private val _weatherState = MutableLiveData<Result<List<WeatherCondition>>>()
    val weatherState: LiveData<Result<List<WeatherCondition>>> = _weatherState

    init {
        observeWeather()
    }

    fun observeWeather() {
        viewModelScope.launch {
            weatherRepository.observeAllWeatherConditions()
                .collectLatest { result ->
                    _weatherState.value = result
                }
        }
    }

    fun refreshData() {
        // Since we use a snapshot listener, re-fetching is usually not necessary,
        // but we can restart the observation if needed for "fresher" state.
        observeWeather()
    }
}
