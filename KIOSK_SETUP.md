# Kiosk setup

How to turn the front-desk tablet (LG G Pad X 8.0, Android 6/7) into a locked waiver kiosk.
You need a Windows/Mac/Linux PC, a USB cable, and the release APK of the app. You don't need to
be an Android developer, but you will type a few commands.

When it's done:

- The tablet starts straight into the waiver app after a reboot, with no lock screen.
- Visitors can't leave the app: Home, Recents (the square button) and the notification shade do
  nothing.
- The screen stays on while the tablet is charging.
- Staff get into the staff area with a PIN, and can exit the kiosk for maintenance.

The app does this by becoming the tablet's **device owner**, a special status Android only
grants to one app, from a PC, on a tablet with no accounts on it.

## Before you start: install adb on the PC

`adb` is Google's command-line tool for talking to Android devices.

1. Download **SDK Platform-Tools** for your PC from
   <https://developer.android.com/tools/releases/platform-tools> and unzip it, for example to
   `C:\platform-tools`.
2. Open a terminal (Windows: PowerShell) in that folder. Every command below starts with `adb`;
   on Windows you may need to type `.\adb` instead.

(If Android Studio is installed, adb is already in `%LOCALAPPDATA%\Android\Sdk\platform-tools`.)

## 1. Back up and factory reset the tablet

Device owner can only be set on a tablet with **no accounts** (no Google account, no LG account,
no email account). The simplest way to get there is a factory reset.

1. Copy off anything on the tablet you want to keep. **The reset erases everything.** If the
   tablet already holds signed waivers from this app, back them up first (staff area → Backup).
2. Settings → **Backup & reset** → **Factory data reset** (roughly; on some LG builds it's under
   Settings → General → Backup & reset).
3. Go through the setup screens after the reset: language, Wi-Fi (fine to connect), date and time
   (set the Arizona time zone). When asked to sign in with a Google account, **skip it** (look for
   "Skip" or "Set up later"). Skip any LG account too.
4. When asked to protect the tablet with a PIN, pattern or password, choose **no screen lock**
   ("None" or "Swipe"). The kiosk turns the lock screen off, and Android refuses to do that while a
   secure screen lock is set.

## 2. Turn on USB debugging and install the app

1. Settings → **About tablet** → Software info (roughly) → tap **Build number** seven times until
   it says you're a developer.
2. Back in Settings, open the new **Developer options** and turn on **USB debugging**.
3. Plug the tablet into the PC. On the tablet, tick "Always allow from this computer" and tap OK.
4. Check the PC can see it:

   ```
   adb devices
   ```

   You should see one line ending in `device`. If it says `unauthorized`, look at the tablet for
   the prompt from step 3.

5. Install the app:

   ```
   adb install app-release.apk
   ```

   It prints `Success`.

## 3. Make the app the device owner

```
adb shell dpm set-device-owner ca.safehaven.intake/.kiosk.KioskAdminReceiver
```

**Success** looks like:

```
Success: Device owner set to package ca.safehaven.intake
Active admin set to component {ca.safehaven.intake/ca.safehaven.intake.kiosk.KioskAdminReceiver}
```

**Common errors** (exact wording varies a little between Android versions):

| Message | What to do |
|---|---|
| `Not allowed to set the device owner because there are already some accounts on the device` | An account exists. Remove it in Settings → Accounts, or factory reset again and skip every account. |
| `Trying to set device owner but device owner is already set` | Already done; carry on. (Or another app is device owner: factory reset.) |
| `Unknown admin: ComponentInfo{...}` | The app isn't installed, or there's a typo in the command. |
| `Not allowed to set the device owner because the device has already been provisioned` / `...several users` | Factory reset and try again straight after setup. |
| `error: no devices/emulators found` | The tablet isn't connected or USB debugging is off (step 2). |

You can check the result with:

```
adb shell dumpsys device_policy
```

and look for `Device Owner` with `ca.safehaven.intake` underneath.

## 4. Add the Google account and Google Drive (if backing up to Drive)

Now that the app is device owner, accounts are allowed again. Do this step **before** opening the
app for the first time: until then the tablet's normal home screen still works, so the Settings
and Drive apps are easy to reach.

1. Settings → **Accounts** → Add account → Google, and sign in with the shelter's account.
2. Open the **Drive** app (install or update it from the Play Store if needed) and sign in once
   with the same account. Pick or create the folder the waivers should go into, for example
   `Waivers`.

Skip this step if backups will go to an SD card or USB stick instead.

## 5. Open the app

Open **Safe Haven** once from the app drawer. On first start it:

- becomes the Home app, so it comes back on reboot and when Home is pressed;
- turns off the lock screen and the status bar, and blocks safe mode and adding users;
- keeps the screen on while plugged in;
- pins itself, so visitors can't leave it.

If Android asks which app to use as Home, it shouldn't, but if it does, choose Safe Haven and
"Always".

Reboot the tablet to check: it should go straight to the waiver Welcome screen.

## 6. Staff access, the PIN and the backup folder

- **Staff area:** long-press the logo on the Welcome screen.
- **First time:** there is no default PIN. The first person in creates the PIN (4 to 8 digits).
  Write it down somewhere safe at the shelter. After 5 wrong tries the PIN is locked for 30
  seconds, then 1 minute, 2, 4, ... up to 15 minutes. Restarting the tablet doesn't reset that.
- **Create the PIN during setup, before the tablet goes to the front desk.** Until a PIN exists,
  anyone who long-presses the logo can create it.
- **Forgotten PIN:** it can only be reset from a PC over USB, never from the tablet's screen.
  Plug the tablet into the PC used for setup (USB debugging must still be on, step 2) and run:

  ```
  adb shell am broadcast -n ca.safehaven.intake/.staff.PinResetReceiver -a ca.safehaven.intake.RESET_STAFF_PIN
  ```

  `result=1` in the output means the PIN is forgotten. Long-press the logo and create a new one
  straight away. The waivers are untouched. If USB debugging was turned off, or the PC isn't
  one the tablet trusts, the only way left is a factory reset (section 8), which erases every
  waiver on the tablet: so keep the PIN written down and back up often.
- **Backup folder:** staff area → **Backup** → **Choose folder**. The app unpins itself while the
  Android folder picker is open. In the picker, open the menu (☰) and pick the place:
  - **Google Drive** (if it's listed, see "Backup notes" below) → the shelter's folder;
  - an **SD card** or **USB stick**;
  - **internal storage** (only protects against the app being deleted, not the tablet being lost).

  Then tap **Select** (or "Use this folder"). Afterwards, **Back up now** copies every waiver not
  yet backed up.
- **Maintenance:** staff area → Maintenance → **Exit kiosk**. The app unpins and the status bar
  comes back, so staff can pull it down for quick settings and Settings (gear icon). The app is
  still the Home app, so Home brings it back. Go back to visitor mode to pin it again.

### Backup notes

- Files go straight into the chosen folder (no sub-folders), named like `2026-11-09_0001.pdf`,
  which sorts by date.
- A waiver is only marked as backed up after its whole file was written. If a run stops part-way
  (no space, folder removed, Wi-Fi drop for Drive), the rest stay pending and the next
  **Back up now** carries on. A single file that fails is skipped and the others are still
  copied. A backup keeps running if the staff area locks itself in the meantime. A file that's already in the folder with the same name and size
  isn't copied twice. Very rarely the folder may end up with a `... (1).pdf` copy; that's harmless.
- **Google Drive in the folder picker is not guaranteed.** Whether Drive shows up as a choice in
  Android's folder picker depends on the version of the Drive app; some versions only offer Drive
  for opening single files, not whole folders. If Drive isn't listed on this tablet, use an SD
  card or USB stick (and copy it to Drive from a PC now and then), or ask the developer about
  another option. The app doesn't include any Google Drive code of its own.
- With Drive, "backed up" means handed to the Drive app, which uploads when it's online. Leave the
  tablet on Wi-Fi, and check in Drive on a PC now and then that the files arrive.
- If the folder is deleted or the Google account removed, the app says the folder is unavailable:
  choose it again.
- Choosing a **different** folder makes every waiver pending again, so the new folder gets all
  of them, not only the new ones.

## 7. Updating the app

Install the new APK over the old one:

```
adb install -r app-release.apk
```

Device-owner status and all waivers are kept **as long as the new APK is signed with the same
key**. A differently signed APK won't install over it, and uninstalling instead is blocked for a
device owner (and would delete every waiver on the tablet). See
[Release signing in the README](README.md#release-signing): keep the keystore backed up.

## 8. Removing kiosk mode for good

On Android 6/7 the reliable way is a **factory reset**: back up the waivers first (staff area →
Backup), then staff area → Maintenance → Exit kiosk, pull down the status bar, Settings →
Backup & reset → Factory data reset. The kiosk doesn't block factory reset on purpose, so this
always works. The reset erases every waiver on the tablet.

Why not a command? `adb shell dpm remove-active-admin` only works for apps marked as test-only
(on newer Android versions); for a normal device owner on Android 6 it fails with a
`SecurityException`. Only the app itself could give up device owner (`clearDeviceOwnerApp`), and
this app has no button for that, so a visitor can't undo the kiosk either.

If the app crashes and the tablet is stuck, the factory reset can also be started from the
hardware recovery menu (on LG tablets roughly: power off, then hold Power + Volume Down; the exact
keys vary, search for "LG G Pad X 8.0 hard reset").

## 9. Testing on the emulator (developers)

A `google_apis` API 23 emulator (like the `LG_GPad_X8_API23` AVD in PROGRESS.md) with **no
accounts** can be made device owner the same way:

```
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell dpm set-device-owner ca.safehaven.intake/.kiosk.KioskAdminReceiver
```

Prints the same `Success: Device owner set to package ca.safehaven.intake` lines. Debug and release
builds are signed with different keys, so you can't switch between them without undoing device
owner.

To undo it on the emulator, wipe its data: Android Studio → Device Manager → the AVD's menu →
**Wipe Data** (or `emulator -avd LG_GPad_X8_API23 -wipe-data`). Use a separate AVD for kiosk
testing so the normal dev emulator stays un-pinned.

Handy commands:

```
adb shell dumpsys device_policy          # who is device owner, which policies are set
adb shell dumpsys activity activities | findstr mLockTaskModeState   # Windows; use grep elsewhere
```

`mLockTaskModeState=LOCKED` means the app is pinned; `NONE` means it isn't.
