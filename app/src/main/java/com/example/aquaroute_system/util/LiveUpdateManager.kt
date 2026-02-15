package com.example.aquaroute_system.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Utility class for managing live updates with Coroutines
 */
class LiveUpdateManager(
    private val onUpdate: suspend () -> Unit,
    private val intervalMs: Long = 3000
) {

    private var updateJob: Job? = null

    /**
     * Start live updates
     */
    fun start(scope: CoroutineScope) {
        stop()
        updateJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                onUpdate()
                delay(intervalMs)
            }
        }
    }

    /**
     * Stop live updates
     */
    fun stop() {
        updateJob?.cancel()
        updateJob = null
    }

    /**
     * Check if updates are running
     */
    fun isRunning(): Boolean = updateJob?.isActive == true
}
