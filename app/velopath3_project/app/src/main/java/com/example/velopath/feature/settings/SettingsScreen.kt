package com.example.velopath.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.velopath.ui.theme.AppThemeMode
import java.util.Locale

@Composable
fun SettingsScreen(
    themeMode: AppThemeMode,
    riderSpeedKph: Float,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onRiderSpeedChange: (Float) -> Unit,
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.background.luminance() < 0.5f
    var speedText by remember(riderSpeedKph) { mutableStateOf(riderSpeedKph.formatSpeed()) }

    LaunchedEffect(riderSpeedKph) {
        speedText = riderSpeedKph.formatSpeed()
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsTopBar(onBack = onBack)
            SettingsSectionCard(
                title = "Оформление",
                subtitle = "Тема приложения."
            ) {
                ThemeButtons(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange
                )
            }
            SettingsSectionCard(
                title = "Райдер",
                subtitle = "Средняя скорость используется для расчётного времени в пути."
            ) {
                OutlinedTextField(
                    value = speedText,
                    onValueChange = { raw ->
                        speedText = raw.filter { it.isDigit() || it == '.' || it == ',' }
                        val parsed = speedText.replace(',', '.').toFloatOrNull()
                        if (parsed != null) {
                            onRiderSpeedChange(parsed)
                        }
                    },
                    singleLine = true,
                    label = { Text("Средняя скорость, км/ч") },
                    supportingText = { Text("Диапазон: от 6 до 45 км/ч") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            SettingsSectionCard(
                title = "О приложении",
                subtitle = "VeloPath"
            ) {
                Text(
                    text = "VeloPath — это приложение для использования встраиваемого в руль навигационного устройства: выбора точки назначения, построения маршрута, просмотра профиля пути и отправки Bluetooth-команд на устройство во время движения.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
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
                text = "Настройки",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeButtons(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ThemeChoiceButton(
            label = "Система",
            icon = Icons.Outlined.SettingsBrightness,
            selected = themeMode == AppThemeMode.System,
            onClick = { onThemeModeChange(AppThemeMode.System) }
        )
        ThemeChoiceButton(
            label = "Светлая",
            icon = Icons.Outlined.LightMode,
            selected = themeMode == AppThemeMode.Light,
            onClick = { onThemeModeChange(AppThemeMode.Light) }
        )
        ThemeChoiceButton(
            label = "Тёмная",
            icon = Icons.Outlined.DarkMode,
            selected = themeMode == AppThemeMode.Dark,
            onClick = { onThemeModeChange(AppThemeMode.Dark) }
        )
    }
}

@Composable
private fun ThemeChoiceButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = if (selected) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = colors
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun Float.formatSpeed(): String =
    if (this % 1f == 0f) String.format(Locale.US, "%.0f", this) else String.format(Locale.US, "%.1f", this)
