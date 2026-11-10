package ca.safehaven.intake.domain

import ca.safehaven.intake.domain.model.IntakeConfig
import ca.safehaven.intake.domain.model.IntakeInput
import ca.safehaven.intake.domain.model.IntakeRecord
import ca.safehaven.intake.domain.model.Waiver
import ca.safehaven.intake.domain.model.WaiverClause
import ca.safehaven.intake.domain.validation.DateOfBirthRules
import ca.safehaven.intake.domain.validation.EmailRules
import ca.safehaven.intake.domain.validation.InitialsRules
import ca.safehaven.intake.domain.validation.IntakeValidator
import ca.safehaven.intake.domain.validation.NameRules
import ca.safehaven.intake.domain.validation.PhoneRules
import ca.safehaven.intake.domain.validation.TextRules
import ca.safehaven.intake.domain.validation.ZipRules
import java.time.Instant
import java.time.LocalDate

object RecordFactory {
    /**
     * Turns valid form input into a normalized record. Throws if [input] is invalid.
     * [signedOn] is the shelter's local date at [createdAt].
     */
    fun create(
        input: IntakeInput,
        config: IntakeConfig,
        id: String,
        sequenceNumber: Long,
        createdAt: Instant,
        signedOn: LocalDate,
    ): IntakeRecord {
        val errors = IntakeValidator.validate(input, config, signedOn)
        require(errors.isEmpty()) { "Invalid input for fields ${errors.keys}" }
        val isMinor = DateOfBirthRules.isMinor(input.dateOfBirth, signedOn, config.adultAge)
        return IntakeRecord(
            id = id,
            sequenceNumber = sequenceNumber,
            recordCode = FileNamer.recordCode(sequenceNumber),
            createdAt = createdAt,
            fullName = NameRules.normalize(input.fullName),
            street = TextRules.normalize(input.street),
            city = TextRules.normalize(input.city),
            state = input.state,
            zip = ZipRules.format(input.zip),
            email = EmailRules.normalize(input.email).ifEmpty { null },
            phoneE164 = checkNotNull(PhoneRules.toE164(input.phone)),
            phoneDisplay = checkNotNull(PhoneRules.formatDisplay(input.phone)),
            reason = checkNotNull(input.reason),
            heardAbout = TextRules.normalize(input.heardAbout).ifEmpty { null },
            dateOfBirth = checkNotNull(DateOfBirthRules.parse(input.dateOfBirth)),
            signedOn = signedOn,
            isMinor = isMinor,
            guardianName = if (isMinor) NameRules.normalize(input.guardianName) else null,
            initials = WaiverClause.entries.associateWith {
                InitialsRules.sanitizeInput(checkNotNull(input.initials[it]))
            },
            signature = input.signature,
            waiverVersion = Waiver.VERSION,
        )
    }
}
