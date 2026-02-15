package com.example.aquaroute_system.data.repository

import com.example.aquaroute_system.data.models.Ferry

/**
 * Repository for managing ferry data (local storage)
 */
class FerryRepository {

    private val ferryData = listOf(
        Ferry("MV Star Express", "Manila-Cavite", 14.55, 120.96, "on_time", 15),
        Ferry("MV Sea Princess", "Manila-Cavite", 14.50, 120.93, "delayed", 25),
        Ferry("MV Ocean King", "Manila-Batangas", 14.20, 120.98, "on_time", 40)
    )

    /**
     * Get all ferries
     */
    fun getAllFerries(): List<Ferry> = ferryData

    /**
     * Get ferry by name
     */
    fun getFerryByName(name: String): Ferry? = ferryData.find { it.name == name }

    /**
     * Update ferry position (for simulation)
     */
    fun updateFerryPosition(name: String, lat: Double, lon: Double): Boolean {
        val ferry = getFerryByName(name)
        return if (ferry != null) {
            ferry.lat = lat
            ferry.lon = lon
            true
        } else {
            false
        }
    }



}
