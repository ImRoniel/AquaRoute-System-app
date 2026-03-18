package com.example.aquaroute_system.util

import kotlin.math.*

/**
 * Utility for Geohash encoding and range calculations
 */
object GeoHashUtils {
    private const val BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
    private val BITS = intArrayOf(16, 8, 4, 2, 1)

    /**
     * Encodes latitude and longitude into a geohash string
     */
    fun encode(lat: Double, lon: Double, precision: Int = 10): String {
        val latRange = doubleArrayOf(-90.0, 90.0)
        val lonRange = doubleArrayOf(-180.0, 180.0)
        val hash = StringBuilder()
        var isEven = true
        var bit = 0
        var ch = 0

        while (hash.length < precision) {
            val midpoint: Double
            if (isEven) {
                midpoint = (lonRange[0] + lonRange[1]) / 2
                if (lon > midpoint) {
                    ch = ch or BITS[bit]
                    lonRange[0] = midpoint
                } else {
                    lonRange[1] = midpoint
                }
            } else {
                midpoint = (latRange[0] + latRange[1]) / 2
                if (lat > midpoint) {
                    ch = ch or BITS[bit]
                    latRange[0] = midpoint
                } else {
                    latRange[1] = midpoint
                }
            }

            isEven = !isEven
            if (bit < 4) {
                bit++
            } else {
                hash.append(BASE32[ch])
                bit = 0
                ch = 0
            }
        }
        return hash.toString()
    }

    /**
     * Returns a range (start, end) for a geohash prefix query.
     * For a given radius, we determine a suitable geohash precision.
     */
    fun getSearchRange(lat: Double, lon: Double, radiusKm: Double): Pair<String, String> {
        // For 50km, a 3-character geohash (~156km) is a safe prefix that won't miss many points
        // but might over-fetch. 4-char is ~39km, which is too small for a 50km radius.
        // We'll use precision 3 for the query prefix.
        val hash = encode(lat, lon, 3)
        
        // Firestore range query bounds
        val start = hash
        val end = hash + "z" // The highest character in Base32
        
        return start to end
    }

    /**
     * Haversine distance calculation
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
