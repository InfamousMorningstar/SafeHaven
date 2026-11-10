package ca.safehaven.intake.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * Index row for one signed waiver (plan 3.4). The PDF is the record; this row exists for staff
 * search and listing, and backup tracking. It is a second copy of personal data, so keep it to
 * what search needs (plan D8): the street address, "heard about us", the initials and the
 * signature strokes are only in the PDF.
 */
@Entity(
    tableName = "submission",
    indices = [
        Index("sequenceNumber", unique = true),
        Index("createdAt"),
        Index("fullName"),
        Index("phoneE164"),
    ],
)
data class SubmissionEntity(
    @PrimaryKey val id: String,
    /** From [SequenceCounterEntity]; never reused, even after a row is deleted. */
    val sequenceNumber: Long,
    /** [sequenceNumber] padded to 4 digits, e.g. `0042`. Printed on the PDF. */
    val recordCode: String,
    val createdAt: Instant,
    /** Local date the waiver was signed, in the shelter's time zone. */
    val signedOn: LocalDate,
    val fullName: String,
    val phoneE164: String,
    val email: String?,
    val city: String,
    /** Postal abbreviation, e.g. `AZ`. */
    val state: String,
    val zip: String,
    /** [ca.safehaven.intake.domain.model.VisitReason] name. */
    val reason: String,
    val dateOfBirth: LocalDate,
    val isMinor: Boolean,
    /** Non-null exactly when [isMinor]. */
    val guardianName: String?,
    /** Which wording the visitor signed ([ca.safehaven.intake.domain.model.Waiver.VERSION]). */
    val waiverVersion: String,
    /** Relative to `filesDir/intake/`, e.g. `2026/11/2026-11-09_0042.pdf`. */
    val pdfPath: String,
    val pdfSha256: String,
    val exportedAt: Instant? = null,
    val backedUpAt: Instant? = null,
)
