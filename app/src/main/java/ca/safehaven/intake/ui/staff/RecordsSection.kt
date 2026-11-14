package ca.safehaven.intake.ui.staff

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.safehaven.intake.R
import ca.safehaven.intake.data.db.SubmissionEntity
import ca.safehaven.intake.domain.validation.TextRules
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val SIGNED_ON_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)

/** Staff > Records: search box, count and the list. Tapping a row opens the viewer. */
@Composable
fun RecordsSection(viewModel: RecordsViewModel, onInteraction: () -> Unit) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.staff_records_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = query,
            onValueChange = {
                // Keystrokes don't pass through the touch observer, so count them here.
                onInteraction()
                viewModel.setQuery(TextRules.singleLine(it).take(SEARCH_MAX_LENGTH))
            },
            label = { Text(stringResource(R.string.staff_records_search)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            trailingIcon = if (query.isNotEmpty()) {
                {
                    TextButton(onClick = { viewModel.setQuery("") }, modifier = Modifier.padding(end = 4.dp)) {
                        Text(stringResource(R.string.staff_records_clear))
                    }
                }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        )

        message?.let { RecordsMessageBanner(it) }

        val list = records
        when {
            list == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(if (query.isBlank()) R.string.staff_records_empty else R.string.staff_records_no_match),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                Text(
                    pluralStringResource(R.plurals.staff_records_count, list.size, list.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(list, key = { it.id }) { record ->
                        RecordRow(record, onClick = {
                            focusManager.clearFocus()
                            viewModel.open(record.id)
                        })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private const val SEARCH_MAX_LENGTH = 100

@Composable
private fun RecordRow(record: SubmissionEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.staff_record_heading, record.recordCode, SIGNED_ON_FORMAT.format(record.signedOn)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(recordName(record), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.staff_record_place, record.city, record.state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BackupMarker(backedUp = record.backedUpAt != null)
    }
}

/** "Jane Smith", or "Sam Smith (minor, guardian: Jane Smith)". */
@Composable
internal fun recordName(record: SubmissionEntity): String {
    val guardian = record.guardianName
    return if (record.isMinor && guardian != null) {
        stringResource(R.string.staff_record_minor, record.fullName, guardian)
    } else {
        record.fullName
    }
}

@Composable
private fun BackupMarker(backedUp: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (backedUp) colors.primaryContainer else colors.secondaryContainer,
        contentColor = if (backedUp) colors.onPrimaryContainer else colors.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            stringResource(if (backedUp) R.string.staff_record_backed_up else R.string.staff_record_not_backed_up),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
internal fun RecordsMessageBanner(message: RecordsMessage) {
    val isError = message !is RecordsMessage.Deleted
    val text = when (message) {
        is RecordsMessage.Deleted -> stringResource(R.string.staff_record_deleted, message.recordCode)
        RecordsMessage.DeleteFailed -> stringResource(R.string.staff_record_delete_failed)
    }
    StaffBanner(text, isError)
}

/** A full-width message box: neutral, or in the error colours. */
@Composable
internal fun StaffBanner(text: String, isError: Boolean, isWarning: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = when {
            isError -> colors.errorContainer
            isWarning -> colors.secondaryContainer
            else -> colors.primaryContainer
        },
        contentColor = when {
            isError -> colors.onErrorContainer
            isWarning -> colors.onSecondaryContainer
            else -> colors.onPrimaryContainer
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
    }
}
