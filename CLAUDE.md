# SafeHaven

Android kiosk app for **Safe Haven for Animals** (Arizona, USA). Visitors fill in and sign the
shelter's **liability waiver** on a front-desk tablet. Kotlin + Compose, minSdk 23; the target
device is an LG G Pad X 8.0 on Android 6/7.

- **Start here:** [PROGRESS.md](PROGRESS.md) lists what's done, what's next, the open decisions
  and the client's requirements. Update it at the end of each session.
- **Design:** `D:\Obsidian\Nebula\Projects\App Idea - Technical Plan.md` (written for a Canadian
  sign-in; PROGRESS.md records where the client's needs differ).
- **Client documents:** `docs/client/` (local only and git-ignored, because the repo is public).
- **Build:** see [README.md](README.md).
- **Code rule:** any API newer than 23 goes behind a `Build.VERSION.SDK_INT` check (Lint
  `NewApi` is an error).
- **Privacy rule:** never log visitor data.
