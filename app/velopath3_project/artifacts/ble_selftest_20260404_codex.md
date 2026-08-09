# BLE Self-Test by Codex

Date: 2026-04-04
Workspace: `C:\Users\vladimir.DESKTOP-L4RFPI0\AndroidStudioProjects\velopath3`

## What Was Tested

I tested the firmware from this machine with the connected ST-LINK and local BLE adapter:

1. rebuilt `firmware/Debug/velopath.elf`
2. flashed it with `STM32_Programmer_CLI.exe`
3. scanned with Bleak-based scripts
4. attempted console connection with `firmware/tools/ble_stable_terminal.py`

## Verified Working Pieces

- ST-LINK flashing works reliably
- the STM32 firmware itself runs after flash
- at least one BLE UART profile on the WH-BLE106 is alive on address `D4:AD:20:B1:B4:DA`
- when the module is in a compatible state, the BLE console path works end-to-end:
  - `ping -> pong`
  - `status`
  - `compass`

## Verified Failures

### 1. Serial AT auto-configuration is not reliable from the STM32 side

Multiple firmware variants were tried:

- full rewrite profile application
- UUID/profile forcing
- factory reset (`AT+RELOAD`) recovery path
- delayed AT retries
- condensed `+++a` fallback

On real hardware these variants did not produce a stable, repeatable boot-time configuration.

### 2. Factory reset can make the module discoverable but not app-compatible

After some recovery flashes the module advertised briefly as `WH-BLE 106`, but the connection path could fail with:

- notify start on fallback char `0002`
- Windows `Access Denied`

That means the module can be alive, but still not be in the expected BLE UART profile for this project.

### 3. Long-running scan stability is still broken

Observed pattern during self-test:

- module may appear immediately after flash/reset
- it can disappear on subsequent scan cycles
- repeated retries after that often fail to rediscover it

## Most Important Conclusion

The real blocker is no longer the STM32-side transport code itself. The blocker is reliable control of the WH-BLE106 module state.

This board/project currently does **not** expose the module control pins in firmware:

- `nWake`
- `nReset`
- `nReload`
- `Link`

Without those pins, the MCU cannot reliably hard-reset or wake the module when it falls into a bad or sleeping state. Boot-time UART AT entry alone was not reliable enough on this hardware to recover it consistently.

## Practical Result

The firmware repository is left in a safe non-destructive state:

- no repeated factory reset on every boot
- no injected UART keepalive garbage into the BLE console channel
- BLE transport code remains isolated in `ble_link.c`

## Recommended Next Step

To finish this properly, one of these is needed:

1. hardware access to `nWake` and ideally `nReset`
2. direct USB-UART access to the WH-BLE106 for one-time provisioning and AT verification
3. a board-level schematic/pin map confirming how the module control pins are actually wired
