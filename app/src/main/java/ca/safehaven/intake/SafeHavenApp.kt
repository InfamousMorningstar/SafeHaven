package ca.safehaven.intake

import android.app.Application

class SafeHavenApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Remove partial PDFs left by a write that was cut off (power loss, crash).
        Thread { container.fileStore.clearTemp() }.start()
    }
}
