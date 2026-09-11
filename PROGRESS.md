# Runner — BLE Heart Rate App

Plan and progress file (can be resumed if interrupted).

## Plan (from architect, with coder adjustments)

- BLE via `androidx.bluetooth:bluetooth` (official Jetpack BLE library, coroutines/Flow API).
  - **Coder adjustment:** no stable `1.0.0` exists yet; only `1.0.0-alpha02` is published
    (verified against Google Maven). Using `1.0.0-alpha02`.
  - No explicit `kotlinx-coroutines-android` dependency added; it is already provided
    transitively by Compose/lifecycle and the bluetooth library's `kotlinx-coroutines-core`.
- Heart Rate Service (UUID `0000180d-...`) / Heart Rate Measurement characteristic
  (UUID `00002a37-...`), notification-based. The device pushes a value roughly every 1 s;
  the app renders the latest value (per requirement, update rate ~1 s).
- Two screens: device scan/select and heart-rate display.
- No ViewModel; state lives in composables + `rememberCoroutineScope` / `LaunchedEffect`.
- Permissions: `BLUETOOTH_SCAN` (with `neverForLocation`) + `BLUETOOTH_CONNECT`,
  requested at runtime (minSdk 31, so no location permission needed).
- **Coder adjustment:** no auto-reconnect across configuration changes; a rotation tears the
  BLE connection down and the user reconnects. Kept simple per requirements; can be revisited.

## Tasks

- [x] Add bluetooth dependency to `libs.versions.toml` + `app/build.gradle.kts`
- [x] Add BLE permissions + `<uses-feature>` to `AndroidManifest.xml`
- [x] Create `ble/HeartRateMonitor.kt` (scan, connect, subscribe, parse, close)
- [x] Create `ui/ConnectionState.kt` (sealed connection state)
- [x] Create `ui/Screens.kt` (`DeviceScanScreen`, `HeartRateScreen`)
- [x] Rewrite `MainActivity.kt` (runtime permissions, screen routing, connect/disconnect)
- [x] Build passes (`./gradlew :app:assembleDebug`) and unit tests pass
- [x] Manual smoke test on device (scan, connect, live HR display)

## Progress Notes

- 2026-09-05: Architect plan obtained (see above). Verified androidx.bluetooth has only
  `1.0.0-alpha02` on Google Maven and inspected its API/source from the published AAR
  (`BluetoothLe.scan/connectGatt`, `GattClientScope.getService/subscribeToCharacteristic`).
- Implementation completed. `./gradlew :app:assembleDebug` BUILD SUCCESSFUL,
  `./gradlew :app:testDebugUnitTest` passes. APK at `app/build/outputs/apk/debug/app-debug.apk`.
- Remaining: manual smoke test on a physical device (scan, connect, live heart rate display).
  The manual test has passed.

---

## GPS Feature Plan (architect, 2026-09-11)

- GPS via plain `LocationManager` + `GPS_PROVIDER` (`minTimeMs=1000`, `minDistance=0m`).
  No Google Play Services, no new dependencies, no foreground service (app is foreground-only).
- Permission: `ACCESS_FINE_LOCATION` only, requested at runtime with the existing BLE
  permission request (minSdk 31).
- Signal strength from `Location.accuracy`: `<=10m` = GOOD, `<=50m` = WEAK, `>50m` = NONE;
  no fix yet = No signal.
- `gps/GpsLocation.kt`: `SignalStrength` enum, `GpsState` data class, `GpsTracker` wrapper
  (callback style, mirrors `ble/HeartRateMonitor.kt`).
- UI: `GpsPanel` composable at the bottom of `HeartRateScreen` (no new screen). Shows signal
  dot + label, lat/lon, accuracy.
- State in composables like the rest of the app; tracker started/stopped via
  `LaunchedEffect`/`DisposableEffect`.

## GPS Tasks

- [x] Add `ACCESS_FINE_LOCATION` to `AndroidManifest.xml`
- [x] Create `gps/GpsLocation.kt` (SignalStrength, GpsState, GpsTracker)
- [x] Add `GpsPanel` to `ui/Screens.kt`; add `gpsState` param to `HeartRateScreen`
- [x] Wire GPS in `MainActivity.kt` (permission, tracker, state)
- [x] Build passes: `./gradlew :app:assembleDebug :app:testDebugUnitTest` BUILD SUCCESSFUL
- [ ] Manual smoke test on device (GPS signal + coordinates on-screen)

## Progress Notes (GPS)

- 2026-09-11: Architect plan obtained. Implementation completed, build + unit tests pass.
  Remaining: manual smoke test on a physical device with GPS.
  The manual test has passed.