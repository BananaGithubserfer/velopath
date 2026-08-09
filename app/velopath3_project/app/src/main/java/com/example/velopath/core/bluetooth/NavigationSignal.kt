package com.example.velopath.core.bluetooth

import org.json.JSONObject

enum class TurnDirection {
    LEFT,
    RIGHT
}

sealed interface NavigationSignal {
    fun toLine(): String

    data class UpcomingTurn(
        val direction: TurnDirection,
        val secondsToTurn: Int,
        val distanceMeters: Int
    ) : NavigationSignal {
        override fun toLine(): String = JSONObject()
            .put("type", "advance_turn")
            .put("direction", direction.name.lowercase())
            .put("seconds_to_turn", secondsToTurn)
            .put("distance_meters", distanceMeters)
            .toString()
    }

    data class ExecuteTurn(
        val direction: TurnDirection,
        val magneticHeadingDegrees: Int
    ) : NavigationSignal {
        override fun toLine(): String = JSONObject()
            .put("type", "turn_now")
            .put("direction", direction.name.lowercase())
            .put("magnetic_heading_degrees", magneticHeadingDegrees)
            .toString()
    }

    data object RouteFinished : NavigationSignal {
        override fun toLine(): String = JSONObject()
            .put("type", "route_finished")
            .toString()
    }
}
