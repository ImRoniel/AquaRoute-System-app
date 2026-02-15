package com.example.aquaroute_system.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility class for formatting dates and times
 */
object DateFormatter {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    fun formatTime(date: Date): String = timeFormat.format(date)

    fun formatDateTime(date: Date): String = dateFormat.format(date)
}
