package com.example.aquaroute_system.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PortStatus(
    val portId: String = "",
    val portName: String = "",
    val status: String = "open", // open, limited, closed
    val statusIcon: String = "🟢",
    val advisory: String? = null,
    val weather: WeatherCondition? = null,
    val congestion: String = "low", // low, medium, high
    val activeVessels: Int = 0,
    val waitingVessels: Int = 0,
    val estimatedWaitTime: Int = 0, // in minutes
    val updatedAt: Long = System.currentTimeMillis()
) : Parcelable {

    fun getStatusColor(): String {
        return when (status.lowercase()) {
            "open" -> "🟢"
            "limited" -> "🟡"
            "closed" -> "🔴"
            else -> "⚪"
        }
    }

    fun getDisplayText(): String {
        val icon = getStatusColor()
        return if (advisory != null) {
            "$icon $portName: ${status.uppercase()} ($advisory)"
        } else {
            "$icon $portName: ${status.uppercase()}"
        }
    }
}