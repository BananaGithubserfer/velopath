# BLE Fix Follow-up

Source test report:

- `artifacts/ble_test_report_20260404_150447.txt`

## What Was Failing

The test report showed two main symptoms:

- advertising was unstable and the device often disappeared from scans
- the Android/desktop tooling could not find or subscribe to the expected notify characteristic

## Firmware Fixes Applied

The WH-BLE106 driver in `firmware/Core/Src/ble_link.c` was adjusted to match the datasheet and the Android client contract:

- explicit GATT UUID programming
  - `AT+UUID=AA+0003CDD0`
  - `AT+UUID=BB+0003CDD1`
  - `AT+UUID=CC+0003CDD2`
- advertising forced on with `AT+ADP=ON`
- advertising interval tightened with `AT+ADPTIM=10`
- auto-sleep disabled with `AT+AUTOSLEEP=OFF`
- UART-side connection prompts disabled with `AT+NOTIFY=OFF`
- final module restart with `AT+Z` after profile updates

After the retry report showed the device was often completely absent from scans, the startup AT handler was tightened again:

- `AT+UUID` success is now matched against `successful` instead of only `OK`
- `AT+UUID` syntax is now corrected to the official `AT+UUID=<slot>+<uuid>` form
- advertising enable is now forced explicitly instead of relying on previous module state
- `AT+AUTOSLEEP` is now sent as `AT+AUTOSLEEP=OFF,5`
- `AT+Z` restart is now explicitly awaited via `+RST:OK`
- `ERR` responses during AT setup now fail fast instead of timing out silently

## Important Clarification

`AT+NOTIFY` in the WH-BLE106 manual is the module connection prompt setting, not the BLE notify characteristic itself. Keeping it `OFF` prevents unsolicited text from appearing on the transparent UART/BLE data channel.

## Validation Performed

- firmware rebuilt successfully in `firmware/Debug`
- updated documentation written to `firmware/docs/wh-ble106-report-and-docs.md`
- Android app remained untouched

## Next Hardware Retest

1. Flash the rebuilt firmware.
2. Power-cycle the board.
3. Scan for `VeloPath`.
4. Verify the following UUIDs are present:
   - service `0003cdd0-0000-1000-8000-00805f9b0131`
   - notify `0003cdd1-0000-1000-8000-00805f9b0131`
   - write `0003cdd2-0000-1000-8000-00805f9b0131`
5. Connect and subscribe to notify.
6. Send `ping` and `status`.

## Remaining Risk

The current firmware project does not expose WH-BLE106 control pins such as `nWake` or `nReset`. If the module still disappears after this firmware fix, the next likely issue is hardware-level wake/reset/power behavior rather than the BLE profile itself.
