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
- [x] Manual smoke test on device (GPS signal + coordinates on-screen)

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
- [x] Manual smoke test on device

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
- [x] Manual smoke test on device (start, record, pause, resume, stop → file in Downloads)

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

---

## Two-Screen Split Plan (architect, 2026-09-13)

**Goal:** Split single-screen app into Config screen + Session screen. No logic changes.

### Design

- `enum class AppScreen { CONFIG, SESSION }` — simple navigation state in `RunnerApp`.
- **ConfigScreen**: Device scan/select + HR connection status/display + GPS panel + "New Session" button.
  - DeviceScanScreen shown when no device selected.
  - When connected: shows HR display, GPS, Disconnect, and "New Session" (enabled only when connected).
- **SessionScreen**: Device name + HR display + RecordingControls + GPS panel + "Disconnect" button.
  - Same as current HeartRateScreen minus the START button (recording starts when entering session).
  - STOP → stops recording + navigates back to CONFIG.
  - Disconnect → navigates back to CONFIG.

### Changes

1. `ui/Screens.kt`: Add `ConfigScreen` and `SessionScreen` composables. Remove old `HeartRateScreen`.
2. `MainActivity.kt`: Add `currentScreen` state. Config → "New Session" starts session + navigates to Session. Session → "Stop" stops + navigates to Config. Session → "Disconnect" navigates to Config.

### Tasks

- [x] Write plan
- [x] Add `AppScreen` enum to `ui/Screens.kt`
- [x] Create `ConfigScreen` composable
- [x] Create `SessionScreen` composable
- [x] Update `MainActivity.kt` navigation
- [x] Build and verify

## Progress Notes (Two-Screen Split)

- 2026-09-13: Plan obtained. Implementation completed.
  `./gradlew :app:assembleDebug :app:testDebugUnitTest` BUILD SUCCESSFUL.
  Manual smoke test on device passed (config → new session → record → stop → back to config).

---

## Session Screen Simplification Plan (architect, 2026-09-13)

**Goal:** Session screen shows ONLY heart rate value + session buttons. Remove everything else.

### Design Decisions

- RecordingControls already covers Start/Pause/Resume/Stop — no changes needed.
- Remove unused params from SessionScreen (device, connectionState, gpsState, onDisconnect).
- Do NOT edit HeartRateDisplay (shared with ConfigScreen) — render value-only HR inline.
- Disconnect button removed from session; STOP returns to config.

### Changes

1. `ui/Screens.kt`: Rewrite `SessionScreen` — new params: `heartRate`, `recordingState`, 4 callbacks. Body: HR value text + Spacer + RecordingControls. Remove device name, connection-state block, Disconnect button, GpsPanel.
2. `MainActivity.kt`: Simplify `SessionScreen(...)` call-site to pass only new params. Remove device/connectionState/gpsState/onDisconnect.

### Tasks

- [x] Rewrite SessionScreen in Screens.kt
- [x] Update SessionScreen call-site in MainActivity.kt
- [x] Build and verify

## Progress Notes (Session Screen Simplification)

- 2026-09-13: Plan obtained. Implementation completed.
  `./gradlew :app:assembleDebug :app:testDebugUnitTest` BUILD SUCCESSFUL.
  SessionScreen now shows only HR value + RecordingControls (Start/Pause/Resume/Stop).
  Manual smoke test on device passed (session shows only HR value + buttons; START/PAUSE/RESUME/STOP work; STOP returns to config; config screen unchanged).

---

## Heartbeat Training Zone Plan (architect, 2026-09-15)

**Goal:** Add min/max heartbeat training zone fields to the Config screen, persisted across restarts.

### Design

- Persistence: `SharedPreferences` (file `runner_settings`, keys `zone_min_bpm` / `zone_max_bpm`,
  defaults 100 / 180). Zero new dependencies.
- `TrainingZonePrefs` (`app/src/main/java/com/example/runner/TrainingZonePrefs.kt`): thin wrapper
  with `read()` / `write(min, max)`.
- UI: `TrainingZoneCard` composable in `ui/Screens.kt` (title "Heart rate zone", two
  `OutlinedTextField`s "Min bpm"/"Max bpm", `KeyboardType.Number`), placed between `GpsPanel`
  and the "New Session" button. Column now scrolls (`verticalScroll`) instead of `Arrangement.Center`.
- State: `zoneMinText` / `zoneMaxText` Strings in `RunnerApp`; typed text updates immediately,
  valid ints persisted via `.apply()`. Invalid/empty input keeps last persisted value.

### Tasks

- [x] Write plan (`HEARTBEAT_ZONE_PLAN.md`)
- [x] Create `TrainingZonePrefs.kt`
- [x] Add `TrainingZoneCard` + params to `ui/Screens.kt`
- [x] Wire state + persistence in `MainActivity.kt`
- [x] Build and verify (`./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL)
- [x] Manual smoke test on device

## Progress Notes (Heartbeat Zone)

- 2026-09-15: Architect plan obtained. Implementation completed.
  `./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL. Manual test passed.

---

## Virtual Trainer Plan (architect, 2026-09-15)

**Goal:** Session screen shows a virtual trainer (RUN/WALK) driven by the heart
rate vs. the configured training zone (min/max bpm from SharedPreferences).

### Design

- `enum TrainerState { RUN, WALK }` in `ui/RecordingState.kt`.
- State `trainerState: TrainerState?` in `RunnerApp`; `null` when not RECORDING.
- Two `LaunchedEffect`s in `MainActivity.kt`:
  1. On recording state change: RECORDING -> RUN, else null.
  2. On heartRate update: hysteresis check with `zoneStore.read()` —
     RUN: HR >= max -> WALK; WALK: HR <= min -> RUN.
- `SessionScreen` gains `trainerState: TrainerState? = null`; root Column gets
  `Modifier.background(...)` (green 15% alpha for RUN, blue 15% for WALK);
  "RUN"/"WALK" `headlineLarge` text between HR value and elapsed time.

### Tasks

- [x] Write plan (`TRAINER_PLAN.md`)
- [x] Add `TrainerState` enum to `ui/RecordingState.kt`
- [x] Add trainer state + hysteresis logic in `MainActivity.kt`
- [x] Update `SessionScreen` (background color + RUN/WALK text) in `ui/Screens.kt`
- [x] Build passes (`./gradlew :app:assembleDebug` BUILD SUCCESSFUL)
- [x] Manual smoke test on device

## Progress Notes (Virtual Trainer)

- 2026-09-15: Architect plan obtained. Implementation completed.
  `./gradlew :app:assembleDebug` BUILD SUCCESSFUL. Manual test passed.