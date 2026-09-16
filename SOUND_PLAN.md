# Sound Feedback Plan

## Goal
Play audio cues when the virtual trainer switches between RUN and WALK.

## Behavior
- RUN cue: beep ×3 (400ms gap) then run.mp3
- WALK cue: beep ×3 (400ms gap) then walk.mp3
- Cues fire on trainerState changes (separate LaunchedEffect keyed on trainerState)

## Files
1. **NEW** `app/src/main/java/com/example/runner/audio/TrainerSoundPlayer.kt`
   - Owns SoundPool, loads beep/run/walk
   - `suspend fun playCue(isRun: Boolean)` — plays 3 beeps + run/walk sound
   - `fun release()` — frees SoundPool

2. **MODIFY** `app/src/main/java/com/example/runner/MainActivity.kt`
   - `remember { TrainerSoundPlayer(context) }` + `DisposableEffect` for release
   - `LaunchedEffect(trainerState)` that calls `playCue()` when state changes

## Edge cases
- Start of session: null→RUN fires run cue ✓
- Pause: effect returns early, in-flight beeps cancelled ✓
- Rapid HR flip: cancellation + maxStreams=1 prevents overlap ✓
- Sound load race: wait loop in coroutine ✓
