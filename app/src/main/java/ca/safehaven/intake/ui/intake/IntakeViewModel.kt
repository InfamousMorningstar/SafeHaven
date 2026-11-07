package ca.safehaven.intake.ui.intake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.safehaven.intake.AppContainer
import ca.safehaven.intake.domain.IntakeSubmitter
import ca.safehaven.intake.domain.SubmitResult
import ca.safehaven.intake.domain.model.IntakeConfig
import ca.safehaven.intake.domain.model.IntakeInput
import ca.safehaven.intake.domain.validation.Field
import ca.safehaven.intake.domain.validation.IntakeValidator
import ca.safehaven.intake.domain.validation.NameRules
import ca.safehaven.intake.domain.validation.ValidationError
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface VisitorScreen {
    data object Welcome : VisitorScreen
    data object Form : VisitorScreen
    data class Thanks(val firstName: String) : VisitorScreen
}

data class IntakeFormState(
    val input: IntakeInput,
    val errors: Map<Field, ValidationError>,
    val touched: Set<Field> = emptySet(),
    val submitAttempted: Boolean = false,
    val isSubmitting: Boolean = false,
    val saveFailed: Boolean = false,
) {
    /** Errors appear only once a field has lost focus, or after Submit (plan 3.3). */
    fun isTouched(field: Field): Boolean = submitAttempted || field in touched

    fun visibleError(field: Field): ValidationError? = if (isTouched(field)) errors[field] else null
}

data class IntakeUiState(
    val screen: VisitorScreen,
    val form: IntakeFormState,
    /** Non-null while the "Are you still there?" countdown is showing. */
    val stillThereSeconds: Int? = null,
)

/**
 * Visitor flow: Welcome -> Form -> Thanks -> Welcome.
 *
 * Privacy: visitor data lives only in this in-memory state (never SavedStateHandle), and every
 * exit path goes through [reset], which replaces it with a fresh, empty form.
 */
class IntakeViewModel(
    private val submitter: IntakeSubmitter,
    val config: IntakeConfig,
    /** Monotonic milliseconds; replaced by virtual time in tests. */
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel() {

    private val _state = MutableStateFlow(IntakeUiState(VisitorScreen.Welcome, freshForm()))
    val state: StateFlow<IntakeUiState> = _state.asStateFlow()

    private var idleJob: Job? = null
    private var thanksJob: Job? = null
    private var lastInteractionAt = 0L

    fun start() {
        thanksJob?.cancel()
        _state.value = IntakeUiState(VisitorScreen.Form, freshForm())
        startIdleWatch()
    }

    fun updateInput(transform: (IntakeInput) -> IntakeInput) {
        _state.update { s ->
            if (s.screen != VisitorScreen.Form || s.form.isSubmitting) return@update s
            val input = transform(s.form.input)
            s.copy(
                form = s.form.copy(
                    input = input,
                    errors = IntakeValidator.validate(input, config),
                    saveFailed = false,
                ),
            )
        }
        onUserInteraction()
    }

    fun onFieldBlurred(field: Field) {
        _state.update { s ->
            if (s.screen != VisitorScreen.Form) s else s.copy(form = s.form.copy(touched = s.form.touched + field))
        }
    }

    /** Called on every touch anywhere in the app and on every keystroke. */
    fun onUserInteraction() {
        lastInteractionAt = now()
        if (_state.value.stillThereSeconds != null) {
            _state.update { it.copy(stillThereSeconds = null) }
            startIdleWatch()
        }
    }

    fun submit() {
        val current = _state.value
        if (current.screen != VisitorScreen.Form || current.form.isSubmitting) return
        if (current.form.errors.isNotEmpty()) {
            _state.update { it.copy(form = it.form.copy(submitAttempted = true)) }
            return
        }

        idleJob?.cancel()
        _state.update {
            it.copy(
                form = it.form.copy(submitAttempted = true, isSubmitting = true, saveFailed = false),
                stillThereSeconds = null,
            )
        }
        val input = current.form.input
        viewModelScope.launch {
            when (val result = submitter.submit(input)) {
                is SubmitResult.Saved -> showThanks(NameRules.firstName(input.fullName))
                is SubmitResult.Invalid -> {
                    _state.update { it.copy(form = it.form.copy(isSubmitting = false, errors = result.errors)) }
                    startIdleWatch()
                }
                is SubmitResult.Failed -> {
                    _state.update { it.copy(form = it.form.copy(isSubmitting = false, saveFailed = true)) }
                    startIdleWatch()
                }
            }
        }
    }

    /** Wipes everything and returns to the welcome screen. */
    fun reset() {
        idleJob?.cancel()
        thanksJob?.cancel()
        _state.value = IntakeUiState(VisitorScreen.Welcome, freshForm())
    }

    private fun showThanks(firstName: String) {
        idleJob?.cancel()
        // The form is cleared now; only the first name is kept, for the thank-you message.
        _state.value = IntakeUiState(VisitorScreen.Thanks(firstName), freshForm())
        thanksJob = viewModelScope.launch {
            delay(config.thanksMillis)
            reset()
        }
    }

    private fun startIdleWatch() {
        idleJob?.cancel()
        lastInteractionAt = now()
        idleJob = viewModelScope.launch {
            while (true) {
                val remaining = lastInteractionAt + config.idleTimeoutMillis - now()
                if (remaining <= 0) break
                delay(remaining)
            }
            for (seconds in config.stillThereSeconds downTo 1) {
                _state.update { it.copy(stillThereSeconds = seconds) }
                delay(1_000)
            }
            reset()
        }
    }

    private fun freshForm(): IntakeFormState {
        val input = IntakeInput(city = config.defaultCity, province = config.defaultProvince)
        return IntakeFormState(input = input, errors = IntakeValidator.validate(input, config))
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { IntakeViewModel(container.submitter, container.config) }
        }
    }
}
