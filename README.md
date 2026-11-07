# SafeHaven: visitor intake kiosk

Android tablet app for the cat shelter's front desk. A visitor types their name, phone and
address and taps Submit. The app saves one PDF per visitor on the device, works fully offline,
and clears itself for the next person. The full design is in the Obsidian note
`Projects/App Idea - Technical Plan.md`.

> **Picking this up?** Read [`PROGRESS.md`](PROGRESS.md) first. The client is in
> Arizona and wants a liability waiver, so parts of the description below (Canadian sign-in)
> are about to change.

**Status:** phases 1–3 of the plan (project setup, form MVP, PDF + storage). The staff area,
kiosk lock and hardening are still to do (see below).

## Build and run

Prerequisites: **Android Studio** (recent stable), which bundles JDK 17+ and the Android SDK.

1. Open this folder in Android Studio and let Gradle sync. The wrapper downloads Gradle 9.1.0
   (the first version that runs on the JDK 25 bundled with current Android Studio).
2. For daily work, create emulators with **API 23** and **API 24** system images, in landscape
   at roughly 1280×800 dp, to match the LG G Pad X 8.0.
3. Run the `app` configuration.

Command line, once a JDK and SDK are installed:

```
gradlew test                 # JVM unit tests
gradlew connectedAndroidTest # instrumented tests (needs a device/emulator)
gradlew lint                 # NewApi is an error: newer APIs must sit behind SDK_INT checks
gradlew assembleRelease      # R8-minified release build
```

### Release signing

Release builds are signed only if `keystore.properties` exists in the project root (it is
git-ignored):

```
storeFile=../keys/safehaven-release.jks
storePassword=...
keyAlias=safehaven
keyPassword=...
```

**Back up the keystore in two places.** Every update has to be signed with the same key, and
reinstalling instead wipes all PDFs stored on the tablet.

## What's built

| Area | Where |
|---|---|
| Welcome → Form → Thanks flow, auto-reset after 5 s | `ui/intake/IntakeViewModel.kt`, `ui/VisitorApp.kt` |
| Inactivity timeout (90 s, then a 10 s "Are you still there?") | `IntakeViewModel.startIdleWatch` |
| Canadian validation (names with accents, NANP phone, postal code, soft province check) | `domain/validation/` |
| Phone `(403) 555-0123` and postal `T2X 1A1` formatting as you type | `ui/InsertingVisualTransformation.kt` |
| Keyboard privacy (no suggestions; no personalized learning on API 26+) and Autofill off | `ui/PrivateTextInput.kt`, `MainActivity.kt` |
| One-page US Letter PDF via the built-in `PdfDocument` | `data/pdf/AndroidIntakePdfWriter.kt` |
| File names `2026-11-04_1432_Smith-Jane_K7QF.pdf` under `intake/YYYY/MM/` | `domain/FileNamer.kt` |
| Atomic writes (temp file, fsync, rename) + SHA-256 | `data/files/IntakeFileStore.kt` |
| Room index of submissions | `data/db/` |
| Pipeline: validate → PDF → file → DB row (removes the PDF if the DB insert fails) | `data/SubmitIntakeUseCase.kt` |
| Backups off (`allowBackup=false`, data extraction rules) | `AndroidManifest.xml` |

Settings that the client may change (consent on/off, reason chips, name in file name, timeouts,
device label) are in `IntakeConfig` (`domain/model/IntakeModels.kt`). They're hard-coded until
the staff Settings screen exists.

### Deviations from the plan

- **No navigation-compose** for the visitor flow. It's a three-state machine in the ViewModel,
  so there is no back stack that could lead to the previous visitor's form. Navigation can
  come in with the staff area.
- **Address is stored as separate columns**, not `addressJson`, which makes staff search easier.
- **Core library desugaring is on**, because `java.time` doesn't exist below API 26.

## Still to do

- **Phase 4: staff area.** PIN (salted hash in DataStore), records list and search, `PdfRenderer`
  viewer, share/export (SAF + `FileProvider`), print, delete, settings, `RetentionWorker`.
- **Phase 5: kiosk.** `DeviceAdminReceiver`, `dpm set-device-owner` provisioning, Lock Task,
  persistent HOME activity, user restrictions, maintenance exit.
- **Phases 5b/6:** baseline profile, encryption decision (D4), `FLAG_SECURE` on staff screens,
  accessibility pass.
- **Client inputs** (marked `TODO(client)`): shelter name and logo, consent wording and
  retention period, whether names go in file names.

## Verify on the real tablet

These can't be checked without a device:

- **`PrivateTextInput` uses `InterceptPlatformTextInput`** (experimental Compose API). Confirm it
  compiles on the pinned Compose BOM, and that the LG keyboard shows no suggestions from a
  previous visitor. Also turn off prediction and learning in the keyboard's own settings.
- **Library versions** in `gradle/libs.versions.toml` were chosen to be known-good, not the
  newest. Let Android Studio suggest upgrades once the project builds.
- Cold start, typing lag and PDF generation time on 2 GB RAM, using a **release** build.
