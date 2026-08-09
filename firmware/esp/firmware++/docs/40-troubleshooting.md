# Troubleshooting

## Motor Beeps but Does Not Spin

1. Verify ESC signal on `GPIO4` and common `GND`.
2. Run arm sequence:
   - `arm`
   - wait for `ARM: done`
3. Increase pulse gradually:
   - `us 1050`
   - `us 1100`
   - `us 1200`
4. If still no spin, calibrate ESC:
   - send `us 2000`
   - power-cycle ESC
   - send `us 1000`
   - try `us 1100` again

## BLE Command Returns `err:bad_cmd`

Use supported formats:

- `t 50`
- `50`
- `arm`
- `stop`
- `status`
- `us 1100`
- `led on`

Values like `0.5` are intentionally rejected.

## Serial Port Busy / Access Denied

- Close Arduino Serial Monitor.
- Close any open `serial_console.py` window.
- Retry flashing.

## Wrong Response in Status

- Use `status` command in Serial.
- Check BLE debug fields:
  - `last_ble_text`
  - `last_ble_hex`
- These show what payload actually arrived over BLE.

## LED Does Not Work

- Verify external LED is on `GPIO5` and `GND`.
- Test with `led on` and `led off`.
- Confirm wiring polarity (anode to signal, cathode to ground).

