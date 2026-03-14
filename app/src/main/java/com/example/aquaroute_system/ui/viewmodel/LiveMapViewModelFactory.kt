package com.example.aquaroute_system.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aquaroute_system.data.repository.*
import com.example.aquaroute_system.util.SessionManager

class LiveMapViewModelFactory(
    private val sessionManager: SessionManager,
    private val portRepository: PortRepository,
    private val ferryRepository: FerryRepository,
    private val ferryRefreshRepository: FerryRefreshRepository   // ADDED
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LiveMapViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LiveMapViewModel(
                sessionManager,
                portRepository,
                ferryRepository,
                ferryRefreshRepository   // PASSED HERE
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}