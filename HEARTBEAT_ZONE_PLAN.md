# Heartbeat Training Zone — Implementation Plan

## 1. Persistence approach — **SharedPreferences**

**Recommended: `SharedPreferences`** (Android core API, already available, zero new dependencies).

- Prefs file name: `"runner_settings"`
- Keys: `"zone_min_bpm"`, `"zone_max_bpm"` (type `Int`)
- Defaults: **100** (min), **180** (max)
- Load: synchronous read once at app start (`getInt`)
- Write: `edit().putInt(...).apply()` on value change

## 2. UI fields placement

In `ConfigScreen` (`Screens.kt`), insert a **"Heart rate zone" card** between the `GpsPanel` and the **"New Session"** button:

```
Spacer(24.dp)
TrainingZoneCard(zoneMinText, zoneMaxText, onZoneMinChanged, onZoneMaxChanged)   // new private composable
Spacer(24.dp)
Button("New Session")   // unchanged
```

`TrainingZoneCard` (new private composable in `Screens.kt`, mirroring `GpsPanel`'s Card style):
- `Card` with title `"Heart rate zone"`
- Two `OutlinedTextField`s, `singleLine = true`, in a `Row`
- Both use `KeyboardOptions(keyboardType = KeyboardType.Number)`
- Labels: `"Min bpm"`, `"Max bpm"`
- Values are `String`

Layout caveat: adding 2 fields makes the existing `Arrangement.Center` column tight on small screens. Add `verticalScroll(rememberScrollState())` to the `Column` modifier.

## 3. Wiring: state → UI → persistence

All wiring lives in `RunnerApp` (`MainActivity.kt`), consistent with existing state style.

**State (in `RunnerApp`):**
```kotlin
val prefs = remember { context.getSharedPreferences("runner_settings", Context.MODE_PRIVATE) }
val zoneStore = remember { TrainingZonePrefs(prefs) }
var zoneMinText by remember { mutableStateOf(zoneStore.read().first.toString()) }
var zoneMaxText by remember { mutableStateOf(zoneStore.read().second.toString()) }
```

**Handlers (passed to `ConfigScreen`):**
```kotlin
onZoneMinChanged = { text ->
    zoneMinText = text
    text.toIntOrNull()?.let { zoneStore.write(min = it) }
},
onZoneMaxChanged = { text ->
    zoneMaxText = text
    text.toIntOrNull()?.let { zoneStore.write(max = it) }
}
```

**Data flow:** user types → `String` updates immediately → valid int parsed and persisted with `apply()` → reloaded next app start. Invalid/empty input simply doesn't overwrite last persisted value.

## 4. Files

### Create: `app/src/main/java/com/example/runner/TrainingZonePrefs.kt`
Tiny wrapper isolating storage (see plan body for full class).

### Modify: `app/src/main/java/com/example/runner/ui/Screens.kt`
- Add 4 params to `ConfigScreen` signature: `zoneMinText`, `zoneMaxText`, `onZoneMinChanged`, `onZoneMaxChanged`
- Add `verticalScroll` to the `Column` modifier
- Add new private `TrainingZoneCard` composable + imports

### Modify: `app/src/main/java/com/example/runner/MainActivity.kt`
- Import `Context`
- Add `zoneMinText` / `zoneMaxText` state + `TrainingZonePrefs` instance in `RunnerApp`
- Pass 4 new args to `ConfigScreen` call

No new Gradle dependencies, no version-catalog changes, no coroutines, no tests.

## 5. Edge cases
- Empty field: UI shows empty; persisted value stays last valid
- Non-numeric / out-of-range: parsed to `null`, not persisted
- `min > max`: optional guard; not required

## Out of scope
- No validation UI (red borders etc.)
- Defaults 100/180 are arbitrary; adjust constants if desired
- If zone values should later drive recording logic, `TrainingZonePrefs` is the single seam to change