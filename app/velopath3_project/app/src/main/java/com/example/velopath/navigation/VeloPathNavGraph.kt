package com.example.velopath.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.velopath.feature.dashboard.DashboardScreen
import com.example.velopath.feature.device.DeviceDiagnosticsScreen
import com.example.velopath.feature.ride.RideMapScreen
import com.example.velopath.feature.settings.SettingsScreen
import com.example.velopath.ui.theme.AppThemeMode

@Composable
fun VeloPathNavGraph(
    modifier: Modifier = Modifier,
    startDestinationOverride: String? = null,
    themeMode: AppThemeMode,
    riderSpeedKph: Float,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onRiderSpeedChange: (Float) -> Unit
) {
    val navController = rememberNavController()

    LaunchedEffect(startDestinationOverride) {
        if (!startDestinationOverride.isNullOrBlank()) {
            navController.navigate(startDestinationOverride) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestinationOverride ?: VeloPathDestination.Dashboard.route,
        modifier = modifier
    ) {
        composable(VeloPathDestination.Dashboard.route) {
            DashboardScreen(
                onOpenDiagnostics = {
                    navController.navigate(VeloPathDestination.DeviceLab.route)
                },
                onOpenSettings = {
                    navController.navigate(VeloPathDestination.Settings.route)
                },
                onDestinationPicked = { latitude, longitude ->
                    navController.navigate(
                        VeloPathDestination.RideMap.createRoute(latitude, longitude)
                    )
                }
            )
        }

        composable(VeloPathDestination.DeviceLab.route) {
            DeviceDiagnosticsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(VeloPathDestination.Settings.route) {
            SettingsScreen(
                themeMode = themeMode,
                riderSpeedKph = riderSpeedKph,
                onThemeModeChange = onThemeModeChange,
                onRiderSpeedChange = onRiderSpeedChange,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = VeloPathDestination.RideMap.route,
            arguments = listOf(
                navArgument("latitude") { type = NavType.StringType },
                navArgument("longitude") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val latitude = backStackEntry.arguments?.getString("latitude")?.toDoubleOrNull() ?: 0.0
            val longitude = backStackEntry.arguments?.getString("longitude")?.toDoubleOrNull() ?: 0.0

            RideMapScreen(
                destinationLatitude = latitude,
                destinationLongitude = longitude,
                riderSpeedKph = riderSpeedKph,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
