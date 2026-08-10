# Changelog

All notable changes to this repository should be recorded in this file.

## 2026-08-10

### Added

- Added a dedicated hardware audit report in `documentation/reports/hardware_audit_2026-08-10/`.
- Added archived KiCad ERC and DRC JSON artifacts for the audited hardware source set.

### Changed

- Updated the BLE hardware report to reflect the actual WH-BLE106 control-line wiring on the board.
- Updated the project index with the new hardware audit location.

### Fixed

- Closed the PCB clearance issues previously reported around the `D13` / `Q1` area.
- Closed the schematic ERC issues on `PC6` and `PC7`.
- Re-ran KiCad checks and confirmed the final hardware source set passes with `ERC = 0`, `DRC = 0`, and `0` unconnected items.

### Notes

- A design-review follow-up remains open for the intended `HY2122-AB1B` current-sense (`CS`) implementation. This is not a current KiCad ERC/DRC violation.
