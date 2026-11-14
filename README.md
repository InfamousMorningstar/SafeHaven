# SafeHaven: visitor waiver kiosk

Android tablet app for the front desk of **Safe Haven for Animals**, an animal shelter in
Arizona. A visitor fills in their contact details, reads the shelter's liability waiver, initials
each of its six clauses and signs with a finger. If the visitor is under 18, a parent or guardian
initials and signs instead. The app saves each signed waiver as a PDF on the device, works fully
offline, and clears itself for the next person.

> **Picking this up?** Read [`PROGRESS.md`](PROGRESS.md) first: what's done, what's next, the
> decisions taken and the client's requirements.

**Status:** the visitor waiver, the PIN-protected staff area, backup to a chosen folder and the
kiosk lock are built and tested on an Android 6.0 emulator that matches the target tablet (LG G
Pad X 8.0). Nothing has run on the real tablet yet. Setting the tablet up as a kiosk is in
[`KIOSK_SETUP.md`](KIOSK_SETUP.md).

## Build and run

Prerequisites: **Android Studio** (recent stable), which bundles a JDK and the Android SDK.

1. Open this folder in Android Studio and let Gradle sync. The wrapper downloads Gradle 9.1.0
   (the first version that runs on the JDK 25 bundled with current Android Studio).
2. Create a test emulator that matches the tablet: **API 23** (`google_apis` x86_64), a
   1200×1920 screen used in landscape, 320 dpi (960×600 dp), 2 GB RAM. `PROGRESS.md` describes
   the `LG_GPad_X8_API23` AVD used so far.
3. Run the `app` configuration.

Command line:

```
gradlew test                 # JVM unit tests
gradlew connectedAndroidTest # instrumented tests (needs a device/emulator; see the note below)
gradlew lint                 # NewApi is an error: newer APIs must sit behind SDK_INT checks
gradlew assembleRelease      # R8-minified release build
```

`gradlew` needs `JAVA_HOME` to point at a JDK, for example Android Studio's own
(`C:\Program Files\Android\Android Studio\jbr`).

On an emulator where the app is the **device owner** (kiosk testing), don't use
`connectedAndroidTest`: it tries to uninstall the app afterwards, which a device owner refuses.
Build with `gradlew assembleDebug assembleDebugAndroidTest`, install both APKs with
`adb install -r`, then run
`adb shell am instrument -w ca.safehaven.intake.test/androidx.test.runner.AndroidJUnitRunner`.

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
| One long scrolling form; scrolls to the first error on Submit | `ui/intake/IntakeFormScreen.kt` |
| The waiver wording, shared by the form and the PDF | `domain/model/Waiver.kt` |
| Typed initials on six clauses; a finger-drawn signature that doesn't scroll the page | `IntakeFormScreen.kt`, `ui/intake/SignaturePad.kt` |
| Under 18 (from the date of birth): parent/guardian name, initials and signature | `domain/validation/Rules.kt` (`DateOfBirthRules`) |
| US validation: names with accents, NANP phone, state, ZIP / ZIP+4, optional email | `domain/validation/` |
| Formatting as you type: `(602) 555-0123`, `85001-1234`, `06/01/2012` | `ui/InsertingVisualTransformation.kt` |
| Keyboard privacy (no suggestions; no personalized learning on API 26+) and Autofill off | `ui/PrivateTextInput.kt`, `MainActivity.kt` |
| 2-page US Letter PDF (logo, contact details, waiver, initials table, signature) via the built-in `PdfDocument` | `data/pdf/AndroidIntakePdfWriter.kt` |
| File names `2026-11-09_0001.pdf` under `intake/YYYY/MM/`, from a counter that never repeats | `domain/FileNamer.kt`, `data/db/SequenceCounter.kt` |
| Atomic writes (temp file, fsync, rename) + SHA-256 | `data/files/IntakeFileStore.kt` |
| Room index of submissions (search fields only; the signature stays in the PDF) | `data/db/` |
| Pipeline: validate → reserve number → PDF in `.tmp/` → DB row → move the PDF into place. A save cut off part-way is finished or removed on the next start | `data/SubmitIntakeUseCase.kt`, `SafeHavenApp.kt` |
| Arizona time (`America/Phoenix`, no DST) for dates, the under-18 rule and file names | `IntakeConfig` |
| Android's own backups off (`allowBackup=false`, data extraction rules) | `AndroidManifest.xml` |
| Staff area behind a PIN (long-press the logo): salted PBKDF2 hash, lockout after 5 wrong tries, auto-lock after 2 idle minutes, no screenshots | `data/staff/`, `ui/staff/`, `MainActivity.kt` |
| Records list and search, PDF viewer, share one PDF, delete | `ui/staff/RecordsSection.kt`, `WaiverViewerScreen.kt` |
| Backup: copies PDFs not yet backed up to a folder picked with Android's folder picker (SD card, USB stick, or Google Drive if the Drive app offers folders) | `data/backup/`, `ui/staff/BackupSection.kt` |
| Kiosk: device owner, Lock Task, Home app, starts on boot | `kiosk/`, [`KIOSK_SETUP.md`](KIOSK_SETUP.md) |
| Forgotten-PIN reset from a PC over USB | `staff/PinResetReceiver.kt` |

Settings the client may want to change (timeouts, adult age, default state, device label) are in
`IntakeConfig` (`domain/model/IntakeModels.kt`). They're hard-coded; there is no staff Settings
screen. If the client changes the waiver wording, update `Waiver.kt` and bump
`Waiver.VERSION`, which is printed on every PDF.

### Deviations from the plan

- **The PDF is drawn**, not filled into the client's own fillable PDF. This avoids adding a PDF
  library, and the form and the PDF share one copy of the wording.
- **No navigation-compose** for the visitor flow. It's a three-state machine in the ViewModel,
  so there is no back stack that could lead to the previous visitor's form.
- **Core library desugaring is on**, because `java.time` doesn't exist below API 26.

## Still to do

- **Testing on the real tablet** with a release build (see below). Nothing has run on it yet.
- **Google Drive:** the app has no Drive code of its own. If Drive doesn't appear in the
  tablet's folder picker, backups go to an SD card or USB stick, or Drive upload has to be built.
- **A staff Settings screen** for the values in `IntakeConfig`.
- **Hardening:** baseline profile, encryption decision, accessibility pass.
- **Later:** more forms (pre-adoption questionnaire, adoption contract).

## Verify on the real tablet

These can't be checked on an emulator:

- The screen density (`adb shell wm density`); the test emulator assumes 320 dpi.
- The LG keyboard shows no suggestions from a previous visitor. Also turn off prediction and
  learning in the keyboard's own settings.
- Signing with a finger on the real screen.
- Every step of [`KIOSK_SETUP.md`](KIOSK_SETUP.md), and whether Google Drive appears in the
  folder picker.
- The PDF viewer's page scaling, and how long the PIN check takes.
- Cold start, typing lag and PDF time on the Snapdragon 617 with 2 GB RAM, using a **release**
  build.
