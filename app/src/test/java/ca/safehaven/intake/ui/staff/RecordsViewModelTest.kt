package ca.safehaven.intake.ui.staff

import ca.safehaven.intake.data.db.escapeLike
import ca.safehaven.intake.data.files.IntakeFileStore
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class RecordsViewModelTest {
    @get:Rule val temp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-11-10T18:00:00Z"), ZoneOffset.UTC)
    private lateinit var root: File
    private lateinit var fileStore: IntakeFileStore

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        root = temp.newFolder("intake")
        fileStore = IntakeFileStore(root)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(dao: FakeSubmissionDao) = RecordsViewModel(dao, fileStore, clock, io = dispatcher)

    private fun writePdf(relativePath: String): File =
        File(root, relativePath).apply {
            parentFile!!.mkdirs()
            writeText("%PDF-1.4")
        }

    @Test fun searchDigitsKeepsOnlyAsciiDigits() {
        assertEquals("6025550123", searchDigits("(602) 555-0123"))
        assertEquals("", searchDigits("Jane Smith"))
        assertEquals("0042", searchDigits("#0042"))
        assertEquals("", searchDigits("john2@example.com"))
    }

    @Test fun likeWildcardsInASearchAreEscaped() {
        assertEquals("50\\%", escapeLike("50%"))
        assertEquals("a\\_b\\\\c", escapeLike("a_b\\c"))
        assertEquals("O'Brien", escapeLike("O'Brien"))
    }

    @Test fun emptyQueryListsEverythingNewestFirst() = runTest(dispatcher) {
        val dao = FakeSubmissionDao(listOf(StaffFixtures.entity(1), StaffFixtures.entity(2)))
        val vm = viewModel(dao)
        runCurrent()
        assertEquals(listOf("" to ""), dao.searches)
        assertEquals(listOf("0002", "0001"), vm.records.value!!.map { it.recordCode })
        assertEquals(2, vm.totalCount.value)
    }

    @Test fun typingIsDebouncedTrimmedAndSplitIntoDigits() = runTest(dispatcher) {
        val dao = FakeSubmissionDao(
            listOf(
                StaffFixtures.entity(1, fullName = "Jane Smith", phoneE164 = "+16025550123"),
                StaffFixtures.entity(2, fullName = "Sam Jones", phoneE164 = "+14805550199"),
            ),
        )
        val vm = viewModel(dao)
        runCurrent()

        vm.setQuery("J")
        assertEquals("J", vm.query.value) // the text box updates at once
        advanceTimeBy(100)
        vm.setQuery("  (602) 555 ")
        advanceTimeBy(SEARCH_DEBOUNCE_MILLIS + 1)

        // "J" was never searched: typing continued within the debounce window.
        assertEquals(listOf("" to "", "(602) 555" to "602555"), dao.searches)
        assertEquals(listOf("0001"), vm.records.value!!.map { it.recordCode })
    }

    @Test fun openFollowsTheDatabaseAndClosing() = runTest(dispatcher) {
        val dao = FakeSubmissionDao(listOf(StaffFixtures.entity(1)))
        val vm = viewModel(dao)
        runCurrent()
        vm.open("id-1")
        runCurrent()
        assertEquals("0001", vm.selected.value?.recordCode)
        vm.closeViewer()
        runCurrent()
        assertNull(vm.selected.value)
    }

    @Test fun shareMarksExported() = runTest(dispatcher) {
        val dao = FakeSubmissionDao(listOf(StaffFixtures.entity(1)))
        val vm = viewModel(dao)
        vm.onShared("id-1")
        runCurrent()
        assertEquals(listOf("id-1"), dao.exported)
        assertEquals(clock.instant(), dao.rows.value.single().exportedAt)
    }

    @Test fun pdfFileIsNullWhenMissing() = runTest(dispatcher) {
        val record = StaffFixtures.entity(1)
        val vm = viewModel(FakeSubmissionDao(listOf(record)))
        assertNull(vm.pdfFile(record))
        writePdf(record.pdfPath)
        assertTrue(vm.pdfFile(record)!!.isFile)
    }

    @Test fun deleteRemovesFileAndRowAfterConfirmation() = runTest(dispatcher) {
        val record = StaffFixtures.entity(1)
        val pdf = writePdf(record.pdfPath)
        val dao = FakeSubmissionDao(listOf(record, StaffFixtures.entity(2)))
        val vm = viewModel(dao)
        runCurrent()
        vm.open(record.id)
        runCurrent()

        vm.requestDelete()
        assertTrue(vm.confirmingDelete.value)
        vm.confirmDelete()
        runCurrent()

        assertFalse(pdf.exists())
        assertEquals(listOf("id-2"), dao.rows.value.map { it.id })
        assertNull(vm.selected.value)
        assertFalse(vm.confirmingDelete.value)
        assertFalse(vm.isDeleting.value)
        assertEquals(RecordsMessage.Deleted("0001"), vm.message.value)
    }

    @Test fun cancelDeleteKeepsEverything() = runTest(dispatcher) {
        val record = StaffFixtures.entity(1)
        val pdf = writePdf(record.pdfPath)
        val dao = FakeSubmissionDao(listOf(record))
        val vm = viewModel(dao)
        runCurrent()
        vm.open(record.id)
        runCurrent()
        vm.requestDelete()
        vm.cancelDelete()
        runCurrent()
        assertFalse(vm.confirmingDelete.value)
        assertTrue(pdf.exists())
        assertEquals(1, dao.rows.value.size)
    }

    @Test fun deleteWithMissingFileStillRemovesRow() = runTest(dispatcher) {
        val record = StaffFixtures.entity(1)
        val dao = FakeSubmissionDao(listOf(record))
        val vm = viewModel(dao)
        runCurrent()
        vm.open(record.id)
        runCurrent()
        vm.requestDelete()
        vm.confirmDelete()
        runCurrent()
        assertTrue(dao.rows.value.isEmpty())
        assertEquals(RecordsMessage.Deleted("0001"), vm.message.value)
    }
}
