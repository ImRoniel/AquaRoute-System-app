package com.example.aquaroute_system.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Cargo(
    val id: String = "",
    val userId: String = "",
    // Keep trackingNumber for backward compatibility
    val trackingNumber: String = "",
    // Firebase reference field
    val reference: String = "",
    val origin: String = "",
    val destination: String = "",
    val originPortId: String = "",
    val destinationPortId: String = "",
    val status: String = "processing",
    val statusColor: String = "🟡",
    val eta: Long? = null,
    val departureTime: Long? = null,
    val deliveryDate: Long? = null,
    val isOnTime: Boolean? = true,
    val delayMinutes: Int? = 0,
    val delayReason: String? = null,
    val cargoType: String = "",
    val description: String = "",
    val weight: Double = 0.0,
    val volume: Double = 0.0,
    val units: Int = 1,
    val value: Double? = null,
    val assignedFerryId: String? = null,
    val assignedFerryName: String? = null,
    // Firebase fields
    val ferryId: String? = null,
    val createdBy: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val currentLocation: Location? = null,
    val progress: Int = 0,
    val recipientName: String = "",
    val recipientPhone: String = "",
    val recipientEmail: String = "",
    val recipientAddress: String = "",
    val specialHandling: List<String> = emptyList(),
    val notes: String = "",
    val documents: List<String> = emptyList()
) : Parcelable {

    fun getStatusDisplay(): String {
        return when (status.lowercase()) {
            "in_transit" -> "IN TRANSIT"
            "delivered" -> "DELIVERED"
            "delayed" -> "DELAYED"
            "processing", "pending" -> "PROCESSING"
            else -> status.uppercase()
        }
    }

    fun getStatusEmoji(): String {
        return when (status.lowercase()) {
            "in_transit" -> "🟢"
            "delivered" -> "✅"
            "delayed" -> "🔴"
            "processing", "pending" -> "🟡"
            else -> "⚪"
        }
    }

    fun getStatusColorRes(): Int {
        return when (status.lowercase()) {
            "processing", "pending" -> android.R.color.holo_orange_dark
            "in_transit" -> android.R.color.holo_green_dark
            "delivered" -> android.R.color.holo_blue_dark
            "delayed" -> android.R.color.holo_red_dark
            else -> android.R.color.darker_gray
        }
    }

    // Helper to get display reference
    fun getDisplayReference(): String {
        return if (reference.isNotEmpty()) reference else trackingNumber
    }
}

@Parcelize
data class Location(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = ""
) : Parcelable