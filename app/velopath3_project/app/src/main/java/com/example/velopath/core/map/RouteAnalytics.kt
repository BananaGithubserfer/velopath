package com.example.velopath.core.map

import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.transport.bicycle.Route
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class RouteAnalytics(
    val distanceMeters: Double,
    val durationSeconds: Double,
    val ascentMeters: Double? = null,
    val descentMeters: Double? = null,
    val steepAscentGainMeters: Double? = null,
    val steepDescentDropMeters: Double? = null,
    val maxAscentGradePercent: Double? = null,
    val maxDescentGradePercent: Double? = null,
    val hasAutoRoad: Boolean = false,
    val requiresAccessPass: Boolean = false
)

class RouteAnalyticsService {
    private val cache = LinkedHashMap<String, RouteAnalytics>()

    suspend fun analyze(route: Route): RouteAnalytics = withContext(Dispatchers.IO) {
        val weight = route.weight
        val points = route.geometry.points
        val sampledPoints = samplePoints(points, maxSamples = 32)
        val cacheKey = sampledPoints.joinToString("|") { "${it.latitude.format6()},${it.longitude.format6()}" }
        cache[cacheKey]?.let { return@withContext it }
        val profile = fetchElevationProfile(sampledPoints)

        val (ascent, descent, steepUpGain, steepDownDrop, maxUpGrade, maxDownGrade) = profile?.let { elevations ->
            computeElevationStats(sampledPoints, elevations)
        } ?: ElevationStats(null, null, null, null, null, null)

        RouteAnalytics(
            distanceMeters = weight.distance.value,
            durationSeconds = weight.time.value,
            ascentMeters = ascent,
            descentMeters = descent,
            steepAscentGainMeters = steepUpGain,
            steepDescentDropMeters = steepDownDrop,
            maxAscentGradePercent = maxUpGrade,
            maxDescentGradePercent = maxDownGrade,
            hasAutoRoad = route.flags?.hasAutoRoad == true,
            requiresAccessPass = route.flags?.requiresAccessPass == true
        ).also { analytics ->
            if (cache.size >= 12) {
                cache.remove(cache.entries.first().key)
            }
            cache[cacheKey] = analytics
        }
    }

    private fun samplePoints(points: List<Point>, maxSamples: Int): List<Point> {
        if (points.size <= maxSamples) return points
        val result = ArrayList<Point>(maxSamples)
        val step = (points.size - 1).toDouble() / (maxSamples - 1).toDouble()
        for (index in 0 until maxSamples) {
            result += points[(index * step).toInt().coerceIn(points.indices)]
        }
        return result.distinctBy { "${it.latitude},${it.longitude}" }
    }

    private fun fetchElevationProfile(points: List<Point>): List<Double>? {
        if (points.size < 2) return null

        val locationQuery = points.joinToString("|") { "${it.latitude},${it.longitude}" }
        val connection = URL("https://api.opentopodata.org/v1/mapzen?locations=$locationQuery").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 4000
        connection.readTimeout = 4000
        connection.useCaches = true

        return runCatching {
            connection.inputStream.bufferedReader().use { reader ->
                val body = reader.readText()
                val root = JSONObject(body)
                val results = root.optJSONArray("results") ?: JSONArray()
                buildList(results.length()) {
                    for (index in 0 until results.length()) {
                        add(results.getJSONObject(index).optDouble("elevation"))
                    }
                }
            }
        }.getOrNull()
    }

    private fun computeElevationStats(points: List<Point>, elevations: List<Double>): ElevationStats {
        if (points.size < 2 || elevations.size < 2) return ElevationStats(null, null, null, null, null, null)

        var ascent = 0.0
        var descent = 0.0
        var steepUpGain = 0.0
        var steepDownDrop = 0.0
        var maxUpGrade = Double.NEGATIVE_INFINITY
        var maxDownGrade = Double.POSITIVE_INFINITY

        val size = minOf(points.size, elevations.size)
        for (index in 1 until size) {
            val elevationDelta = elevations[index] - elevations[index - 1]
            val distance = haversineDistanceMeters(points[index - 1], points[index])
            if (distance < 2.0) continue

            if (elevationDelta > 0) ascent += elevationDelta else descent += -elevationDelta

            val gradePercent = (elevationDelta / distance) * 100.0
            if (gradePercent > maxUpGrade) maxUpGrade = gradePercent
            if (gradePercent < maxDownGrade) maxDownGrade = gradePercent
            if (gradePercent >= 6.0) steepUpGain += elevationDelta.coerceAtLeast(0.0)
            if (gradePercent <= -6.0) steepDownDrop += (-elevationDelta).coerceAtLeast(0.0)
        }

        return ElevationStats(
            ascentMeters = ascent,
            descentMeters = descent,
            steepAscentGainMeters = steepUpGain,
            steepDescentDropMeters = steepDownDrop,
            maxAscentGradePercent = maxUpGrade.takeIf { it.isFinite() && it > 0.0 },
            maxDescentGradePercent = maxDownGrade.takeIf { it.isFinite() && it < 0.0 }?.let { -it }
        )
    }

    private fun haversineDistanceMeters(first: Point, second: Point): Double {
        val earthRadius = 6371000.0
        val lat1 = Math.toRadians(first.latitude)
        val lat2 = Math.toRadians(second.latitude)
        val dLat = Math.toRadians(second.latitude - first.latitude)
        val dLon = Math.toRadians(second.longitude - first.longitude)
        val a = sin(dLat / 2).pow(2.0) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}

private fun Double.format6(): String = String.format(java.util.Locale.US, "%.6f", this)

private data class ElevationStats(
    val ascentMeters: Double?,
    val descentMeters: Double?,
    val steepAscentGainMeters: Double?,
    val steepDescentDropMeters: Double?,
    val maxAscentGradePercent: Double?,
    val maxDescentGradePercent: Double?
)
