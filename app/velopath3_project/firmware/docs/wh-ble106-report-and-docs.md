# WH-BLE106 BLE Rewrite Report

## Scope

This rewrite only touches the firmware project in `firmware/`. The Android application was intentionally left unchanged.

## Current Status

Boot-time AT provisioning is currently disabled in the firmware image after hardware self-tests on 2026-04-04 showed that repeated automatic reconfiguration could push the WH-BLE106 into less reliable states on this board. The firmware keeps the BLE transport isolated in `ble_link.c`, but the module should be treated as externally provisioned until the board exposes reliable `nWake`/`nReset`-level control or direct serial provisioning.

## What Was Removed

The previous BLE integration mixed several concerns in `Core/Src/main.c`:

- low-level UART helpers for the WH-BLE106 module
- AT mode entry and configuration logic
- ad-hoc baud probing
- one-off BLE echo and beacon test modes
- direct console writes to `USART2` without a BLE driver boundary

That logic was removed from `main.c` and replaced with a dedicated WH-BLE106 transport layer in `Core/Inc/ble_link.h` and `Core/Src/ble_link.c`.

## New Architecture

The new firmware BLE stack is intentionally small and is based on the module datasheet rather than implicit assumptions:

1. `ble_link_init()`
   Initializes `USART2` at `57600 8N1`, which the manual lists as the UART default after startup.

2. Serial AT mode entry
   The driver enters AT mode with the documented handshake from section 4.1.1:
   - guard time before `+++`
   - wait for `a`
   - reply with `a` within the confirmation window
   - exit with `AT+ENTM`

3. Deterministic profile application
   The driver applies a fixed slave transparent profile aligned with the Android client:
   - `AT+NAME=VeloPath`
   - `AT+UART=57600,8,0,0`
   - `AT+MODE=S`
   - `AT+WMODE=0`
   - `AT+UUID=AA+0003CDD0`
   - `AT+UUID=BB+0003CDD1`
   - `AT+UUID=CC+0003CDD2`
   - `AT+ADP=ON`
   - `AT+MULTILINK=OFF`
   - `AT+PAIR=OFF`
   - `AT+PASSEN=OFF`
   - `AT+TRENC=OFF`
   - `AT+ADPTIM=10`
   - `AT+NOTIFY=OFF`
   - `AT+UARTFT=3`
   - `AT+PKLEN=200`
   - `AT+AUTOSLEEP=OFF`
   - `AT+Z`

4. Transparent runtime transport
   After configuration, the driver only does transparent UART transport:
   - incoming bytes are assembled into lines
   - `console_diag` and `app_protocol` consume those lines
   - outgoing console text goes through `ble_link_write()`

## Why These Settings

- `MODE=S`: the handlebar firmware is a peripheral endpoint; the phone connects to it.
- `WMODE=0`: transparent transmission is required because the Android app writes plain command lines ending with `\r\n`.
- `MULTILINK=OFF`: the application uses a single phone connection and does not need multi-link routing.
- `PAIR=OFF` and `PASSEN=OFF`: avoids extra pairing and password flows that the current Android client does not implement.
- `TRENC=OFF`: keeps transport compatible with the existing app, which does not provision encryption keys.
- `UUID=AA/BB/CC`: pins the module to the exact service, notify, and write UUIDs the Android client subscribes to. The official AT syntax uses `AT+UUID=<slot>+<uuid>`.
- `ADP=ON`: forces the slave to advertise even if the module was previously left with broadcasting disabled in NVM.
- `ADPTIM=10`: lowers the advertising interval so the device is easier to rediscover after power-up and reconnects.
- `NOTIFY=OFF`: this command controls UART-side connection prompts, not the BLE notify characteristic, so it stays off to keep the transparent channel clean.
- `UARTFT=3`: aligns AT-entry timing with the manual and keeps UART packetization predictable.
- `PKLEN=200`: follows the documented default upper bound; the module still uses the smaller of MTU and configured packet length.
- `AUTOSLEEP=OFF`: keeps the radio awake instead of disappearing from scans after idle periods.
- `Z`: performs a final restart after profile updates so advertising and UUID changes come up in one known state.

## Runtime Behavior

- `main.c` now starts the BLE link by calling `ble_link_init()` once at boot.
- The main loop only polls the transparent UART channel with `ble_link_poll_uart()`.
- `console_diag_printf()` now writes through the BLE driver instead of writing directly to `USART2`.
- `status` and `info` output now include a BLE summary line so the module state is visible during diagnostics.

## Datasheet Mapping

Implementation decisions were taken from these parts of the manual:

- Quick start and slave/master examples: transparent transmission workflow and mode switching.
- Section 3.2: transparent vs fixed-point transmission.
- Section 3.3.3: low power mode behavior and wake-up implications.
- Section 4.1.1: serial AT command entry timing for `+++`, `a`, and `AT+ENTM`.
- Section 4.1.3: AT command framing and `OK` / `ERR` response pattern.
- Section 4.1.4: command availability for `MODE`, `UART`, `UARTFT`, `PAIR`, `PASSEN`, `TRENC`, `MULTILINK`, `WMODE`, `UUID`, `ADPTIM`, `NOTIFY`, `AUTOSLEEP`, `Z`, and `PKLEN`.

For AT response handling details that are not spelled out in the short WH-BLE106 user manual, the implementation also follows the official USR AT command references for the same BLE UART family:

- `AT+UUID` set operations may acknowledge with `successful` instead of a trailing `OK`
- `AT+ADP` controls whether BLE advertising is enabled at all
- `AT+AUTOSLEEP` uses `AT+AUTOSLEEP=<Status>,<Time>`
- `AT+Z` acknowledges with `+RST:OK`

Reference manual:

- [WH-BLE106 User Manual](https://empere.in/wp-content/uploads/2023/08/WH-BLE106-User-Manual.pdf?srsltid=AfmBOoqeaEF_zBBNuAXmFrw11SsDXzJnkwkBNimYcTUrPpMwNVNLkeHL)

## Files Changed

- `Core/Inc/ble_link.h`
- `Core/Src/ble_link.c`
- `Core/Inc/board_config.h`
- `Core/Src/main.c`
- `Core/Src/console_diag.c`
- `README.md`

## Verification Notes

The Android app was checked read-only to confirm that it still sends command lines terminated with `\r\n`, so the firmware keeps line-based framing on the transparent transport.

The Android app also expects this exact GATT profile:

- service: `0003cdd0-0000-1000-8000-00805f9b0131`
- notify: `0003cdd1-0000-1000-8000-00805f9b0131`
- write: `0003cdd2-0000-1000-8000-00805f9b0131`

The firmware now programs those UUIDs explicitly on every boot before switching the module back to runtime mode.

The driver also treats `AT+UUID` as a special case during startup configuration and accepts `successful` as the success token, because the official USR BLE AT references use that response for UUID writes.

## Hardware Limitations

The current CubeMX project exposes only the BLE UART pins on `PA2/PA3`. The optional WH-BLE106 control pins from the datasheet are not mapped in firmware today:

- `nWake`
- `nReset`
- `nReload`
- `Link`

That means the rewritten driver can disable auto-sleep in software, but it cannot hard-reset or externally wake the module unless those pins are also wired on the PCB and added to the project.

If you want hardware validation next, the most useful smoke checks are:

1. Power the board and verify it advertises as `VeloPath`.
2. Connect from the Android app and request `status`.
3. Send `ping`, `compass`, and one navigation JSON command.
4. Confirm `ble status` reports `state=transparent` and `configured=yes`.
