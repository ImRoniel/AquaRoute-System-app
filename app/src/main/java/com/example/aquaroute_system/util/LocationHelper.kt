package com.example.aquaroute_system.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object LocationHelper {

    private const val TAG = "LocationHelper"

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get last known location (suspend function for coroutines)
     */
    @SuppressLint("MissingPermission") // Safe because we check manually below
    suspend fun getLastLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) {
            Log.d(TAG, "No location permission")
            return null
        }

        val fusedLocationClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

        return suspendCancellableCoroutine { continuation ->
            val task: Task<Location> = fusedLocationClient.lastLocation

            task.addOnSuccessListener { location ->
                if (continuation.isActive) {
                    if (location != null) {
                        Log.d(TAG, "Got location: ${location.latitude}, ${location.longitude}")
                        continuation.resume(location)
                    } else {
                        Log.d(TAG, "Location is null")
                        continuation.resume(null)
                    }
                }
            }

            task.addOnFailureListener { exception ->
                if (continuation.isActive) {
                    Log.e(TAG, "Failed to get location", exception)
                    continuation.resumeWithException(exception)
                }
            }

            continuation.invokeOnCancellation {
                Log.d(TAG, "Location request was cancelled")
            }
        }
    }
}