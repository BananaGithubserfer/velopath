# Rezonit order package - 2026-08-21 full rebuild

Fresh package generated from a standalone KiCad project copy.

- KiCad project to inspect: `kicad_project/velopath_rezonit_2026_08_21.kicad_pro`
- Upload archive: `rezonit_upload_2026-08-21_from_scratch.zip`
- The original project under `pcb/velopath_final.*` was not modified.

## Upload archive contents

The zip contains exactly 11 production files for a 2-layer PCB:

- `*-F_Cu.gtl`
- `*-B_Cu.gbl`
- `*-F_Mask.gts`
- `*-B_Mask.gbs`
- `*-F_Paste.gtp`
- `*-B_Paste.gbp`
- `*-F_Silkscreen.gto`
- `*-B_Silkscreen.gbo`
- `*-Edge_Cuts.gm1`
- `*-PTH.drl`
- `*-NPTH.drl`

`*.gbrjob` and drill maps are kept outside the upload zip for inspection only.

## Generated artifacts

- Rezonit-compatible Gerbers and drill files: `gerbers_rezonit_compat/`
- Raw KiCad CLI fabrication export: `gerbers/`
- Grouped BOM and both-side position CSV: `assembly/`
- Top and bottom silkscreen previews: `previews/`
- Drill export report: `drill_report.txt`
- DRC reports: `drc_errors_only.txt`, `drc_report.txt`

## Generation settings

- Gerbers: `F.Cu,B.Cu,F.Paste,B.Paste,F.SilkS,B.SilkS,F.Mask,B.Mask,Edge.Cuts`
- Gerbers exported with solder mask subtraction enabled.
- Drill: Excellon, millimeters, decimal zeros, absolute origin, separate PTH/NPTH.
- Assembly extras: grouped BOM and both-side position CSV.

## Validation

- `drc_errors_only.txt`: 0 DRC violations, 0 unconnected pads, 0 footprint errors.
- `drc_report.txt`: full DRC report kept for review; all reported violations are `silk_over_copper` warnings from silkscreen clipped by solder mask.
