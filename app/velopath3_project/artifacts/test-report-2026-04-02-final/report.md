# VeloPath Test Report

Date: 2026-04-02  
Environment: Android Emulator `Medium_Phone_API_36.1`  
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Build and static checks

- `:app:assembleDebug` — passed
- `:app:testDebugUnitTest` — passed
- `:app:lintDebug` — passed with warnings only, `0 errors`

## Functional scenarios covered

1. Cold start after clean launch
   - verified branded splash instead of a blank white screen
   - app reaches dashboard successfully
   - cold start after final fixes observed in emulator at about `6.6-9.1s`

2. Theme switching
   - switched app to dark theme
   - verified dark theme on dashboard, device lab and route screen
   - verified theme persists after restart

3. Dashboard flow
   - dashboard renders without white-screen stall
   - mini-map, search, coordinates and quick-start sections render
   - compact theme control remains functional
   - dark palette is more consistent after card/background cleanup

4. Device lab
   - diagnostics screen opens from dashboard
   - BLE controls, telemetry placeholders, test command groups and console render
   - verified contrast/readability in dark theme

5. Route screen
   - route screen opens with destination parameters
   - route analytics render with actual values
   - Bluetooth panel collapse works
   - current-location return button is visible
   - dark theme route cards remain readable on top of the map

## Limitations of this run

- Real BLE connection to the physical handlebar device was not verified in the emulator.
- Hardware compass, motors and ESC interactions were not exercised from the Android app in this run.

## Screenshots

- `01-dashboard-final.png` — dashboard in dark theme after visual cleanup
- `dashboard-current.png` — dashboard state used during visual verification
- `device-lab-current.png` — diagnostics/device lab in dark theme
- `route-current.png` — route screen with analytics values
- `route-collapsed-final.png` — route screen with Bluetooth panel collapsed
