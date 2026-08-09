package com.example.velopath.core.bluetooth

sealed interface BleConnectionState {
    data object Idle : BleConnectionState
    data object Scanning : BleConnectionState
    data class Connecting(val deviceName: String) : BleConnectionState
    data class Connected(val deviceName: String) : BleConnectionState
    data class Error(val message: String) : BleConnectionState
    data object Unsupported : BleConnectionState
}
