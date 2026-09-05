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