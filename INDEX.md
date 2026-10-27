# VeloPath project index

This repository contains the Android application, STM32 firmware, PCB design files,
engineering documentation and production materials.

## Source trees

- Android application: [`app/velopath3_project`](app/velopath3_project)
- STM32 firmware and diagnostics: [`app/velopath3_project/firmware`](app/velopath3_project/firmware)
- ESP firmware: [`firmware/esp`](firmware/esp)
- PCB source and manufacturing files: [`pcb`](pcb)
- Android/firmware build instructions: [`app/velopath3_project/README.md`](app/velopath3_project/README.md)

## Documentation

- Reports and explanatory materials: [`documentation/reports`](documentation/reports)
- Engineering process notes: [`documentation/engineering`](documentation/engineering)
- Mechanical CAD: [`documentation/mechanical_cad`](documentation/mechanical_cad)
- Software architecture: [`documentation/software_stack_non_esp.md`](documentation/software_stack_non_esp.md)
- Component and source map: [`documentation/materials_origin_and_import_map.md`](documentation/materials_origin_and_import_map.md)

## Product materials

- Presentations: [`advertising/presentations`](advertising/presentations)
- Brochures: [`advertising/brochures`](advertising/brochures)
- Shared visual assets: [`advertising/assets`](advertising/assets)
- Missing-component table: [`outputs`](outputs)

Generated IDE workspaces, build output, firmware binaries, temporary test files,
backups and machine-specific configuration are intentionally excluded from the
repository. See [`.gitignore`](.gitignore).
