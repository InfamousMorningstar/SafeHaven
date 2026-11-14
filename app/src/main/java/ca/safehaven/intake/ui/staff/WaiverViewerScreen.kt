package ca.safehaven.intake.ui.staff

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.safehaven.intake.R
import ca.safehaven.intake.data.db.SubmissionEntity
import java.io.File

/** One waiver: the PDF pages, with Share and Delete. */
@Composable
fun WaiverViewerScreen(
    record: SubmissionEntity,
    viewModel: RecordsViewModel,
    fileProviderAuthority: String,
    host: StaffHost,
    onLock: () -> Unit,
) {
    val context = LocalContext.current
    val confirmingDelete by viewModel.confirmingDelete.collectAsStateWithLifecycle()
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    // Looked up once per record; the viewer reports a missing file instead of crashing.
    val file: File? = remember(record.id, record.pdfPath) { viewModel.pdfFile(record) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedButton(
                onClick = viewModel::closeViewer,
                modifier = Modifier.heightIn(min = 64.dp),
            ) { Text(stringResource(R.string.staff_viewer_back)) }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.staff_viewer_title, record.recordCode),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    recordName(record),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = {
                    if (file != null && shareWaiver(context, file, record, fileProviderAuthority, host)) {
                        viewModel.onShared(record.id)
                    }
                },
                enabled = file != null && !isDeleting,
                modifier = Modifier.heightIn(min = 64.dp),
            ) { Text(stringResource(R.string.staff_viewer_share)) }
            OutlinedButton(
                onClick = viewModel::requestDelete,
                enabled = !isDeleting,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.heightIn(min = 64.dp),
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                } else {
                    Text(stringResource(R.string.staff_viewer_delete))
                }
            }
            LockButton(onLock)
        }
        message?.let {
            Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) { RecordsMessageBanner(it) }
        }
        HorizontalDivider()
        if (file != null) {
            PdfPages(file, Modifier.fillMaxWidth().weight(1f))
        } else {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.staff_record_file_missing), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    if (confirmingDelete) {
        DeleteDialog(
            record = record,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

@Composable
private fun DeleteDialog(record: SubmissionEntity, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val notBackedUp = record.backedUpAt == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.staff_delete_title, record.recordCode), style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (notBackedUp) {
                    Text(
                        stringResource(R.string.staff_delete_not_backed_up),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(stringResource(R.string.staff_delete_message), style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.staff_delete_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 56.dp)) {
                Text(stringResource(R.string.staff_delete_cancel))
            }
        },
    )
}

/** Opens the share sheet for one PDF through the FileProvider. Returns true if it opened. */
private fun shareWaiver(
    context: Context,
    file: File,
    record: SubmissionEntity,
    authority: String,
    host: StaffHost,
): Boolean {
    val uri = try {
        FileProvider.getUriForFile(context, authority, file)
    } catch (e: IllegalArgumentException) {
        return false
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        // Only the record number: the visitor's name stays out of email subjects and file lists.
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.staff_viewer_share_subject, record.recordCode))
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, context.getString(R.string.staff_viewer_share_title))
    host.onLaunchingOtherApp()
    return try {
        context.startActivity(chooser)
        true
    } catch (e: ActivityNotFoundException) {
        host.onLaunchFailed()
        Toast.makeText(context, R.string.staff_viewer_no_share_app, Toast.LENGTH_LONG).show()
        false
    }
}
