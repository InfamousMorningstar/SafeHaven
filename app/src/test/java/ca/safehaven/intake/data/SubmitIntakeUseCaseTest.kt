package ca.safehaven.intake.data

import ca.safehaven.intake.data.db.SubmissionDao
import ca.safehaven.intake.data.db.SubmissionEntity
import ca.safehaven.intake.data.files.IntakeFileStore
import ca.safehaven.intake.domain.IntakePdfWriter
import ca.safehaven.intake.domain.SubmitResult
import ca.safehaven.intake.domain.model.IntakeConfig
import ca.safehaven.intake.domain.model.IntakeInput
import ca.safehaven.intake.domain.model.Province
import ca.safehaven.intake.domain.validation.Field
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SubmitIntakeUseCaseTest {
    @get:Rule val tmp = TemporaryFolder()

    private val input = IntakeInput(
        fullName = "  Jane   Smith ",
        phone = "4035550123",
        street = "123 Example St NW",
        unit = "4",
        city = "Calgary",
        province = Province.AB,
        postalCode = "T2X1A1",
        consentGiven = true,
    )

    // 2026-11-04 21:32 UTC = 14:32 MST
    private val clock = Clock.fixed(Instant.parse("2026-11-04T21:32:00Z"), ZoneOffset.UTC)

    private class FakeDao(var failInsert: Boolean = false) : SubmissionDao {
        val rows = mutableListOf<SubmissionEntity>()
        override suspend fun insert(submission: SubmissionEntity) {
            if (failInsert) throw IllegalStateException("disk full")
            rows += submission
        }
        override fun observeAll(): Flow<List<SubmissionEntity>> = emptyFlow()
        override suspend fun getById(id: String) = rows.find { it.id == id }
    }

    private val fakePdf = IntakePdfWriter { record, out -> out.write("%PDF ${record.recordCode}".toByteArray()) }

    private fun useCase(dao: SubmissionDao, root: File, config: IntakeConfig = IntakeConfig()) = SubmitIntakeUseCase(
        config = { config },
        pdfWriter = fakePdf,
        fileStore = IntakeFileStore(root),
        dao = dao,
        clock = clock,
        zone = ZoneId.of("America/Edmonton"),
        random = Random(1),
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    @Test fun savesPdfAndIndexRow() = runTest {
        val dao = FakeDao()
        val root = File(tmp.root, "intake")

        val result = useCase(dao, root).submit(input)

        assertTrue(result is SubmitResult.Saved)
        val row = dao.rows.single()
        assertEquals("Jane Smith", row.fullName)
        assertEquals("+14035550123", row.phoneE164)
        assertEquals("T2X 1A1", row.postalCode)
        assertEquals("1.0", row.consentTextVersion)
        assertTrue(row.pdfPath, Regex("^2026/11/2026-11-04_1432_Smith-Jane_[0-9A-Z]{4}\\.pdf$").matches(row.pdfPath))
        assertEquals((result as SubmitResult.Saved).recordCode, row.recordCode)
        assertTrue(File(root, row.pdfPath).readText().startsWith("%PDF"))
    }

    @Test fun privacyModeKeepsNameOutOfFileName() = runTest {
        val dao = FakeDao()
        useCase(dao, File(tmp.root, "intake"), IntakeConfig(includeNameInFileName = false)).submit(input)
        assertTrue(Regex("^2026/11/2026-11-04_1432_[0-9A-Z]{4}\\.pdf$").matches(dao.rows.single().pdfPath))
    }

    @Test fun invalidInputWritesNothing() = runTest {
        val dao = FakeDao()
        val root = File(tmp.root, "intake")

        val result = useCase(dao, root).submit(input.copy(phone = "123"))

        assertEquals(setOf(Field.PHONE), (result as SubmitResult.Invalid).errors.keys)
        assertTrue(dao.rows.isEmpty())
        assertTrue(!root.exists() || root.walk().none { it.isFile })
    }

    @Test fun databaseFailureRemovesThePdf() = runTest {
        val dao = FakeDao(failInsert = true)
        val root = File(tmp.root, "intake")

        val result = useCase(dao, root).submit(input)

        assertTrue(result is SubmitResult.Failed)
        assertTrue(root.walk().none { it.isFile && it.extension == "pdf" })
    }
}
