package com.example.velopath.core.navigation

import com.yandex.mapkit.geometry.Point

data class GuidanceLocationUpdate(
    val point: Point,
    val speedMetersPerSecond: Double,
    val timestampMillis: Long
)
