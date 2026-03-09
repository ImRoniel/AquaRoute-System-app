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
            Log.d(TAG, "Attempting signup for: $email")
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("User creation failed")
            Log.d(TAG, "Firebase Auth successful for UID: ${firebaseUser.uid}")

            // Update profile with display name
            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .setDisplayName(displayName)
                .build()
            firebaseUser.updateProfile(profileUpdates).await()

            // Create user object
            val user = User(
                uid = firebaseUser.uid,
                email = email,
                displayName = displayName,
                createdAt = System.currentTimeMillis(),
                lastLoginAt = System.currentTimeMillis(),
                isEmailVerified = firebaseUser.isEmailVerified
            )
            Log.d(TAG, "Created user object: $user")

            // Save user to Firestore
            firestore.collection(USERS_COLLECTION)
                .document(firebaseUser.uid)
                .set(user)
                .await()
            Log.d(TAG, "Saved user to Firestore")

            // Save session locally
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
            Log.d(TAG, "Attempting login for: $email")
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
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

    // Get user from Firestore
    private suspend fun getUserFromFirestore(uid: String): User {
        val document = firestore.collection(USERS_COLLECTION)
            .document(uid)
            .get()
            .await()

        return if (document.exists()) {
            document.toObject(User::class.java) ?: createDefaultUser(uid)
        } else {
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