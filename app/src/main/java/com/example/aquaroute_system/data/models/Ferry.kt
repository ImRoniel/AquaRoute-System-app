package com.example.aquaroute_system.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize  // <- import this

/**
 * Data class representing a ferry
 */
@Parcelize
data class Ferry(
    val id: String = "",
    val name: String = "",
    var lat: Double = 0.0,
    var lon: Double = 0.0,
    val status: String = "",
    val eta: Int = 0,
    val route: String = ""
) : Parcelable