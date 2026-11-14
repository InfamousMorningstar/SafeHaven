package ca.safehaven.intake.staff

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ca.safehaven.intake.data.staff.SharedPrefsPinStore

/**
 * The way back in after a forgotten staff PIN (KIOSK_SETUP.md): from a PC, over USB,
 *
 * `adb shell am broadcast -n ca.safehaven.intake/.staff.PinResetReceiver -a ca.safehaven.intake.RESET_STAFF_PIN`
 *
 * forgets the PIN, and the next long-press on the logo asks for a new one. Waivers are kept.
 *
 * The manifest only lets holders of `android.permission.DUMP` send this. That permission is
 * for the system and the adb shell; no app a visitor could install can hold it. So the reset
 * needs USB debugging turned on and a PC the tablet has been told to trust.
 */
class PinResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        // The result code shows in adb's output: 1 = PIN forgotten, 0 = it couldn't be saved.
        resultCode = if (SharedPrefsPinStore.forget(context)) 1 else 0
    }

    private companion object {
        const val ACTION = "ca.safehaven.intake.RESET_STAFF_PIN"
    }
}
