package com.example.aquaroute_system.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * Repository for managing search history via SharedPreferences
 */
class SearchRepository(context: Context) {

    companion object {
        private const val PREFS_NAME = "MainDashboardPrefs"
        private const val KEY_LAST_SEARCH = "last_search_query"
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Get last search query
     */
    fun getLastSearchQuery(): String {
        return sharedPreferences.getString(KEY_LAST_SEARCH, "") ?: ""
    }

    /**
     * Save search query
     */
    fun saveSearchQuery(query: String) {
        sharedPreferences.edit().apply {
            putString(KEY_LAST_SEARCH, query)
            apply()
        }
    }

    /**
     * Clear search history
     */
    fun clearSearchHistory() {
        sharedPreferences.edit().apply {
            remove(KEY_LAST_SEARCH)
            apply()
        }
    }
}
