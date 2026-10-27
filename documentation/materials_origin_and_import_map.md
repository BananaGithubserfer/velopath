# Materials and repository layout

This map describes the current canonical locations inside the repository. Paths
are repository-relative so the project can be opened on another machine without
editing documentation.

## Source material

- Android and STM32 source: [`app/velopath3_project`](../app/velopath3_project)
- ESP source: [`firmware/esp`](../firmware/esp)
- PCB source and manufacturing data: [`pcb`](../pcb)
- Engineering and mechanical documents: [`documentation`](.)
- Product presentations and brochures: [`advertising`](../advertising)

## Generated material policy

Build directories, IDE metadata, release packages, logs, temporary exports,
backup copies and local credentials are not source material. They are ignored by
the repository rules and should be regenerated locally when needed.

The canonical source for the component inventory is the spreadsheet under
[`outputs`](../outputs). Do not create a second copy in a temporary directory.
