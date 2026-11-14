package ca.safehaven.intake.ui.staff

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.safehaven.intake.R

/** Staff > Maintenance: change the PIN, kiosk status and exit, app info. */
@Composable
fun MaintenanceSection(
    changePin: PinFlowViewModel,
    isDeviceOwner: Boolean,
    appVersion: String,
    deviceLabel: String,
    waiverCount: Int?,
    host: StaffHost,
) {
    var changingPin by remember { mutableStateOf(false) }
    var pinChanged by remember { mutableStateOf(false) }

    if (changingPin) {
        val state by changePin.state.collectAsStateWithLifecycle()
        LaunchedEffect(state.finished) {
            if (state.finished) {
                changingPin = false
                pinChanged = true
            }
        }
        BackHandler { changingPin = false }
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            PinPanel(
                purpose = PinPurpose.CHANGE,
                state = state,
                onDigit = changePin::onDigit,
                onBackspace = changePin::onBackspace,
                onSubmit = changePin::onSubmit,
                onCancel = { changingPin = false },
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeading(R.string.staff_maint_pin_title)
        if (pinChanged) StaffBanner(stringResource(R.string.staff_pin_changed), isError = false)
        OutlinedButton(
            onClick = {
                pinChanged = false
                changePin.restart()
                changingPin = true
            },
            modifier = Modifier.heightIn(min = 72.dp).widthIn(min = 220.dp),
        ) { Text(stringResource(R.string.staff_maint_change_pin)) }

        HorizontalDivider()
        SectionHeading(R.string.staff_maint_kiosk_title)
        if (isDeviceOwner) {
            Text(stringResource(R.string.staff_maint_kiosk_on), style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(
                onClick = host::openSystemSettings,
                modifier = Modifier.heightIn(min = 72.dp).widthIn(min = 220.dp),
            ) { Text(stringResource(R.string.staff_maint_kiosk_exit)) }
            Text(
                stringResource(R.string.staff_maint_kiosk_exit_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                stringResource(R.string.staff_maint_kiosk_off),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalDivider()
        SectionHeading(R.string.staff_maint_about_title)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.staff_maint_version, appVersion), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.staff_maint_device, deviceLabel), style = MaterialTheme.typography.bodyLarge)
            Text(
                if (waiverCount != null) {
                    stringResource(R.string.staff_maint_count, waiverCount)
                } else {
                    stringResource(R.string.staff_maint_count_loading)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SectionHeading(@StringRes title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
}
