# Software stack documentation (non-ESP)

Last updated: 2026-04-12

Scope of this document:
- Included: Android app, STM32 firmware, desktop/diagnostic tools.
- Excluded: all ESP-related code/tools/artifacts.

ESP is fully isolated in:
`C:\Users\vladimir.DESKTOP-L4RFPI0\Documents\velopath\firmware\esp`

## 1) Android application

Path:
`C:\Users\vladimir.DESKTOP-L4RFPI0\Documents\velopath\app\velopath3_project`

Key modules and files:
- `app` - main Android module.
- `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`.
- `gradlew`, `gradlew.bat`.

Typical build commands (PowerShell, run in project root):
- `.\gradlew.bat :app:assembleDebug`
- `.\gradlew.bat :app:assembleRelease`

Release output location used in this structure:
- `C:\Users\vladimir.DESKTOP-L4RFPI0\Documents\velopath\app\releases`

## 2) STM32 firmware

Primary STM32 tree:
`C:\Users\vladimir.DESKTOP-L4RFPI0\Documents\velopath\firmware\firmware`

Additional legacy STM32 tree:
`C:\Users\vladimir.DESKTOP-L4RFPI0\Documents\velopath\firmware\stm32_legacy_workspace`

Typical STM32 files present:
- `firmware.ioc` (CubeMX configuration)
- `Core`, `Drivers`, `Startup`
- linker scripts (`STM32F103RGTX_FLASH.ld`, `STM32F103RGTX_RAM.ld`)

## 3) Desktop diagnostics and interactive tools

Path:
`C:\Users\vladimir.DESKTOP-L4RFPI0\Documents\velopath\app\velopath3_project\firmware\tools`

Includes:
- BLE terminal/stream scripts (`ble_*.py`)
- console utilities (`console_terminal.py`, command senders)
- WinRT support scripts (`winrt_ble_*.ps1`)

Purpose:
- board diagnostics
- terminal stability checks
- command-level interaction tests

## 4) Software architecture boundaries

Non-ESP software folders:
- `app\velopath3_project`
- `firmware\firmware`
- `firmware\stm32_legacy_workspace`
- `documentation` (non-code docs and reports)

ESP software folder:
- `firmware\esp` (single containment area for ESP source + build artifacts + temp files)

## 5) Notes for new contributors

If you need to:
- build mobile app: start from `app\velopath3_project`
- work with STM32 firmware: start from `firmware\firmware`
- run diagnostics from PC: use scripts in `app\velopath3_project\firmware\tools`
- work with ESP only: use `firmware\esp` and do not mix ESP artifacts into other folders
