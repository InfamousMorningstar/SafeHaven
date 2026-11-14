package ca.safehaven.intake.data

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ca.safehaven.intake.data.db.AppDatabase
import ca.safehaven.intake.data.db.escapeLike
import ca.safehaven.intake.data.files.IntakeFileStore
import ca.safehaven.intake.data.pdf.AndroidIntakePdfWriter
import ca.safehaven.intake.domain.SubmitResult
import ca.safehaven.intake.domain.model.IntakeConfig
import ca.safehaven.intake.domain.model.IntakeInput
import ca.safehaven.intake.domain.model.Signature
import ca.safehaven.intake.domain.model.SignaturePoint
import ca.safehaven.intake.domain.model.UsState
import ca.safehaven.intake.domain.model.VisitReason
import ca.safehaven.intake.domain.model.WaiverClause
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole save on a real device: the real PDF writer, file store and Room database (in
 * memory), then the staff queries against what was saved. Made-up data only (the repo is public).
 */
@RunWith(AndroidJUnit4::class)
class SaveAndSearchOnDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val config = IntakeConfig(deviceLabel = "TEST-01")
    private val clock = Clock.fixed(Instant.parse("2026-11-09T21:32:00Z"), ZoneOffset.UTC)

    private lateinit var root: File
    private lateinit var db: AppDatabase
    private lateinit var fileStore: IntakeFileStore
    private lateinit var useCase: SubmitIntakeUseCase

    private val jane = IntakeInput(
        fullName = "Jane Smith",
        street = "123 Example Rd",
        city = "Phoenix",
        state = UsState.AZ,
        zip = "85001",
        email = "jane@example.com",
        phone = "6025550123",
        reason = VisitReason.VOLUNTEERING,
        heardAbout = "A friend",
        dateOfBirth = "03151990",
        initials = WaiverClause.entries.associateWith { "JS" },
        signature = Signature(
            width = 600f,
            height = 200f,
            strokes = listOf(List(20) { i -> SignaturePoint(20f + i * 25f, if (i % 2 == 0) 60f else 140f) }),
        ),
    )

    @Before fun setUp() {
        root = File(context.cacheDir, "save-test-${System.nanoTime()}")
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        fileStore = IntakeFileStore(root)
        useCase = SubmitIntakeUseCase(
            config = { config },
            pdfWriter = AndroidIntakePdfWriter(
                resources = context.resources,
                config = { config },
                shelterName = "Safe Haven for Animals",
                appName = "SafeHaven",
                appVersion = "test",
            ),
            fileStore = fileStore,
            dao = db.submissionDao(),
            reserveSequenceNumber = db.sequenceCounterDao()::next,
            clock = clock,
        )
    }

    @After fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    private fun save(input: IntakeInput): String = runBlocking {
        val result = useCase.submit(input)
        assertTrue("expected Saved but was $result", result is SubmitResult.Saved)
        (result as SubmitResult.Saved).recordCode
    }

    private fun search(text: String): List<String> = runBlocking {
        val digits = if (text.any { it.isLetter() }) "" else text.filter { it in '0'..'9' }
        db.submissionDao().search(escapeLike(text), digits).first().map { it.fullName }
    }

    @Test fun aSavedWaiverHasItsPdfInPlaceAndItsRow() = runBlocking {
        assertEquals("0001", save(jane))
        assertEquals("0002", save(jane.copy(fullName = "Sam Jones", phone = "4805550199")))

        val dao = db.submissionDao()
        assertEquals(2, dao.observeCount().first())
        val rows = dao.pendingBackup()
        assertEquals(listOf("0001", "0002"), rows.map { it.recordCode })

        for (row in rows) {
            val pdf = fileStore.resolve(row.pdfPath)
            assertTrue(pdf.isFile)
            assertEquals(1, dao.countByPdfPath(row.pdfPath))
            ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { assertEquals(2, it.pageCount) }
            }
        }
        // Nothing left waiting: both saves were moved into place.
        assertTrue(fileStore.pendingTemp().isEmpty())
    }

    @Test fun searchMatchesNamesPhonesAndTreatsWildcardsAsText() {
        save(jane)
        save(jane.copy(fullName = "Sam Jones", phone = "4805550199", email = ""))

        assertEquals(listOf("Sam Jones", "Jane Smith"), search(""))
        assertEquals(listOf("Jane Smith"), search("smi"))
        assertEquals(listOf("Jane Smith"), search("JANE@EXAMPLE"))
        assertEquals(listOf("Sam Jones"), search("(480) 555"))
        assertEquals(listOf("Sam Jones"), search("0002"))
        // Wildcards typed by staff are ordinary characters, not "match everything".
        assertEquals(emptyList<String>(), search("%"))
        assertEquals(emptyList<String>(), search("_"))
        assertEquals(emptyList<String>(), search("J_ne"))
        // A name with a digit isn't a phone search.
        assertEquals(emptyList<String>(), search("Smith 5"))
    }

    @Test fun aNewBackupFolderMakesEverythingPendingAgain() = runBlocking {
        save(jane)
        val dao = db.submissionDao()
        val row = dao.pendingBackup().single()
        dao.markBackedUp(row.id, clock.instant())
        assertTrue(dao.pendingBackup().isEmpty())

        dao.markAllPendingBackup()

        assertEquals(1, dao.pendingBackup().size)
        assertNull(dao.observeLastBackupAt().first())
    }
}
