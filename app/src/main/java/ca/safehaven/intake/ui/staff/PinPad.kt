package ca.safehaven.intake.ui.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ca.safehaven.intake.R
import ca.safehaven.intake.staff.PinStore
import ca.safehaven.intake.ui.theme.SafeHavenTheme
import java.util.Locale

private val KEY_WIDTH = 104.dp
private val KEY_HEIGHT = 76.dp
private val KEY_GAP = 12.dp
private val DOT_SIZE = 22.dp

/** Full-screen PIN gate in front of the staff area. */
@Composable
fun PinScreen(
    state: PinUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        PinPanel(
            purpose = PinPurpose.UNLOCK,
            state = state,
            onDigit = onDigit,
            onBackspace = onBackspace,
            onSubmit = onSubmit,
            onCancel = onCancel,
        )
    }
}

/** Instructions and status on the left, a big number pad on the right. No system keyboard. */
@Composable
fun PinPanel(
    purpose: PinPurpose,
    state: PinUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(48.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Fixed width: if this column followed its text, the number pad beside it would jump
        // sideways between steps ("Create" -> "Enter again" -> "Wrong PIN") and staff would
        // tap the wrong keys.
        Column(Modifier.width(360.dp)) {
            Text(stringResource(pinTitle(purpose, state.step)), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(pinInstruction(purpose, state.step)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            PinDots(state.digitCount)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.heightIn(min = 64.dp), contentAlignment = Alignment.CenterStart) {
                PinStatus(state)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.heightIn(min = 72.dp).widthIn(min = 200.dp),
            ) { Text(stringResource(R.string.staff_pin_cancel)) }
        }
        NumberPad(
            enabled = state.acceptsInput,
            canSubmit = state.canSubmit,
            canDelete = state.acceptsInput && state.digitCount > 0,
            onDigit = onDigit,
            onBackspace = onBackspace,
            onSubmit = onSubmit,
        )
    }
}

@Composable
private fun PinStatus(state: PinUiState) {
    val lockedOut = state.lockedOutSeconds
    when {
        state.isBusy -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.staff_pin_busy), style = MaterialTheme.typography.bodyLarge)
        }
        lockedOut != null -> Text(
            stringResource(R.string.staff_pin_locked_out, formatCountdown(lockedOut)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        else -> when (val message = state.message) {
            is PinMessage.Wrong -> Text(
                pluralStringResource(R.plurals.staff_pin_wrong, message.attemptsLeft, message.attemptsLeft),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            PinMessage.Mismatch -> Text(
                stringResource(R.string.staff_pin_mismatch),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            PinMessage.Error -> Text(
                stringResource(R.string.staff_pin_error),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            null -> Unit
        }
    }
}

/** One dot per digit entered, with empty rings up to the minimum length. */
@Composable
private fun PinDots(count: Int) {
    val description = pluralStringResource(R.plurals.staff_pin_dots_description, count, count)
    val filled = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier.heightIn(min = DOT_SIZE).semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(maxOf(PinStore.PIN_MIN_LENGTH, count)) { index ->
            val dot = Modifier.size(DOT_SIZE)
            if (index < count) {
                Box(dot.background(filled, CircleShape))
            } else {
                Box(dot.border(2.dp, empty, CircleShape))
            }
        }
    }
}

@Composable
private fun NumberPad(
    enabled: Boolean,
    canSubmit: Boolean,
    canDelete: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KEY_GAP)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
                row.forEach { digit -> DigitKey(digit, enabled, onDigit) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
            OutlinedButton(
                onClick = onBackspace,
                enabled = canDelete,
                modifier = Modifier.size(KEY_WIDTH, KEY_HEIGHT),
            ) { Text(stringResource(R.string.staff_pin_backspace), style = MaterialTheme.typography.bodyMedium) }
            DigitKey('0', enabled, onDigit)
            Button(
                onClick = onSubmit,
                enabled = canSubmit,
                modifier = Modifier.size(KEY_WIDTH, KEY_HEIGHT),
            ) { Text(stringResource(R.string.staff_pin_ok), style = MaterialTheme.typography.headlineSmall) }
        }
    }
}

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onDigit: (Char) -> Unit) {
    OutlinedButton(
        onClick = { onDigit(digit) },
        enabled = enabled,
        modifier = Modifier.size(KEY_WIDTH, KEY_HEIGHT),
    ) { Text(digit.toString(), style = MaterialTheme.typography.headlineMedium) }
}

private fun pinTitle(purpose: PinPurpose, step: PinStep): Int = when (purpose) {
    PinPurpose.UNLOCK -> if (step == PinStep.CURRENT) R.string.staff_pin_enter_title else R.string.staff_pin_create_title
    PinPurpose.CHANGE -> R.string.staff_pin_change_title
}

private fun pinInstruction(purpose: PinPurpose, step: PinStep): Int = when (purpose) {
    PinPurpose.UNLOCK -> when (step) {
        PinStep.CURRENT -> R.string.staff_pin_enter_message
        PinStep.NEW -> R.string.staff_pin_create_message
        PinStep.CONFIRM -> R.string.staff_pin_confirm_message
    }
    PinPurpose.CHANGE -> when (step) {
        PinStep.CURRENT -> R.string.staff_pin_change_current_message
        PinStep.NEW -> R.string.staff_pin_change_new_message
        PinStep.CONFIRM -> R.string.staff_pin_change_confirm_message
    }
}

/** `4:05` */
private fun formatCountdown(seconds: Long): String =
    String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

@Preview(widthDp = 960, heightDp = 600)
@Composable
private fun PinScreenPreview() = SafeHavenTheme {
    PinScreen(
        state = PinUiState(step = PinStep.CURRENT, digitCount = 3, message = PinMessage.Wrong(2)),
        onDigit = {},
        onBackspace = {},
        onSubmit = {},
        onCancel = {},
    )
}
