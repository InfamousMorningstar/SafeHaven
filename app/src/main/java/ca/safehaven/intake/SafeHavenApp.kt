package ca.safehaven.intake

import android.app.Application
import ca.safehaven.intake.kiosk.KioskWatchdogReceiver
import kotlinx.coroutines.runBlocking

class SafeHavenApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Kiosk only: an alarm that restarts the app if its process is ever killed in the background.
        try {
            KioskWatchdogReceiver.schedule(this)
        } catch (e: RuntimeException) {
            // The kiosk still works without the watchdog; never stop the app from starting.
        }
        // Saves that were cut off (power loss, crash): finish the ones whose row was written,
        // remove the rest. The list is taken now, before any new save can start.
        val leftovers = container.fileStore.pendingTemp()
        if (leftovers.isNotEmpty()) {
            Thread {
                try {
                    val dao = container.database.submissionDao()
                    container.fileStore.recoverTemp(leftovers) { path ->
                        runBlocking { dao.countByPdfPath(path) } > 0
                    }
                } catch (e: Exception) {
                    // Leave the leftovers for the next start. Nothing is logged (file names
                    // contain record numbers).
                }
            }.start()
        }
    }
}
