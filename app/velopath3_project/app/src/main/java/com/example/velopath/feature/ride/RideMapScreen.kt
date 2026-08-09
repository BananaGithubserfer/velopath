package com.example.velopath.feature.ride

import android.Manifest
import android.graphics.Color
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.velopath.core.bluetooth.BleConnectionState
import com.example.velopath.core.bluetooth.HandlebarBleManager
import com.example.velopath.core.map.MapKitInitializer
import com.example.velopath.core.map.RouteAnalytics
import com.example.velopath.core.map.RouteAnalyticsService
import com.example.velopath.core.map.YandexRoutePlanner
import com.example.velopath.core.navigation.RouteGuidanceEngine
import com.example.velopath.core.navigation.RouteLocationTracker
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.CameraUpdateReason
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.mapkit.mapview.MapView
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun RideMapScreen(
    destinationLatitude: Double,
    destinationLongitude: Double,
    riderSpeedKph: Float,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    val routePlanner = remember { YandexRoutePlanner(context) }
    val routeAnalyticsService = remember { RouteAnalyticsService() }
    val locationTracker = remember { RouteLocationTracker(context) }
    val bleManager = remember { HandlebarBleManager(context) }
    val bleState by bleManager.connectionState.collectAsStateWithLifecycle()
    val telemetry by bleManager.telemetry.collectAsStateWithLifecycle()

    var currentLocation by remember { mutableStateOf<Point?>(null) }
    var selectedDestination by remember { mutableStateOf(Point(destinationLatitude, destinationLongitude)) }
    var routeStatus by remember { mutableStateOf("Ждём текущую позицию и готовим маршрут до выбранной точки.") }
    var lastSignalStatus by remember { mutableStateOf("Сигналы на устройство пока не отправлялись.") }
    var isLoading by remember { mutableStateOf(true) }
    var isAnalyticsLoading by remember { mutableStateOf(false) }
    var hasRuntimePermissions by remember { mutableStateOf(false) }
    var guidanceEngine by remember { mutableStateOf<RouteGuidanceEngine?>(null) }
    var activeRoutePoints by remember { mutableStateOf<List<Point>>(emptyList()) }
    var routeAnalytics by remember { mutableStateOf<RouteAnalytics?>(null) }
    var lastRerouteTimestamp by remember { mutableLongStateOf(0L) }
    var followUser by remember { mutableStateOf(true) }
    var routePanelExpanded by remember { mutableStateOf(true) }
    var bluetoothPanelExpanded by remember { mutableStateOf(true) }

    MapKitInitializer.ensureInitialized(context)
    val mapView = remember { MapView(context) }
    val mapObjects: MapObjectCollection = remember { mapView.map.mapObjects }

    fun moveCameraToUser(point: Point) {
        mapView.map.move(CameraPosition(point, 16.3f, 0f, 0f))
    }

    fun redrawMap(start: Point?, destination: Point, routePoints: List<Point>?) {
        mapObjects.clear()
        start?.let { mapObjects.addPlacemark(it) }
        mapObjects.addPlacemark(destination)
        routePoints?.let {
            val polylineObject = mapObjects.addPolyline(Polyline(it))
            polylineObject.setStrokeColor(Color.parseColor("#0F95D0"))
            polylineObject.setOutlineColor(Color.parseColor("#D8F1FA"))
            polylineObject.strokeWidth = 6f
            polylineObject.outlineWidth = 2f
        }
    }

    fun buildRoute() {
        val start = currentLocation
        if (start == null) {
            isLoading = false
            isAnalyticsLoading = false
            guidanceEngine = null
            activeRoutePoints = emptyList()
            routeAnalytics = null
            routeStatus = "Нет текущей геопозиции. Проверьте разрешение на локацию и сигнал навигации."
            redrawMap(null, selectedDestination, null)
            return
        }

        isLoading = true
        isAnalyticsLoading = true
        routeStatus = "Строим маршрут и собираем профиль пути."
        redrawMap(start, selectedDestination, null)
        if (followUser) moveCameraToUser(start)

        coroutineScope.launch {
            val result = routePlanner.requestRoute(start, selectedDestination)
            result.fold(
                onSuccess = { route ->
                    guidanceEngine = RouteGuidanceEngine(route.geometry.points)
                    activeRoutePoints = route.geometry.points
                    lastRerouteTimestamp = System.currentTimeMillis()
                    redrawMap(start, selectedDestination, route.geometry.points)
                    routeStatus = "Маршрут готов. Следим за положением и готовим сигналы на руль."
                    isLoading = false
                    routeAnalytics = routeAnalyticsService.analyze(route)
                    isAnalyticsLoading = false
                },
                onFailure = { error ->
                    guidanceEngine = null
                    activeRoutePoints = emptyList()
                    routeAnalytics = null
                    routeStatus = "Маршрут не построился: ${error.message ?: "неизвестная ошибка"}"
                    isLoading = false
                    isAnalyticsLoading = false
                }
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasRuntimePermissions = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }.toTypedArray()
        permissionLauncher.launch(permissions)
    }

    LaunchedEffect(hasRuntimePermissions) {
        if (!hasRuntimePermissions) return@LaunchedEffect

        locationTracker.updates().collectLatest { update ->
            currentLocation = update.point

            if (followUser) {
                moveCameraToUser(update.point)
            }

            if (guidanceEngine == null) {
                buildRoute()
            } else if (
                activeRoutePoints.isNotEmpty() &&
                !isLoading &&
                isOffRoute(update.point, activeRoutePoints) &&
                System.currentTimeMillis() - lastRerouteTimestamp > 8000L
            ) {
                routeStatus = "Обнаружен сход с маршрута. Перестраиваем путь."
                buildRoute()
            }

            guidanceEngine?.process(update)?.forEach { signal ->
                bleManager.sendSignal(signal)
                    .onSuccess {
                        lastSignalStatus = "Отправлен сигнал: ${signal.javaClass.simpleName}"
                    }
                    .onFailure { error ->
                        lastSignalStatus = "Сигнал не отправлен: ${error.message ?: "неизвестная ошибка"}"
                    }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val inputListener = object : InputListener {
            override fun onMapTap(map: Map, point: Point) {
                selectedDestination = point
                routeStatus = "Точка назначения обновлена. Нажмите «Перестроить», чтобы получить новый маршрут."
            }

            override fun onMapLongTap(map: Map, point: Point) {
                selectedDestination = point
                routeStatus = "Точка назначения обновлена. Нажмите «Перестроить», чтобы получить новый маршрут."
            }
        }

        val cameraListener = CameraListener { _, _, reason, finished ->
            if (reason == CameraUpdateReason.GESTURES && finished) {
                followUser = false
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    mapView.map.addInputListener(inputListener)
                    mapView.map.addCameraListener(cameraListener)
                    mapView.onStart()
                    MapKitFactory.getInstance().onStart()
                }
                Lifecycle.Event.ON_STOP -> {
                    mapView.map.removeInputListener(inputListener)
                    mapView.map.removeCameraListener(cameraListener)
                    mapView.onStop()
                    MapKitFactory.getInstance().onStop()
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.map.removeInputListener(inputListener)
            mapView.map.removeCameraListener(cameraListener)
            bleManager.close()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDarkTheme) {
                        listOf(ComposeColor(0xFF071018), colorScheme.background, ComposeColor(0xFF0E2130))
                    } else {
                        listOf(ComposeColor(0xFFE8F7FF), ComposeColor(0xFFF7FBF9))
                    }
                )
            )
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RideTopBar(onBack = onBack)
            OverlayCard(
                title = "Bluetooth и устройство",
                icon = Icons.AutoMirrored.Outlined.BluetoothSearching,
                expanded = bluetoothPanelExpanded,
                onToggle = { bluetoothPanelExpanded = !bluetoothPanelExpanded }
            ) {
                BluetoothContent(
                    state = bleState,
                    lastSignalStatus = lastSignalStatus,
                    heading = telemetry.calibratedHeadingDegrees ?: telemetry.headingDegrees,
                    onConnect = { bleManager.startScan() },
                    onRefresh = { bleManager.requestStatusSnapshot() }
                )
            }

            OverlayCard(
                title = "Маршрут",
                icon = Icons.Outlined.Route,
                expanded = routePanelExpanded,
                onToggle = { routePanelExpanded = !routePanelExpanded }
            ) {
                RideStatusContent(
                    destination = selectedDestination,
                    status = routeStatus,
                    isLoading = isLoading,
                    isAnalyticsLoading = isAnalyticsLoading,
                    analytics = routeAnalytics,
                    riderSpeedKph = riderSpeedKph,
                    onRefresh = { buildRoute() }
                )
            }
        }

        FloatingActionButton(
            onClick = {
                followUser = true
                currentLocation?.let(::moveCameraToUser)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = if (isDarkTheme) colorScheme.surfaceContainerHigh else ComposeColor.White,
            contentColor = colorScheme.onSurface
        ) {
            Icon(
                imageVector = Icons.Outlined.MyLocation,
                contentDescription = "Вернуться к текущей позиции"
            )
        }
    }
}

private fun isOffRoute(currentPoint: Point, routePoints: List<Point>): Boolean {
    val minDistance = routePoints.minOfOrNull { routePoint ->
        haversineDistanceMeters(currentPoint, routePoint)
    } ?: return false
    return minDistance > 35.0
}

private fun haversineDistanceMeters(first: Point, second: Point): Double {
    val earthRadius = 6371000.0
    val lat1 = Math.toRadians(first.latitude)
    val lat2 = Math.toRadians(second.latitude)
    val dLat = Math.toRadians(second.latitude - first.latitude)
    val dLon = Math.toRadians(second.longitude - first.longitude)
    val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadius * c
}

@Composable
private fun RideTopBar(onBack: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Назад"
                )
            }
            Text(
                text = "Маршрут поездки",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun OverlayCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "Свернуть" else "Развернуть"
                    )
                }
            }

            if (expanded) content()
        }
    }
}

@Composable
private fun BluetoothContent(
    state: BleConnectionState,
    lastSignalStatus: String,
    heading: Float?,
    onConnect: () -> Unit,
    onRefresh: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = when (state) {
                BleConnectionState.Idle -> "Устройство не подключено."
                BleConnectionState.Scanning -> "Ищем BLE-модуль на руле."
                is BleConnectionState.Connecting -> "Подключаемся к ${state.deviceName}."
                is BleConnectionState.Connected -> "Подключено к ${state.deviceName}."
                is BleConnectionState.Error -> state.message
                BleConnectionState.Unsupported -> "Bluetooth LE недоступен на этом устройстве."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant
        )

        heading?.let {
            Text(
                text = "Курс устройства: ${formatFloat(it, 1)}°",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurface
            )
        }

        Text(
            text = lastSignalStatus,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onConnect,
                enabled = state !is BleConnectionState.Scanning && state !is BleConnectionState.Connected
            ) {
                Text(
                    when (state) {
                        BleConnectionState.Scanning -> "Поиск..."
                        is BleConnectionState.Connected -> "Подключено"
                        else -> "Подключить"
                    }
                )
            }

            Button(
                onClick = onRefresh,
                enabled = state is BleConnectionState.Connected
            ) {
                Icon(Icons.Outlined.Sync, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Обновить")
            }
        }
    }
}

@Composable
private fun RideStatusContent(
    destination: Point,
    status: String,
    isLoading: Boolean,
    isAnalyticsLoading: Boolean,
    analytics: RouteAnalytics?,
    riderSpeedKph: Float,
    onRefresh: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Цель: ${formatDouble(destination.latitude)}, ${formatDouble(destination.longitude)}",
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface
        )

        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface
        )

        RouteAnalyticsBlock(
            analytics = analytics,
            riderSpeedKph = riderSpeedKph,
            isLoading = isAnalyticsLoading
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Explore,
                    contentDescription = null,
                    tint = colorScheme.primary
                )
            }

            Button(onClick = onRefresh) {
                Icon(Icons.Outlined.Sync, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Перестроить")
            }
        }
    }
}

@Composable
private fun RouteAnalyticsBlock(
    analytics: RouteAnalytics?,
    riderSpeedKph: Float,
    isLoading: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Сводка маршрута",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            when {
                isLoading -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Считаем профиль маршрута.")
                    }
                }

                analytics == null -> {
                    Text(
                        text = "После построения здесь появятся дистанция, расчётное время, набор и сброс высоты, а также уклон самых крутых участков.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    AnalyticsRow("Дистанция", "${(analytics.distanceMeters / 1000.0).format(1)} км")
                    AnalyticsRow("Время в пути", analytics.distanceMeters.toRideDurationLabel(riderSpeedKph))
                    AnalyticsRow("Общий набор", analytics.ascentMeters?.format(0)?.plus(" м") ?: "нет данных")
                    AnalyticsRow("Общий сброс", analytics.descentMeters?.format(0)?.plus(" м") ?: "нет данных")
                    AnalyticsRow("Макс. градиент вверх", analytics.maxAscentGradePercent?.format(1)?.plus(" %") ?: "нет данных")
                    AnalyticsRow("Набор на крутых подъёмах", analytics.steepAscentGainMeters?.format(0)?.plus(" м") ?: "нет данных")
                    AnalyticsRow("Макс. градиент вниз", analytics.maxDescentGradePercent?.format(1)?.plus(" %") ?: "нет данных")
                    AnalyticsRow("Сброс на крутых спусках", analytics.steepDescentDropMeters?.format(0)?.plus(" м") ?: "нет данных")
                    AnalyticsRow("Автодороги", if (analytics.hasAutoRoad) "есть участки" else "не отмечены")
                    AnalyticsRow("Пропуск/доступ", if (analytics.requiresAccessPass) "нужен" else "не нужен")
                }
            }
        }
    }
}

@Composable
private fun AnalyticsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatDouble(value: Double): String = String.format(Locale.US, "%.5f", value)

private fun formatFloat(value: Float, digits: Int): String = "%.${digits}f".format(Locale.US, value)

private fun Double.format(digits: Int): String = "%.${digits}f".format(Locale.US, this)

private fun Double.toRideDurationLabel(riderSpeedKph: Float): String {
    val safeSpeed = riderSpeedKph.coerceAtLeast(1f).toDouble()
    val durationHours = (this / 1000.0) / safeSpeed
    val totalMinutes = (durationHours * 60.0).toInt().coerceAtLeast(1)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours ч $minutes мин" else "$minutes мин"
}
