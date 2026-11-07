package ca.safehaven.intake.data.pdf

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ca.safehaven.intake.domain.model.IntakeRecord
import ca.safehaven.intake.domain.model.Province
import ca.safehaven.intake.domain.model.VisitReason
import java.io.File
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidIntakePdfWriterTest {

    @Test
    fun writesOneLetterSizedPage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val writer = AndroidIntakePdfWriter(
            resources = context.resources,
            zone = ZoneId.of("America/Edmonton"),
            shelterName = "Test Shelter",
            appName = "SafeHaven",
            deviceLabel = "TEST-01",
            appVersion = "test",
        )
        val record = IntakeRecord(
            id = "id-1",
            recordCode = "K7QF-20261104-1432",
            createdAt = Instant.parse("2026-11-04T21:32:00Z"),
            fullName = "Jane Smith",
            phoneE164 = "+14035550123",
            phoneDisplay = "(403) 555-0123",
            street = "123 Example St NW",
            unit = "4",
            city = "Calgary",
            province = Province.AB,
            postalCode = "T2X 1A1",
            reason = VisitReason.ADOPTING,
            consentGiven = true,
            consentTextVersion = "1.0",
        )
        val file = File(context.cacheDir, "pdf-writer-test.pdf")

        file.outputStream().use { writer.write(record, it) }

        assertTrue(file.length() > 0)
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            assertEquals(1, renderer.pageCount)
            val page = renderer.openPage(0)
            assertEquals(AndroidIntakePdfWriter.PAGE_WIDTH, page.width)
            assertEquals(AndroidIntakePdfWriter.PAGE_HEIGHT, page.height)
            page.close()
        } finally {
            renderer.close()
            file.delete()
        }
    }
}
