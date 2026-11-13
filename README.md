# Multi Calculator (Android)

Multi Calculator is a production-ready Kotlin + Jetpack Compose Android application combining an **arbitrary-precision Standard & Scientific Calculator**, an **integrated Notepad**, **searchable Calculation History**, **offline-first Room persistence**, **lossless JSON backup & restore**, and **Google AdMob monetization**.

---

## Core Features

1. **Arbitrary-Precision Calculator (`PrecisionMathEngine` & `BigDecimalMath`)**
   - Standard Mode: `+`, `−`, `×`, `÷`, `%`, `±`, `( )`, `.`, `AC`, `⌫`, live expression preview, copy full result, save to Notes.
   - Scientific Mode: `sin`, `cos`, `tan`, `sin⁻¹`, `cos⁻¹`, `tan⁻¹`, `log`, `ln`, `√`, `xʸ`, `x²`, `x³`, `n!`, `π`, `e`, `DEG`/`RAD` toggle.
   - **No 15/16-digit truncation**: Exact integer arithmetic (`123456789123456789 × 987654321987654321 = 121932631356500531347203169112635269`), configurable precision (16–100 digits) for division and transcendental functions, horizontal scroll for long numbers, and optional Scientific Notation display.
2. **Calculation History**
   - Auto-saves every evaluated calculation with date, time, mode, angle unit, and precision.
   - Search, copy full result, reuse in Calculator, save to Note, delete individual items, or clear all with confirmation.
3. **Advanced Notepad**
   - Create, edit, search, pin/unpin (pinned notes sorted first by latest update), copy, and delete notes.
   - Auto-save + manual Save + BackHandler flush so unsaved edits are never silently discarded.
   - Insert latest calculation directly inside the Note Editor.
4. **Offline-First Storage & JSON Backup/Restore**
   - Room SQLite database + DataStore Preferences work 100% offline.
   - Export and restore complete Notes & History via lossless JSON (Storage Access Framework — zero broad storage permissions).
5. **Google AdMob & UMP Consent**
   - Test ad unit IDs strictly isolated to `debug` builds; `release` builds inject production IDs from environment secrets.
   - Non-intrusive Banner ads (placed outside active calculator controls), cooldown-gated Interstitials, and voluntary Rewarded ads in Settings.

---

## Project Architecture

```text
app/src/main/java/com/example/
├── MainActivity.kt               # SplashScreen, Edge-to-Edge, Adaptive NavBar/NavRail, NavHost
├── ads/                          # AdMobManager (UMP + Banner/Interstitial/Rewarded)
├── data/
│   ├── backup/                   # BackupSerializer (kotlinx.serialization JSON)
│   ├── local/                    # Room Database, Entities, DAOs
│   ├── network/                  # ConnectivityMonitor (NetworkCallback)
│   ├── preferences/              # DataStore UserPreferencesRepository
│   └── repository/               # MultiCalculatorRepository
├── di/                           # AppContainer (manual constructor DI)
├── domain/math/                  # BigDecimalMath & PrecisionMathEngine
├── ui/
│   ├── navigation/               # Type-safe @Serializable routes
│   ├── screens/                  # Calculator, Notes, NoteEditor, History, Settings
│   └── theme/                    # Material 3 Light/Dark/System theme & bundled fonts
└── viewmodel/                    # Calculator, Notes, History, Settings ViewModels
```

---

## Building Locally

### Prerequisites
- JDK 21+
- Android SDK (Compile SDK 36, Min SDK 24, Target SDK 36)

### Debug APK (Personal Testing)
```bash
gradle :app:assembleDebug
```
Output APK: `app/build/outputs/apk/debug/app-debug.apk`

### Run Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

### Run Android Lint
```bash
gradle :app:lintDebug
```

---

## Configuration Guides
- [Firebase Auth & Cloud Firestore Setup](docs/FIREBASE_SETUP.md)
- [Google AdMob Production Setup](docs/ADMOB_SETUP.md)
- [Google Play Store Release & Signing Guide](docs/PLAY_STORE_RELEASE_GUIDE.md)

## Security & Secret Hygiene
Never commit `.jks` / `.keystore` files, signing passwords, `.env`, or `google-services.json` containing private credentials. All secrets are excluded in `.gitignore` and injected via CI environment variables or local Secrets configuration.
