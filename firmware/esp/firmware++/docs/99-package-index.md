# Documentation Package Index

This package documents the current firmware behavior and wiring for the ESP32-C3 ESC controller.

## Included Files

- `README.md`
- `docs/00-quick-start.md`
- `docs/10-hardware-wiring.md`
- `docs/20-control-reference.md`
- `docs/30-build-flash-tools.md`
- `docs/40-troubleshooting.md`
- `docs/99-package-index.md`

## Firmware Notes

- Control range is `0..100` only.
- ESC output is generated via `ESP32Servo` (`writeMicroseconds`).
- BLE debug logging is enabled in Serial output.

