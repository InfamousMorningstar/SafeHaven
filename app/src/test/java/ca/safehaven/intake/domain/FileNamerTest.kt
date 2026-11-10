package ca.safehaven.intake.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamerTest {
    private val phoenix = ZoneId.of("America/Phoenix")
    private val at = ZonedDateTime.of(2026, 11, 9, 14, 32, 10, 0, phoenix)

    @Test fun dateAndSequentialNumber() {
        assertEquals("2026-11-09_0001.pdf", FileNamer.fileName(at, 1))
        assertEquals("2026/11/2026-11-09_0042.pdf", FileNamer.relativePath(at, 42))
        assertEquals("0042", FileNamer.recordCode(42))
    }

    @Test fun numberGrowsPastFourDigits() {
        assertEquals("12345", FileNamer.recordCode(12_345))
        assertEquals("2026-11-09_12345.pdf", FileNamer.fileName(at, 12_345))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroIsNotASequenceNumber() {
        FileNamer.recordCode(0)
    }

    @Test fun usesArizonaLocalDate() {
        // 05:30 UTC on 2026-11-10 is still 22:30 on 2026-11-09 in Phoenix (UTC-7, no DST).
        val utc = ZonedDateTime.of(2026, 11, 10, 5, 30, 0, 0, ZoneId.of("UTC"))
        assertEquals("2026-11-09_0007.pdf", FileNamer.fileName(utc.withZoneSameInstant(phoenix), 7))
    }
}
