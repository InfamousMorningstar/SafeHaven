package ca.safehaven.intake.data

import ca.safehaven.intake.TestFixtures
import ca.safehaven.intake.data.db.SubmissionDao
import ca.safehaven.intake.data.db.SubmissionEntity
import ca.safehaven.intake.data.files.IntakeFileStore
import ca.safehaven.intake.domain.IntakePdfWriter
import ca.safehaven.intake.domain.SubmitResult
import ca.safehaven.intake.domain.model.IntakeConfig
import ca.safehaven.intake.domain.model.Waiver
import ca.safehaven.intake.domain.validation.Field
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SubmitIntakeUseCaseTest {
    @get:Rule val tmp = TemporaryFolder()

    // 2026-11-09 21:32 UTC = 14:32 MST in Phoenix, i.e. TestFixtures.TODAY.
    private val clock = Clock.fixed(Instant.parse("2026-11-09T21:32:00Z"), ZoneOffset.UTC)

    private val root get() = File(tmp.root, "intake")

    private class FakeDao(var failure: Throwable? = null) : SubmissionDao {
        val rows = mutableListOf<SubmissionEntity>()
        override suspend fun insert(submission: SubmissionEntity) {
            failure?.let { throw it }
            rows += submission
        }
        override fun observeAll(): Flow<List<SubmissionEntity>> = emptyFlow()
        override suspend fun getById(id: String) = rows.find { it.id == id }
    }

    /** Stands in for SequenceCounterDao: only goes up, even when a save fails. */
    private class FakeCounter {
        var last = 0L
        suspend fun next(): Long = ++last
    }

    private val fakePdf = IntakePdfWriter { record, out -> out.write("%PDF ${record.recordCode}".toByteArray()) }

    private fun useCase(dao: SubmissionDao, counter: FakeCounter = FakeCounter(), config: IntakeConfig = IntakeConfig()) =
        SubmitIntakeUseCase(
            config = { config },
            pdfWriter = fakePdf,
            fileStore = IntakeFileStore(root),
            dao = dao,
            reserveSequenceNumber = counter::next,
            clock = clock,
            ioDispatcher = UnconfinedTestDispatcher(),
        )

    private fun pdfFiles() = if (root.exists()) root.walk().filter { it.isFile && it.extension == "pdf" }.toList() else emptyList()

    @Test fun savesPdfAndIndexRow() = runTest {
        val dao = FakeDao()

        val result = useCase(dao).submit(TestFixtures.VALID_ADULT)

        assertEquals(SubmitResult.Saved("0001"), result)
        val row = dao.rows.single()
        assertEquals(1L, row.sequenceNumber)
        assertEquals("0001", row.recordCode)
        assertEquals("2026/11/2026-11-09_0001.pdf", row.pdfPath)
        assertEquals("Jane Smith", row.fullName)
        assertEquals("+16025550123", row.phoneE164)
        assertEquals("AZ", row.state)
        assertEquals("VOLUNTEERING", row.reason)
        assertEquals(TestFixtures.TODAY, row.signedOn)
        assertEquals(LocalDate.of(1990, 3, 15), row.dateOfBirth)
        assertFalse(row.isMinor)
        assertNull(row.guardianName)
        assertEquals(Waiver.VERSION, row.waiverVersion)
        assertEquals("%PDF 0001", File(root, row.pdfPath).readText())
    }

    @Test fun minorRowRecordsTheGuardian() = runTest {
        val dao = FakeDao()

        useCase(dao).submit(TestFixtures.VALID_MINOR)

        val row = dao.rows.single()
        assertTrue(row.isMinor)
        assertEquals("Jane Smith", row.guardianName)
    }

    @Test fun numbersAreSequentialAndInFileNames() = runTest {
        val dao = FakeDao()
        val submitter = useCase(dao)

        val codes = List(3) { (submitter.submit(TestFixtures.VALID_ADULT) as SubmitResult.Saved).recordCode }

        assertEquals(listOf("0001", "0002", "0003"), codes)
        assertEquals(listOf(1L, 2L, 3L), dao.rows.map { it.sequenceNumber })
        assertEquals(
            listOf("2026-11-09_0001.pdf", "2026-11-09_0002.pdf", "2026-11-09_0003.pdf"),
            dao.rows.map { File(it.pdfPath).name },
        )
        assertEquals(3, pdfFiles().size)
    }

    @Test fun failedSaveLeavesAGapNotARepeat() = runTest {
        val dao = FakeDao(failure = IllegalStateException("disk full"))
        val counter = FakeCounter()
        val submitter = useCase(dao, counter)

        assertTrue(submitter.submit(TestFixtures.VALID_ADULT) is SubmitResult.Failed)
        dao.failure = null
        val result = submitter.submit(TestFixtures.VALID_ADULT)

        assertEquals(SubmitResult.Saved("0002"), result)
    }

    @Test fun existingFileTakesTheNextNumber() = runTest {
        // E.g. the database was restored while the PDFs stayed.
        File(root, "2026/11/2026-11-09_0001.pdf").apply { parentFile!!.mkdirs(); writeText("old") }
        val dao = FakeDao()

        val result = useCase(dao).submit(TestFixtures.VALID_ADULT)

        assertEquals(SubmitResult.Saved("0002"), result)
        assertEquals("old", File(root, "2026/11/2026-11-09_0001.pdf").readText())
        assertEquals("2026/11/2026-11-09_0002.pdf", dao.rows.single().pdfPath)
    }

    @Test fun invalidInputWritesNothing() = runTest {
        val dao = FakeDao()
        val counter = FakeCounter()

        val result = useCase(dao, counter).submit(TestFixtures.VALID_ADULT.copy(phone = "123"))

        assertEquals(setOf(Field.PHONE), (result as SubmitResult.Invalid).errors.keys)
        assertTrue(dao.rows.isEmpty())
        assertEquals("no number reserved", 0L, counter.last)
        assertTrue(!root.exists() || root.walk().none { it.isFile })
    }

    @Test fun minorWithoutGuardianIsRejected() = runTest {
        val dao = FakeDao()

        val result = useCase(dao).submit(TestFixtures.VALID_MINOR.copy(guardianName = ""))

        assertEquals(setOf(Field.GUARDIAN_NAME), (result as SubmitResult.Invalid).errors.keys)
        assertTrue(dao.rows.isEmpty())
    }

    @Test fun databaseFailureRemovesThePdf() = runTest {
        val dao = FakeDao(failure = IllegalStateException("disk full"))

        val result = useCase(dao).submit(TestFixtures.VALID_ADULT)

        assertTrue(result is SubmitResult.Failed)
        assertTrue(pdfFiles().isEmpty())
    }

    @Test fun cancellationIsRethrownAndRemovesThePdf() = runTest {
        val dao = FakeDao(failure = CancellationException("screen left"))

        try {
            useCase(dao).submit(TestFixtures.VALID_ADULT)
            fail("expected CancellationException")
        } catch (expected: CancellationException) {
        }
        assertTrue(pdfFiles().isEmpty())
    }
}
