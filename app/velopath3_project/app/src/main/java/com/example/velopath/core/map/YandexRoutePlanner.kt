package com.example.velopath.core.map

import android.content.Context
import com.yandex.mapkit.RequestPoint
import com.yandex.mapkit.RequestPointType
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.transport.TransportFactory
import com.yandex.mapkit.transport.bicycle.BicycleRouter
import com.yandex.mapkit.transport.bicycle.Route
import com.yandex.mapkit.transport.bicycle.Session
import com.yandex.mapkit.transport.bicycle.VehicleType
import com.yandex.runtime.Error
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class YandexRoutePlanner {
    constructor(context: Context) {
        MapKitInitializer.ensureInitialized(context)
    }

    private val bicycleRouter: BicycleRouter by lazy {
        TransportFactory.getInstance().createBicycleRouter()
    }

    suspend fun requestRoute(start: Point, finish: Point): Result<Route> {
        return suspendCancellableCoroutine { continuation ->
            val requestPoints = listOf(
                RequestPoint(start, RequestPointType.WAYPOINT, null, null, null),
                RequestPoint(finish, RequestPointType.WAYPOINT, null, null, null)
            )

            var session: Session? = null
            continuation.invokeOnCancellation { session?.cancel() }

            session = bicycleRouter.requestRoutes(
                requestPoints,
                VehicleType.BICYCLE,
                object : Session.RouteListener {
                    override fun onBicycleRoutes(routes: List<Route>) {
                        val route = routes.firstOrNull()
                        continuation.resume(
                            route?.let { Result.success(it) }
                                ?: Result.failure(IllegalStateException("No bicycle routes returned"))
                        )
                    }

                    override fun onBicycleRoutesError(error: Error) {
                        continuation.resume(
                            Result.failure(IllegalStateException(error.toString()))
                        )
                    }
                }
            )
        }
    }
}
