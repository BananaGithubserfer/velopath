# VeloPath Hardware Audit 2026-08-10

## Scope

This audit covers only the current KiCad hardware source set:

- `pcb/velopath_final.kicad_sch`
- `pcb/velopath_final.kicad_pcb`
- `velopath_final-current.net`

Fresh CAD checks were run on August 10, 2026 with KiCad `10.0.2`:

- `erc-2026-08-10.json`
- `drc-2026-08-10.json`

## Executive Summary

The board is largely coherent at the schematic/topology level: the STM32, WH-BLE106, LIS3MDL, charger, LDO, reset/SWD, and vibration outputs are wired in a way that matches the intended product architecture.

The audit found three concrete issues that should be tracked:

1. PCB DRC still reports three real clearance violations around the `D13` / `Q1` area.
2. Schematic ERC still reports two unconnected STM32 pins: `PC6` and `PC7`.
3. The BLE firmware document was stale: the board does route `nWake`, `nReset`, `nReload`, and `Link`, but the current firmware `.ioc` does not assign those pins yet.

An additional open engineering risk remains in the battery protection section: the `HY2122-AB1B` `CS` node is not obviously tied to the MOSFET current-sense node in the current netlist, so the overcurrent model should be rechecked directly against the intended application circuit before release.

## Verified Hardware Topology

### Power and Battery Path

- `J2` is a 3-wire battery connector with `B+`, `BM`, and `B-`.
- `Q1` is `HY2122-AB1B` and drives `Q2` / `Q3` gate nets (`Net-(Q2-G)`, `Net-(Q3-G)`), which is consistent with a 2-cell protection stage using back-to-back N-MOSFETs.
- `J5` (`ESC_power`) is fed from `B+` on pin 2 and the protected system negative (`GND`) on pin 1.
- `D3` is `HT7333-A`; its `VIN` is on `B+`, and its `VOUT` creates the `+3.3V` rail used by the MCU, BLE module, and magnetometer.

### Charger Section

- `J1` is the charger input connector.
- `U7` is `CN3765`.
- `PA0` on the STM32 monitors charger status through the `chrg_done` net.
- The charge indicator LED path is present on `D13` and `U7 pin 3`; this same physical area is where the current PCB clearance errors are located.

### MCU and Core Support

- `U3` is `STM32F103RGTx`.
- `PD0` / `PD1` are connected to the external `8 MHz` resonator `Y2`.
- `NRST` is wired to `SW1`.
- `PA13` / `PA14` are routed to the SWD header `P2`.
- `BOOT0` is strapped through `R11`.
- All power pins (`VBAT`, `VDDA`, `VDD`) are on `+3.3V`; all ground pins (`VSSA`, `VSS`) are on `GND`.

### BLE Section

- `U6` is `WH-BLE106`.
- `U6` power pins `VBAT_6` and `VBAT_14` are on `+3.3V`; `GND_13` and `GND_15` are on `GND`.
- `UART_TX_9` -> `U3 PA3`
- `UART_RX_10` -> `U3 PA2`
- `Link_11` -> `U3 PA5`
- `nReset_12` -> `U3 PA4`
- `nWake_2` -> `U3 PC4`
- `nReload_1` -> `U3 PC5`
- `SWDIO`, `SWCLK`, three GPIO pads, and the module `ANT` pad are marked no-connect in the current schematic/netlist.

### Sensor and Actuator Section

- `U1` is `LIS3MDL`.
- `PB10` / `PB11` carry `I2C2` (`SCL2` / `SDA2`) to the sensor.
- `U1` is powered from `+3.3V` and `GND`.
- `DRDY` and `INT` on `LIS3MDL` are intentionally left unconnected.
- `PC1` and `PC2` drive the right and left vibration connectors.

## Confirmed Findings

### 1. PCB clearance violations remain open

Fresh DRC reports three `error`-level clearance violations:

- `Net-(D13-K)` track to `GND` via at approximately `25.75967, 57.929301 mm`
- `Net-(D13-K)` track to the same `GND` via at approximately `25.3875, 59.15 mm`
- `Q1 pad 3` (`Net-(C25-+)`) to `Net-(C24--)` track at approximately `27.17, 61.18 mm`

These are fabrication blockers until the copper geometry is cleaned up.

### 2. ERC still has two unclosed MCU pins

Fresh ERC reports two `error`-level unconnected pins on `U3`:

- `PC6` (`pin 37`)
- `PC7` (`pin 38`)

If these pins are intentionally unused, they should be marked `no connect` in the schematic to close ERC cleanly.

### 3. BLE hardware and BLE firmware documentation were out of sync

The board already routes the optional WH-BLE106 control pins to the STM32, but the current firmware `.ioc` only assigns:

- `PA2` / `PA3` for `USART2`
- `PB10` / `PB11` for `I2C2`
- `PC1` / `PC2` for the vibration outputs
- `PA13` / `PA14` for SWD

That means the hardware is ready for stronger BLE control, but the generated firmware pin map has not been updated to use those lines yet.

### 4. Open risk: HY2122 current-sense topology needs final confirmation

The protection section is mostly plausible, but the present `CS`-side node for `Q1 pin 3` is only visible on `Net-(C25-+)` together with `C25` and `R18`. The current netlist does not make the intended DC sense path to the MOSFET switching node obvious.

This is not logged here as a proven schematic error yet. It is an open verification item that should be checked against the exact `HY2122-AB1B` application circuit used for the design.

## Artifacts

- [ERC JSON](./erc-2026-08-10.json)
- [DRC JSON](./drc-2026-08-10.json)

## Documentation Impact

The BLE report in `app/velopath3_project/firmware/docs/wh-ble106-report-and-docs.md` was updated alongside this audit so that it reflects the actual board wiring:

- the board does expose the WH-BLE106 control pins
- the firmware project still does not assign them in the current `.ioc`
