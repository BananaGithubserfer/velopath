package com.example.velopath.core.navigation

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.yandex.mapkit.geometry.Point
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class RouteLocationTracker(
    context: Context
) {
    private val appContext = context.applicationContext
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    fun updates(): Flow<GuidanceLocationUpdate> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(
                        GuidanceLocationUpdate(
                            point = Point(location.latitude, location.longitude),
                            speedMetersPerSecond = location.speed.toDouble().takeIf { it > 0.5 } ?: 4.0,
                            timestampMillis = location.time
                        )
                    )
                }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, appContext.mainLooper)
        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }
}
