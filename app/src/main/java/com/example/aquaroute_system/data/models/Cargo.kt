package com.example.aquaroute_system.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Cargo(
    val id: String = "",
    val userId: String = "",
    val trackingNumber: String = "",
    val origin: String = "",
    val destination: String = "",
    val originPortId: String = "",
    val destinationPortId: String = "",
    val status: String = "processing", // processing, in_transit, delivered, delayed
    val eta: Long? = null,
    val departureTime: Long? = null,
    val cargoType: String = "",
    val weight: Double = 0.0,
    val volume: Double = 0.0,
    val units: Int = 1,
    val description: String = "",
    val value: Double? = null,
    val assignedFerryId: String? = null,
    val assignedFerryName: String? = null,
    val currentLocation: Location? = null,
    val progress: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deliveryDate: Long? = null,
    val isOnTime: Boolean? = true,
    val delayMinutes: Int? = 0
) : Parcelable

@Parcelize
data class Location(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
) : Parcelable

@Parcelize
data class CargoStatus(
    val cargoId: String = "",
    val status: String = "",
    val location: Location? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String = ""
) : Parcelable