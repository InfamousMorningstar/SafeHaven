package ca.safehaven.intake.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Index row for one submission (plan 3.4). The PDF is the record; this row exists for staff
 * search, retention clean-up and backup tracking. It is a second copy of personal data, so
 * keep it to what search needs (plan D8).
 */
@Entity(
    tableName = "submission",
    indices = [Index("createdAt"), Index("fullName"), Index("phoneE164")],
)
data class SubmissionEntity(
    @PrimaryKey val id: String,
    val recordCode: String,
    val createdAt: Instant,
    val fullName: String,
    val phoneE164: String,
    val street: String,
    val unit: String?,
    val city: String,
    val province: String,
    val postalCode: String,
    val reason: String?,
    val consentGiven: Boolean,
    val consentTextVersion: String?,
    val hasSignature: Boolean,
    /** Relative to `filesDir/intake/`, e.g. `2026/11/2026-11-04_1432_Smith-Jane_K7QF.pdf`. */
    val pdfPath: String,
    val pdfSha256: String,
    val exportedAt: Instant? = null,
    val backedUpAt: Instant? = null,
)
