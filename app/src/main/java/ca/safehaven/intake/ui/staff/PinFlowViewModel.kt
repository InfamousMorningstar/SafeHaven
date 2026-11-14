package ca.safehaven.intake.ui.staff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.safehaven.intake.AppContainer
import ca.safehaven.intake.staff.PinCheck
import ca.safehaven.intake.staff.PinStore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PinPurpose {
    /** The gate in front of the staff area (or, the first time, creating the PIN). */
    UNLOCK,

    /** Maintenance > Change PIN: current PIN, then the new one twice. */
    CHANGE,
}

enum class PinStep {
    /** Enter the existing PIN. */
    CURRENT,

    /** Choose a new PIN (first time, or after the current one was accepted when changing). */
    NEW,

    /** Enter the new PIN again. */
    CONFIRM,
}

sealed interface PinMessage {
    data class Wrong(val attemptsLeft: Int) : PinMessage
    /** The two new PINs were different. */
    data object Mismatch : PinMessage
    data object Error : PinMessage
}

data class PinUiState(
    val step: PinStep,
    /** How many digits are entered. The digits themselves never leave the ViewModel. */
    val digitCount: Int = 0,
    /** Verifying (slow PBKDF2) or saving. Input is ignored meanwhile. */
    val isBusy: Boolean = false,
    val message: PinMessage? = null,
    /** Non-null during a lockout: seconds until the next try is allowed. */
    val lockedOutSeconds: Long? = null,
    /** [PinPurpose.UNLOCK]: the staff area may open. [PinPurpose.CHANGE]: the new PIN is saved. */
    val finished: Boolean = false,
) {
    val acceptsInput: Boolean get() = !isBusy && lockedOutSeconds == null && !finished
    val canSubmit: Boolean get() = acceptsInput && digitCount >= PinStore.PIN_MIN_LENGTH
}

/**
 * The on-screen number pad's logic, for unlocking the staff area and for changing the PIN.
 * There is no default PIN: with none saved, the flow starts at [PinStep.NEW].
 */
class PinFlowViewModel(
    private val pinStore: PinStore,
    val purpose: PinPurpose,
) : ViewModel() {

    private val entered = StringBuilder()
    private var firstNewPin: String? = null
    private var countdownJob: Job? = null

    private val _state = MutableStateFlow(PinUiState(initialStep()))
    val state: StateFlow<PinUiState> = _state.asStateFlow()

    fun onDigit(digit: Char) {
        if (digit !in '0'..'9' || !_state.value.acceptsInput) return
        if (entered.length >= PinStore.PIN_MAX_LENGTH) return
        entered.append(digit)
        _state.update { it.copy(digitCount = entered.length, message = null) }
    }

    fun onBackspace() {
        if (!_state.value.acceptsInput || entered.isEmpty()) return
        entered.setLength(entered.length - 1)
        _state.update { it.copy(digitCount = entered.length) }
    }

    fun onSubmit() {
        val current = _state.value
        if (!current.canSubmit) return
        val pin = entered.toString()
        clearEntry()
        when (current.step) {
            PinStep.CURRENT -> verify(pin)
            PinStep.NEW -> {
                firstNewPin = pin
                _state.update { it.copy(step = PinStep.CONFIRM, digitCount = 0, message = null) }
            }
            PinStep.CONFIRM -> if (pin == firstNewPin) {
                save(pin)
            } else {
                firstNewPin = null
                _state.update { it.copy(step = PinStep.NEW, digitCount = 0, message = PinMessage.Mismatch) }
            }
        }
    }

    /** Starts over, e.g. each time Maintenance > Change PIN is opened. */
    fun restart() {
        countdownJob?.cancel()
        countdownJob = null
        clearEntry()
        firstNewPin = null
        _state.value = PinUiState(initialStep())
    }

    private fun verify(pin: String) {
        _state.update { it.copy(isBusy = true, digitCount = 0, message = null) }
        viewModelScope.launch {
            val result = try {
                pinStore.verify(pin)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            when (result) {
                PinCheck.Ok -> _state.update {
                    when (purpose) {
                        PinPurpose.UNLOCK -> it.copy(isBusy = false, finished = true)
                        PinPurpose.CHANGE -> it.copy(isBusy = false, step = PinStep.NEW)
                    }
                }
                is PinCheck.Wrong -> _state.update {
                    it.copy(isBusy = false, message = PinMessage.Wrong(result.attemptsLeft))
                }
                is PinCheck.LockedOut -> {
                    _state.update { it.copy(isBusy = false) }
                    startCountdown(result.secondsLeft)
                }
                PinCheck.NoPin -> _state.update { it.copy(isBusy = false, step = PinStep.NEW) }
                null -> _state.update { it.copy(isBusy = false, message = PinMessage.Error) }
            }
        }
    }

    private fun save(pin: String) {
        firstNewPin = null
        _state.update { it.copy(isBusy = true, digitCount = 0, message = null) }
        viewModelScope.launch {
            val saved = try {
                pinStore.setPin(pin)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            _state.update {
                if (saved) {
                    it.copy(isBusy = false, finished = true)
                } else {
                    it.copy(isBusy = false, step = PinStep.NEW, message = PinMessage.Error)
                }
            }
        }
    }

    private fun startCountdown(seconds: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var left = seconds
            while (left > 0) {
                _state.update { it.copy(lockedOutSeconds = left, message = null) }
                delay(1_000)
                left--
            }
            _state.update { it.copy(lockedOutSeconds = null) }
        }
    }

    private fun clearEntry() {
        entered.setLength(0)
    }

    private fun initialStep(): PinStep = if (pinStore.hasPin()) PinStep.CURRENT else PinStep.NEW

    override fun onCleared() {
        clearEntry()
        firstNewPin = null
    }

    companion object {
        fun factory(container: AppContainer, purpose: PinPurpose): ViewModelProvider.Factory = viewModelFactory {
            initializer { PinFlowViewModel(container.pinStore, purpose) }
        }
    }
}
