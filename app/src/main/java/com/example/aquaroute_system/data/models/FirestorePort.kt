package com.example.aquaroute_system.data.models

import com.google.firebase.Timestamp
import com.google.type.TimeZone
import java.util.*
/**
 * Data class representing a port loaded from Firestore
 */
data class FirestorePort(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val type: String,
    val status: String,
    val source: String,
    val createdAt: Timestamp,

    val openHour: Int? = null,
    val closeHour: Int? = null,
    val timeZone: String? = null


)
