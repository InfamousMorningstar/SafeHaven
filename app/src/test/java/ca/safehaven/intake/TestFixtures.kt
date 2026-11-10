package ca.safehaven.intake

import ca.safehaven.intake.domain.model.IntakeInput
import ca.safehaven.intake.domain.model.Signature
import ca.safehaven.intake.domain.model.SignaturePoint
import ca.safehaven.intake.domain.model.UsState
import ca.safehaven.intake.domain.model.VisitReason
import ca.safehaven.intake.domain.model.WaiverClause
import java.time.LocalDate

/** Shared, made-up test data. Never use real people's details here (the repo is public). */
object TestFixtures {
    /** The shelter's local date in tests. */
    val TODAY: LocalDate = LocalDate.of(2026, 11, 9)

    /** A short zig-zag stroke, comfortably over the minimum point count. */
    val SIGNATURE = Signature(
        width = 600f,
        height = 200f,
        strokes = listOf(List(20) { i -> SignaturePoint(20f + i * 25f, if (i % 2 == 0) 60f else 140f) }),
    )

    val ALL_INITIALS: Map<WaiverClause, String> = WaiverClause.entries.associateWith { "JS" }

    /** A complete, valid adult waiver (born 1990). */
    val VALID_ADULT = IntakeInput(
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
        initials = ALL_INITIALS,
        signature = SIGNATURE,
    )

    /** A complete, valid waiver for a 14-year-old, signed by a guardian. */
    val VALID_MINOR = VALID_ADULT.copy(
        fullName = "Sam Smith",
        dateOfBirth = "06012012",
        guardianName = "Jane Smith",
    )
}
