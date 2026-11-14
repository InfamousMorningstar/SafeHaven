package ca.safehaven.intake.ui.staff

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.safehaven.intake.AppContainer
import ca.safehaven.intake.BuildConfig
import ca.safehaven.intake.R
import ca.safehaven.intake.ui.PrivateTextInput
import ca.safehaven.intake.ui.recordInteractions

private val RAIL_WIDTH = 240.dp

private enum class StaffSection(@param:StringRes val label: Int) {
    RECORDS(R.string.staff_section_records),
    BACKUP(R.string.staff_section_backup),
    MAINTENANCE(R.string.staff_section_maintenance),
}

/**
 * Everything shown in [AppMode.PIN] and [AppMode.STAFF]. The staff ViewModels live in a
 * ViewModelStore owned by this composable and cleared when it leaves the screen (on any lock),
 * so search results and open records never outlive the staff session.
 */
@Composable
fun StaffArea(
    mode: AppMode,
    container: AppContainer,
    session: StaffSessionViewModel,
    host: StaffHost,
) {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }

    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        PrivateTextInput {
            Surface(
                modifier = Modifier.fillMaxSize().recordInteractions(session::onUserInteraction),
                color = MaterialTheme.colorScheme.background,
            ) {
                if (mode == AppMode.PIN) {
                    PinGate(container, onAccepted = session::onPinAccepted, onCancel = session::exitStaff)
                } else {
                    StaffHome(container, host, onLock = session::exitStaff, onInteraction = session::onUserInteraction)
                }
            }
        }
    }
}

@Composable
private fun PinGate(container: AppContainer, onAccepted: () -> Unit, onCancel: () -> Unit) {
    val pin: PinFlowViewModel = viewModel(key = "unlock", factory = PinFlowViewModel.factory(container, PinPurpose.UNLOCK))
    val state by pin.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onAccepted() }
    BackHandler(onBack = onCancel)
    PinScreen(
        state = state,
        onDigit = pin::onDigit,
        onBackspace = pin::onBackspace,
        onSubmit = pin::onSubmit,
        onCancel = onCancel,
    )
}

@Composable
private fun StaffHome(
    container: AppContainer,
    host: StaffHost,
    onLock: () -> Unit,
    onInteraction: () -> Unit,
) {
    val records: RecordsViewModel = viewModel(factory = RecordsViewModel.factory(container))
    val backup: BackupViewModel = viewModel(factory = BackupViewModel.factory(container))
    val changePin: PinFlowViewModel =
        viewModel(key = "changePin", factory = PinFlowViewModel.factory(container, PinPurpose.CHANGE))
    val selected by records.selected.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf(StaffSection.RECORDS) }

    val open = selected
    if (open != null) {
        BackHandler(onBack = records::closeViewer)
        WaiverViewerScreen(
            record = open,
            viewModel = records,
            fileProviderAuthority = container.fileProviderAuthority,
            host = host,
            onLock = onLock,
        )
    } else {
        BackHandler(onBack = onLock)
        Row(Modifier.fillMaxSize()) {
            SectionRail(selected = section, onSelect = { section = it }, onLock = onLock)
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (section) {
                    StaffSection.RECORDS -> RecordsSection(records, onInteraction)
                    StaffSection.BACKUP -> BackupSection(backup, container.config.timeZoneId, host)
                    StaffSection.MAINTENANCE -> {
                        val count by records.totalCount.collectAsStateWithLifecycle()
                        MaintenanceSection(
                            changePin = changePin,
                            isDeviceOwner = container.kiosk.isDeviceOwner,
                            appVersion = BuildConfig.VERSION_NAME,
                            deviceLabel = container.config.deviceLabel,
                            waiverCount = count,
                            host = host,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionRail(selected: StaffSection, onSelect: (StaffSection) -> Unit, onLock: () -> Unit) {
    Column(
        modifier = Modifier.width(RAIL_WIDTH).fillMaxHeight().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.staff_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 16.dp),
        )
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StaffSection.entries.forEach { item ->
                RailItem(item.label, isSelected = item == selected, onClick = { onSelect(item) })
            }
        }
        Spacer(Modifier.weight(1f))
        LockButton(onLock, Modifier.fillMaxWidth())
    }
}

@Composable
private fun RailItem(@StringRes label: Int, isSelected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (isSelected) colors.secondaryContainer else Color.Transparent)
            .selectable(selected = isSelected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.titleMedium,
            color = if (isSelected) colors.onSecondaryContainer else colors.onSurface,
        )
    }
}

/** Always visible in the staff area: back to the visitor Welcome screen. */
@Composable
fun LockButton(onLock: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onLock, modifier = modifier.heightIn(min = 64.dp).widthIn(min = 120.dp)) {
        Text(stringResource(R.string.staff_lock))
    }
}
