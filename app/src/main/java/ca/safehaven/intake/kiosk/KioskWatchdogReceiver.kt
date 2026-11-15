package ca.safehaven.intake.kiosk

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import ca.safehaven.intake.MainActivity

/**
 * Brings the kiosk back when the app isn't running at all.
 *
 * While the app is alive, MainActivity keeps itself in front and pinned. But if Android kills the
 * app's process while another screen is showing (staff left Settings open and the tablet ran low
 * on memory, or the app crashed with Recents underneath), nothing in the app is left to do that,
 * and the tablet would sit unpinned until someone pressed Home. An alarm outlives the process:
 * about once a minute it checks, and starts the app again if its screen is gone.
 *
 * Does nothing unless the app is the device owner. Not exported: only the alarm reaches it.
 */
class KioskWatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // The screen exists: MainActivity looks after itself (and knows if staff are away on purpose).
        if (MainActivity.isAlive) return
        if (!DeviceOwnerKioskController(context).isDeviceOwner) return
        try {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: RuntimeException) {
            // Try again at the next alarm. Nothing to log.
        }
    }

    companion object {
        /** Android may batch inexact alarms, so "a minute" can stretch to a few. */
        private const val INTERVAL_MILLIS = 60_000L

        /** Call on every app start; alarms don't survive a reboot or a force-stop. */
        fun schedule(context: Context) {
            val appContext = context.applicationContext
            if (!DeviceOwnerKioskController(appContext).isDeviceOwner) return
            val alarms = appContext.getSystemService(AlarmManager::class.java) ?: return
            val intent = PendingIntent.getBroadcast(
                appContext,
                0,
                Intent(appContext, KioskWatchdogReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            // ELAPSED_REALTIME without WAKEUP: with the screen off there is nothing to bring back.
            alarms.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + INTERVAL_MILLIS,
                INTERVAL_MILLIS,
                intent,
            )
        }
    }
}
