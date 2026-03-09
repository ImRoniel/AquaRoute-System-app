package com.example.aquaroute_system.data.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DashboardStats(
    val totalShipments: Int = 0,
    val inTransit: Int = 0,
    val delivered: Int = 0,
    val delayed: Int = 0,
    val activeVessels: Int = 0,
    var portsOpen: Int = 0,
    val portsLimited: Int = 0,
    val portsClosed: Int = 0
) : Parcelable