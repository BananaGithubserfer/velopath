package com.example.velopath.navigation

sealed class VeloPathDestination(val route: String) {
    data object Dashboard : VeloPathDestination("dashboard")
    data object Settings : VeloPathDestination("settings")
    data object DeviceLab : VeloPathDestination("device_lab")
    data object RideMap : VeloPathDestination("ride_map/{latitude}/{longitude}") {
        fun createRoute(latitude: Double, longitude: Double): String {
            return "ride_map/$latitude/$longitude"
        }
    }
}
