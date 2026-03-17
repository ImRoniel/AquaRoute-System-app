package com.example.aquaroute_system.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class WeatherCondition(
    val locationId: String = "",
    val location: String = "",
    val condition: String = "", // sunny, cloudy, rainy, stormy
    val icon: String = "☀️",
    val temperature: Double = 0.0,
    val feelsLike: Double = 0.0,
    val humidity: Int = 0,
    val waves: String = "0.0m",
    val windSpeed: String = "0 km/h",
    val windDirection: String = "N",
    val visibility: String = "10 km",
    val pressure: String = "1013 hPa",
    val hasAdvisory: Boolean = false,
    val advisoryMessage: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val portType: String? = null
) : Parcelable {

    val displayText: String
        get() = "$location: $icon $condition · Waves: $waves · Wind: $windSpeed"

    val detailedDisplay: String
        get() = "$location: $icon $condition\nWaves: $waves · Wind: $windSpeed\nHumidity: $humidity% · Visibility: $visibility"

    fun getAdvisoryText(): String {
        return if (hasAdvisory) {
            "[ADVISORY: $advisoryMessage]"
        } else {
            ""
        }
    }
}