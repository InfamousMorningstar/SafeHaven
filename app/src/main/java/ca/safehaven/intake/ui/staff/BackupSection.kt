package ca.safehaven.intake.ui.staff

import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.safehaven.intake.R
import ca.safehaven.intake.staff.BackupResult
import ca.safehaven.intake.staff.BackupStatus
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Staff > Backup: folder, pending count, last backup, "Choose folder…" and "Back up now". */
@Composable
fun BackupSection(viewModel: BackupViewModel, timeZoneId: String, host: StaffHost) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isWorking by viewModel.isWorking.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val lastBackupFormat = remember(timeZoneId) {
        DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm a", Locale.US).withZone(ZoneId.of(timeZoneId))
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) viewModel.onFolderPicked(uri)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.staff_backup_title), style = MaterialTheme.typography.headlineSmall)

        val current = status
        if (current == null) {
            Text(stringResource(R.string.staff_loading), style = MaterialTheme.typography.bodyLarge)
        } else {
            StatusCard(current, lastBackupFormat)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = {
                    host.onLaunchingOtherApp()
                    try {
                        picker.launch(viewModel.folderPickerIntent())
                    } catch (_: ActivityNotFoundException) {
                        host.onLaunchFailed()
                        viewModel.onPickerUnavailable()
                    }
                },
                enabled = !isWorking && current?.isRunning != true,
                modifier = Modifier.heightIn(min = 72.dp).widthIn(min = 220.dp),
            ) { Text(stringResource(R.string.staff_backup_choose)) }
            Text(
                stringResource(R.string.staff_backup_choose_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }

        val running = isWorking || current?.isRunning == true
        Button(
            onClick = viewModel::backUpNow,
            enabled = !running && current?.folderName != null,
            modifier = Modifier.heightIn(min = 72.dp).widthIn(min = 260.dp),
        ) {
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(16.dp))
                Text(stringResource(R.string.staff_backup_running))
            } else {
                Text(stringResource(R.string.staff_backup_now))
            }
        }

        message?.let { BackupMessageBanner(it) }
        val missing = when (val result = (message as? BackupMessage.Finished)?.result) {
            is BackupResult.Done -> result.missing
            is BackupResult.Failed -> result.missing
            else -> 0
        }
        if (missing > 0) {
            StaffBanner(pluralStringResource(R.plurals.staff_backup_missing, missing, missing), isError = true)
        }
    }
}

@Composable
private fun StatusCard(status: BackupStatus, lastBackupFormat: DateTimeFormatter) {
    val colors = MaterialTheme.colorScheme
    val pending = status.pendingCount > 0
    Surface(
        // A gentle warning colour while some waivers exist only on this tablet.
        color = if (pending) colors.secondaryContainer else colors.surfaceVariant,
        contentColor = if (pending) colors.onSecondaryContainer else colors.onSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val folder = status.folderName
            Text(
                if (folder != null) stringResource(R.string.staff_backup_folder, folder) else stringResource(R.string.staff_backup_no_folder),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (pending) {
                    pluralStringResource(R.plurals.staff_backup_pending, status.pendingCount, status.pendingCount)
                } else {
                    stringResource(R.string.staff_backup_all_done)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            val last = status.lastBackupAt
            Text(
                if (last != null) stringResource(R.string.staff_backup_last, lastBackupFormat.format(last)) else stringResource(R.string.staff_backup_never),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun BackupMessageBanner(message: BackupMessage) {
    when (message) {
        BackupMessage.FolderFailed -> StaffBanner(stringResource(R.string.staff_backup_folder_failed), isError = true)
        BackupMessage.NoPicker -> StaffBanner(stringResource(R.string.staff_backup_no_picker), isError = true)
        is BackupMessage.Finished -> when (val result = message.result) {
            is BackupResult.Done -> StaffBanner(
                if (result.copied == 0) {
                    stringResource(R.string.staff_backup_nothing_new)
                } else {
                    pluralStringResource(R.plurals.staff_backup_done, result.copied, result.copied)
                },
                isError = false,
            )
            BackupResult.NoFolder -> StaffBanner(stringResource(R.string.staff_backup_result_no_folder), isError = true)
            BackupResult.FolderUnavailable -> StaffBanner(stringResource(R.string.staff_backup_unavailable), isError = true)
            is BackupResult.Failed -> StaffBanner(
                stringResource(R.string.staff_backup_failed, result.copied),
                isError = false,
                isWarning = true,
            )
        }
    }
}
