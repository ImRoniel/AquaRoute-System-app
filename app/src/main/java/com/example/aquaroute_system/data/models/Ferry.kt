package com.example.aquaroute_system.data.models

/**
 * Data class representing a ferry
 */
data class Ferry(
    val name: String,
    val route: String,
    var lat: Double,
    var lon: Double,
    val status: String,
    val eta: Int
)
