# SafeHaven: progress and handoff

Last updated: 2026-11-06 (end of session 2). Read this first when resuming work.

**Design source:** `D:\Obsidian\Nebula\Projects\App Idea - Technical Plan.md`. Section numbers
below (3.2, D3, etc.) refer to that plan. **Build and run steps:** [README.md](README.md).

## TL;DR

- The app **builds**, all **39 unit tests pass**, and it **runs on an emulator**. A full
  sign-in on the emulator produced a correct one-page PDF.
- The code is still the **first scaffold, a Canadian visitor sign-in**. The client turned out to
  be in **Arizona, USA**, and wants a **liability waiver** with initials and a signature. The next
  big job is reworking the app for that, **after the four open decisions below are answered**.

## Current state

- **Git:** the first commit is pushed to `github.com/InfamousMorningstar/SafeHaven`, which is a
  **public** repo, branch `main`.
- **Toolchain:** Android Studio's bundled **JDK 25**. Gradle 9.1.0, AGP 8.13.0, Kotlin 2.2.21,
  KSP 2.3.13, Compose BOM 2025.08.00, Room 2.7.2.
- **Client documents** are in `docs/client/`, which is **git-ignored on purpose because the repo
  is public**. They stay on this PC only:
  - `Proposal-with-client-answers.pdf`: the proposal, with the client's answers typed in.
  - `SHFA-WaiverForm.pdf`: their 2-page fillable (AcroForm) paper waiver.
  - `logo.png`: the Safe Haven house-and-cats logo, black and white.

Verified on 2026-11-06 on the emulator `Medium_Phone_API_37.0` (a landscape phone):

| Check | Result |
|---|---|
| `assembleDebug` + `testDebugUnitTest` | Pass (39 tests) |
| Welcome → Start → form → Submit → "Thank you, Jane!" → Welcome after 5 s | Works |
| Phone `4035550123` → `(403) 555-0123`; postal `t2x1a1` → `T2X 1A1` | Works |
| Field errors appear after leaving a field (blur) | Works |
| Gboard incognito mode (keyboard doesn't learn) | Works |
| Saved file `files/intake/2026/11/2026-11-06_2142_Smith-Jane_MEB8.pdf` | One US Letter page, all fields correct |
| Crashes or errors in logcat | None |
| `lint`, `connectedAndroidTest` | **Not run yet** |

## Session log

**Session 1:** scaffolded phases 1–3 without a JDK or SDK. Nothing was compiled.

**Session 2 (2026-11-06):**

1. **Toolchain.** Gradle 8.14.3 can't run on JDK 25. Upgraded Gradle → 9.1.0, AGP → 8.13.0,
   Kotlin → 2.2.21, and KSP → 2.3.13 (KSP versions no longer have to match Kotlin). Stayed on
   AGP 8 on purpose to avoid the AGP 9 migration for now. All the "risk spots" from session 1
   compiled as written, including `InterceptPlatformTextInput`.
2. **Bug fix: pasted line breaks.** Compose single-line `OutlinedTextField`s accept pasted
   `\n`. Everything after the first line was hidden but still validated and saved, so a visitor
   saw a correct-looking name with an error they couldn't fix. Fix: `TextRules.singleLine()` in
   `domain/validation/Rules.kt`, applied in `IntakeTextField.onValueChange`. Test:
   `TextRulesTest` in `RulesTest.kt`.
3. Received the client's inputs (see the next section) and stored them in `docs/client/`.
4. First git commit and push.

## Client requirements (these change the scope)

Client: **Safe Haven for Animals (SHFA)**, Arizona, USA. Public contact: shinfo@azshfa.org.

- **Purpose:** a general **liability waiver** that indemnifies the shelter for anything that
  happens during a visit. It is not a visitor log.
- **Fields, from their paper form:**
  - name, street address, city/state/ZIP, email, mobile
  - reason for visit, "Where did you hear about Safe Haven"
  - **initials on 6 clauses:** no medication will be given; I'm in good health; no communicable
    diseases; I indemnify the shelter; no nurse on site; the facts I've given are true
  - **signature** (a parent or guardian signs if the visitor is under 18), **date of birth**,
    **date**
  - The form also includes the full waiver/release text and the list of risks.
- **Privacy notice or "I agree" box:** none, so set `consentRequired = false`. A signature is
  required instead.
- **Retention:** no time limit. Drop the automatic-deletion worker for now.
- **File names:** date plus a **sequential number** (1, 2, 3…). No visitor name and no random code.
- **Backups:** possibly Google Drive or other cloud storage. **No printer**; they would print from
  the cloud.
- English only. **One tablet.**
- **Later:** maybe more forms (pre-adoption questionnaire, adoption contract), so design the form
  layer to be reusable.

### What in the code is still Canadian or placeholder

- `domain/model/IntakeModels.kt`: `Province`, `defaultCity = "Calgary"`,
  `timeZoneId = "America/Edmonton"` (should be `America/Phoenix`, which has no DST),
  `consentRequired = true`.
- `domain/validation/Rules.kt`: `PostalCodeRules` is Canadian. Replace it with US ZIP
  (`12345` or `12345-6789`) and US states. `PhoneRules` (NANP) stays.
- `domain/FileNamer.kt`: name + random suffix. Change it to date + sequential number, which needs
  a counter (for example from Room).
- `data/pdf/AndroidIntakePdfWriter.kt`: uses `Locale.CANADA` and the sign-in layout.
- `res/values/strings.xml`: the `[Shelter Name]` placeholder, the Canadian consent text and the
  sign-in wording.
- The UI has no email, date of birth, "heard about us", initials or signature fields.
- The package and applicationId are `ca.safehaven.intake`. Consider changing them before the first
  real install, because they can't change after release without a reinstall.

## Open decisions (ask the user before building)

1. **PDF output:** fill **their actual waiver PDF**, or **draw a new PDF** with the same wording?
   - Filling their PDF: it's an AcroForm with fields such as `Name`, `Address`, `Initials…`,
     `Signature`, `Birthdate` and `Todaysdate`. The legal wording and layout stay exact. It needs
     a library such as PdfBox-Android (about 2–3 MB, possibly slow on a 2 GB tablet). The template
     would go in `app/src/main/assets/`.
   - Drawing a new one: no new library, but the waiver text has to be copied and kept in sync by
     hand.
   - Leaning towards **filling their PDF**, because it's a legal document.
2. **Flow:** one long scrolling screen, or steps (contact details → waiver + initials → signature)?
3. **Initials:** typed letters, or drawn with a finger?
4. **Under 18:** should a date of birth under 18 switch the signature to "Parent/Guardian"?

## Decisions already taken

| Decision | Choice |
|---|---|
| D1 framework | Kotlin + Jetpack Compose, Material 3 |
| D2 PDF | Built-in `PdfDocument`, US Letter 612×792 pt. **May change** (open decision 1) |
| D3 storage | App-private `filesDir/intake/YYYY/MM/` (staff export comes in phase 4) |
| D4 encryption | **Not decided** |
| D5 backup | **Not decided.** The client prefers Google Drive or cloud |
| D6 kiosk | Device Owner + Lock Task (**not built**) |
| D8 database | Room index + PDFs |
| D9 SDK | minSdk 23, target/compileSdk 36, core library desugaring on |
| DI | Manual `AppContainer`, no Hilt |
| Visitor flow | State machine in `IntakeViewModel`, no navigation-compose and no back stack |

## Done (phases 1–3, verified this session)

| Item | File(s) under `app/src/main/java/ca/safehaven/intake/` |
|---|---|
| Models, `IntakeConfig` (hard-coded settings) | `domain/model/IntakeModels.kt` |
| Validation (name, NANP phone, postal, province soft check, single-line paste) | `domain/validation/` |
| Normalized record | `domain/RecordFactory.kt` |
| File naming | `domain/FileNamer.kt` |
| Interfaces (`IntakeSubmitter`, `SubmitResult`, `IntakePdfWriter`) | `domain/IntakeSubmitter.kt` |
| Atomic file store (temp, fsync, rename, SHA-256, path-escape guard) | `data/files/IntakeFileStore.kt` |
| Room entity, DAO, DB v1 (schema exported to `app/schemas/`) | `data/db/` |
| PDF writer | `data/pdf/AndroidIntakePdfWriter.kt` |
| Submit pipeline (validate → PDF → file → row; removes the PDF if the row insert fails) | `data/SubmitIntakeUseCase.kt` |
| ViewModel: 90 s idle + 10 s countdown, 5 s thanks screen, reset | `ui/intake/IntakeViewModel.kt` |
| Screens | `ui/intake/WelcomeAndThanks.kt`, `IntakeFormScreen.kt`, `ui/VisitorApp.kt` |
| Keyboard privacy, as-you-type formatting | `ui/PrivateTextInput.kt`, `ui/InsertingVisualTransformation.kt` |
| Wiring and manifest (backups off, landscape, Autofill off) | `AppContainer.kt`, `SafeHavenApp.kt`, `MainActivity.kt`, `AndroidManifest.xml` |

## Known issues and notes

- "Unit / Apt (optional)" wraps onto two lines because the field is too narrow. Fix it in the US
  address rework.
- Gboard ignores `TYPE_TEXT_FLAG_NO_SUGGESTIONS` and still suggests dictionary words. Incognito
  (no learning) works on API 26+. The **LG tablet runs Android 6 and its keyboard is untested**:
  turn off prediction and learning in its settings, and test on the real device.
- Testing so far was only on an API 37 **phone**. Create a landscape **tablet** emulator with
  **API 23 or 24**.
- Dev PC quirks:
  - `adb` run from the agent's shell often stalls or reports late. The IDE's own deploy,
    screenshot and logcat tools work.
  - The emulator won't start if C: has less than a few GB free.
  - The emulator syncs the host clipboard, which is what exposed the paste bug.
- Gradle prints deprecation warnings about Gradle 10. Harmless for now.

## Next steps (in order)

1. **Get the user's answers to the 4 open decisions**, then write a short plan.
2. **Arizona waiver rework.** All of these:
   - US address with state and ZIP; the new fields; the waiver text
   - initials, a signature pad, the under-18 rule
   - sequential file names; the new PDF
   - `America/Phoenix`; `consentRequired = false`
   - the logo on the Welcome screen and in the PDF; shelter name "Safe Haven for Animals"
3. Run `lint` and `connectedAndroidTest` on an API 23/24 tablet emulator.
4. **Phase 4: staff area.**
   - PIN (salted PBKDF2 hash in DataStore, 5-try lockout, auto-lock after 2 minutes); hidden entry
     by long-pressing the logo.
   - Records list and search, `PdfRenderer` viewer, export/share (SAF + `FileProvider`), delete,
     settings. No printing and no retention worker, per the client.
   - `FLAG_SECURE` on staff screens.
5. **Backups:** upload to Google Drive or cloud storage.
6. **Phase 5: kiosk.** Device owner, Lock Task, persistent HOME activity, maintenance exit behind
   the staff PIN.
7. **Hardening:** baseline profile; an encryption decision (the health clauses are only
   initialed, but if ID or medical data is ever collected, Arizona's breach law applies);
   accessibility; and release-build performance on the 2 GB tablet.
8. **Future:** more forms (pre-adoption questionnaire, adoption contract).
9. **Client milestones** (from the proposal): discovery chat → first demo (2–3 weeks) → 1–2-week
   front-desk pilot → handover (6–10 weeks in total).
