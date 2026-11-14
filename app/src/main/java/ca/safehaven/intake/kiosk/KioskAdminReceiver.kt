package ca.safehaven.intake.kiosk

import android.app.admin.DeviceAdminReceiver

/**
 * The device-admin component that makes the app the device owner (see KIOSK_SETUP.md):
 * `adb shell dpm set-device-owner ca.safehaven.intake/.kiosk.KioskAdminReceiver`
 */
class KioskAdminReceiver : DeviceAdminReceiver()
