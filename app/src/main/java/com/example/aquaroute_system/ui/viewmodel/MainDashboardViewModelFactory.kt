package com.example.aquaroute_system.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.aquaroute_system.data.repository.FerryRepository
import com.example.aquaroute_system.data.repository.PortRepository
import com.example.aquaroute_system.data.repository.SearchRepository

/**
 * Factory for creating MainDashboardViewModel instances
 */
class MainDashboardViewModelFactory(context: Context) : ViewModelProvider.Factory {

    private val portRepository = PortRepository()
    private val ferryRepository = FerryRepository()
    private val searchRepository = SearchRepository(context)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainDashboardViewModel::class.java)) {
            return MainDashboardViewModel(
                portRepository = portRepository,
                ferryRepository = ferryRepository,
                searchRepository = searchRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
