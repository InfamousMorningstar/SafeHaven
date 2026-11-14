package ca.safehaven.intake.ui.staff

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Which half of the app is showing. */
enum class AppMode {
    /** The visitor waiver. Kiosk locked. */
    VISITOR,

    /** The staff PIN screen. Still kiosk locked: a visitor who long-presses the logo gains nothing. */
    PIN,

    /** The staff area, after a correct PIN. Kiosk unlocked so Android's pickers and share targets open. */
    STAFF,
}

/** Two minutes without a touch in the staff area (or on the PIN screen) returns to the visitor screen. */
const val STAFF_IDLE_TIMEOUT_MILLIS = 2 * 60_000L

/**
 * When staff deliberately open another app (share sheet, folder picker, Settings), the idle timer
 * pauses. If they stay away longer than this, the staff area is locked when the app comes back.
 */
const val STAFF_MAX_AWAY_MILLIS = 10 * 60_000L

/**
 * Activity-scoped: the current [AppMode] and the staff auto-lock. MainActivity watches [mode] to
 * set FLAG_SECURE and the kiosk lock. Nothing here is saved across process death, so a restart
 * always comes back in visitor mode.
 */
class StaffSessionViewModel(
    /** Milliseconds that keep counting while the tablet sleeps; replaced by virtual time in tests. */
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {

    private val _mode = MutableStateFlow(AppMode.VISITOR)
    val mode: StateFlow<AppMode> = _mode.asStateFlow()

    private var idleJob: Job? = null
    private var lastInteractionAt = 0L

    /** Non-null while staff are away in another app they opened on purpose. */
    private var awaySince: Long? = null

    /** Long press on the Welcome logo. The caller resets the visitor form first. */
    fun enterStaff() {
        if (_mode.value != AppMode.VISITOR) return
        awaySince = null
        _mode.value = AppMode.PIN
        startIdleWatch()
    }

    /** The PIN was accepted (or a new one created). */
    fun onPinAccepted() {
        if (_mode.value != AppMode.PIN) return
        _mode.value = AppMode.STAFF
        startIdleWatch()
    }

    /** Lock button, Back from the staff home, Cancel on the PIN screen, auto-lock, leaving the app. */
    fun exitStaff() {
        idleJob?.cancel()
        idleJob = null
        awaySince = null
        _mode.value = AppMode.VISITOR
    }

    /** Called on every touch in the staff area and on every keystroke in its text fields. */
    fun onUserInteraction() {
        lastInteractionAt = now()
    }

    /**
     * The activity went to the background. [deliberate] is true when staff opened another app on
     * purpose (share sheet, folder picker, Settings): the staff area then stays open, with the idle
     * timer paused. Anything else (Home, Recents, the screen turning off) locks it.
     */
    fun onActivityStopped(deliberate: Boolean) {
        when (_mode.value) {
            AppMode.VISITOR -> Unit
            AppMode.PIN -> exitStaff()
            AppMode.STAFF -> if (deliberate) {
                idleJob?.cancel()
                idleJob = null
                awaySince = now()
            } else {
                exitStaff()
            }
        }
    }

    /** The activity is visible again. Restarts the idle timer after a deliberate trip to another app. */
    fun onActivityStarted() {
        val since = awaySince ?: return
        awaySince = null
        if (_mode.value != AppMode.STAFF) return
        if (now() - since > STAFF_MAX_AWAY_MILLIS) exitStaff() else startIdleWatch()
    }

    private fun startIdleWatch() {
        idleJob?.cancel()
        lastInteractionAt = now()
        idleJob = viewModelScope.launch {
            while (true) {
                val remaining = lastInteractionAt + STAFF_IDLE_TIMEOUT_MILLIS - now()
                if (remaining <= 0) break
                delay(remaining)
            }
            exitStaff()
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer { StaffSessionViewModel() }
        }
    }
}
