package ca.safehaven.intake.ui.intake

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ca.safehaven.intake.R
import ca.safehaven.intake.domain.model.IntakeConfig
import ca.safehaven.intake.domain.model.IntakeInput
import ca.safehaven.intake.domain.model.Province
import ca.safehaven.intake.domain.model.VisitReason
import ca.safehaven.intake.domain.validation.Field
import ca.safehaven.intake.domain.validation.IntakeValidator
import ca.safehaven.intake.domain.validation.NameRules
import ca.safehaven.intake.domain.validation.PhoneRules
import ca.safehaven.intake.domain.validation.PostalCodeRules
import ca.safehaven.intake.domain.validation.TextRules
import ca.safehaven.intake.domain.validation.ValidationError
import ca.safehaven.intake.labelRes
import ca.safehaven.intake.ui.InsertingVisualTransformation
import ca.safehaven.intake.ui.theme.SafeHavenTheme

private val TWO_COLUMN_MIN_WIDTH = 840.dp
private val FIELD_MIN_HEIGHT = 64.dp
private val BUTTON_MIN_HEIGHT = 72.dp

typealias InputChange = ((IntakeInput) -> IntakeInput) -> Unit

@Composable
fun IntakeFormScreen(
    form: IntakeFormState,
    config: IntakeConfig,
    shelterName: String,
    onInputChange: InputChange,
    onFieldBlurred: (Field) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.form_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.form_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (form.saveFailed) {
            ErrorBanner(R.string.form_save_failed)
        } else if (form.submitAttempted && form.errors.isNotEmpty() && !form.isSubmitting) {
            ErrorBanner(R.string.form_fix_errors)
        }

        // Two columns in landscape on a tablet, one column otherwise (plan 3.2).
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= TWO_COLUMN_MIN_WIDTH) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        PersonFields(form, config, onInputChange, onFieldBlurred)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AddressFields(form, onInputChange, onFieldBlurred)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PersonFields(form, config, onInputChange, onFieldBlurred)
                    AddressFields(form, onInputChange, onFieldBlurred)
                }
            }
        }

        if (config.consentRequired) {
            ConsentRow(
                checked = form.input.consentGiven,
                enabled = !form.isSubmitting,
                notice = stringResource(R.string.consent_notice, shelterName),
                error = form.visibleError(Field.CONSENT),
                onChange = { checked -> onInputChange { it.copy(consentGiven = checked) } },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
        ) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !form.isSubmitting,
                modifier = Modifier.heightIn(min = BUTTON_MIN_HEIGHT).widthIn(min = 160.dp),
            ) { Text(stringResource(R.string.form_cancel)) }

            Button(
                onClick = onSubmit,
                enabled = !form.isSubmitting,
                modifier = Modifier.heightIn(min = BUTTON_MIN_HEIGHT).widthIn(min = 240.dp),
            ) {
                if (form.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.form_submit))
                }
            }
        }
    }
}

@Composable
private fun PersonFields(
    form: IntakeFormState,
    config: IntakeConfig,
    onInputChange: InputChange,
    onFieldBlurred: (Field) -> Unit,
) {
    val input = form.input
    IntakeTextField(
        value = input.fullName,
        onValueChange = { v -> onInputChange { it.copy(fullName = v.take(NameRules.MAX_LENGTH)) } },
        label = R.string.field_full_name,
        field = Field.FULL_NAME,
        form = form,
        onFieldBlurred = onFieldBlurred,
        keyboardOptions = textKeyboard(KeyboardCapitalization.Words),
    )
    IntakeTextField(
        value = input.phone,
        onValueChange = { v -> onInputChange { it.copy(phone = PhoneRules.sanitizeInput(v)) } },
        label = R.string.field_phone,
        field = Field.PHONE,
        form = form,
        onFieldBlurred = onFieldBlurred,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Next,
        ),
        visualTransformation = InsertingVisualTransformation.Phone,
    )
    if (config.askReason) {
        ReasonChips(
            selected = input.reason,
            enabled = !form.isSubmitting,
            onSelect = { reason -> onInputChange { it.copy(reason = if (it.reason == reason) null else reason) } },
        )
    }
}

@Composable
private fun AddressFields(
    form: IntakeFormState,
    onInputChange: InputChange,
    onFieldBlurred: (Field) -> Unit,
) {
    val input = form.input
    IntakeTextField(
        value = input.street,
        onValueChange = { v -> onInputChange { it.copy(street = v.take(IntakeValidator.STREET_MAX)) } },
        label = R.string.field_street,
        field = Field.STREET,
        form = form,
        onFieldBlurred = onFieldBlurred,
        keyboardOptions = textKeyboard(KeyboardCapitalization.Words),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IntakeTextField(
            value = input.unit,
            onValueChange = { v -> onInputChange { it.copy(unit = v.take(IntakeValidator.UNIT_MAX)) } },
            label = R.string.field_unit,
            optional = true,
            field = Field.UNIT,
            form = form,
            onFieldBlurred = onFieldBlurred,
            keyboardOptions = textKeyboard(KeyboardCapitalization.Characters),
            modifier = Modifier.weight(1f),
        )
        IntakeTextField(
            value = input.city,
            onValueChange = { v -> onInputChange { it.copy(city = v.take(IntakeValidator.CITY_MAX)) } },
            label = R.string.field_city,
            field = Field.CITY,
            form = form,
            onFieldBlurred = onFieldBlurred,
            keyboardOptions = textKeyboard(KeyboardCapitalization.Words),
            modifier = Modifier.weight(1.5f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ProvinceField(
            selected = input.province,
            enabled = !form.isSubmitting,
            onSelect = { province -> onInputChange { it.copy(province = province) } },
            modifier = Modifier.weight(1.5f),
        )
        val postalWarning =
            if (form.isTouched(Field.POSTAL_CODE) &&
                form.errors[Field.POSTAL_CODE] == null &&
                !PostalCodeRules.matchesProvince(input.postalCode, input.province)
            ) {
                stringResource(R.string.warn_postal_province, input.province.displayName)
            } else {
                null
            }
        IntakeTextField(
            value = input.postalCode,
            onValueChange = { v -> onInputChange { it.copy(postalCode = PostalCodeRules.sanitizeInput(v)) } },
            label = R.string.field_postal_code,
            field = Field.POSTAL_CODE,
            form = form,
            onFieldBlurred = onFieldBlurred,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            visualTransformation = InsertingVisualTransformation.PostalCode,
            warning = postalWarning,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun textKeyboard(capitalization: KeyboardCapitalization) = KeyboardOptions(
    capitalization = capitalization,
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Text,
    imeAction = ImeAction.Next,
)

@Composable
private fun IntakeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    field: Field,
    form: IntakeFormState,
    onFieldBlurred: (Field) -> Unit,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    optional: Boolean = false,
    warning: String? = null,
) {
    val error = form.visibleError(field)
    var hadFocus by remember { mutableStateOf(false) }
    val labelText = if (optional) {
        stringResource(R.string.field_optional, stringResource(label))
    } else {
        stringResource(label)
    }
    val supporting = error?.let { errorMessage(field, it) } ?: warning

    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(TextRules.singleLine(it)) },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FIELD_MIN_HEIGHT)
            .onFocusChanged { focus ->
                if (focus.isFocused) {
                    hadFocus = true
                } else if (hadFocus) {
                    hadFocus = false
                    onFieldBlurred(field)
                }
            },
        enabled = !form.isSubmitting,
        label = { Text(labelText) },
        isError = error != null,
        supportingText = if (supporting != null) {
            { Text(supporting) }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun ProvinceField(
    selected: Province,
    enabled: Boolean,
    onSelect: (Province) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = "${selected.name} – ${selected.displayName}",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(stringResource(R.string.field_province)) },
            trailingIcon = { Text("▾") },
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().heightIn(min = FIELD_MIN_HEIGHT),
        )
        // The read-only field swallows taps, so a transparent layer on top opens the menu.
        Box(Modifier.matchParentSize().clickable(enabled = enabled, role = Role.DropdownList) { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Province.entries.forEach { province ->
                DropdownMenuItem(
                    text = { Text("${province.name} – ${province.displayName}", style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onSelect(province)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ReasonChips(selected: VisitReason?, enabled: Boolean, onSelect: (VisitReason) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.field_reason), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VisitReason.entries.forEach { reason ->
                FilterChip(
                    selected = selected == reason,
                    onClick = { onSelect(reason) },
                    enabled = enabled,
                    label = { Text(stringResource(reason.labelRes()), style = MaterialTheme.typography.bodyLarge) },
                    modifier = Modifier.heightIn(min = 56.dp),
                )
            }
        }
    }
}

@Composable
private fun ConsentRow(
    checked: Boolean,
    enabled: Boolean,
    notice: String,
    error: ValidationError?,
    onChange: (Boolean) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(notice, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.consent_agree),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (error != null) {
            Text(
                errorMessage(Field.CONSENT, error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

@Composable
private fun ErrorBanner(@StringRes message: Int) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            stringResource(message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun errorMessage(field: Field, error: ValidationError): String = stringResource(
    when (error) {
        ValidationError.REQUIRED -> when (field) {
            Field.FULL_NAME -> R.string.err_name_required
            Field.PHONE -> R.string.err_phone_required
            Field.STREET -> R.string.err_street_required
            Field.CITY -> R.string.err_city_required
            Field.POSTAL_CODE -> R.string.err_postal_required
            Field.CONSENT -> R.string.err_consent
            Field.UNIT -> R.string.err_required
        }
        ValidationError.TOO_SHORT -> when (field) {
            Field.FULL_NAME -> R.string.err_name_short
            Field.STREET -> R.string.err_street_short
            else -> R.string.err_invalid
        }
        ValidationError.TOO_LONG -> R.string.err_too_long
        ValidationError.INVALID -> when (field) {
            Field.FULL_NAME -> R.string.err_name_invalid
            Field.PHONE -> R.string.err_phone_invalid
            Field.POSTAL_CODE -> R.string.err_postal_invalid
            else -> R.string.err_invalid
        }
    },
)

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun IntakeFormPreview() {
    val config = IntakeConfig()
    val input = IntakeInput(fullName = "Jane Smith", phone = "40355", city = "Calgary")
    SafeHavenTheme {
        IntakeFormScreen(
            form = IntakeFormState(
                input = input,
                errors = IntakeValidator.validate(input, config),
                submitAttempted = true,
            ),
            config = config,
            shelterName = "Calgary Cat Shelter",
            onInputChange = {},
            onFieldBlurred = {},
            onSubmit = {},
            onCancel = {},
        )
    }
}
