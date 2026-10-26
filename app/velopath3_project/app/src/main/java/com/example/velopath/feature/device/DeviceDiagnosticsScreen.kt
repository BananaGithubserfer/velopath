package com.example.velopath.feature.device

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SettingsInputComponent
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.velopath.core.bluetooth.BleConnectionState
import com.example.velopath.core.bluetooth.DeviceTelemetry
import com.example.velopath.core.bluetooth.HandlebarBleManager
import java.util.Locale

@Composable
fun DeviceDiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val bleManager = remember { HandlebarBleManager(context) }
    val connectionState by bleManager.connectionState.collectAsStateWithLifecycle()
    val telemetry by bleManager.telemetry.collectAsStateWithLifecycle()
    val consoleLines by bleManager.consoleLines.collectAsStateWithLifecycle()
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Re-run the same preflight check after the system permission dialog closes.
        bleManager.startScan()
    }

    LaunchedEffect(connectionState) {
        if (connectionState is BleConnectionState.Connected) {
            bleManager.requestStatusSnapshot()
        }
    }

    DisposableEffect(Unit) {
        onDispose { bleManager.close() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDarkTheme) {
                        listOf(Color(0xFF071018), colorScheme.background, Color(0xFF102531))
                    } else {
                        listOf(Color(0xFFF4FBFF), Color(0xFFEAF4F0), Color(0xFFFFFFFF))
                    }
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { DiagnosticsTopBar(onBack = onBack) }
            item {
                ConnectionCard(
                    connectionState = connectionState,
                    onConnect = {
                        permissionLauncher.launch(bleManager.requiredRuntimePermissions())
                    },
                    onRefresh = bleManager::requestStatusSnapshot
                )
            }
            item {
                TelemetryCard(
                    telemetry = telemetry,
                    onSetZero = bleManager::calibrateHeadingZero
                )
            }
            item { ManualControlCard(onCommand = bleManager::sendCommand) }
            item { ConsoleCard(lines = consoleLines, onClear = bleManager::clearConsole) }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DiagnosticsTopBar(onBack: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Назад"
                )
            }
            Text(
                text = "Лаборатория устройства",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ConnectionCard(
    connectionState: BleConnectionState,
    onConnect: () -> Unit,
    onRefresh: () -> Unit
) {
    val panelColor = panelColor()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = panelColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Outlined.BluetoothSearching, null, tint = MaterialTheme.colorScheme.primary)
                }
                Column {
                    Text("Bluetooth и консоль", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = when (connectionState) {
                            BleConnectionState.Idle -> "Модуль не подключён."
                            BleConnectionState.Scanning -> "Сканируем BLE-устройства."
                            is BleConnectionState.Connecting -> "Подключаемся к ${connectionState.deviceName}."
                            is BleConnectionState.Connected -> "Подключено к ${connectionState.deviceName}."
                            is BleConnectionState.Error -> connectionState.message
                            BleConnectionState.Unsupported -> "Bluetooth LE недоступен на этом устройстве."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onConnect,
                    enabled = connectionState !is BleConnectionState.Scanning && connectionState !is BleConnectionState.Connected
                ) {
                    Text(if (connectionState is BleConnectionState.Scanning) "Поиск..." else "Подключить")
                }
                OutlinedButton(onClick = onRefresh, enabled = connectionState is BleConnectionState.Connected) {
                    Icon(Icons.Outlined.Refresh, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Обновить")
                }
            }
        }
    }
}

@Composable
private fun TelemetryCard(
    telemetry: DeviceTelemetry,
    onSetZero: () -> Unit
) {
    val panelColor = panelColor()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = panelColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Explore, null, tint = MaterialTheme.colorScheme.secondary)
                Column {
                    Text("Компас и телеметрия", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Нулевой курс хранится локально в приложении, чтобы можно было компенсировать неровный руль.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted
                    )
                }
            }

            TelemetryRow(
                "Компас",
                when (telemetry.compassReady) {
                    true -> "готов"
                    false -> "не готов"
                    null -> "нет данных"
                }
            )
            TelemetryRow("WHO_AM_I", telemetry.whoAmI ?: "—")
            TelemetryRow(
                "Курс",
                telemetry.headingDegrees?.let {
                    "${formatFloat(it, 1)}° / ${formatFloat(telemetry.calibratedHeadingDegrees ?: it, 1)}° после коррекции"
                } ?: "—"
            )
            TelemetryRow(
                "Raw XYZ",
                if (telemetry.rawX != null && telemetry.rawY != null && telemetry.rawZ != null) {
                    "${formatFloat(telemetry.rawX, 0)}, ${formatFloat(telemetry.rawY, 0)}, ${formatFloat(telemetry.rawZ, 0)}"
                } else {
                    "—"
                }
            )
            TelemetryRow("Uptime", telemetry.uptimeMs?.let { "$it ms" } ?: "—")
            TelemetryRow(
                "Каналы",
                "L=${boolMark(telemetry.leftActive)}  R=${boolMark(telemetry.rightActive)}  ESC-cal=${boolMark(telemetry.escCalibrating)}"
            )

            ElevatedButton(onClick = onSetZero, enabled = telemetry.headingDegrees != null) {
                Text("Сделать текущий курс нулём")
            }
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ManualControlCard(onCommand: (String) -> Result<Unit>) {
    val panelColor = panelColor()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = panelColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SettingsInputComponent, null, tint = Color(0xFFB26611))
                Column {
                    Text("Тестовые команды", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Быстрые проверки вибро, ESC, компаса и служебных сценариев без перепрошивки.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted
                    )
                }
            }

            CommandButtonRow("Связь", listOf("ping", "status", "uptime"), onCommand)
            CommandButtonRow("Компас", listOf("compass", "compass raw", "compass whoami", "compass reinit"), onCommand)
            CommandButtonRow("Сигналы", listOf("left on", "left off", "right on", "right off", "both on", "stop"), onCommand)
            CommandButtonRow("ESC", listOf("esc arm", "esc calibrate", "esc 10", "esc 30", "esc 0"), onCommand)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommandButtonRow(
    title: String,
    commands: List<String>,
    onCommand: (String) -> Result<Unit>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            commands.forEach { command ->
                OutlinedButton(onClick = { onCommand(command) }) {
                    Icon(Icons.Outlined.PlayArrow, null)
                    Spacer(Modifier.size(6.dp))
                    Text(command)
                }
            }
        }
    }
}

@Composable
private fun ConsoleCard(lines: List<String>, onClear: () -> Unit) {
    val consoleColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color(0xFF111C26)
    } else {
        Color(0xFF0E1720)
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = consoleColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Memory, null, tint = Color(0xFF84D9FF))
                Text(
                    "Консоль устройства",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
            OutlinedButton(onClick = onClear) {
                Text("Очистить")
            }
            if (lines.isEmpty()) {
                Text(
                    "После подключения сюда начнут приходить строки из модуля.",
                    color = Color.White.copy(alpha = 0.76f)
                )
            } else {
                lines.takeLast(20).forEach { line ->
                    Text(line, color = Color(0xFFD5F5FF), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun panelColor(): Color {
    return if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface
    }
}

private fun boolMark(value: Boolean?): String = when (value) {
    true -> "on"
    false -> "off"
    null -> "—"
}

private fun formatFloat(value: Float, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)
