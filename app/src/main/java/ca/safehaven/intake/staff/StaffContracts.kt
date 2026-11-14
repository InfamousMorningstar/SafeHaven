package ca.safehaven.intake.staff

import android.app.Activity
import android.content.Intent
import android.net.Uri
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/*
 * Contracts between the staff UI and the services behind it. The UI depends only on these
 * interfaces; implementations are wired in AppContainer.
 */

/**
 * The staff PIN. Only a salted PBKDF2 hash is stored, never the PIN. There is no default PIN:
 * the first person to open the staff area creates one.
 */
interface PinStore {
    /** False until a PIN has been created. */
    fun hasPin(): Boolean

    /**
     * Creates or replaces the PIN. Callers must have verified the old PIN (or there is none).
     * @throws IllegalArgumentException if [pin] isn't [PIN_MIN_LENGTH]..[PIN_MAX_LENGTH] digits.
     */
    suspend fun setPin(pin: String)

    /** Checks [pin], counting failures. Runs the slow hash off the main thread. */
    suspend fun verify(pin: String): PinCheck

    companion object {
        const val PIN_MIN_LENGTH = 4
        const val PIN_MAX_LENGTH = 8
        /** Wrong attempts allowed before a lockout. */
        const val MAX_ATTEMPTS = 5
    }
}

sealed interface PinCheck {
    data object Ok : PinCheck
    data class Wrong(val attemptsLeft: Int) : PinCheck
    /** Too many wrong attempts; try again after [secondsLeft]. Survives an app restart or reboot. */
    data class LockedOut(val secondsLeft: Long) : PinCheck
    /** No PIN has been created yet. */
    data object NoPin : PinCheck
}

/**
 * Copies waiver PDFs that haven't been backed up to a folder the staff picked once through
 * Android's folder picker (the storage access framework). The folder can be on Google Drive,
 * an SD card, a USB stick, or any other provider that supports folder access.
 */
interface BackupService {
    fun observeStatus(): Flow<BackupStatus>

    /** An `ACTION_OPEN_DOCUMENT_TREE` intent for choosing the backup folder. */
    fun folderPickerIntent(): Intent

    /** Remembers the folder the staff picked, keeping the permission across reboots. */
    suspend fun setFolder(treeUri: Uri)

    /** Copies every PDF not yet backed up, marking each one as it succeeds. */
    suspend fun backUpNow(): BackupResult
}

data class BackupStatus(
    /** Display name of the chosen folder, or null when none is chosen. */
    val folderName: String?,
    /** Saved waivers not yet copied to the folder. */
    val pendingCount: Int,
    /** When a waiver was last copied, or null if never. */
    val lastBackupAt: Instant?,
    val isRunning: Boolean,
)

sealed interface BackupResult {
    /** [missing]: waivers skipped because their PDF is no longer on the tablet. They stay pending. */
    data class Done(val copied: Int, val missing: Int = 0) : BackupResult
    data object NoFolder : BackupResult
    /** The folder is gone or the app lost permission to it: staff must pick it again. */
    data object FolderUnavailable : BackupResult
    /** Some files failed ([cause] is the first error); [copied] made it, the rest stay pending. */
    data class Failed(val copied: Int, val cause: Throwable, val missing: Int = 0) : BackupResult
}

/**
 * Kiosk lock (plan phase 5). Only does anything when the app is the device owner; on a normal
 * device (or the dev emulator) every call is a harmless no-op.
 */
interface KioskController {
    val isDeviceOwner: Boolean

    /** Sets the device-owner policies (lock-task packages, HOME activity, etc.). Idempotent. */
    fun applyPolicies()

    /** Pins the app: no Home, Recents, notifications or status bar. Called in visitor mode. */
    fun lock(activity: Activity)

    /** Releases the pin so staff can use other apps (e.g. the Drive folder picker). */
    fun unlock(activity: Activity)

    /** True while the app is pinned. */
    fun isLocked(activity: Activity): Boolean
}
