# ESP32-C3 ESC Controller

Firmware for an ESP32-C3 board to control an ESC (electronic speed controller) over BLE and Serial.

This project is built with PlatformIO and Arduino framework.

## Documentation Package

- [Quick Start](docs/00-quick-start.md)
- [Hardware and Wiring](docs/10-hardware-wiring.md)
- [Control Reference (BLE and Serial)](docs/20-control-reference.md)
- [Build, Flash, and Tools](docs/30-build-flash-tools.md)
- [Troubleshooting](docs/40-troubleshooting.md)

## Current Runtime Configuration

- Board: `esp32-c3-devkitm-1`
- ESC signal pin: `GPIO4`
- External test LED pin: `GPIO5`
- ESC pulse range: `1000..2000 us`
- BLE name: `ESP32C3-ESC`

