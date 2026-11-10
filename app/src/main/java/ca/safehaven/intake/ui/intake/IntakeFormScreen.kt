package ca.safehaven.intake.ui.intake

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ca.safehaven.intake.R
import ca.safehaven.intake.domain.model.IntakeConfig
import ca.safehaven.intake.domain.model.IntakeInput
import ca.safehaven.intake.domain.model.Signature
import ca.safehaven.intake.domain.model.UsState
import ca.safehaven.intake.domain.model.VisitReason
import ca.safehaven.intake.domain.model.Waiver
import ca.safehaven.intake.domain.model.WaiverClause
import ca.safehaven.intake.domain.validation.DateOfBirthRules
import ca.safehaven.intake.domain.validation.EmailRules
import ca.safehaven.intake.domain.validation.Field
import ca.safehaven.intake.domain.validation.InitialsRules
import ca.safehaven.intake.domain.validation.IntakeValidator
import ca.safehaven.intake.domain.validation.NameRules
import ca.safehaven.intake.domain.validation.PhoneRules
import ca.safehaven.intake.domain.validation.ValidationError
import ca.safehaven.intake.domain.validation.ZipRules
import ca.safehaven.intake.domain.validation.TextRules
import ca.safehaven.intake.domain.validation.initialsField
import ca.safehaven.intake.labelRes
import ca.safehaven.intake.ui.InsertingVisualTransformation
import ca.safehaven.intake.ui.theme.SafeHavenTheme
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val CONTENT_MAX_WIDTH = 960.dp
private val FIELD_MIN_HEIGHT = 64.dp
private val BUTTON_MIN_HEIGHT = 72.dp
private val INITIALS_WIDTH = 150.dp
private val LOGO_HEIGHT = 80.dp

private val SIGNED_ON_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)

/** Fields in the order they appear on screen, so Submit can scroll to the first one with an error. */
private val SCREEN_ORDER: List<Field> =
    listOf(
        Field.FULL_NAME, Field.STREET, Field.CITY, Field.ZIP, Field.EMAIL, Field.PHONE,
        Field.REASON, Field.HEARD_ABOUT,
    ) + WaiverClause.entries.map { it.initialsField } +
        listOf(Field.DATE_OF_BIRTH, Field.GUARDIAN_NAME, Field.SIGNATURE)

/** One [BringIntoViewRequester] per field, provided by [IntakeFormScreen]. */
private val LocalFieldRequesters = staticCompositionLocalOf<Map<Field, BringIntoViewRequester>> { emptyMap() }

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
    val requesters = remember { Field.entries.associateWith { BringIntoViewRequester() } }
    val scope = rememberCoroutineScope()
    val submitAndShowFirstError: () -> Unit = {
        onSubmit()
        // The errors are always up to date (validated on every change), so the first one is known now.
        SCREEN_ORDER.firstOrNull { it in form.errors }?.let { field ->
            scope.launch { requesters[field]?.bringIntoView() }
        }
    }

    CompositionLocalProvider(LocalFieldRequesters provides requesters) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Header(shelterName)
                Text(
                    stringResource(R.string.form_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                SectionTitle(R.string.section_contact)
                ContactFields(form, onInputChange, onFieldBlurred)

                SectionTitle(R.string.section_visit)
                VisitFields(form, onInputChange, onFieldBlurred)

                SectionTitle(R.string.section_waiver)
                WaiverText()
                WaiverClause.entries.forEach { clause ->
                    ClauseRow(clause, form, onInputChange, onFieldBlurred)
                }

                SectionTitle(R.string.section_sign)
                SignFields(form, config, onInputChange, onFieldBlurred)

                if (form.saveFailed) {
                    ErrorBanner(R.string.form_save_failed)
                } else if (form.submitAttempted && form.errors.isNotEmpty() && !form.isSubmitting) {
                    ErrorBanner(R.string.form_fix_errors)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !form.isSubmitting,
                        modifier = Modifier.heightIn(min = BUTTON_MIN_HEIGHT).widthIn(min = 160.dp),
                    ) { Text(stringResource(R.string.form_cancel)) }

                    Button(
                        onClick = submitAndShowFirstError,
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
    }
}

@Composable
private fun Header(shelterName: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.shfa_logo),
            contentDescription = stringResource(R.string.shelter_logo_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(LOGO_HEIGHT),
        )
        Spacer(Modifier.width(24.dp))
        Column {
            Text(shelterName, style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.form_title), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        HorizontalDivider()
    }
}

@Composable
private fun ContactFields(
    form: IntakeFormState,
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
            value = input.city,
            onValueChange = { v -> onInputChange { it.copy(city = v.take(IntakeValidator.CITY_MAX)) } },
            label = R.string.field_city,
            field = Field.CITY,
            form = form,
            onFieldBlurred = onFieldBlurred,
            keyboardOptions = textKeyboard(KeyboardCapitalization.Words),
            modifier = Modifier.weight(2f),
        )
        StateField(
            selected = input.state,
            enabled = !form.isSubmitting,
            onSelect = { state -> onInputChange { it.copy(state = state) } },
            modifier = Modifier.weight(1f),
        )
        IntakeTextField(
            value = input.zip,
            onValueChange = { v -> onInputChange { it.copy(zip = ZipRules.sanitizeInput(v)) } },
            label = R.string.field_zip,
            field = Field.ZIP,
            form = form,
            onFieldBlurred = onFieldBlurred,
            keyboardOptions = numberKeyboard(),
            visualTransformation = InsertingVisualTransformation.Zip,
            modifier = Modifier.weight(1.3f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IntakeTextField(
            value = input.email,
            onValueChange = { v -> onInputChange { it.copy(email = EmailRules.sanitizeInput(v).take(EmailRules.MAX_LENGTH)) } },
            label = R.string.field_email,
            optional = true,
            field = Field.EMAIL,
            form = form,
            onFieldBlurred = onFieldBlurred,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.weight(1.5f),
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
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun VisitFields(
    form: IntakeFormState,
    onInputChange: InputChange,
    onFieldBlurred: (Field) -> Unit,
) {
    ReasonChips(
        selected = form.input.reason,
        enabled = !form.isSubmitting,
        error = form.visibleError(Field.REASON),
        onSelect = { reason ->
            // Required, so a second tap doesn't clear it; it only marks the field as answered.
            onInputChange { it.copy(reason = reason) }
            onFieldBlurred(Field.REASON)
        },
    )
    IntakeTextField(
        value = form.input.heardAbout,
        onValueChange = { v -> onInputChange { it.copy(heardAbout = v.take(IntakeValidator.HEARD_ABOUT_MAX)) } },
        label = R.string.field_heard_about,
        optional = true,
        field = Field.HEARD_ABOUT,
        form = form,
        onFieldBlurred = onFieldBlurred,
        keyboardOptions = textKeyboard(KeyboardCapitalization.Sentences),
    )
}

/** The waiver's legal wording, from [Waiver], in the paper form's order. */
@Composable
private fun WaiverText() {
    val body = MaterialTheme.typography.bodyMedium
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(Waiver.WARNING, style = body, fontWeight = FontWeight.Bold)
            Text(Waiver.READ_CAREFULLY, style = body, fontWeight = FontWeight.Bold)
            Text(Waiver.ACKNOWLEDGEMENT, style = body)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Waiver.RISKS.forEach { risk ->
                    Row {
                        Text("•", style = body, modifier = Modifier.padding(start = 8.dp, end = 12.dp))
                        Text(risk, style = body)
                    }
                }
            }
            Text(Waiver.RELEASE, style = body)
            Text(Waiver.INITIALS_INTRO, style = body)
        }
    }
}

@Composable
private fun ClauseRow(
    clause: WaiverClause,
    form: IntakeFormState,
    onInputChange: InputChange,
    onFieldBlurred: (Field) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Clause on the left, initials on the right, like the paper form's "Initials" column.
        Text(
            clause.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(top = 8.dp),
        )
        IntakeTextField(
            value = form.input.initials[clause].orEmpty(),
            onValueChange = { v ->
                onInputChange { it.copy(initials = it.initials + (clause to InitialsRules.sanitizeInput(v))) }
            },
            label = R.string.field_initials,
            field = clause.initialsField,
            form = form,
            onFieldBlurred = onFieldBlurred,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.width(INITIALS_WIDTH),
        )
    }
}

@Composable
private fun SignFields(
    form: IntakeFormState,
    config: IntakeConfig,
    onInputChange: InputChange,
    onFieldBlurred: (Field) -> Unit,
) {
    IntakeTextField(
        value = form.input.dateOfBirth,
        onValueChange = { v -> onInputChange { it.copy(dateOfBirth = DateOfBirthRules.sanitizeInput(v)) } },
        label = R.string.field_date_of_birth,
        field = Field.DATE_OF_BIRTH,
        form = form,
        onFieldBlurred = onFieldBlurred,
        keyboardOptions = numberKeyboard(),
        visualTransformation = InsertingVisualTransformation.DateOfBirth,
        modifier = Modifier.widthIn(max = 420.dp),
    )

    if (form.isMinor) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(R.string.guardian_notice),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(16.dp),
            )
        }
        IntakeTextField(
            value = form.input.guardianName,
            onValueChange = { v -> onInputChange { it.copy(guardianName = v.take(NameRules.MAX_LENGTH)) } },
            label = R.string.field_guardian_name,
            field = Field.GUARDIAN_NAME,
            form = form,
            onFieldBlurred = onFieldBlurred,
            keyboardOptions = textKeyboard(KeyboardCapitalization.Words, ImeAction.Done),
        )
    }

    SignatureBlock(form, config, onInputChange)
}

@Composable
private fun SignatureBlock(form: IntakeFormState, config: IntakeConfig, onInputChange: InputChange) {
    val error = form.visibleError(Field.SIGNATURE)
    // Display only; the record's signing date is set when it is submitted.
    val today = SIGNED_ON_FORMAT.format(LocalDate.now(ZoneId.of(config.timeZoneId)))

    Column(
        modifier = Modifier.fillMaxWidth().fieldTarget(Field.SIGNATURE),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(if (form.isMinor) R.string.signature_label_guardian else R.string.signature_label),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            Waiver.SIGNATURE_NOTE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
            SignaturePad(
                signature = form.input.signature,
                onSignatureChange = { signature -> onInputChange { it.copy(signature = signature) } },
                enabled = !form.isSubmitting,
                isError = error != null,
                modifier = Modifier.weight(1f),
            )
            Column(Modifier.width(180.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.signature_date),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(today, style = MaterialTheme.typography.bodyLarge)
                HorizontalDivider()
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (error != null) {
                    Text(
                        errorMessage(Field.SIGNATURE, error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(
                onClick = { onInputChange { it.copy(signature = Signature.EMPTY) } },
                enabled = !form.isSubmitting && form.input.signature.strokes.isNotEmpty(),
                modifier = Modifier.heightIn(min = 56.dp),
            ) { Text(stringResource(R.string.signature_clear)) }
        }
    }
}

private fun textKeyboard(capitalization: KeyboardCapitalization, imeAction: ImeAction = ImeAction.Next) =
    KeyboardOptions(
        capitalization = capitalization,
        autoCorrectEnabled = false,
        keyboardType = KeyboardType.Text,
        imeAction = imeAction,
    )

private fun numberKeyboard() = KeyboardOptions(
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Number,
    imeAction = ImeAction.Next,
)

/** Lets Submit scroll this element into view when [field] is the first with an error. */
@Composable
private fun Modifier.fieldTarget(field: Field): Modifier {
    val requester = LocalFieldRequesters.current[field] ?: return this
    return this.bringIntoViewRequester(requester)
}

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
) {
    val error = form.visibleError(field)
    var hadFocus by remember { mutableStateOf(false) }
    val labelText = if (optional) {
        stringResource(R.string.field_optional, stringResource(label))
    } else {
        stringResource(label)
    }

    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(TextRules.singleLine(it)) },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = FIELD_MIN_HEIGHT)
            .fieldTarget(field)
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
        supportingText = if (error != null) {
            { Text(errorMessage(field, error)) }
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
private fun StateField(
    selected: UsState,
    enabled: Boolean,
    onSelect: (UsState) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            // The code only: the full name doesn't fit beside City and ZIP.
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(stringResource(R.string.field_state)) },
            trailingIcon = { Text("▾") },
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().heightIn(min = FIELD_MIN_HEIGHT),
        )
        // The read-only field swallows taps, so a transparent layer on top opens the menu.
        Box(Modifier.matchParentSize().clickable(enabled = enabled, role = Role.DropdownList) { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 480.dp),
        ) {
            UsState.entries.forEach { state ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.state_option, state.name, state.displayName),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    onClick = {
                        onSelect(state)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ReasonChips(
    selected: VisitReason?,
    enabled: Boolean,
    error: ValidationError?,
    onSelect: (VisitReason) -> Unit,
) {
    Column(
        modifier = Modifier.fieldTarget(Field.REASON),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.field_reason),
            style = MaterialTheme.typography.titleMedium,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
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
        if (error != null) {
            Text(
                errorMessage(Field.REASON, error),
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
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun errorMessage(field: Field, error: ValidationError): String = stringResource(
    when (error) {
        ValidationError.REQUIRED -> when (field) {
            Field.FULL_NAME -> R.string.err_name_required
            Field.STREET -> R.string.err_street_required
            Field.CITY -> R.string.err_city_required
            Field.ZIP -> R.string.err_zip_required
            Field.PHONE -> R.string.err_phone_required
            Field.REASON -> R.string.err_reason_required
            Field.DATE_OF_BIRTH -> R.string.err_dob_required
            Field.GUARDIAN_NAME -> R.string.err_guardian_required
            Field.SIGNATURE -> R.string.err_signature_required
            Field.INITIALS_NO_MEDICATION, Field.INITIALS_GOOD_HEALTH,
            Field.INITIALS_NO_COMMUNICABLE_DISEASE, Field.INITIALS_INDEMNIFY,
            Field.INITIALS_NO_NURSE, Field.INITIALS_FACTS_TRUE,
            -> R.string.err_initials_required
            Field.EMAIL, Field.HEARD_ABOUT -> R.string.err_required
        }
        ValidationError.TOO_SHORT -> when (field) {
            Field.FULL_NAME, Field.GUARDIAN_NAME -> R.string.err_name_short
            Field.STREET -> R.string.err_street_short
            Field.CITY -> R.string.err_city_short
            Field.SIGNATURE -> R.string.err_signature_short
            else -> R.string.err_invalid
        }
        ValidationError.TOO_LONG -> R.string.err_too_long
        ValidationError.INVALID -> when (field) {
            Field.FULL_NAME, Field.GUARDIAN_NAME -> R.string.err_name_invalid
            Field.ZIP -> R.string.err_zip_invalid
            Field.EMAIL -> R.string.err_email_invalid
            Field.PHONE -> R.string.err_phone_invalid
            Field.DATE_OF_BIRTH -> R.string.err_dob_invalid
            else -> R.string.err_invalid
        }
    },
)

@Preview(widthDp = 960, heightDp = 600)
@Composable
private fun IntakeFormPreview() {
    val config = IntakeConfig()
    val today = LocalDate.now(ZoneId.of(config.timeZoneId))
    val input = IntakeInput(fullName = "Jane Smith", phone = "60255", city = "Phoenix", dateOfBirth = "0315")
    SafeHavenTheme {
        IntakeFormScreen(
            form = IntakeFormState(
                input = input,
                errors = IntakeValidator.validate(input, config, today),
                submitAttempted = true,
            ),
            config = config,
            shelterName = "Safe Haven for Animals",
            onInputChange = {},
            onFieldBlurred = {},
            onSubmit = {},
            onCancel = {},
        )
    }
}
