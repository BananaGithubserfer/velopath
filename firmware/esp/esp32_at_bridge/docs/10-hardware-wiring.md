# Hardware and Wiring

## Target Board

- ESP32-C3 compatible board (configured as `esp32-c3-devkitm-1` in PlatformIO)

## Pin Assignment

- ESC signal: `GPIO4`
- ESC ground: any board `GND`
- External test LED signal: `GPIO5`

## ESC Wiring

- ESC signal wire -> `GPIO4`
- ESC GND wire -> board `GND`
- ESC power input -> ESC battery (normal ESC power path)

Use common ground between ESC and ESP32-C3.

## LED Wiring

- LED anode -> `GPIO5`
- LED cathode -> `GND`

For real hardware, use a current-limiting resistor for external LED.

## Power Options for ESP32-C3

- Recommended: `5V` + `GND` from a stable 5V rail.
- Alternative: regulated `3.3V` + `GND` is possible.
- Never feed raw Li-ion voltage directly into `3.3V` pin.

