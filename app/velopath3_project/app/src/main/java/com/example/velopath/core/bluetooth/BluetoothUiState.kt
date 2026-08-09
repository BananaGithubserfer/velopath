package com.example.velopath.core.bluetooth

enum class BluetoothUiState(
    val title: String,
    val description: String
) {
    Ready(
        title = "Готово к подключению",
        description = "Приложение готово к связке с рулевым модулем по Bluetooth."
    ),
    Connecting(
        title = "Поиск устройства",
        description = "После подключения здесь появятся телеметрия, ручные команды и диагностика руля."
    )
}
