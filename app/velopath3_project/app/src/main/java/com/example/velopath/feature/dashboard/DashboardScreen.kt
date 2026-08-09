package com.example.velopath.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.BuildCircle
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.velopath.core.bluetooth.BluetoothUiState
import com.example.velopath.core.location.UserLocationClient
import com.example.velopath.core.map.MapKitInitializer
import com.example.velopath.core.search.PlaceSearchResult
import com.example.velopath.core.search.YandexPlacesSearchService
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.mapview.MapView
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class QuickDestination(
    val title: String,
    val latitude: Double,
    val longitude: Double
)

@Composable
fun DashboardScreen(
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    onDestinationPicked: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    val searchService = remember { YandexPlacesSearchService() }
    val locationClient = remember { UserLocationClient(context) }
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f

    var query by rememberSaveable { mutableStateOf("") }
    var latitudeText by rememberSaveable { mutableStateOf("") }
    var longitudeText by rememberSaveable { mutableStateOf("") }
    var selectedPlaceTitle by rememberSaveable { mutableStateOf("Точка пока не выбрана") }
    var selectedPlaceSubtitle by rememberSaveable { mutableStateOf("Выберите место для поездки") }
    var isSearching by remember { mutableStateOf(false) }
    var isResolvingSelection by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var searchResults by remember { mutableStateOf<List<PlaceSearchResult>>(emptyList()) }
    var userPoint by remember { mutableStateOf<Point?>(null) }
    val sessionToken = rememberSaveable { UUID.randomUUID().toString() }

    val presets = remember {
        listOf(
            QuickDestination("Красная площадь", 55.753930, 37.620795),
            QuickDestination("Парк Горького", 55.729876, 37.603918),
            QuickDestination("ВДНХ", 55.828531, 37.629940)
        )
    }

    val latitude = latitudeText.toDoubleOrNull()
    val longitude = longitudeText.toDoubleOrNull()
    val selectedPoint = if (latitude != null && longitude != null) Point(latitude, longitude) else null
    val isDestinationValid = selectedPoint != null

    fun performSuggestSearch() {
        coroutineScope.launch {
            isSearching = true
            searchError = null
            val result = searchService.suggest(query, userPoint, sessionToken)
            result.fold(
                onSuccess = { results ->
                    searchResults = results
                    if (query.length >= 3 && results.isEmpty()) {
                        searchError = "Подсказки не найдены."
                    }
                },
                onFailure = { error ->
                    searchResults = emptyList()
                    searchError = when {
                        error.message?.contains("SEARCH_API_KEY") == true -> "Не найден SEARCH_API_KEY."
                        error.message?.contains("GEOCODER_API_KEY") == true -> "Не найден GEOCODER_API_KEY."
                        else -> "Поиск сейчас недоступен: ${error.message ?: "неизвестная ошибка"}"
                    }
                }
            )
            isSearching = false
        }
    }

    fun previewResult(result: PlaceSearchResult) {
        coroutineScope.launch {
            isResolvingSelection = true
            searchError = null
            searchService.resolve(result).fold(
                onSuccess = { place ->
                    val resolvedLatitude = place.latitude
                    val resolvedLongitude = place.longitude
                    if (resolvedLatitude != null && resolvedLongitude != null) {
                        latitudeText = resolvedLatitude.toString()
                        longitudeText = resolvedLongitude.toString()
                        selectedPlaceTitle = place.title
                        selectedPlaceSubtitle = place.subtitle.ifBlank { "Точка на карте" }
                    } else {
                        searchError = "Не удалось определить координаты."
                    }
                },
                onFailure = { error ->
                    searchError = "Не удалось открыть место: ${error.message ?: "неизвестная ошибка"}"
                }
            )
            isResolvingSelection = false
        }
    }

    LaunchedEffect(Unit) {
        if (locationClient.hasLocationPermission()) {
            userPoint = locationClient.getCurrentPoint()
        }
    }

    LaunchedEffect(query, userPoint) {
        if (query.trim().length < 3) {
            searchResults = emptyList()
            searchError = null
            isSearching = false
            return@LaunchedEffect
        }
        delay(350)
        performSuggestSearch()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = if (isDarkTheme) {
                        listOf(Color(0xFF071018), colorScheme.background, Color(0xFF102531))
                    } else {
                        listOf(Color(0xFF0C1620), Color(0xFF112A38), Color(0xFFF2F7F5))
                    }
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item { Spacer(modifier = Modifier.height(20.dp)) }
            item { HeroCard(onOpenSettings = onOpenSettings) }
            item {
                BluetoothCardCompact(
                    state = BluetoothUiState.Ready,
                    onOpenDiagnostics = onOpenDiagnostics
                )
            }
            item {
                PreviewMapCard(
                    point = selectedPoint,
                    title = selectedPlaceTitle,
                    subtitle = selectedPlaceSubtitle,
                    isDestinationValid = isDestinationValid,
                    onOpenRide = {
                        selectedPoint?.let { point ->
                            onDestinationPicked(point.latitude, point.longitude)
                        }
                    }
                )
            }
            item {
                SearchCard(
                    query = query,
                    onQueryChanged = { query = it },
                    isSearching = isSearching,
                    isResolvingSelection = isResolvingSelection,
                    searchError = searchError,
                    results = searchResults,
                    onSearch = ::performSuggestSearch,
                    onResultSelected = ::previewResult
                )
            }
            item {
                DestinationCard(
                    latitudeText = latitudeText,
                    longitudeText = longitudeText,
                    onLatitudeChanged = { latitudeText = it },
                    onLongitudeChanged = { longitudeText = it },
                    isDestinationValid = isDestinationValid,
                    onOpenRide = {
                        selectedPoint?.let { point ->
                            onDestinationPicked(point.latitude, point.longitude)
                        }
                    }
                )
            }
            item {
                QuickStartCard(
                    presets = presets,
                    onPresetSelected = { preset ->
                        latitudeText = preset.latitude.toString()
                        longitudeText = preset.longitude.toString()
                        selectedPlaceTitle = preset.title
                        selectedPlaceSubtitle = "Точка на карте"
                    }
                )
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun HeroCard(onOpenSettings: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF17384D), Color(0xFF0D202C), Color(0xFF4CB39A))
                    )
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Route,
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Text(
                text = "VeloPath",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Настройки")
            }
        }
    }
}

@Composable
private fun BluetoothCard(
    state: BluetoothUiState,
    onOpenDiagnostics: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (colorScheme.background.luminance() < 0.5f) {
                colorScheme.surfaceContainerHigh
            } else {
                Color(0xFFF3F8F6)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.BluetoothSearching,
                        contentDescription = null,
                        tint = Color(0xFF0F7B63)
                    )
                }
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Подключить")
                }
                OutlinedButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.BuildCircle, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Лаборатория\nустройства")
                }
            }
        }
    }
}

@Composable
private fun BluetoothCardCompact(
    state: BluetoothUiState,
    onOpenDiagnostics: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (colorScheme.background.luminance() < 0.5f) {
                colorScheme.surfaceContainerHigh
            } else {
                Color(0xFFF3F8F6)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.BluetoothSearching,
                        contentDescription = null,
                        tint = Color(0xFF0F7B63)
                    )
                }
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.weight(0.95f)
                ) {
                    Text("Подключить")
                }
                OutlinedButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.weight(1.05f)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Outlined.BuildCircle, contentDescription = null)
                        Text(
                            text = "Лаборатория\nустройства",
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchCard(
    query: String,
    onQueryChanged: (String) -> Unit,
    isSearching: Boolean,
    isResolvingSelection: Boolean,
    searchError: String?,
    results: List<PlaceSearchResult>,
    onSearch: () -> Unit,
    onResultSelected: (PlaceSearchResult) -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = Color(0xFF0D6D86)
                )
                Text(
                    text = "Поиск точки маршрута",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                label = { Text("Адрес, парк, улица или точка интереса") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = onSearch,
                enabled = query.isNotBlank() && !isSearching && !isResolvingSelection,
                modifier = Modifier.fillMaxWidth()
            ) {
                when {
                    isResolvingSelection -> {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Открываем точку")
                    }
                    isSearching -> {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Обновляем подсказки")
                    }
                    else -> Text("Найти")
                }
            }

            searchError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (results.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    results.forEach { result ->
                        ResultCard(result = result, onClick = { onResultSelected(result) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    result: PlaceSearchResult,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (result.subtitle.isNotBlank()) {
                Text(
                    text = result.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PreviewMapCard(
    point: Point?,
    title: String,
    subtitle: String,
    isDestinationValid: Boolean,
    onOpenRide: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapKitInitializer.ensureInitialized(context)
        MapView(context).apply {
            map.move(CameraPosition(Point(55.751244, 37.618423), 10.5f, 0f, 0f))
        }
    }

    LaunchedEffect(point) {
        point?.let {
            mapView.map.mapObjects.clear()
            mapView.map.mapObjects.addPlacemark(it)
            mapView.map.move(CameraPosition(it, 14.5f, 0f, 0f))
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    mapView.onStart()
                    MapKitFactory.getInstance().onStart()
                }
                Lifecycle.Event.ON_STOP -> {
                    mapView.onStop()
                    MapKitFactory.getInstance().onStop()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onStop()
        }
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Map, contentDescription = null, tint = Color(0xFF0D6D86))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AndroidView(
                factory = { mapView },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(22.dp))
            )

            Button(
                onClick = onOpenRide,
                enabled = isDestinationValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Построить маршрут")
            }
        }
    }
}

@Composable
private fun DestinationCard(
    latitudeText: String,
    longitudeText: String,
    onLatitudeChanged: (String) -> Unit,
    onLongitudeChanged: (String) -> Unit,
    isDestinationValid: Boolean,
    onOpenRide: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = Color(0xFF0D6D86))
                Text(
                    text = "Координаты точки",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedTextField(
                value = latitudeText,
                onValueChange = onLatitudeChanged,
                singleLine = true,
                label = { Text("Широта") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = longitudeText,
                onValueChange = onLongitudeChanged,
                singleLine = true,
                label = { Text("Долгота") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = onOpenRide,
                enabled = isDestinationValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Открыть маршрут")
            }
        }
    }
}

@Composable
private fun QuickStartCard(
    presets: List<QuickDestination>,
    onPresetSelected: (QuickDestination) -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Быстрые сценарии",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.forEach { preset ->
                    Card(
                        onClick = { onPresetSelected(preset) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = preset.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
