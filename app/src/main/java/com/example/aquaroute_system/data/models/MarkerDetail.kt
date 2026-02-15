package com.example.aquaroute_system.data.models

/**
 * Sealed class representing different types of marker details
 */
sealed class MarkerDetail {
    data class FerryDetail(
        val ferry: Ferry
    ) : MarkerDetail()

    data class PortDetail(
        val port: Port
    ) : MarkerDetail()

    data class FirestorePortDetail(
        val port: FirestorePort
    ) : MarkerDetail()
}
