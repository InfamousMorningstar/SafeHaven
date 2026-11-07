package ca.safehaven.intake.domain.validation

import ca.safehaven.intake.domain.model.IntakeConfig
import ca.safehaven.intake.domain.model.IntakeInput

object IntakeValidator {
    const val STREET_MIN = 3
    const val STREET_MAX = 120
    const val UNIT_MAX = 20
    const val CITY_MIN = 2
    const val CITY_MAX = 60

    /** Returns one error per invalid field; an empty map means the form can be submitted. */
    fun validate(input: IntakeInput, config: IntakeConfig): Map<Field, ValidationError> = buildMap {
        NameRules.validate(input.fullName)?.let { put(Field.FULL_NAME, it) }
        PhoneRules.validate(input.phone)?.let { put(Field.PHONE, it) }
        TextRules.validate(input.street, required = true, minLength = STREET_MIN, maxLength = STREET_MAX)
            ?.let { put(Field.STREET, it) }
        TextRules.validate(input.unit, required = false, minLength = 0, maxLength = UNIT_MAX)
            ?.let { put(Field.UNIT, it) }
        TextRules.validate(input.city, required = true, minLength = CITY_MIN, maxLength = CITY_MAX)
            ?.let { put(Field.CITY, it) }
        PostalCodeRules.validate(input.postalCode)?.let { put(Field.POSTAL_CODE, it) }
        if (config.consentRequired && !input.consentGiven) put(Field.CONSENT, ValidationError.REQUIRED)
    }
}
