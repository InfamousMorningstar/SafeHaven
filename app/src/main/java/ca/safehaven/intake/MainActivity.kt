package ca.safehaven.intake

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.ActionMode
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ca.safehaven.intake.ui.AppRoot
import ca.safehaven.intake.ui.intake.IntakeViewModel
import ca.safehaven.intake.ui.staff.AppMode
import ca.safehaven.intake.ui.staff.StaffHost
import ca.safehaven.intake.ui.staff.StaffSessionViewModel
import ca.safehaven.intake.ui.theme.SafeHavenTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as SafeHavenApp).container

    private val viewModel: IntakeViewModel by viewModels { IntakeViewModel.factory(container) }
    private val staffSession: StaffSessionViewModel by viewModels { StaffSessionViewModel.factory() }

    /**
     * Set just before staff deliberately open another app (share sheet, folder picker, Settings),
     * so the onStop that follows doesn't lock the staff area. Cleared in onResume: if the other
     * app never took over (e.g. the share sheet was dismissed), the next onStop locks as usual.
     */
    private var launchingOtherApp = false

    /** True between onStop and onStart: another app (or nothing, with the screen off) is showing. */
    private var isHidden = false
    private var returnJob: Job? = null

    private val staffHost = object : StaffHost {
        override fun onLaunchingOtherApp() {
            launchingOtherApp = true
        }

        override fun onLaunchFailed() {
            launchingOtherApp = false
        }

        override fun openSystemSettings() {
            if (!container.kiosk.isDeviceOwner) return
            launchingOtherApp = true
            kiosk("unlock for maintenance") { container.kiosk.unlock(this@MainActivity) }
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                launchingOtherApp = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isAlive = true
        // Front desk: keep the screen on while the app is showing (tablet stays plugged in).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Autofill (API 26+) could offer one visitor's details to the next. Android 6/7 has no Autofill.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }

        // Device-owner policies (no-op otherwise). The kiosk lock itself follows the mode below.
        kiosk("apply policies") { container.kiosk.applyPolicies() }

        // FLAG_SECURE and the kiosk lock follow the mode. Applied while resumed, because
        // startLockTask needs the activity in the foreground.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                staffSession.mode.collectLatest { mode ->
                    applyMode(mode)
                    if (mode != AppMode.STAFF) keepPinned()
                }
            }
        }

        // Staff area locked while the app is behind another one (staff stayed away too long in
        // Settings or the folder picker): come back to the front, where the kiosk pins again.
        lifecycleScope.launch {
            staffSession.mode.collect { if (it != AppMode.STAFF) returnToFrontIfHidden() }
        }

        setContent {
            SafeHavenTheme {
                AppRoot(
                    container = container,
                    intakeViewModel = viewModel,
                    session = staffSession,
                    host = staffHost,
                    shelterName = stringResource(R.string.shelter_name),
                )
            }
        }
    }

    override fun onDestroy() {
        // Not on rotation: the next instance is created straight away.
        if (!isChangingConfigurations) isAlive = false
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        isHidden = false
        staffSession.onActivityStarted()
        // A form left behind a switched-off screen for longer than the idle timeout is wiped.
        viewModel.onShownAgain()
    }

    override fun onResume() {
        super.onResume()
        launchingOtherApp = false
    }

    override fun onStop() {
        super.onStop()
        // Home, Recents, the power button or anything else not launched by staff locks the staff
        // area. FLAG_SECURE stays set until the visitor screen is showing again (next onResume),
        // so the Recents thumbnail never shows staff screens.
        if (isChangingConfigurations) return
        isHidden = true
        staffSession.onActivityStopped(deliberate = launchingOtherApp)
        // Left through Recents or anything else staff didn't open from here: don't stay behind
        // another app while unpinned.
        if (staffSession.mode.value != AppMode.STAFF) returnToFrontIfHidden()
    }

    /**
     * Kiosk only: if the app is behind another one while the staff area is locked, the tablet is
     * unpinned and open to whoever picks it up. Come back to the front; onResume then pins.
     *
     * Keeps trying until the app is showing again: for about five seconds after Home or Recents
     * is pressed, Android ignores an app's request to come forward.
     */
    private fun returnToFrontIfHidden() {
        if (!isHidden || !container.kiosk.isDeviceOwner || returnJob?.isActive == true) return
        returnJob = lifecycleScope.launch {
            while (isHidden && staffSession.mode.value != AppMode.STAFF) {
                // With the screen off the app is "hidden" too, but nothing else is showing.
                if (getSystemService(PowerManager::class.java)?.isInteractive != false) {
                    kiosk("return to front") {
                        getSystemService(ActivityManager::class.java)?.moveTaskToFront(taskId, 0)
                        startActivity(
                            Intent(this@MainActivity, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                delay(RETURN_RETRY_MILLIS)
            }
        }
    }

    /**
     * No Cut/Copy/Paste menu anywhere in the app. What one visitor copied would be offered to the
     * next one as "Paste", and Android adds other apps' text actions (Translate, Web search) to
     * the same menu. Compose shows that menu as a platform action mode, so closing every action
     * mode as it starts removes it whichever Compose code path opened it (PrivateTextInput covers
     * the older path). Nothing else in the app uses action modes.
     */
    override fun onActionModeStarted(mode: ActionMode) {
        super.onActionModeStarted(mode)
        mode.finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed (this is the kiosk's Home app) or the launcher icon tapped: always land on
        // the visitor screen, even if staff had left for another app on purpose.
        if (intent.action == Intent.ACTION_MAIN) staffSession.exitStaff()
    }

    private fun applyMode(mode: AppMode) {
        if (mode == AppMode.VISITOR && BuildConfig.DEBUG) {
            // Debug builds only, so the visitor form can be screenshotted during development.
            // In a release build the half-filled form is personal data too: never in a
            // screenshot or the Recents thumbnail.
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            // PIN and staff screens: no screenshots, blank in Recents.
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        val kioskController = container.kiosk
        if (mode == AppMode.STAFF) {
            // Released only after the PIN, so Android's folder picker and share targets can open.
            kiosk("unlock") { if (kioskController.isLocked(this)) kioskController.unlock(this) }
        } else {
            kiosk("lock") { if (!kioskController.isLocked(this)) kioskController.lock(this) }
        }
        // Nothing copied by staff (or by anyone, through the keyboard) is left for a visitor to paste.
        if (mode == AppMode.VISITOR) kiosk("clear clipboard") { clearClipboard() }
    }

    private fun clearClipboard() {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            // Android 6/7 can't empty the clipboard, only replace what's in it.
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    /**
     * Runs for as long as the visitor or PIN screen is showing. Android can refuse a pin request
     * (for example while the activity is still coming to the front), and a refusal is silent, so
     * one attempt isn't enough: a visitor screen left unpinned would let a visitor leave the app.
     * This checks again, quickly at first and then every [PIN_CHECK_MILLIS], and pins whenever
     * the app is found unpinned. Cancelled when staff unlock or the app leaves the screen.
     */
    private suspend fun keepPinned() {
        val kioskController = container.kiosk
        if (!kioskController.isDeviceOwner) return
        var wait = FIRST_PIN_CHECK_MILLIS
        while (true) {
            delay(wait)
            kiosk("re-lock") { if (!kioskController.isLocked(this)) kioskController.lock(this) }
            wait = (wait * 2).coerceAtMost(PIN_CHECK_MILLIS)
        }
    }

    /** A kiosk call must never crash the front-desk app. Logs only the step, never visitor data. */
    private inline fun kiosk(step: String, block: () -> Unit) {
        try {
            block()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Kiosk step failed: $step", e)
        }
    }

    companion object {
        /**
         * True while this screen exists in the process. KioskWatchdogReceiver restarts the app
         * only when it doesn't (the process was killed, or Android destroyed the screen).
         */
        @Volatile
        var isAlive = false
            private set

        private const val TAG = "MainActivity"
        private const val FIRST_PIN_CHECK_MILLIS = 300L
        private const val PIN_CHECK_MILLIS = 5_000L
        private const val RETURN_RETRY_MILLIS = 3_000L
    }
}
