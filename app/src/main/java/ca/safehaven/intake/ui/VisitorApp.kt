package ca.safehaven.intake.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.safehaven.intake.R
import ca.safehaven.intake.ui.intake.IntakeFormScreen
import ca.safehaven.intake.ui.intake.IntakeViewModel
import ca.safehaven.intake.ui.intake.ThanksScreen
import ca.safehaven.intake.ui.intake.VisitorScreen
import ca.safehaven.intake.ui.intake.WelcomeScreen

@Composable
fun VisitorApp(viewModel: IntakeViewModel, shelterName: String) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // There is no "back" for visitors: it must never lead to a previous visitor's form.
    // The form has its own Cancel button.
    BackHandler(enabled = true) {}

    PrivateTextInput {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .recordInteractions(viewModel::onUserInteraction),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (val screen = state.screen) {
                VisitorScreen.Welcome -> WelcomeScreen(shelterName = shelterName, onStart = viewModel::start)
                VisitorScreen.Form -> IntakeFormScreen(
                    form = state.form,
                    config = viewModel.config,
                    shelterName = shelterName,
                    onInputChange = viewModel::updateInput,
                    onFieldBlurred = viewModel::onFieldBlurred,
                    onSubmit = viewModel::submit,
                    onCancel = viewModel::reset,
                )
                is VisitorScreen.Thanks -> ThanksScreen(firstName = screen.firstName, onDone = viewModel::reset)
            }
        }

        state.stillThereSeconds?.let { seconds ->
            StillThereDialog(
                seconds = seconds,
                onStay = viewModel::onUserInteraction,
                onLeave = viewModel::reset,
            )
        }
    }
}

/** Sees every touch (without consuming it) so the inactivity timer can restart. */
private fun Modifier.recordInteractions(onInteraction: () -> Unit): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                awaitPointerEvent(PointerEventPass.Initial)
                onInteraction()
            }
        }
    },
)

@Composable
private fun StillThereDialog(seconds: Int, onStay: () -> Unit, onLeave: () -> Unit) {
    val stay by rememberUpdatedState(onStay)
    AlertDialog(
        onDismissRequest = { stay() },
        title = { Text(stringResource(R.string.idle_title), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Text(pluralStringResource(R.plurals.idle_message, seconds, seconds), style = MaterialTheme.typography.bodyLarge)
        },
        confirmButton = { Button(onClick = { stay() }) { Text(stringResource(R.string.idle_stay)) } },
        dismissButton = { TextButton(onClick = onLeave) { Text(stringResource(R.string.idle_leave)) } },
    )
}
