package com.example.aquaroute_system.data.repository

import android.util.Log
import com.example.aquaroute_system.data.models.User
import com.example.aquaroute_system.data.models.UserPreferences
import com.example.aquaroute_system.util.SessionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val sessionManager: SessionManager
) {

    companion object {
        private const val TAG = "AuthRepository"
        private const val USERS_COLLECTION = "users"
    }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isUserLoggedIn: Boolean
        get() = sessionManager.isSessionValid() && auth.currentUser != null

    // Sign up with email and password
    suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): Result<User> {
        return try {
            val cleanEmail = email.trim().lowercase()
            Log.d(TAG, "Attempting signup for: $cleanEmail")
            val authResult = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
            val firebaseUser = authResult.user ?: throw Exception("User creation failed")
            Log.d(TAG, "Firebase Auth successful for UID: ${firebaseUser.uid}")

            // Update profile with display name
            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
            firebaseUser.updateProfile(profileUpdates).await()

            // ── Write the SAME schema the web dashboard uses (models/users.js) ──
            // This ensures any user — however created — has the same document shape.
            val firestorePayload = mapOf(
                "uid"         to firebaseUser.uid,
                "email"       to cleanEmail,
                "displayName" to displayName,
                "role"        to "user",
                "userType"    to "user",
                "createdAt"   to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "lastLoginAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "isEmailVerified" to firebaseUser.isEmailVerified,
                "preferences" to mapOf(
                    "darkMode"               to false,
                    "notificationsEnabled"   to true,
                    "locationTrackingEnabled" to true,
                    "mapLayer"               to "normal",
                    "language"               to "en"
                )
            )

            firestore.collection(USERS_COLLECTION)
                .document(firebaseUser.uid)
                .set(firestorePayload)
                .await()
            Log.d(TAG, "Saved user to Firestore")

            // Build a local User object for the session
            val user = User(
                uid           = firebaseUser.uid,
                email         = cleanEmail,
                displayName   = displayName,
                createdAt     = System.currentTimeMillis(),
                lastLoginAt   = System.currentTimeMillis(),
                isEmailVerified = firebaseUser.isEmailVerified,
                role          = "user",
                userType      = "user"
            )

            Log.d(TAG, "Saving session for user: ${user.email}")
            sessionManager.saveUserSession(user, firebaseUser.uid)

            Result.success(user)
        } catch (e: Exception) {
            Log.e(TAG, "signUpWithEmail error: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Login with email and password
    suspend fun loginWithEmail(email: String, password: String): Result<User> {
        return try {
            // ── Credential sanitization ──────────────────────────────────────────
            // The web admin form field is named 'username' and may not trim before
            // submitting to Firebase Auth. Normalize on the Android side to be safe.
            val cleanEmail    = email.trim().lowercase()
            val cleanPassword = password.trim()

            Log.d(TAG, "Attempting login for: $cleanEmail")
            val authResult = auth.signInWithEmailAndPassword(cleanEmail, cleanPassword).await()
            val firebaseUser = authResult.user ?: throw Exception("Login failed")
            Log.d(TAG, "Firebase Auth successful for UID: ${firebaseUser.uid}")

            // Get user data from Firestore
            val user = getUserFromFirestore(firebaseUser.uid)
            Log.d(TAG, "Got user from Firestore: $user")

            // Update last login time
            val updatedUser = user.copy(lastLoginAt = System.currentTimeMillis())

            // Save to Firestore
            firestore.collection(USERS_COLLECTION)
                .document(firebaseUser.uid)
                .update("lastLoginAt", System.currentTimeMillis())
                .await()
            Log.d(TAG, "Updated lastLogin in Firestore")

            // CRITICAL: Save session locally
            Log.d(TAG, "Saving session for user: ${updatedUser.email}")
            sessionManager.saveUserSession(updatedUser, firebaseUser.uid)

            Result.success(updatedUser)
        } catch (e: Exception) {
            Log.e(TAG, "loginWithEmail error: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Get user from Firestore — manually maps fields to avoid toObject() crashing on
    // Firestore Timestamp fields (createdAt) that cannot be auto-coerced to Long.
    private suspend fun getUserFromFirestore(uid: String): User {
        val document = firestore.collection(USERS_COLLECTION)
            .document(uid)
            .get()
            .await()

        if (!document.exists()) return createDefaultUser(uid)

        return try {
            val data = document.data ?: return createDefaultUser(uid)

            // Safely convert Firestore Timestamp OR Long to epoch milliseconds
            fun toMillis(raw: Any?): Long? = when (raw) {
                is com.google.firebase.Timestamp -> raw.toDate().time
                is Long                          -> raw
                is Double                        -> raw.toLong()
                else                             -> null
            }

            // Safely read the preferences sub-map (web only writes 3 of 5 fields)
            val prefsMap = data["preferences"] as? Map<*, *>
            val preferences = UserPreferences(
                darkMode               = prefsMap?.get("darkMode")               as? Boolean ?: false,
                notificationsEnabled   = prefsMap?.get("notificationsEnabled")   as? Boolean ?: true,
                locationTrackingEnabled= prefsMap?.get("locationTrackingEnabled")as? Boolean ?: true,
                mapLayer               = prefsMap?.get("mapLayer")               as? String  ?: "normal",
                language               = prefsMap?.get("language")               as? String  ?: "en"
            )

            User(
                uid             = data["uid"]             as? String  ?: uid,
                email           = data["email"]           as? String  ?: (auth.currentUser?.email ?: ""),
                displayName     = (data["displayName"]    as? String)?.trim(),
                photoUrl        = data["photoUrl"]        as? String,
                phoneNumber     = data["phoneNumber"]     as? String,
                createdAt       = toMillis(data["createdAt"]),
                lastLoginAt     = toMillis(data["lastLoginAt"]),
                isEmailVerified = data["isEmailVerified"] as? Boolean ?: (auth.currentUser?.isEmailVerified ?: false),
                userType        = data["userType"]        as? String  ?: "user",
                role            = data["role"]            as? String  ?: "user",
                preferences     = preferences
            ).also {
                Log.d(TAG, "✅ User mapped from Firestore: uid=${it.uid} email=${it.email} role=${it.role}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Manual mapping failed, falling back to default: ${e.message}", e)
            createDefaultUser(uid)
        }
    }

    private fun createDefaultUser(uid: String): User {
        val firebaseUser = auth.currentUser
        return User(
            uid = uid,
            email = firebaseUser?.email ?: "",
            displayName = firebaseUser?.displayName,
            photoUrl = firebaseUser?.photoUrl?.toString(),
            createdAt = System.currentTimeMillis(),
            lastLoginAt = System.currentTimeMillis()
        )
    }

    // Logout
    fun logout() {
        auth.signOut()
        sessionManager.clearSession()
    }

    // Send password reset email
    suspend fun sendPasswordResetEmail(email: String): Result<Nothing?> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Get current user from session
    fun getCurrentUser(): User? {
        return sessionManager.getUserData()
    }

    // Update user profile
    suspend fun updateUserProfile(
        displayName: String? = null,
        photoUrl: String? = null
    ): Result<User> {
        return try {
            val firebaseUser = auth.currentUser ?: throw Exception("No user logged in")

            // Update Firebase Auth profile
            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .apply {
                    displayName?.let { setDisplayName(it) }
                    photoUrl?.let { setPhotoUri(android.net.Uri.parse(it)) }
                }
                .build()

            firebaseUser.updateProfile(profileUpdates).await()

            // Get current user data
            val currentUser = sessionManager.getUserData() ?: throw Exception("User data not found")

            // Update user object
            val updatedUser = currentUser.copy(
                displayName = displayName ?: currentUser.displayName,
                photoUrl = photoUrl ?: currentUser.photoUrl
            )

            // Update in Firestore
            firestore.collection(USERS_COLLECTION)
                .document(firebaseUser.uid)
                .update(
                    mapOf(
                        "displayName" to updatedUser.displayName,
                        "photoUrl" to updatedUser.photoUrl
                    )
                )
                .await()

            // Update session
            sessionManager.updateUserData(updatedUser)

            Result.success(updatedUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Update user preferences
    suspend fun updateUserPreferences(preferences: UserPreferences): Result<Nothing?> {
        return try {
            val firebaseUser = auth.currentUser ?: throw Exception("No user logged in")
            val currentUser = sessionManager.getUserData() ?: throw Exception("User data not found")

            val updatedUser = currentUser.copy(preferences = preferences)

            // Update in Firestore
            firestore.collection(USERS_COLLECTION)
                .document(firebaseUser.uid)
                .update("preferences", preferences)
                .await()

            // Update session
            sessionManager.updateUserData(updatedUser)

            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Check if email is verified
    fun isEmailVerified(): Boolean {
        return auth.currentUser?.isEmailVerified == true
    }

    // Send email verification
    suspend fun sendEmailVerification(): Result<Nothing?> {
        return try {
            auth.currentUser?.sendEmailVerification()?.await()
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}