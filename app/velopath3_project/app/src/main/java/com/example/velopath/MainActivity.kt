package com.example.velopath

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.velopath.navigation.VeloPathNavGraph
import com.example.velopath.ui.theme.AppThemePreferences
import com.example.velopath.ui.theme.VelopathTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startDestinationOverride = buildStartDestinationOverride()

        setContent {
            val themePreferences = remember { AppThemePreferences(applicationContext) }
            val themeMode by themePreferences.mode.collectAsState()
            val riderSpeedKph by themePreferences.riderSpeedKph.collectAsState()

            VelopathTheme(themeMode = themeMode) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VeloPathNavGraph(
                        modifier = Modifier.padding(innerPadding),
                        startDestinationOverride = startDestinationOverride,
                        themeMode = themeMode,
                        riderSpeedKph = riderSpeedKph,
                        onThemeModeChange = themePreferences::setMode,
                        onRiderSpeedChange = themePreferences::setRiderSpeedKph
                    )
                }
            }
        }
    }

    private fun buildStartDestinationOverride(): String? {
        if (intent?.getBooleanExtra("open_device_lab", false) == true) {
            return "device_lab"
        }

        val latitude = intent?.getStringExtra("route_latitude")?.toDoubleOrNull()
        val longitude = intent?.getStringExtra("route_longitude")?.toDoubleOrNull()
        return if (latitude != null && longitude != null) {
            "ride_map/$latitude/$longitude"
        } else {
            null
        }
    }
}
