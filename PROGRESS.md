# SafeHaven: progress and handoff

Last updated: 2026-11-14 (end of session 6, the attack pass). Read this first when resuming work.

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
- **123 unit tests** pass and `lint` reports no errors (2026-11-14, session 6). The
  **6 instrumented tests** last ran before session 6, on the Android 6.0 emulator that matches
  the client's tablet; session 6 didn't change the code they cover.
- Session 6 attacked the app on the emulator (see the session log) and fixed what it found.
  Committed and pushed the same day.
- Next: test on the **real tablet** with a **release build**. Nothing has run on the LG G Pad
  yet. See "Next steps".

## Current state

- **Git:** `github.com/InfamousMorningstar/SafeHaven`, a **public** repo, branch `main`. Session 3
  is **pushed** (2026-11-09), including the client's **logo** and the **waiver wording**
  (`domain/model/Waiver.kt`). Sessions 4 and 5 (the staff area, backup, kiosk, the review fixes,
  `KIOSK_SETUP.md`) are committed and pushed (2026-11-14, `0c937d0`). The kiosk re-pin work
  from the afternoon (`AppUpdatedReceiver`, `keepPinned`, `returnToFrontIfHidden`, the 5-minute
  away limit) is committed and pushed after it (2026-11-14).
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

**Kiosk re-pin check (2026-11-14, afternoon).** The user had seen the kiosk stay unpinned
after staff pressed Lock. On the emulator the app re-pinned (`mLockTaskModeState=LOCKED`) after
every route back to the visitor screen: Lock, Back, Home, Recents then Home, screen off and on,
the folder picker then Lock, and "Exit kiosk" (Settings) then Back or Home. Not reproduced.
Two real weaknesses were found and fixed:

- **After an app update** (`adb install -r`) Android killed the app and left the tablet on its
  own launcher, unpinned, with Settings one tap away, until Home was pressed. The earlier
  sessions redeployed often, so this is the likely cause of the report. `kiosk/AppUpdatedReceiver`
  now restarts the app after an update; checked: it is pinned again within 5 s.
- **A pin request was tried once and a refusal was ignored.** `MainActivity.keepPinned` now
  re-checks while the visitor or PIN screen shows (after 0.3 s, backing off to every 5 s) and
  pins again if needed. It could not be exercised: nothing outside the app can end the pin
  (`am task lock stop` is refused), so this path has only been read, not seen working.

- **The app now comes back by itself** (kiosk only, `MainActivity.returnToFrontIfHidden`):
  - Staff in another app they opened (Settings, folder picker, share target) for more than
    **5 minutes** (`STAFF_MAX_AWAY_MILLIS`, was 10 and only checked on return): the staff area
    locks, the app returns to the front and pins. Checked: Settings left open was replaced by
    the pinned Welcome screen between 4 min and 5 min 15 s.
  - Staff leaving any other way (Recents): back in front and pinned within 2 s.
  - It retries every 3 s, because Android 6 ignores an app's request to come forward for about
    5 s after Home or Recents. A plain `startActivity` alone was not enough; it also calls
    `moveTaskToFront` (hence the `REORDER_TASKS` permission).
  - A 20 s folder-picker visit still returns to an open staff area.
  - Limit: if Android kills the app's process while Settings is open, nothing brings it back
    until Home is pressed. The window is then open to whoever is at the tablet.
- 121 unit tests pass; lint is clean.

**Re-checked the same day, after the afternoon changes** (fresh boot, `adb install -r` of the
new build): pinned again within 1 s of the update; the staff home is `NONE`; **Lock** gives
`LOCKED` within 0.5 s with no "Kiosk step failed" in logcat; Recents from the staff area brings
the pinned Welcome screen back within 1 s; 121 unit tests, 6 instrumented tests and lint pass.
The session 4 "Lock doesn't re-pin" report is closed. The emulator's staff PIN is now 1357.

**Session 6 (2026-11-14, evening): attack pass.** Debug build on `LG_GPad_X8_API23`, device owner.

Held up (no change needed):

- Pinned after: `kill -9` of the process, `am force-stop`, a system-server restart, an update,
  5 hours idle.
- While pinned, none of these left the app: Home, Recents, Assist, Search, Camera, Settings,
  Browser, Menu and Power keys; a status-bar swipe; `am start` of Settings from the shell; the
  keyboard's "Input options" (Languages, Keyboard settings: logcat shows "Attempted Lock Task
  Mode violation"); another app's button in the text menu.
- Hostile start intent (VIEW, a `content://` path with `..`, fake extras): Welcome screen, no crash.
- FileProvider read without a grant: `SecurityException`. Broadcasts to the two non-exported
  receivers and a fake admin broadcast: not delivered.
- PIN: 5 wrong tries lock it; the right PIN is refused during the lockout; Cancel and re-enter,
  and killing the app, don't clear it; it works again afterwards.
- 12,000 random touch/key events (`monkey`): no crash, no ANR, still pinned.
- Submit hammered (5 at once, then 3 in a row) with a valid form: **one** row, **one** PDF,
  counter +1, `.tmp/` empty, nothing of the visitor's in logcat. The PDF has 2 pages.
  ZIP `850011234` saved as `85001-1234`; street `99_Percent-Rd.#5` accepted.

Found and fixed:

- **Copy/paste leaked between visitors.** A long press on a field offered Cut/Copy/Paste, and
  the next visitor's empty field offered Paste with the previous visitor's text. The menu also
  listed other apps' text actions ("API Demos" on the emulator; Translate and the like on a real
  tablet). `MainActivity.onActionModeStarted` now closes every action mode, and the clipboard
  is emptied whenever the visitor screen comes up. Checked: text can be selected, no menu.
  (`PrivateTextInput` also replaces Compose's `LocalTextToolbar`, but this Compose version
  doesn't go through it; the activity hook is what works.)
- **Microphone key on the keyboard.** Fields now ask for no mic key (`privateImeOptions`);
  on the emulator's keyboard the key and its empty strip are gone. Other keyboards may ignore it.
- **A half-filled form could outlive a sleeping tablet.** The idle timer read `System.nanoTime`,
  which stops in deep sleep, as coroutine delays do. It now reads `elapsedRealtime`, and
  `IntakeViewModel.onShownAgain` (from `onStart`) wipes a form left longer than the idle timeout
  plus the countdown (100 s). 2 new unit tests. The emulator never deep-sleeps, so this is
  covered by the unit tests only.
- **Nothing restarted the app if its process died in the background** (the limit noted above).
  `kiosk/KioskWatchdogReceiver`: an inexact alarm about once a minute starts `MainActivity` if
  the app is device owner and no `MainActivity` exists. Checked: staff in Settings, process
  killed, pinned Welcome screen back within 30 s. A force-stop cancels the alarm; that only
  happens from adb (running the instrumented tests ends with one: press Home afterwards).
- **Submit tapped again while the form scrolls to the first error** could stop the scroll
  part-way. Reproduced only with overlapping injected taps, faster than a finger. Touches are
  now swallowed while that scroll runs. Checked: 6 taps in 2 s land on the first error, 4 of 4.

Not fixed, on purpose or not yet:

- On a **full disk** the PIN can't be checked (the attempt counter can't be saved), so staff
  can't get in to back up or delete. Not reproduced, read from the code. A waiver is about
  75 KB, so waivers alone won't fill the tablet.
- The PIN screen shows a lockout only after the next try, not on opening.
- The emulator now holds test waivers 0002 and 0003 ("Zed Test-Case").

Still open:

- While the screen is off, `returnToFrontIfHidden` keeps waking every 3 s to check whether the
  screen is on. Cheap, and the tablet is plugged in, but worth a look if battery is ever a concern.
- Backup duplicates: a provider that reports no file size, or a half-written file left by a
  killed run, leads to a `name (1).pdf` copy. Left alone on purpose: deleting a same-named file
  in the backup folder could destroy a real waiver from an earlier install.
- Rows whose PDF is missing still count as "pending" and have no marker in the records list
  (the viewer and the backup result do say so).
- The PDF tests only count pages; nothing checks the text on them.

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
| Staff auto-lock | 2 minutes idle, or the app leaving the foreground. More than 5 minutes in another app opened from the staff area also locks, and the kiosk pulls the app back to the front. A running backup carries on |
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
| Kiosk (device owner, Lock Task); restart after an update; watchdog alarm | `kiosk/`, `res/xml/device_admin.xml` |
| No copy/paste menu, clipboard wipe, no mic key | `MainActivity.onActionModeStarted`, `ui/PrivateTextInput.kt` |
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
- Dev PC: the emulator refuses to start with about 1 GB free on C: ("not enough disk space").
  It starts with 2.9 GB or more, using `-gpu swiftshader_indirect` (it crashed with hardware
  graphics in session 4).
- Command-line builds need `JAVA_HOME` set to Android Studio's JDK
  (`C:\Program Files\Android\Android Studio\jbr`); without it `gradlew` prints an error and
  does nothing.
- The staff Settings screen from the plan was not built. `IntakeConfig` is still hard-coded.

## Next steps (in order)

1. ~~Commit sessions 4 and 5; re-check that Lock re-pins~~ (done 2026-11-14).
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
   - USB debugging on the front-desk tablet: on or off (`KIOSK_SETUP.md` section 6)
   - accessibility
   - consider changing the `ca.safehaven.intake` package/applicationId before the first real
     install
6. **Future:** more forms (pre-adoption questionnaire, adoption contract).
7. **Client milestones** (from the proposal): discovery chat → first demo (2–3 weeks) → 1–2-week
   front-desk pilot → handover (6–10 weeks in total).
