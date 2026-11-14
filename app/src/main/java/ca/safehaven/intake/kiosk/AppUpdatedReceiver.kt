package ca.safehaven.intake.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ca.safehaven.intake.MainActivity

/**
 * Brings the kiosk back after the app is updated (`adb install -r`, KIOSK_SETUP.md).
 *
 * An update kills the app, which ends the kiosk pin, and Android then shows the tablet's own
 * launcher: unpinned, with Settings one tap away, until someone presses Home. This starts the
 * app again straight away; MainActivity then re-applies the policies and pins itself.
 *
 * Does nothing unless the app is the device owner, so a developer's phone or emulator isn't
 * taken over after every install.
 */
class AppUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val kiosk = DeviceOwnerKioskController(context)
        if (!kiosk.isDeviceOwner) return
        kiosk.applyPolicies()
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
