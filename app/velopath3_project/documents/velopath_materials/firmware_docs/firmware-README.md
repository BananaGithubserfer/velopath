# VeloPath Handlebar Firmware

Firmware scaffold for the handlebar module based on `STM32F103RGTx`.

Hardware mapping:
- BLE UART bridge `WH-BLE106`
  - `BRX -> PA2`
  - `BTX -> PA3`
  - uses `USART2`
- Compass `LIS3MDL`
  - `PB10 -> I2C2_SCL`
  - `PB11 -> I2C2_SDA`
- Vibration channels
  - `PC1`
  - `PC2`

Current firmware includes:
- WH-BLE106 slave-mode transparent bridge on `USART2`
- WH-BLE106 profile pinned to:
  - service `0003cdd0-0000-1000-8000-00805f9b0131`
  - notify `0003cdd1-0000-1000-8000-00805f9b0131`
  - write `0003cdd2-0000-1000-8000-00805f9b0131`
  - programmed via `AT+UUID=<slot>+<uuid>`
- line-based BLE/UART receiver
- JSON command parsing for:
  - `advance_turn`
  - `turn_now`
  - `route_finished`
- Text diagnostic console over the same UART bridge
- Basic `LIS3MDL` driver
- Motor output controller
- Navigation controller logic

Console commands:

```text
help
ping
compass
left on
left off
right on
right off
both on
both off
stop
```

Navigation test script:

```bash
python tools/send_nav_commands.py COM7 advance_left
python tools/send_nav_commands.py COM7 turn_right --heading 135
python tools/send_nav_commands.py COM7 finish
```

WH-BLE106 implementation notes and rewrite report:

- [docs/wh-ble106-report-and-docs.md](docs/wh-ble106-report-and-docs.md)
