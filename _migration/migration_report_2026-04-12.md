# VeloPath migration report

Date: 2026-04-12

## Target structure created
- C:\Users\vladimir.DESKTOP-L4RFPI0\Documents\velopath\app
- C:\Users\vladimir.DESKTOP-L4RFPI0\Documents\velopath\firmware
- C:\Users\vladimir.DESKTOP-L4RFPI0\Documents\velopath\documentation
- C:\Users\vladimir.DESKTOP-L4RFPI0\Documents\velopath\pcb
- C:\Users\vladimir.DESKTOP-L4RFPI0\Documents\velopath\advertising

## Main sources collected
- Android project snapshot: AndroidStudioProjects\velopath3 -> ...\app\velopath3_project
- ESP32 firmware: Documents\firmware++ -> ...\firmware\firmware++
- STM32 firmware workspace: Documents\firmware -> ...\firmware\firmware
- PCB/meta: Documents\Velopath_meta -> ...\pcb\Velopath_meta
- PCB unpacked: Documents\velopath_unpacked -> ...\pcb\velopath_unpacked
- Legacy KiCad/web files from old Documents\velopath root sorted into app/pcb/documentation
- Booklets, logo and promo assets moved into advertising
- APK and archive moved into app\releases and documentation\archives
- STM32 workspace metadata moved: Documents\stm32_ws_codex -> ...\documentation\workspaces\stm32_ws_codex

## Inventory after migration
- app: files=1238, dirs=260
- firmware: files=573, dirs=84
- documentation: files=24, dirs=19
- pcb: files=140, dirs=11
- advertising: files=33, dirs=2

## Validation
- Remaining VeloPath-like matches outside target: 0

Detailed copy log: C:\Users\vladimir.DESKTOP-L4RFPI0\Documents\velopath\_migration\copy_log.txt

## Flash import update
- Source: D:\\ (removable)
- Local technical docs moved from Documents root into documentation\\engineering_local: 6 files
- Flash files copied (without deleting on flash): 97 files
- Details: C:\\Users\\vladimir.DESKTOP-L4RFPI0\\Documents\\velopath\\_migration\\flash_import_report_2026-04-12.md
