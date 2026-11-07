package ca.safehaven.intake.domain

import ca.safehaven.intake.domain.model.IntakeConfig
import ca.safehaven.intake.domain.model.IntakeInput
import ca.safehaven.intake.domain.model.IntakeRecord
import ca.safehaven.intake.domain.validation.IntakeValidator
import ca.safehaven.intake.domain.validation.NameRules
import ca.safehaven.intake.domain.validation.PhoneRules
import ca.safehaven.intake.domain.validation.PostalCodeRules
import ca.safehaven.intake.domain.validation.TextRules
import java.time.Instant

object RecordFactory {
    /** Turns valid form input into a normalized record. Throws if [input] is invalid. */
    fun create(
        input: IntakeInput,
        config: IntakeConfig,
        id: String,
        recordCode: String,
        createdAt: Instant,
    ): IntakeRecord {
        val errors = IntakeValidator.validate(input, config)
        require(errors.isEmpty()) { "Invalid input for fields ${errors.keys}" }
        return IntakeRecord(
            id = id,
            recordCode = recordCode,
            createdAt = createdAt,
            fullName = NameRules.normalize(input.fullName),
            phoneE164 = checkNotNull(PhoneRules.toE164(input.phone)),
            phoneDisplay = checkNotNull(PhoneRules.formatDisplay(input.phone)),
            street = TextRules.normalize(input.street),
            unit = TextRules.normalize(input.unit).ifEmpty { null },
            city = TextRules.normalize(input.city),
            province = input.province,
            postalCode = PostalCodeRules.format(input.postalCode),
            reason = if (config.askReason) input.reason else null,
            consentGiven = config.consentRequired && input.consentGiven,
            consentTextVersion = if (config.consentRequired) config.consentTextVersion else null,
        )
    }
}
