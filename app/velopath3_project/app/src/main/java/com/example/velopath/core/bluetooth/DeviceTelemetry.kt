package com.example.velopath.core.bluetooth

data class DeviceTelemetry(
    val compassReady: Boolean? = null,
    val leftActive: Boolean? = null,
    val rightActive: Boolean? = null,
    val escCalibrating: Boolean? = null,
    val uptimeMs: Long? = null,
    val headingDegrees: Float? = null,
    val zeroOffsetDegrees: Float = 0f,
    val rawX: Float? = null,
    val rawY: Float? = null,
    val rawZ: Float? = null,
    val whoAmI: String? = null,
    val lastMessage: String? = null
) {
    val calibratedHeadingDegrees: Float?
        get() = headingDegrees?.let { normalizeDegrees(it - zeroOffsetDegrees) }

    fun withZeroOffset(): DeviceTelemetry {
        return copy(zeroOffsetDegrees = headingDegrees ?: zeroOffsetDegrees)
    }
}

internal fun normalizeDegrees(value: Float): Float {
    var normalized = value % 360f
    if (normalized < 0f) normalized += 360f
    return normalized
}
