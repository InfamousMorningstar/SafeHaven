package ca.safehaven.intake

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
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
                staffSession.mode.collect { applyMode(it) }
            }
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

    override fun onStart() {
        super.onStart()
        staffSession.onActivityStarted()
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
        if (!isChangingConfigurations) staffSession.onActivityStopped(deliberate = launchingOtherApp)
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
    }

    /** A kiosk call must never crash the front-desk app. Logs only the step, never visitor data. */
    private inline fun kiosk(step: String, block: () -> Unit) {
        try {
            block()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Kiosk step failed: $step", e)
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
