package ca.safehaven.intake.data.pdf

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ca.safehaven.intake.domain.RecordFactory
import ca.safehaven.intake.domain.model.IntakeConfig
import ca.safehaven.intake.domain.model.IntakeInput
import ca.safehaven.intake.domain.model.IntakeRecord
import ca.safehaven.intake.domain.model.Signature
import ca.safehaven.intake.domain.model.SignaturePoint
import ca.safehaven.intake.domain.model.UsState
import ca.safehaven.intake.domain.model.VisitReason
import ca.safehaven.intake.domain.model.WaiverClause
import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Made-up data only (the repo is public). Mirrors the unit tests' TestFixtures. */
@RunWith(AndroidJUnit4::class)
class AndroidIntakePdfWriterTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val config = IntakeConfig(deviceLabel = "TEST-01")
    private val today = LocalDate.of(2026, 11, 9)

    private val adult = IntakeInput(
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
            strokes = listOf(
                List(20) { i -> SignaturePoint(20f + i * 25f, if (i % 2 == 0) 60f else 140f) },
                listOf(SignaturePoint(560f, 100f)), // a dot
            ),
        ),
    )

    private val minor = adult.copy(fullName = "Sam Smith", dateOfBirth = "06012012", guardianName = "Jane Smith")

    private fun writer() = AndroidIntakePdfWriter(
        resources = context.resources,
        config = { config },
        shelterName = "Safe Haven for Animals",
        appName = "SafeHaven",
        appVersion = "test",
    )

    private fun record(input: IntakeInput, sequenceNumber: Long = 42): IntakeRecord = RecordFactory.create(
        input = input,
        config = config,
        id = "id-$sequenceNumber",
        sequenceNumber = sequenceNumber,
        createdAt = Instant.parse("2026-11-09T21:32:00Z"),
        signedOn = today,
    )

    /** Writes [record] and returns its page count, checking every page is US Letter. */
    private fun render(record: IntakeRecord): Int {
        val file = File(context.cacheDir, "pdf-writer-test-${record.recordCode}.pdf")
        try {
            file.outputStream().use { writer().write(record, it) }
            assertTrue(file.length() > 0)
            assertEquals("%PDF", file.readBytes().copyOf(4).toString(Charsets.US_ASCII))

            val renderer = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
            try {
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    assertEquals(AndroidIntakePdfWriter.PAGE_WIDTH, page.width)
                    assertEquals(AndroidIntakePdfWriter.PAGE_HEIGHT, page.height)
                    page.close()
                }
                return renderer.pageCount
            } finally {
                renderer.close() // Also closes the file descriptor.
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun adultWaiverFitsOnTwoLetterPages() {
        val record = record(adult)
        assertTrue(!record.isMinor)
        assertEquals(2, render(record))
    }

    @Test
    fun minorWaiverFitsOnTwoLetterPages() {
        val record = record(minor, sequenceNumber = 43)
        assertTrue(record.isMinor)
        assertEquals(2, render(record))
    }

    @Test
    fun longestAllowedValuesStillPaginate() {
        val long = adult.copy(
            fullName = "Jane " + "Abcdefghij".repeat(9), // 95 letters
            street = "1234 " + "Long Street Name ".repeat(6).trim(),
            city = "Abcdefghij ".repeat(5).trim(),
            zip = "850011234",
            email = "a".repeat(60) + "@example.com",
            heardAbout = "Heard about it ".repeat(8).trim(),
            reason = VisitReason.VISITING,
            initials = WaiverClause.entries.associateWith { "WWWW" },
        )
        val pages = render(record(long, sequenceNumber = 12345))
        assertTrue("got $pages pages", pages in 2..3)
    }
}
