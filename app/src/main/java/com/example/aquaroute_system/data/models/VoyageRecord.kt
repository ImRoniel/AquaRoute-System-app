package com.example.aquaroute_system.data.models

data class VoyageRecord(
    val id: String,
    val trackingNumber: String,
    val origin: String,
    val destination: String,
    val cargoType: String,
    val weight: String,
    val status: String,
    val statusIcon: String,
    val deliveryDate: String,
    val deliveryTime: String,
    val isOnTime: Boolean,
    val delayMinutes: Int = 0
)