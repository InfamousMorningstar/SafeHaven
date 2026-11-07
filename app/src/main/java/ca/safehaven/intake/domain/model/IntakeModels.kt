package ca.safehaven.intake.domain.model

import java.time.Instant

/** Canadian provinces and territories, with the first letters their postal codes use. */
enum class Province(val displayName: String, val postalPrefixes: String) {
    AB("Alberta", "T"),
    BC("British Columbia", "V"),
    MB("Manitoba", "R"),
    NB("New Brunswick", "E"),
    NL("Newfoundland and Labrador", "A"),
    NS("Nova Scotia", "B"),
    NT("Northwest Territories", "X"),
    NU("Nunavut", "X"),
    ON("Ontario", "KLMNP"),
    PE("Prince Edward Island", "C"),
    QC("Quebec", "GHJ"),
    SK("Saskatchewan", "S"),
    YT("Yukon", "Y"),
}

enum class VisitReason { ADOPTING, VOLUNTEERING, DROPPING_OFF, OTHER }

/** Raw form contents, exactly as typed. Only ever held in memory. */
data class IntakeInput(
    val fullName: String = "",
    /** Digits only (the UI strips everything else). */
    val phone: String = "",
    val street: String = "",
    val unit: String = "",
    val city: String = "",
    val province: Province = Province.AB,
    /** Letters and digits only, upper case, no space. */
    val postalCode: String = "",
    val reason: VisitReason? = null,
    val consentGiven: Boolean = false,
)

/**
 * App behaviour that the client may want to change. Hard-coded for now; the staff Settings
 * screen (phase 4) will persist these in DataStore.
 */
data class IntakeConfig(
    val consentRequired: Boolean = true,
    val consentTextVersion: String = "1.0",
    val askReason: Boolean = true,
    /** Privacy option (plan 3.6): false gives `2026-11-04_1432_K7QF.pdf`. Ask the client. */
    val includeNameInFileName: Boolean = true,
    val defaultCity: String = "Calgary",
    val defaultProvince: Province = Province.AB,
    val idleTimeoutMillis: Long = 90_000,
    val stillThereSeconds: Int = 10,
    val thanksMillis: Long = 5_000,
    val timeZoneId: String = "America/Edmonton",
    val deviceLabel: String = "FRONT-DESK-01",
)

/** A validated, normalized submission. This is what goes into the PDF and the database. */
data class IntakeRecord(
    val id: String,
    val recordCode: String,
    val createdAt: Instant,
    val fullName: String,
    val phoneE164: String,
    val phoneDisplay: String,
    val street: String,
    val unit: String?,
    val city: String,
    val province: Province,
    val postalCode: String,
    val reason: VisitReason?,
    val consentGiven: Boolean,
    /** Null when the consent checkbox is turned off in config. */
    val consentTextVersion: String?,
)
