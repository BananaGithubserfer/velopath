# Build, Flash, and Tools

## Requirements

- Python 3.14+
- PlatformIO (`python -m pip install platformio`)
- USB cable with data support

## Build

```powershell
python -m platformio run
```

## Flash

```powershell
python -m platformio run -t upload --upload-port COM5
```

If `COM5` is busy, close all serial monitors first.

## Detect Serial Port

```powershell
python -m platformio device list
```

## Monitor

```powershell
python -m platformio device monitor -p COM5 -b 115200 --echo -f send_on_enter --eol LF
```

## Interactive Python Console

```powershell
python tools\serial_console.py --port COM5 --baud 115200 --eol LF
```

Useful built-ins in the console:

- `:help`
- `:status`
- `:arm`
- `:stop`
- `:quit`

