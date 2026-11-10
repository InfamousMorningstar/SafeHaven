package ca.safehaven.intake.domain.validation

import ca.safehaven.intake.domain.model.IntakeConfig
import ca.safehaven.intake.domain.model.IntakeInput
import ca.safehaven.intake.domain.model.WaiverClause
import java.time.LocalDate

object IntakeValidator {
    const val STREET_MIN = 3
    const val STREET_MAX = 120
    const val CITY_MIN = 2
    const val CITY_MAX = 60
    const val HEARD_ABOUT_MAX = 120

    /**
     * Returns one error per invalid field; an empty map means the form can be submitted.
     * [today] is the shelter's local date, used for the date of birth and the under-18 rule.
     */
    fun validate(input: IntakeInput, config: IntakeConfig, today: LocalDate): Map<Field, ValidationError> = buildMap {
        NameRules.validate(input.fullName)?.let { put(Field.FULL_NAME, it) }
        TextRules.validate(input.street, required = true, minLength = STREET_MIN, maxLength = STREET_MAX)
            ?.let { put(Field.STREET, it) }
        TextRules.validate(input.city, required = true, minLength = CITY_MIN, maxLength = CITY_MAX)
            ?.let { put(Field.CITY, it) }
        ZipRules.validate(input.zip)?.let { put(Field.ZIP, it) }
        EmailRules.validate(input.email)?.let { put(Field.EMAIL, it) }
        PhoneRules.validate(input.phone)?.let { put(Field.PHONE, it) }
        if (input.reason == null) put(Field.REASON, ValidationError.REQUIRED)
        TextRules.validate(input.heardAbout, required = false, minLength = 0, maxLength = HEARD_ABOUT_MAX)
            ?.let { put(Field.HEARD_ABOUT, it) }
        DateOfBirthRules.validate(input.dateOfBirth, today)?.let { put(Field.DATE_OF_BIRTH, it) }
        if (DateOfBirthRules.isMinor(input.dateOfBirth, today, config.adultAge)) {
            NameRules.validate(input.guardianName)?.let { put(Field.GUARDIAN_NAME, it) }
        }
        WaiverClause.entries.forEach { clause ->
            InitialsRules.validate(input.initials[clause])?.let { put(clause.initialsField, it) }
        }
        SignatureRules.validate(input.signature)?.let { put(Field.SIGNATURE, it) }
    }
}
