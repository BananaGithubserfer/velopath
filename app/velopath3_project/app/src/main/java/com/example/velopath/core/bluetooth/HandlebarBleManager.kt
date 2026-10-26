package com.example.velopath.core.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.nio.charset.Charset
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HandlebarBleManager(
    private val context: Context
) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow<BleConnectionState>(BleConnectionState.Idle)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _consoleLines = MutableStateFlow<List<String>>(emptyList())
    val consoleLines: StateFlow<List<String>> = _consoleLines.asStateFlow()

    private val _telemetry = MutableStateFlow(DeviceTelemetry())
    val telemetry: StateFlow<DeviceTelemetry> = _telemetry.asStateFlow()

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private val incomingBuffer = StringBuilder()

    private val serviceUuid = UUID.fromString("0003cdd0-0000-1000-8000-00805f9b0131")
    private val notifyUuid = UUID.fromString("0003cdd1-0000-1000-8000-00805f9b0131")
    private val writeUuid = UUID.fromString("0003cdd2-0000-1000-8000-00805f9b0131")
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!hasBluetoothScanPermission() || !hasBluetoothConnectPermission()) {
                _connectionState.value = BleConnectionState.Error("Bluetooth permissions are missing.")
                return
            }

            val device = result.device ?: return
            val name = safeDeviceName(device, result.scanRecord?.deviceName) ?: return
            if (!matchesDeviceName(name)) return

            stopScanSafely()
            _connectionState.value = BleConnectionState.Connecting(name)
            connect(device.address, name)
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = BleConnectionState.Error("BLE scan failed: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                resetConnection("GATT status: $status", gatt)
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    bluetoothGatt = gatt
                    if (!hasBluetoothConnectPermission()) {
                        resetConnection("Bluetooth permissions are missing.", gatt)
                        return
                    }

                    try {
                        gatt.requestMtu(185)
                        gatt.discoverServices()
                    } catch (_: SecurityException) {
                        resetConnection("Bluetooth permissions are missing.", gatt)
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    resetConnection(null, gatt)
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (!hasBluetoothConnectPermission()) {
                    resetConnection("Bluetooth permissions are missing.", gatt)
                    return
                }
                try {
                    gatt.discoverServices()
                } catch (_: SecurityException) {
                    resetConnection("Bluetooth permissions are missing.", gatt)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = BleConnectionState.Error("Service discovery failed: $status")
                return
            }

            val service = gatt.getService(serviceUuid)
            val notify = service?.getCharacteristic(notifyUuid)
            val write = service?.getCharacteristic(writeUuid)

            if (notify == null || write == null) {
                _connectionState.value = BleConnectionState.Error("Device console characteristics are missing.")
                return
            }

            writeCharacteristic = write
            notifyCharacteristic = notify

            if (!hasBluetoothConnectPermission()) {
                _connectionState.value = BleConnectionState.Error("Bluetooth permissions are missing.")
                return
            }

            val notifyEnabled = try {
                gatt.setCharacteristicNotification(notify, true)
            } catch (_: SecurityException) {
                false
            }
            val descriptor = notify.getDescriptor(cccdUuid)
            val descriptorWritten = if (descriptor != null) {
                writeDescriptor(gatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                false
            }

            if (!notifyEnabled || !descriptorWritten) {
                _connectionState.value = BleConnectionState.Error("Failed to enable notifications.")
                return
            }

            val deviceLabel = safeDeviceName(gatt.device, gatt.device.address) ?: gatt.device.address
            _connectionState.value = BleConnectionState.Connected(deviceLabel)
            requestStatusSnapshot()
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == notifyUuid) {
                consumeIncomingBytes(value)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == notifyUuid) {
                consumeIncomingBytes(characteristic.value ?: byteArrayOf())
            }
        }
    }

    fun canUseBluetooth(): Boolean {
        if (bluetoothAdapter == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasBluetoothScanPermission() && hasBluetoothConnectPermission()
        } else {
            hasLegacyLocationPermission()
        }
    }

    fun requiredRuntimePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!canUseBluetooth()) {
            _connectionState.value = BleConnectionState.Error("Bluetooth permissions are missing.")
            return
        }

        val adapter = bluetoothAdapter ?: run {
            _connectionState.value = BleConnectionState.Unsupported
            return
        }

        if (!adapter.isEnabled) {
            _connectionState.value = BleConnectionState.Error("Bluetooth is disabled.")
            return
        }

        close()
        _connectionState.value = BleConnectionState.Scanning
        adapter.bluetoothLeScanner?.startScan(
            null,
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            scanCallback
        )
    }

    @SuppressLint("MissingPermission")
    fun sendSignal(signal: NavigationSignal): Result<Unit> = sendLine(signal.toLine())

    @SuppressLint("MissingPermission")
    fun sendCommand(command: String): Result<Unit> = sendLine(command.trim())

    fun calibrateHeadingZero() {
        _telemetry.value = _telemetry.value.withZeroOffset()
    }

    fun clearConsole() {
        _consoleLines.value = emptyList()
        _telemetry.value = _telemetry.value.copy(lastMessage = null)
    }

    @SuppressLint("MissingPermission")
    fun requestStatusSnapshot() {
        sendCommand("status")
        sendCommand("compass")
        sendCommand("compass raw")
        sendCommand("compass whoami")
    }

    @SuppressLint("MissingPermission")
    fun close() {
        stopScanSafely()
        try {
            bluetoothGatt?.close()
        } catch (_: SecurityException) {
            // Ignore teardown errors when permissions are revoked mid-session.
        }
        bluetoothGatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
        incomingBuffer.clear()
        _connectionState.value = BleConnectionState.Idle
    }

    @SuppressLint("MissingPermission")
    private fun connect(deviceAddress: String, deviceName: String) {
        if (!hasBluetoothConnectPermission()) {
            _connectionState.value = BleConnectionState.Error("Bluetooth permissions are missing.")
            return
        }
        val remoteDevice = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: run {
            _connectionState.value = BleConnectionState.Error("BLE device not found: $deviceName")
            return
        }
        try {
            bluetoothGatt?.close()
            bluetoothGatt = remoteDevice.connectGatt(context, false, gattCallback)
        } catch (_: SecurityException) {
            _connectionState.value = BleConnectionState.Error("Bluetooth permissions are missing.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendLine(line: String): Result<Unit> {
        val gatt = bluetoothGatt ?: return Result.failure(IllegalStateException("Bluetooth device is not connected"))
        val characteristic = writeCharacteristic ?: return Result.failure(IllegalStateException("Device console is not ready"))
        val payload = "$line\r\n".toByteArray(Charsets.UTF_8)

        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        if (!writeCharacteristic(gatt, characteristic, payload)) {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            if (!writeCharacteristic(gatt, characteristic, payload)) {
                return Result.failure(IllegalStateException("Failed to write BLE characteristic"))
            }
        }

        appendConsoleLine(">> $line")
        return Result.success(Unit)
    }

    private fun consumeIncomingBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return

        incomingBuffer.append(bytes.toString(Charset.forName("UTF-8")))

        while (true) {
            val newlineIndex = incomingBuffer.indexOf("\n")
            if (newlineIndex < 0) break

            val line = incomingBuffer.substring(0, newlineIndex)
                .replace("\r", "")
                .trim()
            incomingBuffer.delete(0, newlineIndex + 1)

            if (line.isNotBlank() && line != ">") {
                appendConsoleLine(line)
                parseTelemetry(line)
            }
        }

        if (incomingBuffer.toString().trim() == ">") {
            incomingBuffer.clear()
        }
    }

    private fun appendConsoleLine(line: String) {
        _consoleLines.value = (_consoleLines.value + line).takeLast(48)
        _telemetry.value = _telemetry.value.copy(lastMessage = line)
    }

    private fun parseTelemetry(line: String) {
        when {
            line.startsWith("status:") -> {
                val compassReady = Regex("""compass=(ready|not ready)""").find(line)?.groupValues?.get(1) == "ready"
                val left = Regex("""left=(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { it != 0 }
                val right = Regex("""right=(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { it != 0 }
                val escCal = Regex("""esc_cal=(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { it != 0 }
                val uptime = Regex("""uptime_ms=(\d+)""").find(line)?.groupValues?.get(1)?.toLongOrNull()
                _telemetry.value = _telemetry.value.copy(
                    compassReady = compassReady,
                    leftActive = left,
                    rightActive = right,
                    escCalibrating = escCal,
                    uptimeMs = uptime,
                    lastMessage = line
                )
            }

            line.startsWith("heading=") -> {
                val heading = Regex("""heading=([0-9.]+)""").find(line)?.groupValues?.get(1)?.toFloatOrNull()
                _telemetry.value = _telemetry.value.copy(headingDegrees = heading, lastMessage = line)
            }

            line.startsWith("raw ") -> {
                val x = Regex("""x=([-0-9.]+)""").find(line)?.groupValues?.get(1)?.toFloatOrNull()
                val y = Regex("""y=([-0-9.]+)""").find(line)?.groupValues?.get(1)?.toFloatOrNull()
                val z = Regex("""z=([-0-9.]+)""").find(line)?.groupValues?.get(1)?.toFloatOrNull()
                _telemetry.value = _telemetry.value.copy(rawX = x, rawY = y, rawZ = z, lastMessage = line)
            }

            line.startsWith("whoami=") -> {
                _telemetry.value = _telemetry.value.copy(
                    whoAmI = line.substringAfter("whoami=").trim(),
                    lastMessage = line
                )
            }

            line.startsWith("compass=") && !line.startsWith("compass raw") -> {
                _telemetry.value = _telemetry.value.copy(
                    compassReady = line.contains("ready"),
                    lastMessage = line
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                gatt.writeCharacteristic(characteristic, value, characteristic.writeType) == BluetoothStatusCodes.SUCCESS
            } catch (_: SecurityException) {
                false
            }
        } else {
            @Suppress("DEPRECATION")
            run {
                try {
                    characteristic.value = value
                    gatt.writeCharacteristic(characteristic)
                } catch (_: SecurityException) {
                    false
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeDescriptor(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
            } catch (_: SecurityException) {
                false
            }
        } else {
            @Suppress("DEPRECATION")
            run {
                try {
                    descriptor.value = value
                    gatt.writeDescriptor(descriptor)
                } catch (_: SecurityException) {
                    false
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun resetConnection(errorMessage: String?, gatt: BluetoothGatt) {
        writeCharacteristic = null
        notifyCharacteristic = null
        incomingBuffer.clear()
        gatt.close()
        bluetoothGatt = null
        _connectionState.value = errorMessage?.let { BleConnectionState.Error(it) } ?: BleConnectionState.Idle
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLegacyLocationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun stopScanSafely() {
        if (!hasBluetoothScanPermission()) return
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: SecurityException) {
            // Ignore; the UI will request permissions again when needed.
        }
    }

    private fun safeDeviceName(
        device: android.bluetooth.BluetoothDevice,
        fallback: String?
    ): String? {
        return if (hasBluetoothConnectPermission()) {
            try {
                device.name ?: fallback
            } catch (_: SecurityException) {
                fallback
            }
        } else {
            fallback
        }
    }

    private fun matchesDeviceName(name: String): Boolean {
        val normalized = name.lowercase()
        return normalized.contains("wh-ble") ||
            normalized.contains("ble 106") ||
            normalized.contains("vp-dbg") ||
            normalized.contains("velo")
    }
}
