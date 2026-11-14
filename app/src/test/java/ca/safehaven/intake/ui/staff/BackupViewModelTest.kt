package ca.safehaven.intake.ui.staff

import ca.safehaven.intake.staff.BackupResult
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun showsServiceStatus() = runTest(dispatcher) {
        val service = FakeBackupService()
        val vm = BackupViewModel(service)
        runCurrent()
        assertEquals("Waivers", vm.status.value?.folderName)
        assertEquals(2, vm.status.value?.pendingCount)
    }

    @Test fun backUpNowReportsTheResult() = runTest(dispatcher) {
        val service = FakeBackupService().apply { result = BackupResult.Done(2) }
        val vm = BackupViewModel(service)
        runCurrent()
        vm.backUpNow()
        runCurrent()
        assertEquals(BackupMessage.Finished(BackupResult.Done(2)), vm.message.value)
        assertFalse(vm.isWorking.value)
    }

    @Test fun secondTapWhileRunningIsIgnored() = runTest(dispatcher) {
        val service = FakeBackupService().apply { backupMillis = 5_000 }
        val vm = BackupViewModel(service)
        runCurrent()
        vm.backUpNow()
        runCurrent()
        assertTrue(vm.isWorking.value)
        vm.backUpNow()
        advanceTimeBy(5_001)
        assertEquals(1, service.backupCalls)
        assertFalse(vm.isWorking.value)
    }

    @Test fun exceptionBecomesFailedWithNothingCopied() = runTest(dispatcher) {
        val service = FakeBackupService().apply { throwOnBackup = IOException("gone") }
        val vm = BackupViewModel(service)
        runCurrent()
        vm.backUpNow()
        runCurrent()
        val result = (vm.message.value as BackupMessage.Finished).result
        assertTrue(result is BackupResult.Failed)
        assertEquals(0, (result as BackupResult.Failed).copied)
        assertFalse(vm.isWorking.value)
    }
}
