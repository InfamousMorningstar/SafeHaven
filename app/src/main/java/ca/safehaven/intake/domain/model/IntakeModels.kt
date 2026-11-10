package ca.safehaven.intake.domain.model

import java.time.Instant
import java.time.LocalDate

/** US states plus DC, by postal abbreviation. */
enum class UsState(val displayName: String) {
    AL("Alabama"), AK("Alaska"), AZ("Arizona"), AR("Arkansas"), CA("California"),
    CO("Colorado"), CT("Connecticut"), DE("Delaware"), DC("District of Columbia"),
    FL("Florida"), GA("Georgia"), HI("Hawaii"), ID("Idaho"), IL("Illinois"), IN("Indiana"),
    IA("Iowa"), KS("Kansas"), KY("Kentucky"), LA("Louisiana"), ME("Maine"), MD("Maryland"),
    MA("Massachusetts"), MI("Michigan"), MN("Minnesota"), MS("Mississippi"), MO("Missouri"),
    MT("Montana"), NE("Nebraska"), NV("Nevada"), NH("New Hampshire"), NJ("New Jersey"),
    NM("New Mexico"), NY("New York"), NC("North Carolina"), ND("North Dakota"), OH("Ohio"),
    OK("Oklahoma"), OR("Oregon"), PA("Pennsylvania"), RI("Rhode Island"),
    SC("South Carolina"), SD("South Dakota"), TN("Tennessee"), TX("Texas"), UT("Utah"),
    VT("Vermont"), VA("Virginia"), WA("Washington"), WV("West Virginia"), WI("Wisconsin"),
    WY("Wyoming"),
}

enum class VisitReason { ADOPTING, VOLUNTEERING, VISITING, DROPPING_OFF, OTHER }

/** One point of a signature stroke, in the signature pad's own pixel coordinates. */
data class SignaturePoint(val x: Float, val y: Float)

/**
 * A drawn signature as vector strokes. [width] and [height] are the pad size the points were
 * recorded in, so the PDF can scale the strokes into its own box.
 */
data class Signature(
    val width: Float,
    val height: Float,
    val strokes: List<List<SignaturePoint>>,
) {
    val pointCount: Int get() = strokes.sumOf { it.size }

    companion object {
        val EMPTY = Signature(0f, 0f, emptyList())
    }
}

/** Raw form contents, exactly as typed. Only ever held in memory. */
data class IntakeInput(
    val fullName: String = "",
    val street: String = "",
    val city: String = "",
    val state: UsState = UsState.AZ,
    /** Digits only, at most 9 (the UI strips everything else). */
    val zip: String = "",
    val email: String = "",
    /** Digits only (the UI strips everything else). */
    val phone: String = "",
    val reason: VisitReason? = null,
    val heardAbout: String = "",
    /** Digits only, `MMDDYYYY`, at most 8 (the UI shows `MM/DD/YYYY`). */
    val dateOfBirth: String = "",
    /** Only used, and only required, when the visitor is under [IntakeConfig.adultAge]. */
    val guardianName: String = "",
    val initials: Map<WaiverClause, String> = emptyMap(),
    val signature: Signature = Signature.EMPTY,
)

/**
 * App behaviour that the client may want to change. Hard-coded for now; the staff Settings
 * screen (phase 4) will persist these in DataStore.
 */
data class IntakeConfig(
    val defaultCity: String = "",
    val defaultState: UsState = UsState.AZ,
    /** Under this age, a parent or guardian signs. */
    val adultAge: Int = 18,
    val idleTimeoutMillis: Long = 90_000,
    val stillThereSeconds: Int = 10,
    val thanksMillis: Long = 5_000,
    /** Arizona does not observe daylight saving time. */
    val timeZoneId: String = "America/Phoenix",
    val deviceLabel: String = "FRONT-DESK-01",
)

/** A validated, normalized submission. This is what goes into the PDF and the database. */
data class IntakeRecord(
    val id: String,
    /** 1, 2, 3, ... across the life of the install. Never reused. */
    val sequenceNumber: Long,
    /** [sequenceNumber] padded to 4 digits, e.g. `0042`. */
    val recordCode: String,
    val createdAt: Instant,
    val fullName: String,
    val street: String,
    val city: String,
    val state: UsState,
    /** `12345` or `12345-6789`. */
    val zip: String,
    val email: String?,
    val phoneE164: String,
    val phoneDisplay: String,
    val reason: VisitReason,
    val heardAbout: String?,
    val dateOfBirth: LocalDate,
    /** Local date the waiver was signed, in the shelter's time zone. */
    val signedOn: LocalDate,
    val isMinor: Boolean,
    /** Non-null exactly when [isMinor]. */
    val guardianName: String?,
    /** One entry per [WaiverClause], upper case letters. */
    val initials: Map<WaiverClause, String>,
    val signature: Signature,
    val waiverVersion: String,
)
