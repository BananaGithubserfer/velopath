# Quick Start

## 1) Hardware

- Connect ESC signal wire to `GPIO4`.
- Connect ESC GND to board `GND`.
- Power ESC from its battery as usual.
- Optional external LED: anode to `GPIO5`, cathode to `GND`.

## 2) Build and Flash

From project root:

```powershell
python -m platformio run -t upload --upload-port COM5
```

Replace `COM5` with your actual port.

## 3) Open Serial Console

```powershell
python tools\serial_console.py --port COM5 --baud 115200 --eol LF
```

## 4) Arm and Run

Send:

1. `arm`
2. `us 1050`
3. `us 1100`
4. `us 1200`
5. `stop`

## 5) BLE Basics

- Device name: `ESP32C3-ESC`
- Write control commands to throttle characteristic:
  `7f7b1001-6a66-4a7e-9a4a-57f4f5a22a01`
- Enable notifications on status characteristic:
  `7f7b1002-6a66-4a7e-9a4a-57f4f5a22a01`

