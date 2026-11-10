package ca.safehaven.intake.ui.intake

import ca.safehaven.intake.TestFixtures
import ca.safehaven.intake.domain.IntakeSubmitter
import ca.safehaven.intake.domain.SubmitResult
import ca.safehaven.intake.domain.model.IntakeConfig
import ca.safehaven.intake.domain.model.IntakeInput
import ca.safehaven.intake.domain.model.UsState
import ca.safehaven.intake.domain.validation.Field
import ca.safehaven.intake.domain.validation.ValidationError
import java.io.IOException
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
class IntakeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val config = IntakeConfig()

    private val valid = TestFixtures.VALID_ADULT

    private class FakeSubmitter(var result: SubmitResult = SubmitResult.Saved("0001")) : IntakeSubmitter {
        val received = mutableListOf<IntakeInput>()
        override suspend fun submit(input: IntakeInput): SubmitResult {
            received += input
            return result
        }
    }

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(submitter: IntakeSubmitter = FakeSubmitter()) =
        IntakeViewModel(
            submitter,
            config,
            now = { dispatcher.scheduler.currentTime },
            today = { TestFixtures.TODAY },
        )

    @Test fun submitShowsThanksThenReturnsToEmptyWelcome() = runTest(dispatcher) {
        val submitter = FakeSubmitter()
        val vm = viewModel(submitter)
        vm.start()
        vm.updateInput { valid }
        vm.submit()
        runCurrent()

        assertEquals(VisitorScreen.Thanks("Jane"), vm.state.value.screen)
        assertEquals(listOf(valid), submitter.received)
        // The visitor's details are already gone; only the first name is on screen.
        assertEquals("", vm.state.value.form.input.fullName)

        advanceTimeBy(config.thanksMillis + 1)
        assertEquals(VisitorScreen.Welcome, vm.state.value.screen)
        assertEquals(IntakeInput(city = config.defaultCity, state = UsState.AZ), vm.state.value.form.input)
    }

    @Test fun dateOfBirthUnder18SwitchesToGuardian() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start()
        assertFalse(vm.state.value.form.isMinor)

        vm.updateInput { it.copy(dateOfBirth = TestFixtures.VALID_MINOR.dateOfBirth) }
        assertTrue(vm.state.value.form.isMinor)
        assertEquals(ValidationError.REQUIRED, vm.state.value.form.errors[Field.GUARDIAN_NAME])

        vm.updateInput { it.copy(dateOfBirth = TestFixtures.VALID_ADULT.dateOfBirth) }
        assertFalse(vm.state.value.form.isMinor)
        assertNull(vm.state.value.form.errors[Field.GUARDIAN_NAME])
    }

    @Test fun invalidFormShowsErrorsAndDoesNotSubmit() = runTest(dispatcher) {
        val submitter = FakeSubmitter()
        val vm = viewModel(submitter)
        vm.start()
        assertNull(vm.state.value.form.visibleError(Field.FULL_NAME)) // nothing shown before blur/submit

        vm.submit()
        runCurrent()

        assertEquals(VisitorScreen.Form, vm.state.value.screen)
        assertTrue(submitter.received.isEmpty())
        assertEquals(ValidationError.REQUIRED, vm.state.value.form.visibleError(Field.FULL_NAME))
    }

    @Test fun errorAppearsAfterFieldLosesFocus() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start()
        vm.updateInput { it.copy(phone = "602") }
        assertNull(vm.state.value.form.visibleError(Field.PHONE))
        vm.onFieldBlurred(Field.PHONE)
        assertEquals(ValidationError.INVALID, vm.state.value.form.visibleError(Field.PHONE))
    }

    @Test fun idleFormAsksThenClears() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start()
        vm.updateInput { it.copy(fullName = "Jane") }

        advanceTimeBy(config.idleTimeoutMillis + 1)
        assertEquals(config.stillThereSeconds, vm.state.value.stillThereSeconds)
        assertEquals("Jane", vm.state.value.form.input.fullName)

        advanceTimeBy(config.stillThereSeconds * 1_000L)
        assertEquals(VisitorScreen.Welcome, vm.state.value.screen)
        assertEquals("", vm.state.value.form.input.fullName)
        assertNull(vm.state.value.stillThereSeconds)
    }

    @Test fun interactionDuringCountdownKeepsTheForm() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start()
        vm.updateInput { it.copy(fullName = "Jane") }
        advanceTimeBy(config.idleTimeoutMillis + 1)

        vm.onUserInteraction()
        advanceTimeBy(30_000)

        assertEquals(VisitorScreen.Form, vm.state.value.screen)
        assertNull(vm.state.value.stillThereSeconds)
        assertEquals("Jane", vm.state.value.form.input.fullName)
    }

    @Test fun typingKeepsPushingTheTimeoutBack() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start()
        repeat(5) {
            advanceTimeBy(60_000)
            vm.updateInput { it.copy(fullName = it.fullName + "a") }
        }
        assertEquals(VisitorScreen.Form, vm.state.value.screen)
        assertNull(vm.state.value.stillThereSeconds)
    }

    @Test fun failedSaveKeepsTheFormAndShowsMessage() = runTest(dispatcher) {
        val vm = viewModel(FakeSubmitter(SubmitResult.Failed(IOException("disk full"))))
        vm.start()
        vm.updateInput { valid }
        vm.submit()
        runCurrent()

        assertEquals(VisitorScreen.Form, vm.state.value.screen)
        assertTrue(vm.state.value.form.saveFailed)
        assertEquals(valid, vm.state.value.form.input)
    }

    @Test fun cancelClearsEverything() = runTest(dispatcher) {
        val vm = viewModel()
        vm.start()
        vm.updateInput { valid }
        vm.reset()
        assertEquals(VisitorScreen.Welcome, vm.state.value.screen)
        assertEquals("", vm.state.value.form.input.fullName)
    }
}
