# Software stack

This document covers the Android application, STM32 firmware and desktop
diagnostics. ESP-related code is isolated under [`firmware/esp`](../firmware/esp).

## Android application

The Gradle project is [`app/velopath3_project`](../app/velopath3_project).

Typical commands from that directory:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Build output is generated under `app/velopath3_project/app/build` and is ignored.

## STM32 firmware

The canonical STM32 project is [`app/velopath3_project/firmware`](../app/velopath3_project/firmware).
It contains the CubeMX project, HAL sources, linker scripts and the diagnostic
tools used for BLE and motor-control bench tests.

Important entry points:

- `Core/Src/main.c` - board startup and main loop.
- `Core/Src/ble_link.c` - BLE transport and optional provisioning.
- `Core/Src/motor_control.c` - motor command handling.
- `tools/` - host-side BLE and console utilities.

## Boundaries

- The Android app communicates with the STM32 over the configured BLE module.
- The STM32 firmware owns motor control, console commands and compass access.
- ESP code is independent and must remain under `firmware/esp`.
- Build output, logs, release artifacts and IDE workspaces are local-only.
