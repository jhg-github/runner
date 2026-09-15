# Plan: Virtual trainer (RUN/WALK) on session screen

Status: DONE

## Requirements
- Virtual trainer tells user when to run/walk during a recording session.
- Start RUNNING when recording starts.
- HR >= zone max -> WALK (so HR decreases).
- HR <= zone min -> RUN (so HR increases).
- Loop until session stopped.
- Session screen shows RUN (green-ish bg + "RUN") or WALK (blue-ish bg + "WALK").
- No RUN/WALK display when not recording (null background).

## Design (architect, 2026-09-15)
- `enum TrainerState { RUN, WALK }` in `ui/RecordingState.kt`.
- State `trainerState: TrainerState?` in `RunnerApp` (MainActivity.kt), null when not RECORDING.
- Logic: two `LaunchedEffect` blocks.
  1. On `recordingState` change: RECORDING -> RUN, else null.
  2. On `heartRate`/`recordingState` change: hysteresis check.
     - RUN: HR >= zoneMax -> WALK, else stay RUN.
     - WALK: HR <= zoneMin -> RUN, else stay WALK.
- Zone values from `zoneStore.read()` (SharedPreferences source of truth).
- UI: `SessionScreen` gains `trainerState: TrainerState?`. Root Column gets
  `Modifier.background(...)`; instruction text "RUN"/"WALK" between HR and elapsed.

## Changes
- [x] Write plan file
- [x] 1. `ui/RecordingState.kt` — add `TrainerState` enum
- [x] 2. `MainActivity.kt` — add `trainerState` state + 2 LaunchedEffects, pass to SessionScreen
- [x] 3. `ui/Screens.kt` — SessionScreen param + background color + instruction text
- [x] 4. Build verify (`./gradlew :app:assembleDebug`) BUILD SUCCESSFUL
- [x] 5. Manual test on device

## Verification
- `./gradlew :app:assembleDebug`
- Manual: New Session -> trainer shows RUN -> with HR above max shows WALK
  (set min/max low/high in config to force transitions) -> STOP clears display.