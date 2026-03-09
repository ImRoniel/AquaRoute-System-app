package com.example.aquaroute_system.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String? = null,
    val photoUrl: String? = null,
    val phoneNumber: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLoginAt: Long = System.currentTimeMillis(),
    val isEmailVerified: Boolean = false,
    val userType: String = "captain", // captain, admin, crew
    val preferences: UserPreferences = UserPreferences()
) : Parcelable

@Parcelize
data class UserPreferences(
    val darkMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val locationTrackingEnabled: Boolean = true,
    val mapLayer: String = "normal",
    val language: String = "en"
) : Parcelable