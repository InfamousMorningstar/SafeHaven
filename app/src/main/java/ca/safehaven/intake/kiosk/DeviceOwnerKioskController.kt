package ca.safehaven.intake.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.UserManager
import android.provider.Settings
import ca.safehaven.intake.MainActivity
import ca.safehaven.intake.staff.KioskController

/**
 * Kiosk lock through the device-owner APIs (KIOSK_SETUP.md). Every call is a no-op unless the
 * app is the device owner, so a normal phone or the dev emulator behaves like any other app.
 *
 * Each policy call swallows its own SecurityException / IllegalArgumentException, so one policy
 * that a particular Android build refuses doesn't stop the others. Nothing is logged.
 */
class DeviceOwnerKioskController(context: Context) : KioskController {
    private val appContext = context.applicationContext
    private val packageName = appContext.packageName
    private val dpm = appContext.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(appContext, KioskAdminReceiver::class.java)

    override val isDeviceOwner: Boolean
        get() = dpm?.isDeviceOwnerApp(packageName) == true

    override fun applyPolicies() {
        val dpm = ownerDpm() ?: return

        // Only this app may pin itself; startLockTask() then pins without asking the user.
        attempt { dpm.setLockTaskPackages(admin, arrayOf(packageName)) }

        // This app is the Home screen: Home returns here, and it starts after a reboot.
        attempt {
            val home = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(admin, home, ComponentName(appContext, MainActivity::class.java))
        }

        // No lock screen after a reboot or screen-off (API 23). Android refuses this while a
        // secure lock (PIN/pattern/password) is set; KIOSK_SETUP.md says to use "None"/"Swipe".
        attempt { dpm.setKeyguardDisabled(admin, true) }

        // Visitor mode starts with the status bar off (API 23); unlock() turns it back on.
        attempt { dpm.setStatusBarDisabled(admin, true) }

        // No safe mode (it would start without this app as Home) and no extra users.
        // Deliberately NOT set: DISALLOW_FACTORY_RESET (the only way to undo device owner on
        // Android 6), DISALLOW_USB_FILE_TRANSFER and DISALLOW_MOUNT_PHYSICAL_MEDIA (backups may
        // go to an SD card or USB stick).
        attempt { dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT) }
        attempt { dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER) }

        // Keep the screen on while charging (AC, USB or wireless). Device owners may set this
        // from API 23; it has no effect if a maximum screen-off time policy is set (we set none).
        attempt {
            val anyCharger = BatteryManager.BATTERY_PLUGGED_AC or
                BatteryManager.BATTERY_PLUGGED_USB or
                BatteryManager.BATTERY_PLUGGED_WIRELESS
            dpm.setGlobalSetting(admin, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, anyCharger.toString())
        }
    }

    override fun lock(activity: Activity) {
        val dpm = ownerDpm() ?: return
        attempt { dpm.setStatusBarDisabled(admin, true) }
        if (isLocked(activity)) return
        if (!dpm.isLockTaskPermitted(packageName)) {
            attempt { dpm.setLockTaskPackages(admin, arrayOf(packageName)) }
        }
        // Only when permitted: otherwise Android shows the "screen pinning" prompt instead.
        if (dpm.isLockTaskPermitted(packageName)) {
            attempt { activity.startLockTask() }
        }
    }

    override fun unlock(activity: Activity) {
        val dpm = ownerDpm() ?: return
        if (isLocked(activity)) attempt { activity.stopLockTask() }
        // Staff can pull down the status bar (e.g. for Settings) while unlocked.
        attempt { dpm.setStatusBarDisabled(admin, false) }
    }

    override fun isLocked(activity: Activity): Boolean {
        val am = activity.getSystemService(ActivityManager::class.java) ?: return false
        // getLockTaskModeState() is API 23, which is our minSdk.
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    private fun ownerDpm(): DevicePolicyManager? = dpm?.takeIf { it.isDeviceOwnerApp(packageName) }

    private inline fun attempt(block: () -> Unit) {
        try {
            block()
        } catch (e: SecurityException) {
            // Not allowed on this build; carry on with the next policy.
        } catch (e: IllegalArgumentException) {
            // Unknown setting or restriction on this build.
        } catch (e: IllegalStateException) {
            // startLockTask/stopLockTask when the activity isn't in a state to change it.
        }
    }
}
