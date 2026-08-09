package com.example.velopath.ui.theme

enum class AppThemeMode {
    System,
    Light,
    Dark;

    companion object {
        fun fromStorage(value: String?): AppThemeMode =
            entries.firstOrNull { it.name == value } ?: System
    }
}
