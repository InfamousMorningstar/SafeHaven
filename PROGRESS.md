# SafeHaven: progress and handoff

Last updated: 2026-11-09 (end of session 3). Read this first when resuming work.

**Design source:** `D:\Obsidian\Nebula\Projects\App Idea - Technical Plan.md`. Section numbers
below (3.2, D3, etc.) refer to that plan. **Build and run steps:** [README.md](README.md).

## TL;DR

- The app is now the **Arizona liability waiver**: one long scrolling form, typed initials on the
  six clauses, a drawn signature, and an automatic switch to a parent/guardian signature under 18.
  Each submission is saved as a 2-page PDF named by date and sequential number
  (`2026-11-09_0001.pdf`) plus a Room row.
- **51 unit tests** and **3 instrumented PDF tests** pass, and `lint` reports no errors. A full
  submission works on an emulator that matches the client's tablet (Android 6.0, 1920×1200).
- Next: phase 4 (staff area). Before that, check the items under "Check with the client".

## Current state

- **Git:** `github.com/InfamousMorningstar/SafeHaven`, a **public** repo, branch `main`. Session 3
  is committed locally, **not pushed**. The commit adds the client's **logo** and the **waiver
  wording** (`domain/model/Waiver.kt`) to the repo; confirm that's fine before pushing.
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

| Check | Result |
|---|---|
| `assembleDebug`, `testDebugUnitTest` | Pass (51 tests) |
| `connectedDebugAndroidTest` (PDF writer, adult, minor, longest values) | Pass (3 tests) |
| `lintDebug` | No errors, no `NewApi`; only dependency-version and style warnings |
| Welcome screen (logo, name, Start) | OK. The logo has a white box behind it (the PNG isn't transparent) |
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

- **Email optional?** It's optional in the app because not every visitor has one.
- **Reason for visit:** the paper form has a free-text line; the app has chips (Adopting,
  Volunteering, Visiting, Dropping off, Other), and the answer is required.
- **Who initials for a minor?** The app doesn't say. Probably the guardian, like the signature.
- **Waiver wording:** confirm that `Waiver.kt` matches their current paper form, and that showing
  it in a public GitHub repo is fine.
- **Logo:** a transparent PNG would remove the white box on the Welcome screen.

## Decisions already taken

| Decision | Choice |
|---|---|
| PDF output | **Draw a new PDF** with the built-in `PdfDocument` (2026-11-09; the client left it to us). The screen and the PDF share the wording in `Waiver.kt`; no PdfBox. |
| Flow | **One long scrolling screen** |
| Initials | **Typed**, letters only, upper case, 1–4 |
| Under 18 | A valid date of birth under 18 **switches to a parent/guardian signature** and requires the guardian's name |
| Sequence numbers | One counter that only goes up, never reset or reused; padded to 4 digits |
| Room data | Search fields only; the street, "heard about", initials and signature are only in the PDF |
| D1 framework | Kotlin + Jetpack Compose, Material 3 |
| D2 PDF | `PdfDocument`, US Letter 612×792 pt |
| D3 storage | App-private `filesDir/intake/YYYY/MM/` (staff export comes in phase 4) |
| D4 encryption | **Not decided** |
| D5 backup | **Not decided.** The client prefers Google Drive or cloud |
| D6 kiosk | Device Owner + Lock Task (**not built**) |
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
| Shared test data | `app/src/test/.../TestFixtures.kt` |

## Known issues and notes

- **Sequence numbers after a database wipe:** if the database is ever reset while the PDFs remain,
  numbering restarts at 0001. A collision is only caught when a file with the same date and
  number already exists. Normal use can't trigger this; keep it in mind for backup/restore
  (phase 4/5).
- **Signature pad and scrolling:** a drag that starts on the pad draws instead of scrolling, so
  visitors must scroll from outside it. Clear undoes a stray stroke. Watch for this in the pilot.
- **Keyboard on the real tablet:** untested. Turn off prediction and learning in its settings.
- Gradle prints deprecation warnings about Gradle 10. Harmless for now.
- Dev PC: the emulator won't start if C: has less than a few GB free (21 GB free on 2026-11-09).

## Next steps (in order)

1. **Check the items under "Check with the client"**, and confirm the commit can be pushed.
2. **Phase 4: staff area.**
   - PIN (salted PBKDF2 hash in DataStore, 5-try lockout, auto-lock after 2 minutes); hidden entry
     by long-pressing the logo.
   - Records list and search, a `PdfRenderer` viewer, export/share (SAF + `FileProvider`), delete,
     settings. No printing and no retention worker, per the client.
   - `FLAG_SECURE` on staff screens.
3. **Backups:** upload to Google Drive or cloud storage.
4. **Phase 5: kiosk.** Device owner, Lock Task, persistent HOME activity, maintenance exit behind
   the staff PIN.
5. **Hardening:**
   - a **release build** test on the real tablet (R8; check timing on the Snapdragon 617)
   - a baseline profile
   - the encryption decision
   - accessibility
   - consider changing the `ca.safehaven.intake` package/applicationId before the first real
     install
6. **Future:** more forms (pre-adoption questionnaire, adoption contract).
7. **Client milestones** (from the proposal): discovery chat → first demo (2–3 weeks) → 1–2-week
   front-desk pilot → handover (6–10 weeks in total).
