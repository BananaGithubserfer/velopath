package com.example.velopath.ui.theme

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppThemePreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _mode = MutableStateFlow(AppThemeMode.fromStorage(prefs.getString(KEY_MODE, null)))
    private val _riderSpeedKph = MutableStateFlow(prefs.getFloat(KEY_RIDER_SPEED_KPH, DEFAULT_RIDER_SPEED_KPH))

    val mode: StateFlow<AppThemeMode> = _mode.asStateFlow()
    val riderSpeedKph: StateFlow<Float> = _riderSpeedKph.asStateFlow()

    fun setMode(mode: AppThemeMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        prefs.edit {
            putString(KEY_MODE, mode.name)
        }
    }

    fun setRiderSpeedKph(speedKph: Float) {
        val normalized = speedKph.coerceIn(6f, 45f)
        if (_riderSpeedKph.value == normalized) return
        _riderSpeedKph.value = normalized
        prefs.edit {
            putFloat(KEY_RIDER_SPEED_KPH, normalized)
        }
    }

    companion object {
        private const val PREFS_NAME = "velopath_theme"
        private const val KEY_MODE = "theme_mode"
        private const val KEY_RIDER_SPEED_KPH = "rider_speed_kph"
        const val DEFAULT_RIDER_SPEED_KPH = 18f
    }
}
