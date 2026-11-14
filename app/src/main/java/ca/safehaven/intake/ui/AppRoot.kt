package ca.safehaven.intake.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.safehaven.intake.AppContainer
import ca.safehaven.intake.ui.intake.IntakeViewModel
import ca.safehaven.intake.ui.staff.AppMode
import ca.safehaven.intake.ui.staff.StaffArea
import ca.safehaven.intake.ui.staff.StaffHost
import ca.safehaven.intake.ui.staff.StaffSessionViewModel

/** Switches between the visitor waiver and the hidden, PIN-protected staff area. */
@Composable
fun AppRoot(
    container: AppContainer,
    intakeViewModel: IntakeViewModel,
    session: StaffSessionViewModel,
    host: StaffHost,
    shelterName: String,
) {
    val mode by session.mode.collectAsStateWithLifecycle()
    when (mode) {
        AppMode.VISITOR -> VisitorApp(
            viewModel = intakeViewModel,
            shelterName = shelterName,
            onStaffRequested = {
                // Nothing from a visitor may be in memory while staff are using the tablet.
                intakeViewModel.reset()
                session.enterStaff()
            },
        )
        // One branch for both, so the staff ViewModels survive the step from PIN to staff home.
        AppMode.PIN, AppMode.STAFF -> StaffArea(mode = mode, container = container, session = session, host = host)
    }
}
