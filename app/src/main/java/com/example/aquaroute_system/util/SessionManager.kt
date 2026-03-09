package com.example.aquaroute_system.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.aquaroute_system.data.models.User
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("aquaroute_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_USER_DATA = "user_data"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_LOGIN_TIME = "login_time"
        private const val KEY_RECENT_SEARCHES = "recent_searches"
    }

    fun saveUserSession(user: User, authToken: String = "") {
        Log.d("SessionManager", "Saving user session for: ${user.email}")
        val editor = prefs.edit()
        editor.putString(KEY_USER_ID, user.uid)
        editor.putString(KEY_USER_EMAIL, user.email)
        editor.putString(KEY_USER_NAME, user.displayName)
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.putString(KEY_AUTH_TOKEN, authToken)
        editor.putLong(KEY_LOGIN_TIME, System.currentTimeMillis())

        // Save full user object as JSON
        val userJson = gson.toJson(user)
        Log.d("SessionManager", "User JSON: $userJson")
        editor.putString(KEY_USER_DATA, userJson)

        // Use commit() for immediate write instead of apply()
        val success = editor.commit()
        Log.d("SessionManager", "Session saved successfully: $success")

        // Verify immediately
        debugPrintAllPrefs()
    }

    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun getUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }

    fun getUserEmail(): String? {
        return prefs.getString(KEY_USER_EMAIL, null)
    }

    fun getUserName(): String? {
        return prefs.getString(KEY_USER_NAME, null)
    }

    fun getUserData(): User? {
        val userJson = prefs.getString(KEY_USER_DATA, null) ?: return null
        return try {
            gson.fromJson(userJson, User::class.java)
        } catch (e: Exception) {
            Log.e("SessionManager", "Error parsing user data", e)
            null
        }
    }

    fun getAuthToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    fun clearSession() {
        Log.d("SessionManager", "Clearing session")
        val editor = prefs.edit()
        editor.clear()
        editor.commit()
    }

    fun updateUserData(user: User) {
        Log.d("SessionManager", "Updating user data for: ${user.email}")
        val editor = prefs.edit()
        val userJson = gson.toJson(user)
        editor.putString(KEY_USER_DATA, userJson)
        editor.putString(KEY_USER_NAME, user.displayName)
        editor.commit()
    }

    fun isSessionValid(): Boolean {
        val loginTime = prefs.getLong(KEY_LOGIN_TIME, 0)
        val currentTime = System.currentTimeMillis()
        // Session valid for 30 days
        val sessionDuration = currentTime - loginTime
        return isLoggedIn() && sessionDuration < (30 * 24 * 60 * 60 * 1000)
    }

    fun saveRecentSearches(searches: List<String>) {
        val json = gson.toJson(searches)
        prefs.edit().putString(KEY_RECENT_SEARCHES, json).apply()
    }

    fun getRecentSearches(): List<String> {
        val json = prefs.getString(KEY_RECENT_SEARCHES, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getLoginTime(): Long? {
        return prefs.getLong(KEY_LOGIN_TIME, 0)
    }

    fun debugPrintAllPrefs() {
        Log.d("SessionManager", "=== ALL PREFERENCES ===")
        Log.d("SessionManager", "KEY_USER_ID: ${getUserId()}")
        Log.d("SessionManager", "KEY_USER_EMAIL: ${getUserEmail()}")
        Log.d("SessionManager", "KEY_USER_NAME: ${getUserName()}")
        Log.d("SessionManager", "KEY_IS_LOGGED_IN: ${isLoggedIn()}")
        Log.d("SessionManager", "KEY_LOGIN_TIME: ${getLoginTime()}")
        Log.d("SessionManager", "KEY_USER_DATA exists: ${prefs.contains(KEY_USER_DATA)}")
        Log.d("SessionManager", "All keys: ${prefs.all.keys}")
    }
}