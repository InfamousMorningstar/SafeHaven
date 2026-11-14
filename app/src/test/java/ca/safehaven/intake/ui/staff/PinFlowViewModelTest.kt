package ca.safehaven.intake.ui.staff

import ca.safehaven.intake.staff.PinStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PinFlowViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun PinFlowViewModel.type(pin: String) = pin.forEach { onDigit(it) }

    private fun PinFlowViewModel.enter(pin: String) {
        type(pin)
        onSubmit()
    }

    @Test fun firstTimeCreatesPin() = runTest(dispatcher) {
        val store = FakePinStore(pin = null)
        val vm = PinFlowViewModel(store, PinPurpose.UNLOCK)
        assertEquals(PinStep.NEW, vm.state.value.step)

        vm.enter("2468")
        assertEquals(PinStep.CONFIRM, vm.state.value.step)
        assertEquals(0, vm.state.value.digitCount)

        vm.enter("2468")
        runCurrent()
        assertTrue(vm.state.value.finished)
        assertEquals("2468", store.pin)
    }

    @Test fun createWithMismatchStartsOver() = runTest(dispatcher) {
        val store = FakePinStore(pin = null)
        val vm = PinFlowViewModel(store, PinPurpose.UNLOCK)
        vm.enter("2468")
        vm.enter("1357")
        runCurrent()
        assertEquals(PinStep.NEW, vm.state.value.step)
        assertEquals(PinMessage.Mismatch, vm.state.value.message)
        assertFalse(vm.state.value.finished)
        assertNull(store.pin)
    }

    @Test fun failedSaveShowsErrorAndStartsOver() = runTest(dispatcher) {
        val store = FakePinStore(pin = null).apply { failNextSave = true }
        val vm = PinFlowViewModel(store, PinPurpose.UNLOCK)
        vm.enter("2468")
        vm.enter("2468")
        runCurrent()
        assertEquals(PinStep.NEW, vm.state.value.step)
        assertEquals(PinMessage.Error, vm.state.value.message)
        assertFalse(vm.state.value.finished)
    }

    @Test fun pinShorterThanMinimumCannotBeSubmitted() = runTest(dispatcher) {
        val vm = PinFlowViewModel(FakePinStore(pin = null), PinPurpose.UNLOCK)
        vm.type("123")
        assertFalse(vm.state.value.canSubmit)
        vm.onSubmit()
        assertEquals(PinStep.NEW, vm.state.value.step)
        assertEquals(3, vm.state.value.digitCount)
    }

    @Test fun entryStopsAtMaximumLength() = runTest(dispatcher) {
        val vm = PinFlowViewModel(FakePinStore(pin = "1234"), PinPurpose.UNLOCK)
        vm.type("1234567890")
        assertEquals(PinStore.PIN_MAX_LENGTH, vm.state.value.digitCount)
        vm.onBackspace()
        assertEquals(PinStore.PIN_MAX_LENGTH - 1, vm.state.value.digitCount)
    }

    @Test fun correctPinUnlocks() = runTest(dispatcher) {
        val vm = PinFlowViewModel(FakePinStore(pin = "2468"), PinPurpose.UNLOCK)
        assertEquals(PinStep.CURRENT, vm.state.value.step)
        vm.enter("2468")
        runCurrent()
        assertTrue(vm.state.value.finished)
    }

    @Test fun wrongPinShowsTriesLeft() = runTest(dispatcher) {
        val vm = PinFlowViewModel(FakePinStore(pin = "2468"), PinPurpose.UNLOCK)
        vm.enter("1111")
        runCurrent()
        assertEquals(PinMessage.Wrong(PinStore.MAX_ATTEMPTS - 1), vm.state.value.message)
        assertEquals(0, vm.state.value.digitCount)
        assertFalse(vm.state.value.finished)
    }

    @Test fun inputIsIgnoredWhileVerifying() = runTest(dispatcher) {
        val store = FakePinStore(pin = "2468", verifyMillis = 2_000)
        val vm = PinFlowViewModel(store, PinPurpose.UNLOCK)
        vm.enter("2468")
        runCurrent()
        assertTrue(vm.state.value.isBusy)

        vm.type("99")
        vm.onSubmit()
        assertEquals(0, vm.state.value.digitCount)

        advanceTimeBy(2_001)
        assertFalse(vm.state.value.isBusy)
        assertTrue(vm.state.value.finished)
        assertEquals(listOf("2468"), store.verified)
    }

    @Test fun lockoutShowsCountdownAndBlocksEntry() = runTest(dispatcher) {
        val store = FakePinStore(pin = "2468", lockoutSeconds = 30)
        val vm = PinFlowViewModel(store, PinPurpose.UNLOCK)
        repeat(PinStore.MAX_ATTEMPTS) {
            vm.enter("1111")
            runCurrent()
        }
        assertEquals(30L, vm.state.value.lockedOutSeconds)
        assertFalse(vm.state.value.acceptsInput)

        vm.type("2468")
        assertEquals(0, vm.state.value.digitCount)

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(20L, vm.state.value.lockedOutSeconds)

        advanceTimeBy(20_001)
        assertNull(vm.state.value.lockedOutSeconds)
        assertTrue(vm.state.value.acceptsInput)
    }

    @Test fun noPinFromVerifySwitchesToCreate() = runTest(dispatcher) {
        val store = FakePinStore(pin = "2468")
        val vm = PinFlowViewModel(store, PinPurpose.UNLOCK)
        store.pin = null // e.g. wiped since the screen opened
        vm.enter("2468")
        runCurrent()
        assertEquals(PinStep.NEW, vm.state.value.step)
    }

    @Test fun changePinNeedsCurrentThenNewTwice() = runTest(dispatcher) {
        val store = FakePinStore(pin = "2468")
        val vm = PinFlowViewModel(store, PinPurpose.CHANGE)
        assertEquals(PinStep.CURRENT, vm.state.value.step)

        vm.enter("1111")
        runCurrent()
        assertEquals(PinStep.CURRENT, vm.state.value.step)
        assertEquals(PinMessage.Wrong(PinStore.MAX_ATTEMPTS - 1), vm.state.value.message)

        vm.enter("2468")
        runCurrent()
        assertEquals(PinStep.NEW, vm.state.value.step)
        assertFalse(vm.state.value.finished)

        vm.enter("97531")
        vm.enter("97531")
        runCurrent()
        assertTrue(vm.state.value.finished)
        assertEquals("97531", store.pin)
    }

    @Test fun restartGoesBackToTheFirstStep() = runTest(dispatcher) {
        val store = FakePinStore(pin = "2468")
        val vm = PinFlowViewModel(store, PinPurpose.CHANGE)
        vm.enter("2468")
        runCurrent()
        vm.type("12")
        vm.restart()
        assertEquals(PinUiState(PinStep.CURRENT), vm.state.value)
    }
}
