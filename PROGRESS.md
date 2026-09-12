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

---

## Recording State Machine Plan (architect, 2026-09-11)

- State machine with 3 states: `WAITING_TO_START`, `RECORDING`, `PAUSE` (enum `ui/RecordingState.kt`,
  same pattern as `ui/ConnectionState.kt`).
- WAITING_TO_START: "START" button → RECORDING.
- RECORDING: "PAUSE" button → PAUSE.
- PAUSE: two buttons "RESUME" → RECORDING, "STOP" → WAITING_TO_START.
- State in composables (`RunnerApp`), callbacks matched to existing style (`onStartRecording`, etc.).
  No ViewModel.
- UI: `RecordingControls` composable inside `HeartRateScreen` (between Disconnect button and GPS panel).

## Recording Tasks

- [x] Create `ui/RecordingState.kt` (enum: WAITING_TO_START, RECORDING, PAUSE)
- [x] Add `RecordingControls` composable to `ui/Screens.kt`
- [x] Update `HeartRateScreen` to accept + render recording state/controls
- [x] Wire `recordingState` in `MainActivity.kt` (state + callbacks)
- [x] Build passes: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
- [ ] Manual smoke test on device

## Progress Notes (Recording)

- 2026-09-11: Architect plan obtained. Implementation completed.
  Build + unit tests pass. Remaining: manual smoke test on device.

---

## Session Recording Feature Plan (architect, 2026-09-11)

- `recording/TrackPoint.kt`: data class (lat, lon, elevation, timestamp, heartRate).
- `recording/SessionRecorder.kt`: plain Kotlin class holding points in RAM.
  - `startNewSession()` clears points + records session start.
  - `addPoint()` appends samples.
  - `flushToBackup(cacheDir)` writes full GPX to cache `session_backup.gpx` (every 30 s, crash-safe).
  - `saveToDownloads(context)` writes final GPX to Downloads via `MediaStore.Downloads`
    (no permission needed at minSdk 31), unique date-based name `Run_YYYY-MM-DD_HH-mm-ss.gpx`,
    clears points.
  - `generateGpxXml()` string-builds GPX 1.1 matching `docs/Night_Run.gpx` (trkpt + ele +
    time + gpxtpx hr extension).
- `gps/GpsLocation.kt`: `GpsState` gains `elevation` from `Location.altitude`.
- `MainActivity.kt`: `LaunchedEffect(recordingState)` loop — `delay(1000)` → sample
  latest GPS + HR → `addPoint`; every 30th sample → `flushToBackup`. Backups also on
  START and STOP; downloads save on STOP; backup deleted on disconnect/STOP.

## Session Recording Tasks

- [x] Create `recording/TrackPoint.kt`
- [x] Create `recording/SessionRecorder.kt` (backup + GPX export)
- [x] Add elevation to `gps/GpsLocation.kt`
- [x] Wire recorder + sampling/backup loop in `MainActivity.kt`
- [x] Build passes: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
- [ ] Manual smoke test on device (start, record, pause, resume, stop → file in Downloads)

## Progress Notes (Session Recording)

- 2026-09-11: Architect plan obtained. Implementation completed.
  Build + unit tests pass. Remaining: manual smoke test on device.
  Manual test passed but revealed GPS bug: coordinates freeze after ~5-6 records.

---

## GPS Stale Fix Bug Fix Plan (architect, 2026-09-12)

**Bug:** GPS data updates only in the first ~5-6 records; the rest keep the same value.
Root cause: `GpsTracker` uses bare `GPS_PROVIDER` with no foreground service.
Android throttles GPS after initial burst. No re-registration, no staleness check.

**Fix:** Foreground service (type `location`) + staleness guard + re-register on provider events.

### Changes

1. `AndroidManifest.xml` — add `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` permissions
   and `<service>` declaration for `RecordingService`.
2. `recording/RecordingService.kt` — NEW minimal `Service`: notification + `startForeground(LOCATION)`.
   No binder, no logic — keep-alive only.
3. `res/values/strings.xml` — add notification strings.
4. `gps/GpsLocation.kt` — add `fixElapsedMs` to `GpsState`; extract `register()` method;
   override `onProviderEnabled` to re-register.
5. `MainActivity.kt` — `DisposableEffect(recordingState)` to start/stop foreground service;
   staleness guard in sampling loop (skip points with fix > 5s old).

### Tasks

- [x] Modify `AndroidManifest.xml`
- [x] Create `recording/RecordingService.kt`
- [x] Add strings to `res/values/strings.xml`
- [x] Modify `gps/GpsLocation.kt`
- [x] Modify `MainActivity.kt`
- [x] Build and verify

## Progress Notes (GPS Fix)

- 2026-09-12: Architect plan obtained. Implementation completed.
  `./gradlew :app:assembleDebug :app:testDebugUnitTest` BUILD SUCCESSFUL.
  Manual test on device passed (GPS coords keep changing; no more frozen tail).