# JARVIS – Phase 1 (Skeleton + wake word)

## Build steps
1. Install Android Studio (Ladybug 2024.2 or newer).
2. Unzip this folder and open it: File › Open › select the `JARVIS` folder.
3. Let Gradle sync (first time downloads ~1 GB of tools, needs internet).
4. Phone: Settings › About phone › tap Build number 7 times › Developer options › USB debugging ON.
5. Connect the phone by USB, pick it in the device dropdown, press ▶ Run.
6. First run: allow permissions → finish the battery guide → turn Listening ON → say "Jarvis".

The Vosk small English model (vosk-model-small-en-us-0.15, Apache 2.0) is already bundled in
`app/src/main/assets/model-en-us`. It unpacks to internal storage on first start (a few seconds).

## Files
- `core/JarvisState.kt` – IDLE, LISTENING, THINKING, SPEAKING
- `core/StateHolder.kt` – single StateFlow for state, service on/off, status text
- `core/Engines.kt` – WakeWordEngine, SttEngine, Brain, TtsEngine, WebSearch interfaces
- `wakeword/VoskWakeWordEngine.kt` – Vosk, grammar `["jarvis","[unk]"]`, confidence threshold, 2 s debounce
- `service/JarvisService.kt` – microphone foreground service, Stop action, call pause/resume watchdog, chime, 8 s back to IDLE
- `data/JarvisDatabase.kt` – Room DB (wake events now; leads later)
- `settings/SettingsRepository.kt` – sensitivity, onboarding flags
- `ui/` – Theme, PermissionsScreen, BatteryGuideScreen, MainScreen, SettingsScreen
- `MainActivity.kt`, `JarvisApp.kt`, `AndroidManifest.xml`

## Libraries and licenses
Jetpack Compose, Room, AndroidX (Apache 2.0) · Vosk 0.3.47 + model (Apache 2.0) · JNA 5.13 (Apache 2.0 / LGPL).

## Assumptions
- compileSdk/targetSdk 35 (Android 15), minSdk 26.
- Sensitivity maps to the word confidence Vosk returns: 0% needs 0.95, 50% (default) needs 0.70, 100% needs 0.45. Above 80% it also reacts to partial results (faster, more false triggers).
- Android blocks apps from opening themselves from the background. JARVIS comes to the front if "Display over other apps" is allowed; otherwise it shows a high-priority notification that opens it (and wakes the lock screen if full-screen alerts are allowed).
- During a phone call the mic is paused, and listening restarts within ~2 s after the call ends.
- If the phone kills the service, Android 14 doesn't allow a mic service to restart by itself from the background; open the app and turn listening on.
- Not build-tested on a device yet: if Gradle or Kotlin shows an error, send the full error text.

## Test checklist (Phase 1)
1. Say "Jarvis" from 1 m in a quiet room, 10 times → triggers ___/10 (target 9).
2. Screen off, app in background → triggers ___/10.
3. Quiet TV/music for 5 min → false triggers: ___.
4. 30 min listening → battery used: ___%.
5. Receive a phone call → after it ends, "Jarvis" works again: yes/no.
6. Press Stop in the notification → mic indicator (green dot) turns off: yes/no.
Also: the log shows the last 10 events with time; state goes LISTENING → IDLE after 8 s; one "Jarvis" triggers only once.
