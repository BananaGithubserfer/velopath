# Control Reference (BLE and Serial)

## BLE IDs

- Device name: `ESP32C3-ESC`
- Service UUID: `7f7b1000-6a66-4a7e-9a4a-57f4f5a22a01`
- Throttle write UUID: `7f7b1001-6a66-4a7e-9a4a-57f4f5a22a01`
- Status notify/read UUID: `7f7b1002-6a66-4a7e-9a4a-57f4f5a22a01`

## Accepted Throttle Range

- Strictly `0..100` percent.
- No normalized `0..1` mode.

## Serial Commands

- `help`
- `status`
- `arm`
- `stop`
- `t N` where `N` is `0..100`
- `us P` where `P` is pulse width in microseconds (`1000..2000` recommended)
- `led on`
- `led off`
- `led toggle`
- `ble <text>` send manual BLE notify message

## BLE Commands (write to throttle characteristic)

- `arm`
- `stop`
- `status`
- `help`
- `t N`
- `us P`
- `led on`
- `led off`
- `led toggle`
- Direct numeric text `N` where `N` is `0..100`
- Single-byte binary payload `0..100`

## BLE Status Responses

Typical notifications:

- `connected`
- `throttle=<n>,pulse_us=<p>`
- `ok:arm`
- `ok:stop`
- `ok:t`
- `ok:us`
- `ok:text`
- `ok:byte`
- `ok:led_on`
- `ok:led_off`
- `err:t`
- `err:us`
- `err:bad_cmd`

## Runtime Safety Behavior

- On BLE disconnect: throttle is forced to `0`.
- BLE failsafe timeout: if throttle was set by BLE and no BLE throttle update arrives for 15s, throttle is forced to `0`.
- Serial commands disable BLE failsafe activity until next BLE throttle command.

