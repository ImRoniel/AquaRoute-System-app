package com.example.aquaroute_system.util

import com.example.aquaroute_system.data.models.Ferry
import kotlin.math.*

/**
 * A simple grid-based spatial index to optimize finding the nearest ferry.
 * Automatically buckets ferries into geographic coordinates for O(1) neighbor lookups
 * instead of calculating the exact Haversine distance for every ferry.
 */
class SpatialIndex(private val cellSizeDegrees: Double = 0.05) { // ~5.5km cells
    // Map where Key is "latIndex_lonIndex"
    private val grid = mutableMapOf<String, MutableList<Ferry>>()

    /**
     * Rebuilds the grid given a fresh list of ferries.
     */
    fun build(ferries: List<Ferry>) {
        grid.clear()
        for (ferry in ferries) {
            val latIdx = floor(ferry.lat / cellSizeDegrees).toInt()
            val lonIdx = floor(ferry.lon / cellSizeDegrees).toInt()
            val key = "${latIdx}_${lonIdx}"
            grid.getOrPut(key) { mutableListOf() }.add(ferry)
        }
    }

    // 1 degree latitude is exactly ~111.32 km
    private val degPerKmLat = 1.0 / 111.32
    
    // Longitude degree distance varies by latitude
    private fun getDegPerKmLon(lat: Double): Double {
        return 1.0 / (111.32 * cos(Math.toRadians(lat)).coerceAtLeast(0.01))
    }

    /**
     * Implements a progressive radius search.
     * Starts at initialRadiusKm, doubling until a ferry is found or maxRadiusKm is reached.
     */
    fun findNearestFerryExpandingRadius(
        userLat: Double, 
        userLon: Double, 
        maxRadiusKm: Double = 200.0,
        initialRadiusKm: Double = 1.0
    ): Pair<Ferry?, Double> {
        if (grid.isEmpty()) return null to 0.0

        var currentRadius = initialRadiusKm
        var closestFerry: Ferry? = null
        var minDistance = Double.MAX_VALUE

        val maxRadiusBounded = min(maxRadiusKm, 1000.0) // hard cap just in case

        while (currentRadius <= maxRadiusBounded) {
            val latDelta = currentRadius * degPerKmLat
            val lonDelta = currentRadius * getDegPerKmLon(userLat)

            val minLatIdx = floor((userLat - latDelta) / cellSizeDegrees).toInt()
            val maxLatIdx = floor((userLat + latDelta) / cellSizeDegrees).toInt()
            val minLonIdx = floor((userLon - lonDelta) / cellSizeDegrees).toInt()
            val maxLonIdx = floor((userLon + lonDelta) / cellSizeDegrees).toInt()

            for (latIdx in minLatIdx..maxLatIdx) {
                for (lonIdx in minLonIdx..maxLonIdx) {
                    val key = "${latIdx}_${lonIdx}"
                    val cellFerries = grid[key] ?: continue

                    for (ferry in cellFerries) {
                        val distance = calculateDistance(userLat, userLon, ferry.lat, ferry.lon)
                        if (distance <= currentRadius && distance < minDistance) {
                            minDistance = distance
                            closestFerry = ferry
                        }
                    }
                }
            }

            // Stop condition: at least one ferry found in the current radius
            if (closestFerry != null) {
                return closestFerry to minDistance
            }

            // Increase radius for next pass
            currentRadius *= 2.0
            
            // To ensure we eventually hit maxRadiusKm and don't skip over it infinitely
            if (currentRadius > maxRadiusBounded && closestFerry == null && (currentRadius / 2.0) != maxRadiusBounded) {
                currentRadius = maxRadiusBounded
            } else if (currentRadius > maxRadiusBounded) {
                break
            }
        }

        return null to 0.0
    }

    /**
     * Generic Haversine distance in kilometers
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return r * c
    }
}
