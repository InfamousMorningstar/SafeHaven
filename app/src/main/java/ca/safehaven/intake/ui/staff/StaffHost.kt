package ca.safehaven.intake.ui.staff

/** What the staff screens need from the activity. Implemented by MainActivity. */
interface StaffHost {
    /**
     * Call right before deliberately opening another app (share sheet, folder picker), so the
     * activity going to the background doesn't lock the staff area.
     */
    fun onLaunchingOtherApp()

    /** The launch announced with [onLaunchingOtherApp] didn't happen (no app to open). */
    fun onLaunchFailed()

    /** Device owner only: releases the kiosk lock and opens Android Settings. */
    fun openSystemSettings()
}
