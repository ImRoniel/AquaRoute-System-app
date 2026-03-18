package com.example.aquaroute_system.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aquaroute_system.data.repository.FerryRepository
import com.example.aquaroute_system.data.repository.PortRepository
import com.example.aquaroute_system.util.SessionManager

class PortsViewModelFactory(
    private val portRepository: PortRepository,
    private val ferryRepository: FerryRepository,
    private val sessionManager: SessionManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PortsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PortsViewModel(portRepository, ferryRepository, sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
