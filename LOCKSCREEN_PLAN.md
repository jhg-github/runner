# Lock screen status — plan & progress

Status: DONE (user confirmed working) — 2026-10-11

## Fix 3 (2026-10-11): notification shows only as an icon on the lock screen
REVERTED at user request: caused by the device lock-screen notification
setting ("Icons only"), which the user fixed from phone settings. The
`setShowWhenLocked` change was removed from MainActivity.

## Fix 2 (2026-10-11): "This app does not send notifications", still nothing on lock screen
Real root cause: app targets SDK 37 but never declared or requested the Android
13+ runtime permission `POST_NOTIFICATIONS`. Without it, foreground service
notifications are hidden from the notification drawer AND the lock screen
(only visible in the FGS Task Manager). App settings shows "This app does not
send notifications" because no channel/permission exists.
Fix:
- Manifest: added `<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>`.
- MainActivity: requests the permission at startup (API 33+, non-blocking;
  denial doesn't break BLE/GPS).
- MainActivity.onCreate + RecordingService: create the notification channel at
  app startup (companion `RecordingService.createChannel(context)`) so the
  channel always exists and is toggleable in App settings.

## Fix 1 (2026-10-11): nothing visible on lock screen
Root cause: channel was `IMPORTANCE_LOW` = "silent" notification. Many devices
hide silent notifications from the lock screen ("Hide silent notifications"
setting). Fix:
- Channel now `IMPORTANCE_DEFAULT` with sound/vibration disabled
  (`setSound(null,null)`, `enableVibration(false)`).
- Added `setCategory(CATEGORY_SERVICE)` to the notification.
Lock screen visibility also still requires (user side): app notifications
enabled, and device lock-screen setting not set to "show no notifications".

## Goal
User can see live training status on the lock screen without unlocking:
- run-or-walk instruction, using the app's colors (RUN=green, WALK=blue)
- heart rate
- session time

## Plan (architect)
Simplest approach: render the status in the already-running foreground service
(RecordingService) notification, which is visible on the lock screen.

- Service is alive only while `recordingState == RECORDING` (existing
  DisposableEffect in MainActivity). So the status notification exists during
  active recording only. Accepted as simplest option.

Changes:

1. `RecordingService.kt`
   - Read status extras on every `onStartCommand`:
     - `EXTRA_HEART_RATE` (Int, 0 = unknown -> "--")
     - `EXTRA_ELAPSED_MS` (Long)
     - `EXTRA_TRAINER` (String "RUN"/"WALK")
   - Rebuild notification each time:
     - contentTitle = trainer name (or "Recording" when no extras yet)
     - contentText = "{hr} bpm · {HH:MM:SS}"
     - color accent = 0xFF2E7D32 (RUN) / 0xFF1565C0 (WALK) — matches app text colors
     - setVisibility(VISIBILITY_PUBLIC) so it shows on lock screen
     - setOnlyAlertOnce(true) so 1-second updates don't re-alert
     - setOngoing(true)
   - `startForeground()` each update.

2. `MainActivity.kt`
   - In the existing per-second tick loop while RECORDING, send an update intent:
     `context.startService(Intent(...).putExtra(...))` with latest heartRate,
     elapsedMs, trainerState. Allowed on background because the service is
     already a running foreground service (Android 12+ rule).

3. `ui/RecordingState.kt`
   - Add shared `formatElapsed(ms)`; remove the private duplicate in `Screens.kt`.

## Progress
- [x] Plan recorded
- [x] RecordingService updated
- [x] MainActivity pushes status each tick
- [x] formatElapsed shared
- [x] Build: `./gradlew :app:assembleDebug` OK
- [ ] Manual test (below)

## Manual test
1. `./gradlew :app:installDebug` with a phone connected.
2. Important: notification only exists during an active RECORDING session
   (the foreground service runs only while recording). So a session must be
   recording for anything to appear.
3. Grant Bluetooth/GPS permissions, connect the HR monitor.
4. Start a session (RECORDING). Lock the phone.
5. Lock screen must show: RUN or WALK in green/blue, current bpm, elapsed
   time, updating every ~1 second.
6. If still invisible: check Settings -> Apps -> runner -> Notifications are
   enabled, and Settings -> Lock screen -> "Notifications on lock screen" is
   NOT "Don't show notifications".
7. Raise/lower heart rate across zone bounds -> RUN/WALK + color flip on lock
   screen.
8. Pause the session -> notification disappears (service stops; accepted).
9. Stop -> back to config screen, no stale notification.