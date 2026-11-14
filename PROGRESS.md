# SafeHaven: progress and handoff

Last updated: 2026-11-14 (end of session 5). Read this first when resuming work.

**Design source:** `D:\Obsidian\Nebula\Projects\App Idea - Technical Plan.md`. Section numbers
below (3.2, D3, etc.) refer to that plan. **Build and run steps:** [README.md](README.md).

## TL;DR

- The app is now the **Arizona liability waiver**: one long scrolling form, typed initials on the
  six clauses, a drawn signature, and an automatic switch to a parent/guardian signature under 18.
  Each submission is saved as a 2-page PDF named by date and sequential number
  (`2026-11-09_0001.pdf`) plus a Room row.
- **Staff area, backup and kiosk are built** (sessions 4 and 5, committed 2026-11-14): PIN with
  lockout, records list/search, PDF viewer, share, delete, backup to a folder picked with
  Android's folder picker, device-owner kiosk (`KIOSK_SETUP.md`).
- **120 unit tests** and **6 instrumented tests** pass on the Android 6.0 emulator that matches
  the client's tablet, and `lint` reports no errors (2026-11-14).
- Next: commit, then test on the **real tablet** with a **release build**. Nothing has run on
  the LG G Pad yet. See "Next steps".

## Current state

- **Git:** `github.com/InfamousMorningstar/SafeHaven`, a **public** repo, branch `main`. Session 3
  is **pushed** (2026-11-09), including the client's **logo** and the **waiver wording**
  (`domain/model/Waiver.kt`). Sessions 4 and 5 (the staff area, backup, kiosk, the review fixes,
  `KIOSK_SETUP.md`) are committed and pushed (2026-11-14).
- **Toolchain:** Android Studio's bundled **JDK 25**. Gradle 9.1.0, AGP 8.13.0, Kotlin 2.2.21,
  KSP 2.3.13, Compose BOM 2025.08.00, Room 2.7.2.
- **Client documents** are in `docs/client/`, which is **git-ignored on purpose because the repo
  is public**. They stay on this PC only:
  - `Proposal-with-client-answers.pdf`: the proposal, with the client's answers typed in.
  - `SHFA-WaiverForm.pdf`: their 2-page fillable (AcroForm) paper waiver. The source of the text
    in `Waiver.kt`.
  - `logo.png`: the logo. A copy is in `app/src/main/res/drawable-nodpi/shfa_logo.png`.

### Test emulator matching the client's tablet

AVD **`LG_GPad_X8_API23`**, built to match the LG G Pad X 8.0:

- Android 6.0 (API 23, `google_apis` x86_64), which is the app's minSdk and the worst case
- 1200×1920 panel used in landscape, **320 dpi**, so 960×600 dp
- 2 GB RAM

The 320 dpi density is an assumption: run `adb shell wm density` on the real tablet and change
`hw.lcd.density` in `%USERPROFILE%\.android\avd\LG_GPad_X8_API23.avd\config.ini` if it differs.
The emulator's CPU is much faster than the tablet's Snapdragon 617, so timing numbers here are
optimistic.

The IDE's tap/type tool fails on API 23 (it passes a `-d` flag Android 6 doesn't support). Use
`adb shell input ...` directly.

### Verified on 2026-11-09 on `LG_GPad_X8_API23`

This table is the visitor flow as checked in session 3; the test counts are from that day. The
session 5 checks (PIN reset, save recovery, 6 instrumented tests) are in the session log.

| Check | Result |
|---|---|
| `assembleDebug`, `testDebugUnitTest` | Pass (51 tests) |
| `connectedDebugAndroidTest` (PDF writer, adult, minor, longest values) | Pass (3 tests) |
| `lintDebug` | No errors, no `NewApi`; only dependency-version and style warnings |
| Welcome screen (logo, name, Start) | OK (logo made transparent; no white box) |
| ZIP `850011234` → `85001-1234`; phone → `(602) 555-0123`; DOB → `06/01/2012` | Works |
| Initials typed lower case → upper case | Works |
| DOB under 18 → guardian notice, guardian name field, "Parent/Guardian signature" label | Works |
| Signature pad: draws, and drawing doesn't scroll the page | Works |
| Soft keyboard: no suggestion strip; the focused field stays visible above it | Works. The keyboard covers about 55% of the screen |
| Submit → "Thank you, Sam!" | About 1.7 s, including polling time |
| Saved file `files/intake/2026/11/2026-11-09_0001.pdf` | 81 KB, 2 Letter pages, layout correct |
| App memory after a submission (debug build) | About 80 MB in total, 14 MB Java heap, on a 2 GB device |

## Session log

**Session 1:** scaffolded phases 1–3 without a JDK or SDK. Nothing was compiled.

**Session 2 (2026-11-06):** toolchain upgrade (Gradle 9.1.0, AGP 8.13.0, Kotlin 2.2.21); fixed
the bug where pasted line breaks hid text in single-line fields (`TextRules.singleLine`); received
the client's documents; first commit and push.

**Session 3 (2026-11-09): the Arizona waiver rework.**

1. The client answered the open decisions (see "Decisions already taken").
2. **Domain layer** (written first, as the contract for the rest):
   - US address with state and ZIP; email (optional); date of birth with the under-18 rule;
     guardian name; initials; signature as vector strokes
   - `Waiver.kt`, the legal wording copied from their PDF; date + sequence file names;
     `America/Phoenix`; the consent checkbox removed
3. **UI:** the long scrolling form, `SignaturePad.kt` (consumes touches so drawing doesn't
   scroll), a state dropdown, the guardian section, scroll-to-first-error on Submit, the logo on
   the Welcome screen and in the form header. Done by a sub-agent, then reviewed. Initials were
   moved to the right of each clause to match the paper form.
4. **Data:** the PDF writer redrawn as the 2-page waiver; Room database version 2 with
   `sequence_counter`, a number that only goes up; the v1 schema is dropped destructively because
   v1 was never released. Done by a sub-agent, then reviewed.
5. **Fix:** on short screens the Welcome screen squashed the Start button to 6 px. The logo now
   shrinks first.
6. Created the `LG_GPad_X8_API23` emulator, ran a full submission as a minor, and checked the PDF.
7. **Investigated:** text typed into "Where did you hear" once landed in City. The cause is
   hardware-key navigation: adb's Enter takes the screen out of touch mode, focus lands on a
   reason chip, and the next tap sends focus back to City. With the soft keyboard's own Next
   key, focus skips the chips and this doesn't happen. The kiosk has no hardware keyboard, so no
   code change. Re-test if a keyboard is ever attached.

**Session 4 (2026-11-13):** phases 4 and 5: the staff area (`ui/staff/`, `data/staff/`), backup
(`data/backup/`), kiosk (`kiosk/`, `KIOSK_SETUP.md`). Tested by hand on the emulator only; the
PDF viewer was opened but its result was not confirmed.

**Session 5 (2026-11-14): full review** (two review agents plus a manual security pass), then fixes:

- The save (number, PDF, row) can no longer be cancelled half-way (`NonCancellable`).
- A signature must span part of the pad, not just have 10 points.
- Release builds set `FLAG_SECURE` on the visitor screens too (debug builds don't, for screenshots).
- A backup runs in an app-wide scope, so the staff auto-lock doesn't cancel it; one failed file
  no longer blocks the rest (the run stops after 3 failures in a row).
- Staff search: a query with letters no longer matches phone numbers by its digits.
- Maintenance counts waivers with `COUNT(*)`; a failed share/picker launch no longer leaves the
  staff area unlockable by Home.

Second round, same session:

- **Crash-safe save:** the PDF is written to `.tmp/`, the Room row is inserted, then the PDF is
  renamed into place. On start, `IntakeFileStore.recoverTemp` finishes the rename for any
  leftover whose row exists and deletes the rest. A PDF can no longer exist without its row.
- **Forgotten PIN:** `staff/PinResetReceiver`, reachable only through `adb shell am broadcast`
  (guarded by `android.permission.DUMP`).
- A backup reports waivers whose PDF is missing on the tablet.
- Choosing a different backup folder marks every waiver pending again.
- `%`, `_` and `\` in the staff search are escaped.

Checked on `LG_GPad_X8_API23` (2026-11-14, debug build, the app is device owner there):

- The PIN reset command prints `result=1`, the waiver stays, and a long-press on the logo then
  shows "Create a staff PIN". The emulator's old test PIN (2468) is gone: create a new one.
- A save cut off before the rename (a `.part` file with a row, plus one without) is repaired on
  the next start: the first is moved into place, the second removed.
- **6 instrumented tests** pass. The 3 new ones are in `SaveAndSearchOnDeviceTest`: the real
  save (PDF writer, file store, Room), the staff search with `ESCAPE`, and re-marking for a new
  backup folder. Run them with `adb shell am instrument`, not `connectedDebugAndroidTest`,
  which tries to uninstall the app afterwards (a device owner can't be uninstalled).
- A device-owner app restarts itself straight after `am force-stop`, because it is the Home app.
- C: had only 2.9 GB free; the emulator still started.

Still open:

- **Check first: does Lock re-pin the kiosk?** In session 4 (device owner, debug build), the
  emulator showed `mLockTaskModeState=LOCKED` on the PIN screen and `NONE` in the staff home, as
  intended. But it still showed `NONE` 2 s after tapping **Lock**, when it should be `LOCKED`
  again. Session 5 didn't change the lock code (`MainActivity.applyMode`,
  `DeviceOwnerKioskController.lock`), and this wasn't re-tested before the 2026-11-14 commit
  because the emulator wouldn't start (not enough disk space on C:). To check: device owner,
  enter staff with the PIN, tap Lock, then `adb shell dumpsys activity activities | findstr
  mLockTaskModeState`, and `adb logcat -s MainActivity` for "Kiosk step failed: lock". If it
  stays `NONE`, a visitor could leave the app after staff lock up.
- Backup duplicates: a provider that reports no file size, or a half-written file left by a
  killed run, leads to a `name (1).pdf` copy. Left alone on purpose: deleting a same-named file
  in the backup folder could destroy a real waiver from an earlier install.
- Rows whose PDF is missing still count as "pending" and have no marker in the records list
  (the viewer and the backup result do say so).
- The PDF tests only count pages; nothing checks the text on them. 120 unit tests pass.

## Client requirements

Client: **Safe Haven for Animals (SHFA)**, Arizona, USA. Public contact: shinfo@azshfa.org.

- **Purpose:** a general **liability waiver** that indemnifies the shelter for anything that
  happens during a visit. It is not a visitor log.
- **Fields:** name, street address, city/state/ZIP, email, mobile, reason for visit, "where did
  you hear about Safe Haven", **initials on 6 clauses**, **signature** (a parent or guardian signs
  if the visitor is under 18), **date of birth**, **date**. All built.
- **No privacy notice or "I agree" box.** A signature is required instead.
- **Retention:** no time limit. No automatic deletion.
- **File names:** date plus a sequential number. Built.
- **Backups:** possibly Google Drive or other cloud storage. **No printer.**
- English only. **One tablet.**
- **Later:** maybe more forms (pre-adoption questionnaire, adoption contract).

### Check with the client

- **Waiver wording:** confirm that `Waiver.kt` matches their current paper form, and that showing
  it in a public GitHub repo is fine.

## Decisions already taken

| Decision | Choice |
|---|---|
| PDF output | **Draw a new PDF** with the built-in `PdfDocument` (2026-11-09; the client left it to us). The screen and the PDF share the wording in `Waiver.kt`; no PdfBox. |
| Flow | **One long scrolling screen** |
| Initials | **Typed**, letters only, upper case, 1–4 |
| Under 18 | A valid date of birth under 18 **switches to a parent/guardian signature** and requires the guardian's name. The **parent/guardian also does the initials** (client, 2026-11-09); the form intro and the guardian notice say so |
| Email | **Optional** (client, 2026-11-09) |
| Reason for visit | **Chips** (Adopting, Volunteering, Visiting, Dropping off, Other), required. OK with the client for now (2026-11-09); the paper form had a free-text line |
| Logo | `res/drawable-nodpi/shfa_logo.png` is a **transparent** copy of `docs/client/logo.png` (white turned into transparency, black kept; made 2026-11-09) |
| Sequence numbers | One counter that only goes up, never reset or reused; padded to 4 digits |
| Room data | Search fields only; the street, "heard about", initials and signature are only in the PDF |
| D1 framework | Kotlin + Jetpack Compose, Material 3 |
| D2 PDF | `PdfDocument`, US Letter 612×792 pt |
| D3 storage | App-private `filesDir/intake/YYYY/MM/`. Staff share one PDF at a time through `FileProvider` |
| D4 encryption | **Not decided** |
| D5 backup | **Copy to a folder staff pick with Android's folder picker** (SD card, USB stick, or Google Drive if the Drive app offers folders there). No Google Drive code in the app. Whether Drive shows up on the real tablet is **unknown**; the client prefers Drive |
| D6 kiosk | Device Owner + Lock Task, **built** (`KIOSK_SETUP.md`). Checked on the emulator only |
| Staff PIN | 4–8 digits, salted PBKDF2 hash in a private SharedPreferences file (not DataStore), 5 tries then a lockout that doubles up to 15 minutes. No default PIN. Reset only over USB (`PinResetReceiver`) |
| Staff auto-lock | 2 minutes idle, or the app leaving the foreground. A running backup carries on |
| Save order | PDF to `.tmp/`, then the Room row, then the rename. The row is what makes a save count |
| D8 database | Room index + PDFs |
| D9 SDK | minSdk 23, target/compileSdk 36, core library desugaring on |
| DI | Manual `AppContainer`, no Hilt |
| Visitor flow | State machine in `IntakeViewModel`, no navigation-compose and no back stack |

## Code map (under `app/src/main/java/ca/safehaven/intake/`)

| Item | File(s) |
|---|---|
| Models, `IntakeConfig` (hard-coded settings) | `domain/model/IntakeModels.kt` |
| Waiver wording (update and bump `VERSION` if the client changes it) | `domain/model/Waiver.kt` |
| Validation (name, phone, ZIP, email, DOB/under-18, initials, signature) | `domain/validation/` |
| Normalized record | `domain/RecordFactory.kt` |
| File naming | `domain/FileNamer.kt` |
| Atomic file store | `data/files/IntakeFileStore.kt` |
| Room entity, DAO, sequence counter, DB v2 | `data/db/` (schemas in `app/schemas/`) |
| PDF writer | `data/pdf/AndroidIntakePdfWriter.kt`, `res/values/strings_pdf.xml` |
| Submit pipeline | `data/SubmitIntakeUseCase.kt` |
| ViewModel: idle timeout, thanks screen, reset, `isMinor` | `ui/intake/IntakeViewModel.kt` |
| Screens | `ui/intake/IntakeFormScreen.kt`, `SignaturePad.kt`, `WelcomeAndThanks.kt`, `ui/VisitorApp.kt` |
| Start-up repair of cut-off saves | `SafeHavenApp.kt`, `IntakeFileStore.recoverTemp` |
| Visitor/PIN/staff mode, `FLAG_SECURE`, kiosk lock per mode | `MainActivity.kt`, `ui/AppRoot.kt`, `ui/staff/StaffSessionViewModel.kt` |
| Contracts between the staff UI and its services | `staff/StaffContracts.kt` |
| PIN hashing and lockout; storage | `data/staff/PinLogic.kt`, `SharedPrefsPinStore.kt` |
| Forgotten-PIN reset over USB | `staff/PinResetReceiver.kt` |
| PIN screens | `ui/staff/PinFlowViewModel.kt`, `PinPad.kt` |
| Records list, search, delete | `ui/staff/RecordsViewModel.kt`, `RecordsSection.kt` |
| PDF viewer and share | `ui/staff/WaiverViewerScreen.kt`, `PdfPages.kt`, `res/xml/file_paths.xml` |
| Backup loop; folder-picker adapter; service | `data/backup/BackupRunner.kt`, `SafBackupFolder.kt`, `SafBackupService.kt` |
| Backup and Maintenance screens | `ui/staff/BackupSection.kt`, `MaintenanceSection.kt` |
| Kiosk (device owner, Lock Task) | `kiosk/`, `res/xml/device_admin.xml` |
| Staff strings | `res/values/strings_staff.xml` |
| Shared test data | `app/src/test/.../TestFixtures.kt`, `ui/staff/StaffFakes.kt` |
| Device tests | `app/src/androidTest/` (PDF writer; save and search) |

## Known issues and notes

- **Sequence numbers after a database wipe:** if the database is ever reset while the PDFs remain,
  numbering restarts at 0001. A collision is only caught when a file with the same date and
  number already exists. Normal use can't trigger this. There is no restore feature: backups
  are copies of the PDFs only, not of the database.
- **Signature pad and scrolling:** a drag that starts on the pad draws instead of scrolling, so
  visitors must scroll from outside it. Clear undoes a stray stroke. Watch for this in the pilot.
- **Keyboard on the real tablet:** untested. Turn off prediction and learning in its settings.
- Gradle prints deprecation warnings about Gradle 10. Harmless for now.
- Dev PC: the emulator may not start if C: is nearly full. It did start with 2.9 GB free on
  2026-11-14, using `-gpu swiftshader_indirect` (it crashed with hardware graphics in session 4).
- Command-line builds need `JAVA_HOME` set to Android Studio's JDK
  (`C:\Program Files\Android\Android Studio\jbr`); without it `gradlew` prints an error and
  does nothing.
- The staff Settings screen from the plan was not built. `IntakeConfig` is still hard-coded.

## Next steps (in order)

1. ~~Commit sessions 4 and 5~~ (committed and pushed 2026-11-14). **Free disk space on C:** so
   the emulator starts, then re-check that **Lock re-pins the kiosk** (see "Still open").
2. **Confirm the waiver wording with the client** (`Waiver.kt` is now public on GitHub), and ask
   whether "pick a Drive folder once, then tap Back up now" is what they expect.
3. **By hand on the emulator:** a full visitor submission and a backup run with this build
   (the save was only exercised through the instrumented test), then the "still open" list.
4. **On the real tablet, with a release build:** the whole of `KIOSK_SETUP.md`; whether Google
   Drive appears in the folder picker (if not, backups go to SD/USB, or Drive needs its own
   code); `PdfRenderer` scaling in the viewer; PBKDF2 time for the PIN; typing lag in the form.
5. **Hardening:**
   - a baseline profile
   - the encryption decision
   - accessibility
   - consider changing the `ca.safehaven.intake` package/applicationId before the first real
     install
6. **Future:** more forms (pre-adoption questionnaire, adoption contract).
7. **Client milestones** (from the proposal): discovery chat → first demo (2–3 weeks) → 1–2-week
   front-desk pilot → handover (6–10 weeks in total).
