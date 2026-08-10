# VeloPath Hardware Power Audit

Date: 2026-08-09
Scope: schematic and netlist review of battery, charger, protection, 3.3 V rail, BLE and compass integration
Primary files:
- `pcb/velopath_final.kicad_sch`
- `pcb/velopath.kicad_sym`
- `velopath_final.net`
- `velopath_final-erc.rpt`

## Confirmed findings

### 1. Repository netlist/ERC artifacts are stale relative to the current schematic

- `pcb/velopath_final.kicad_sch` was saved later on 2026-08-09 than the checked-in `velopath_final.net` and `velopath_final-erc.rpt`.
- A fresh ERC run on 2026-08-09 with `kicad-cli 10.0.2` against the current `pcb/velopath_final.kicad_sch` reports `0 violations`.
- The checked-in `velopath_final.net` and `velopath_final-erc.rpt` therefore describe an older schematic revision and cannot be treated as the current electrical truth.

Relevant references:
- `pcb/velopath_final.kicad_sch`
- `velopath_final.net`
- `velopath_final-erc.rpt`

Impact:
- Earlier findings that depended on the checked-in netlist/ERC must be revalidated against a freshly generated netlist.
- This includes prior claims that `Q2/Q3` were unconnected, that `Q1` had floating pins, and that BLE control nets were absent.

### 2. Current BMS topology is mostly coherent, but the `HY2122` current-sense path looks incomplete

- In the current schematic, `Q2` and `Q3` are connected as a low-side back-to-back MOSFET pair:
  - `Q2.S -> B-`
  - `Q3.S -> GND`
  - `Q2.D` and `Q3.D` share the same internal node
  - `Q1` drives the two gate nets separately
- `J5` currently takes `B+` on pin 2 and the protected return on `GND`/`Q3.S` at pin 1, which is topologically consistent with low-side protection.
- Mapping the `HY2122-AB1B` datasheet pinout onto the fresh netlist gives a mostly plausible match:
  - `pin 1 = OD -> Q2.G`
  - `pin 2 = OC -> Q3.G`
  - `pin 4 = VC -> BM` through `R9 = 470R`, filtered by `C23 = 100nF`
  - `pin 5 = VDD -> B+` through `R10 = 470R`, filtered by `C24 = 100nF`
  - `pin 6 = VSS -> B-`
- The remaining problem is `pin 3 = CS`:
  - in the fresh netlist, the `CS` net contains only `Q1 pin 3`, `C25`, and `C26`
  - the MOSFET midpoint `Net-(Q2-D)` contains only `Q2.D` and `Q3.D`
  - there is no resistor or other DC path from the MOSFET sense node to `CS`
- The `HY2122` datasheet application circuit expects a dedicated current-limit resistor `R3` between the MOSFET sense node and `CS`, and the IC uses the `CS-VSS` voltage for discharge overcurrent, charge overcurrent, and short-circuit detection.

Relevant references:
- `pcb/velopath_final.kicad_sch:10440`
- `pcb/velopath_final.kicad_sch:11229`
- `pcb/velopath.kicad_sym:576`

Impact:
- Overcharge and overdischarge control wiring now looks plausible, but current/short detection may not work as intended in the current schematic.
- If `CS` really lacks a DC sense path to the MOSFET midpoint, the pack may fail to cut off on discharge overcurrent, charge overcurrent, or load short according to the `HY2122` operating model.

### 3. Charger and BLE control integration exist in the current schematic, but the local symbols remain weakly documented

- A fresh netlist generated from the current schematic shows:
  - `U7 = CN3765`
  - `chrg_done` connected to `U3`
  - `Link`, `nWake`, `nReset`, and `nReload` connected between `U3` and `U6`
- The older checked-in netlist/ERC that showed missing BLE control connectivity is stale.
- The charger and BLE symbols still use degraded pin metadata in the local libraries, which makes review slower and easier to misread.

Relevant references:
- `pcb/velopath_final.kicad_sch`
- `app/velopath3_project/firmware/docs/wh-ble106-report-and-docs.md:134`

Impact:
- The live schematic no longer supports the claim that BLE control nets are absent.
- The remaining risk is reviewability and symbol clarity, not obviously missing connectivity.

### 4. LIS3MDL interrupt outputs are unused

- `U1` is powered from `+3.3V`.
- `DRDY` and `INT` are left unconnected.

Relevant references:
- `velopath_final.net:5223`
- `velopath_final.net:6531`
- `velopath_final.net:6542`

Impact:
- The MCU likely has to poll the sensor instead of waking on data-ready or interrupt events.
- This increases avoidable active-time energy use.

### 5. 3.3 V rail is generated from battery via linear regulator

- `D3 = HT7333-A` is wired as:
  - pin 2 `VIN` -> `B+`
  - pin 1 `GND` -> `GND`
  - pin 3 `VOUT` -> `+3.3V`
- This is topologically valid for a low-side protected system, but inefficient for a 2-cell battery stack if the digital domain is active for long periods.

Relevant references:
- `pcb/velopath.kicad_sym:448`
- `pcb/velopath.kicad_sym:519`
- `velopath_final.net:5390`
- `velopath_final.net:5223`

Impact:
- Power loss in the regulator can be significant because the design drops battery voltage directly to 3.3 V with an LDO.

## Open issues under active investigation

### A. The current schematic appears to use low-side protection intentionally

- Fresh connectivity strongly suggests an intentional low-side arrangement:
  - battery positive `B+` goes directly to the system/load side
  - the protected path is on the negative return through `Q2/Q3`
- This is consistent with the current `J5` mapping and with the absence of ERC violations in the current schematic.

### B. Naming around the protected return is still easy to misread

- The current schematic uses `GND` on the protected/system side of the low-side switch, where earlier artifacts referred to `P-`.
- That may be electrically fine, but it obscures the distinction between battery negative and protected system return when reading the schematic quickly.
- This is now a documentation/readability issue, not a proven connectivity fault.

## Next steps

1. Reconstruct the exact battery/charger/protection topology around `J1`, `J2`, `J5`, `Q1`, `Q2`, `Q3`, and the charger IC.
2. Recover or rebuild proper functional pin naming for `HY2122-AB1B` and `CN3765`.
3. Decide whether the protected return should remain named `GND` or be renamed/documented explicitly as `P-`.
4. Decide whether the 3.3 V rail should remain LDO-based or move to a buck topology.
