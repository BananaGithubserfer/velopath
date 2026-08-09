# VeloPath

VeloPath is a multi-part cycling navigation project that combines a mobile application, embedded firmware, electronics design, and engineering documentation in one repository.

The repository contains:

- an Android application with map-based routing and BLE integration
- STM32 firmware for the handlebar module
- ESP-based firmware experiments and tools
- PCB design files and production artifacts
- project documentation, reports, and presentation materials

## Project Overview

VeloPath is organized as a single workspace for both software and hardware development. The Android app is responsible for route search, map display, navigation flow, and communication with the handlebar device. The embedded side includes STM32 firmware for the main hardware module and a separate ESP area for related experiments and peripherals.

At the application level, the current Android project includes:

- destination search and map interaction
- route building and route analytics
- BLE device diagnostics
- handlebar communication for navigation cues
- user settings such as theme and rider speed

## Repository Structure

```text
velopath/
|- app/
|  |- velopath3_project/    Android app and related development materials
|  `- releases/             APK copies and release outputs
|- firmware/
|  |- firmware/             Primary STM32 firmware tree
|  |- stm32_legacy_workspace/
|  `- esp/                  ESP code, tools, and isolated build area
|- pcb/                     KiCad projects and manufacturing outputs
|- documentation/           Reports, engineering docs, mechanical docs
|- advertising/             Brochures, presentations, visual assets
|- _migration/              Migration and import logs
|- INDEX.md                 Detailed project map
`- START_HERE.md            Quick onboarding guide
```

## Main Components

### Android App

Path: `app/velopath3_project`

Stack:

- Kotlin
- Jetpack Compose
- Android Navigation Compose
- Yandex MapKit
- Google Play Services Location

The main navigation flow currently includes:

- `DashboardScreen` for search and quick route entry
- `RideMapScreen` for active route guidance and map view
- `DeviceDiagnosticsScreen` for BLE connection and device checks
- `SettingsScreen` for app preferences

### STM32 Firmware

Path: `firmware/firmware`

This area contains firmware for the handlebar module based on `STM32F103RGTx`. The documented hardware integration includes:

- BLE UART bridge `WH-BLE106`
- compass `LIS3MDL`
- left and right vibration channels

The firmware tree includes CubeMX project files, startup code, drivers, linker scripts, and diagnostics tooling.

### ESP Area

Path: `firmware/esp`

This directory is the isolated zone for ESP-related source code, tools, temporary files, and build artifacts. It includes quick-start and troubleshooting documents for the ESP subprojects.

### PCB and Electronics

Path: `pcb`

This directory contains KiCad schematics, PCB layouts, backups, generated fabrication outputs, and related electronics assets.

### Documentation and Materials

Paths:

- `documentation`
- `advertising`
- `_migration`

These directories hold project reports, engineering notes, explanatory materials, brochures, visual assets, and migration history.

## Quick Start

### 1. Explore the Repository

Start here if you are new to the project:

1. Open `START_HERE.md`
2. Read `INDEX.md`
3. Review `documentation/software_stack_non_esp.md`

### 2. Build the Android App

Run in `app/velopath3_project`:

```powershell
.\gradlew.bat :app:assembleDebug
```

Release build:

```powershell
.\gradlew.bat :app:assembleRelease
```

The Android project expects local configuration through standard Gradle local files in the project root.

### 3. Work with STM32 Firmware

Start in `firmware/firmware` if you are working on the main handlebar firmware. Typical files include:

- `firmware.ioc`
- `Core/`
- `Drivers/`
- `Startup/`
- STM32 linker scripts

There is also a legacy STM32 workspace in `firmware/stm32_legacy_workspace`.

### 4. Work with ESP Firmware

For ESP tasks, use `firmware/esp`.

Example PlatformIO upload command from the ESP project root:

```powershell
python -m platformio run -t upload --upload-port COM5
```

## Important Project Documents

- `START_HERE.md` - onboarding order for new contributors
- `INDEX.md` - high-level project map
- `documentation/software_stack_non_esp.md` - software overview outside the ESP area
- `documentation/materials_origin_and_import_map.md` - repository placement and source mapping
- `app/velopath3_project/firmware/README.md` - handlebar firmware overview

## Suggested Entry Points By Task

- Mobile app work: `app/velopath3_project`
- Main embedded firmware: `firmware/firmware`
- ESP work: `firmware/esp`
- PCB and hardware files: `pcb`
- Reports and engineering docs: `documentation`
- Presentations and brochure assets: `advertising`

## Status

This repository is a working engineering workspace that combines product software, hardware, embedded development, design artifacts, and supporting documentation. Some directories contain active project files, while others preserve historical, diagnostic, or manufacturing context for the broader VeloPath system.

## License

This repository is distributed under the GNU General Public License v3.0 or later. See `LICENSE`.
