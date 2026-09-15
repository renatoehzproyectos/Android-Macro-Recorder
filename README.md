# Android Macro Recorder

Record touch input as a fully-editable, timeline-based macro, then play it
back with precise timing and repeat control. Kotlin + Jetpack Compose.

> "Record everything → edit the recorded timeline → configure repetition → play it back exactly."

## What's implemented (MVP / P0 from the spec)

- **Raw event model** — `MacroEvent` / `PointerState`, monotonic nanosecond
  timestamps, multi-touch pointer IDs preserved (never collapsed into a
  single generic swipe).
- **Recording** — `InAppRecorder` captures real `MotionEvent`s from a
  recording surface inside the app (Mode A / full fidelity), with a live
  overlay (timer + action count + Stop).
- **Timeline editor** — zoomable, scrollable Compose canvas timeline with
  event markers and derived-gesture bands (tap/swipe/long-press), drag-to-select.
- **Action editing** — edit timestamp (with optional ripple), edit X/Y, clone
  (with offset), clone selection preserving relative timing, delete, undo/redo.
- **Bulk editing** — multi-select, offset timing, scale timing.
- **Repeat system** — Once / N times / Infinite, configurable from the editor.
- **Playback engine** — schedules against **absolute target timestamps**
  (`playbackStart + event.timestamp`) instead of chained `sleep()`s, so
  per-event scheduling error doesn't accumulate into drift. Supports
  pause/resume/stop and always releases pointers on stop.
- **Two playback backends**:
  - `InAppPlaybackBackend` — replays into an in-app preview (safe, no
    permissions needed).
  - `AccessibilityPlaybackBackend` — dispatches real gestures into *other*
    apps via `AccessibilityService.dispatchGesture`, the only public,
    non-root API Android offers for this. Requires the user to enable the
    Accessibility Service manually in Settings.
- **Storage** — human-readable JSON macros in app-private storage, debounced
  autosave, rename/delete, create multiple macros, Home screen list.
- **Validation** — detects unbalanced DOWN/UP pointer sequences.

## Honest constraint (spec §3, §92, §151)

Android does **not** give a normal app raw system-wide touch events from
other apps. This project is explicit about that:

- Recording is full-fidelity only for gestures performed **inside this
  app's own recording surface** (Mode A).
- System-wide automation is offered only for **playback**, using the public
  `GestureDescription`/`dispatchGesture` Accessibility API — not a raw input
  stream, and not a bypass of Android's permission model.

The `CaptureBackend` / `PlaybackBackend` interfaces exist so a future,
explicitly-privileged/rooted backend could be added later without touching
the editor or timeline code.

## Not yet built (P1/P2 from the spec)

Timeline event filtering/search, loop regions & markers, coordinate
scale/offset UI wiring (logic exists in `MacroEditor`, not yet in the
inspector), swipe path editor, import/export via Storage Access Framework,
orientation-mismatch handling, event compression, copy/paste, snapping,
device-info recording. The architecture (separate `capture/`, `playback/`,
`editor/`, `storage/`, `model/`, `ui/` packages) is built so these slot in
without rewrites.

## Project layout

```
app/src/main/java/com/example/macro/
├── MainActivity.kt
├── model/          MacroEvent, PointerState, Macro, PlaybackSettings
├── editor/          MacroEditor (undo/redo mutation), GestureAnalyzer
├── capture/         CaptureBackend, InAppRecorder
├── playback/         PlaybackEngine, PlaybackBackend, InApp/Accessibility backends
├── storage/         MacroRepository (JSON), AutosaveManager
├── service/         MacroAccessibilityService (gesture dispatch)
└── ui/                MacroViewModel, HomeScreen, EditorScreen (timeline, inspector)
```

## Build locally

Requires JDK 17.

```bash
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

(If you don't have a Gradle wrapper jar committed, run `gradle wrapper` once
with any local Gradle 8.7 install, or just use Android Studio: File → Open
this folder and hit Run.)

## Build via GitHub Actions (no local Android Studio needed)

Push this repo to GitHub. `.github/workflows/build.yml` runs on every push
to `main` and on manual dispatch:

1. Checks out the repo.
2. Sets up JDK 17 and Gradle 8.7 (via `gradle/actions/setup-gradle`).
3. Runs `gradle assembleDebug`.
4. Uploads `app-debug.apk` as a workflow artifact you can download from the
   Actions run summary.

To trigger manually: **Actions → Build APK → Run workflow**.

## Using the app

1. **Home** → New Macro → name it.
2. In the editor, tap **● Record**, perform taps/swipes/holds on the dark
   recording surface, tap **STOP**.
3. Tap any marker on the timeline to select it; edit its timestamp, X/Y,
   clone or delete it in the inspector below.
4. Multi-select by dragging across the timeline; bulk offset/scale/clone/delete.
5. Set **Repeat** (once / N / infinite) from the playback bar.
6. Toggle **System-wide** if you want playback to drive other apps (enable
   the Accessibility Service first: Settings → Accessibility → Macro
   Recorder), otherwise playback replays into the in-app preview.
7. **▶ Play** — **STOP** is always available and pointers are released
   safely even if you stop mid-gesture.
