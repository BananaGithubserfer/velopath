package com.example.velopath.core.navigation

import android.hardware.GeomagneticField
import com.example.velopath.core.bluetooth.NavigationSignal
import com.example.velopath.core.bluetooth.TurnDirection
import com.yandex.mapkit.geometry.Point
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private data class TurnCue(
    val point: Point,
    val direction: TurnDirection,
    val trueBearingDegrees: Double,
    var advanceSent: Boolean = false,
    var turnSent: Boolean = false
)

class RouteGuidanceEngine(
    routePoints: List<Point>
) {
    private val destination = routePoints.lastOrNull()
    private val cues = buildTurnCues(routePoints).toMutableList()
    private var finishSent = false

    fun process(update: GuidanceLocationUpdate): List<NavigationSignal> {
        val signals = mutableListOf<NavigationSignal>()

        val activeCue = cues.firstOrNull { !it.turnSent }
        if (activeCue != null) {
            val distanceToTurn = distanceMeters(update.point, activeCue.point)
            val threshold = max(10.0, update.speedMetersPerSecond * 10.0)

            if (!activeCue.advanceSent && distanceToTurn <= threshold) {
                activeCue.advanceSent = true
                signals += NavigationSignal.UpcomingTurn(
                    direction = activeCue.direction,
                    secondsToTurn = 10,
                    distanceMeters = distanceToTurn.roundToInt()
                )
            }

            if (!activeCue.turnSent && distanceToTurn <= 12.0) {
                activeCue.turnSent = true
                signals += NavigationSignal.ExecuteTurn(
                    direction = activeCue.direction,
                    magneticHeadingDegrees = magneticHeading(update, activeCue.trueBearingDegrees)
                )
            }
        }

        val finishPoint = destination
        if (!finishSent && finishPoint != null && distanceMeters(update.point, finishPoint) <= 18.0) {
            finishSent = true
            signals += NavigationSignal.RouteFinished
        }

        return signals
    }

    private fun magneticHeading(update: GuidanceLocationUpdate, trueBearingDegrees: Double): Int {
        val geomagneticField = GeomagneticField(
            update.point.latitude.toFloat(),
            update.point.longitude.toFloat(),
            0f,
            update.timestampMillis
        )
        return normalizeDegrees(trueBearingDegrees - geomagneticField.declination.toDouble()).roundToInt()
    }

    private fun buildTurnCues(points: List<Point>): List<TurnCue> {
        if (points.size < 3) return emptyList()

        return buildList {
            for (index in 1 until points.lastIndex) {
                val previous = points[index - 1]
                val current = points[index]
                val next = points[index + 1]

                val incomingBearing = bearingDegrees(previous, current)
                val outgoingBearing = bearingDegrees(current, next)
                val delta = normalizedTurnAngle(outgoingBearing - incomingBearing)

                if (abs(delta) < 30.0) continue

                add(
                    TurnCue(
                        point = current,
                        direction = if (delta > 0) TurnDirection.RIGHT else TurnDirection.LEFT,
                        trueBearingDegrees = outgoingBearing
                    )
                )
            }
        }
    }

    private fun distanceMeters(first: Point, second: Point): Double {
        val earthRadius = 6371000.0
        val lat1 = Math.toRadians(first.latitude)
        val lat2 = Math.toRadians(second.latitude)
        val dLat = Math.toRadians(second.latitude - first.latitude)
        val dLon = Math.toRadians(second.longitude - first.longitude)

        val a = sin(dLat / 2).pow(2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun bearingDegrees(from: Point, to: Point): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return normalizeDegrees(Math.toDegrees(atan2(y, x)))
    }

    private fun normalizedTurnAngle(angle: Double): Double {
        var normalized = angle
        while (normalized <= -180.0) normalized += 360.0
        while (normalized > 180.0) normalized -= 360.0
        return normalized
    }

    private fun normalizeDegrees(value: Double): Double {
        var normalized = value % 360.0
        if (normalized < 0) normalized += 360.0
        return normalized
    }
}
