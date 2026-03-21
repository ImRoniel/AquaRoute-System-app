package com.example.aquaroute_system.data.models

import android.os.Parcelable
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.parcelize.Parcelize

/**
 * Crash-proof User data class that maps correctly to documents created by the web dashboard.
 *
 * WEB PAYLOAD (models/users.js → Users.create()):
 * {
 *   uid:         string
 *   email:       string
 *   displayName: string
 *   role:        "user"
 *   userType:    "user"
 *   createdAt:   Firestore ServerTimestamp  ← NOT a Long!
 *   preferences: { darkMode: bool, language: string, notificationsEnabled: bool }
 *   // NOTE: photoUrl, phoneNumber, isEmailVerified, lastLoginAt are NOT written by the web
 * }
 *
 * KEY FIXES:
 * 1. createdAt / lastLoginAt: Firestore Timestamp cannot be deserialized into a Long.
 *    Fixed by keeping them as nullable Long with @JvmField defaults — Firestore SDK will
 *    coerce Timestamps to Long (milliseconds) when the field type is Long? or Long.
 *    Using Date type would also work but Long is already used by the session manager.
 * 2. preferences: Web only writes 3 of the 5 fields. All fields must have defaults or
 *    deserialization will fail with a "Failed to convert" crash.
 * 3. @PropertyName: Ensures field names match exactly between Kotlin (camelCase) and
 *    Firestore (already camelCase from the web), avoiding any mismatch.
 * 4. No required constructor parameters — every property has a default value so that
 *    toObject(User::class.java) never throws "no suitable constructor found".
 */
@Parcelize
data class User(
    @get:PropertyName("uid")     @set:PropertyName("uid")     var uid: String          = "",
    @get:PropertyName("email")   @set:PropertyName("email")   var email: String        = "",

    // Web stores "displayName", Android previously read "displayName" — matches ✓
    @get:PropertyName("displayName") @set:PropertyName("displayName") var displayName: String? = null,

    @get:PropertyName("photoUrl")    @set:PropertyName("photoUrl")    var photoUrl: String?    = null,
    @get:PropertyName("phoneNumber") @set:PropertyName("phoneNumber") var phoneNumber: String? = null,

    // Web writes createdAt as Firestore ServerTimestamp.
    // Firestore's toObject() converts Timestamp → Long (epoch ms) automatically when target field is Long.
    // Using Long? instead of Long avoids a crash when the field is absent.
    @get:PropertyName("createdAt")   @set:PropertyName("createdAt")   var createdAt: Long?    = null,
    @get:PropertyName("lastLoginAt") @set:PropertyName("lastLoginAt") var lastLoginAt: Long?  = null,

    @get:PropertyName("isEmailVerified") @set:PropertyName("isEmailVerified") var isEmailVerified: Boolean = false,

    // Web writes both "role" and "userType". We read "userType" (the canonical Android field).
    // If web-created user only has "role", the fallback is handled by getEffectiveRole().
    @get:PropertyName("userType") @set:PropertyName("userType") var userType: String  = "user",
    @get:PropertyName("role")     @set:PropertyName("role")     var role: String      = "user",

    @get:PropertyName("preferences") @set:PropertyName("preferences") var preferences: UserPreferences = UserPreferences()
) : Parcelable {

    /** Resolves the role field regardless of whether the document uses "role" or "userType" */
    fun getEffectiveRole(): String = when {
        role.isNotBlank() && role != "user" -> role
        userType.isNotBlank()               -> userType
        else                                -> "user"
    }

    /** Safe creation timestamp in millis — falls back to 0 if absent */
    fun createdAtMillis(): Long = createdAt ?: 0L

    /** Safe last-login timestamp in millis — falls back to createdAt or 0 */
    fun lastLoginMillis(): Long = lastLoginAt ?: createdAt ?: 0L
}

/**
 * Web writes: { darkMode: bool, language: string, notificationsEnabled: bool }
 * Android previously expected 5 fields. The 2 extra fields (locationTrackingEnabled, mapLayer)
 * are absent in web-created documents — they now have defaults so deserialization won't crash.
 */
@Parcelize
data class UserPreferences(
    @get:PropertyName("darkMode")               @set:PropertyName("darkMode")               var darkMode: Boolean               = false,
    @get:PropertyName("notificationsEnabled")   @set:PropertyName("notificationsEnabled")   var notificationsEnabled: Boolean   = true,
    @get:PropertyName("locationTrackingEnabled")@set:PropertyName("locationTrackingEnabled") var locationTrackingEnabled: Boolean = true,
    @get:PropertyName("mapLayer")               @set:PropertyName("mapLayer")               var mapLayer: String                = "normal",
    @get:PropertyName("language")               @set:PropertyName("language")               var language: String                = "en"
) : Parcelable