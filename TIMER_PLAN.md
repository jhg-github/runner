# Plan: Total elapsed session time (HH:MM:SS)

Status: DONE

## Requirements
- Show total elapsed session time on SessionScreen, format HH:MM:SS
- Elapsed = time since session started
- Does NOT increase while paused

## Design
Approved by architect. Timing state lives in `SessionRecorder` (already owns
`startTime`). Compose adds ONE new state var `elapsedMs` refreshed by the
existing 1s sampling loop (which is dead during PAUSE => display freezes).

Elapsed = now - startTime - accumulatedPausedMs; while paused, base is frozen.

## Changes
- [x] 1. `recording/SessionRecorder.kt`
      - Add `accumulatedPausedMs: Long`, `pauseStart: Instant?`
      - `onPause()` (idempotent), `onResume()` (folds pause duration)
      - `elapsedMillis()` (clamped >= 0)
      - `startNewSession()` resets timing fields
- [x] 2. `MainActivity.kt`
      - Add `var elapsedMs by remember { mutableStateOf(0L) }`
      - Sampling loop: `elapsedMs = recorder.elapsedMillis()` each tick
      - START: `elapsedMs = 0`; PAUSE: `recorder.onPause(); elapsedMs = ...`
      - RESUME: `recorder.onResume()`
      - Pass `elapsedMs` to `SessionScreen`
- [x] 3. `ui/Screens.kt`
      - `SessionScreen` gains `elapsedMs: Long` param
      - `formatElapsed(ms)` -> "HH:MM:SS" (Duration + String.format, Locale.US)
      - Show as Text between HR value and RecordingControls
- [x] 4. Build (`./gradlew :app:assembleDebug` OK) + manual test PASSED

## Verification
- `./gradlew :app:assembleDebug`
- Manual: START -> counts up; PAUSE -> freezes; RESUME -> continues after exact
  paused duration; STOP -> returns to CONFIG