package ca.safehaven.intake.domain

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * File naming, per the client: date plus a sequential number, no visitor name, no random code.
 * `2026-11-09_0042.pdf`, stored under `2026/11/`. The number comes from a counter in the
 * database that only ever goes up, so names never collide or repeat.
 */
object FileNamer {
    const val MIN_DIGITS = 4

    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    private val MONTH_DIR = DateTimeFormatter.ofPattern("yyyy/MM", Locale.ROOT)

    /** `42` -> `0042`. Grows past 4 digits on its own after 9999. */
    fun recordCode(sequenceNumber: Long): String {
        require(sequenceNumber > 0) { "Sequence numbers start at 1" }
        return sequenceNumber.toString().padStart(MIN_DIGITS, '0')
    }

    /** `2026/11` */
    fun monthDirectory(at: ZonedDateTime): String = MONTH_DIR.format(at)

    /** `2026-11-09_0042.pdf` */
    fun fileName(at: ZonedDateTime, sequenceNumber: Long): String =
        "${DATE.format(at)}_${recordCode(sequenceNumber)}.pdf"

    /** `2026/11/2026-11-09_0042.pdf` */
    fun relativePath(at: ZonedDateTime, sequenceNumber: Long): String =
        monthDirectory(at) + "/" + fileName(at, sequenceNumber)
}
