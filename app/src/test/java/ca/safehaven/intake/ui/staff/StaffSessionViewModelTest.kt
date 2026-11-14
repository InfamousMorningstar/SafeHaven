package ca.safehaven.intake.ui.staff

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StaffSessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = StaffSessionViewModel(now = { dispatcher.scheduler.currentTime })

    @Test fun startsInVisitorMode() {
        assertEquals(AppMode.VISITOR, viewModel().mode.value)
    }

    @Test fun pinThenStaffThenLock() = runTest(dispatcher) {
        val vm = viewModel()
        vm.enterStaff()
        assertEquals(AppMode.PIN, vm.mode.value)
        vm.onPinAccepted()
        assertEquals(AppMode.STAFF, vm.mode.value)
        vm.exitStaff()
        assertEquals(AppMode.VISITOR, vm.mode.value)
    }

    @Test fun pinAcceptedOutsidePinModeIsIgnored() {
        val vm = viewModel()
        vm.onPinAccepted()
        assertEquals(AppMode.VISITOR, vm.mode.value)
    }

    @Test fun autoLocksAfterTwoMinutesWithoutTouches() = runTest(dispatcher) {
        val vm = viewModel()
        vm.enterStaff()
        vm.onPinAccepted()
        advanceTimeBy(STAFF_IDLE_TIMEOUT_MILLIS - 1_000)
        assertEquals(AppMode.STAFF, vm.mode.value)
        advanceTimeBy(2_000)
        assertEquals(AppMode.VISITOR, vm.mode.value)
    }

    @Test fun pinScreenAlsoTimesOut() = runTest(dispatcher) {
        val vm = viewModel()
        vm.enterStaff()
        advanceTimeBy(STAFF_IDLE_TIMEOUT_MILLIS + 1)
        assertEquals(AppMode.VISITOR, vm.mode.value)
    }

    @Test fun touchesKeepPushingTheTimeoutBack() = runTest(dispatcher) {
        val vm = viewModel()
        vm.enterStaff()
        vm.onPinAccepted()
        repeat(5) {
            advanceTimeBy(STAFF_IDLE_TIMEOUT_MILLIS / 2)
            vm.onUserInteraction()
        }
        assertEquals(AppMode.STAFF, vm.mode.value)
        advanceTimeBy(STAFF_IDLE_TIMEOUT_MILLIS + 1)
        assertEquals(AppMode.VISITOR, vm.mode.value)
    }

    @Test fun leavingTheAppLocks() = runTest(dispatcher) {
        val vm = viewModel()
        vm.enterStaff()
        vm.onPinAccepted()
        vm.onActivityStopped(deliberate = false)
        assertEquals(AppMode.VISITOR, vm.mode.value)
    }

    @Test fun leavingThePinScreenLocksEvenIfDeliberate() = runTest(dispatcher) {
        val vm = viewModel()
        vm.enterStaff()
        vm.onActivityStopped(deliberate = true)
        assertEquals(AppMode.VISITOR, vm.mode.value)
    }

    @Test fun deliberateTripPausesTheTimerAndRestartsItOnReturn() = runTest(dispatcher) {
        val vm = viewModel()
        vm.enterStaff()
        vm.onPinAccepted()
        vm.onActivityStopped(deliberate = true)
        // Five minutes in the folder picker: longer than the idle timeout, shorter than the away limit.
        advanceTimeBy(5 * 60_000L)
        assertEquals(AppMode.STAFF, vm.mode.value)

        vm.onActivityStarted()
        advanceTimeBy(STAFF_IDLE_TIMEOUT_MILLIS - 1_000)
        assertEquals(AppMode.STAFF, vm.mode.value)
        advanceTimeBy(2_000)
        assertEquals(AppMode.VISITOR, vm.mode.value)
    }

    @Test fun awayTooLongLocksOnReturn() = runTest(dispatcher) {
        val vm = viewModel()
        vm.enterStaff()
        vm.onPinAccepted()
        vm.onActivityStopped(deliberate = true)
        advanceTimeBy(STAFF_MAX_AWAY_MILLIS + 1)
        vm.onActivityStarted()
        assertEquals(AppMode.VISITOR, vm.mode.value)
    }

    @Test fun reenteringStartsAtThePinAgain() = runTest(dispatcher) {
        val vm = viewModel()
        vm.enterStaff()
        vm.onPinAccepted()
        vm.exitStaff()
        vm.enterStaff()
        assertEquals(AppMode.PIN, vm.mode.value)
    }
}
