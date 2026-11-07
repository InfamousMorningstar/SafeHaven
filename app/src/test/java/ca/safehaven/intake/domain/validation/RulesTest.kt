package ca.safehaven.intake.domain.validation

import ca.safehaven.intake.domain.model.IntakeConfig
import ca.safehaven.intake.domain.model.IntakeInput
import ca.safehaven.intake.domain.model.Province
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextRulesTest {
    // Pasting multi-line text into a single-line field used to hide everything after the first line.
    @Test fun singleLineTurnsLineBreaksIntoSpaces() {
        assertEquals("Jane Smith Second line", TextRules.singleLine("Jane Smith\nSecond line"))
        assertEquals("a b c", TextRules.singleLine("a\r\nb\rc"))
        assertEquals("Jane Smith", TextRules.singleLine("Jane Smith"))
    }
}

class NameRulesTest {
    @Test fun acceptsAccentsHyphensApostrophesAndDots() {
        listOf("Jane Smith", "Zoë O'Brien-Smith", "José Núñez", "J. R. Tolkien", "D’Arcy Lee", "Ng")
            .forEach { assertNull(it, NameRules.validate(it)) }
    }

    @Test fun rejectsEmptyShortLongAndSymbols() {
        assertEquals(ValidationError.REQUIRED, NameRules.validate("   "))
        assertEquals(ValidationError.TOO_SHORT, NameRules.validate("J"))
        assertEquals(ValidationError.TOO_LONG, NameRules.validate("a".repeat(101)))
        assertEquals(ValidationError.INVALID, NameRules.validate("Jane 123"))
        assertEquals(ValidationError.INVALID, NameRules.validate("-Jane"))
    }

    @Test fun normalizesWhitespaceAndFindsFirstName() {
        assertEquals("Jane Smith", NameRules.normalize("  Jane   Smith "))
        assertEquals("Jane", NameRules.firstName("  Jane   Smith "))
    }
}

class PhoneRulesTest {
    @Test fun acceptsCommonFormats() {
        listOf("403-555-0123", "+1 (403) 555 0123", "4035550123", "14035550123").forEach {
            assertNull(it, PhoneRules.validate(it))
            assertEquals("+14035550123", PhoneRules.toE164(it))
            assertEquals("(403) 555-0123", PhoneRules.formatDisplay(it))
        }
    }

    @Test fun rejectsBadAreaCodesExchangesAndLengths() {
        assertEquals(ValidationError.REQUIRED, PhoneRules.validate(""))
        assertEquals(ValidationError.INVALID, PhoneRules.validate("0035550123")) // area code starts with 0
        assertEquals(ValidationError.INVALID, PhoneRules.validate("4031550123")) // exchange starts with 1
        assertEquals(ValidationError.INVALID, PhoneRules.validate("403555012"))
        assertEquals(ValidationError.INVALID, PhoneRules.validate("24035550123")) // 11 digits not starting with 1
        assertNull(PhoneRules.toE164("403555012"))
    }

    @Test fun sanitizeInputLimitsLength() {
        assertEquals("4035550123", PhoneRules.sanitizeInput("(403) 555-01234"))
        assertEquals("14035550123", PhoneRules.sanitizeInput("+1 403 555 01234"))
    }

    @Test fun formatPartialOnlyInsertsBetweenDigits() {
        assertEquals("", PhoneRules.formatPartial(""))
        assertEquals("(40", PhoneRules.formatPartial("40"))
        assertEquals("(403", PhoneRules.formatPartial("403"))
        assertEquals("(403) 5", PhoneRules.formatPartial("4035"))
        assertEquals("(403) 555-0", PhoneRules.formatPartial("4035550"))
        assertEquals("(403) 555-0123", PhoneRules.formatPartial("4035550123"))
        assertEquals("1", PhoneRules.formatPartial("1"))
        assertEquals("1 (403) 555-0123", PhoneRules.formatPartial("14035550123"))
    }
}

class PostalCodeRulesTest {
    @Test fun acceptsAndNormalizes() {
        assertNull(PostalCodeRules.validate("T2X 1A1"))
        assertNull(PostalCodeRules.validate("t2x1a1"))
        assertEquals("T2X 1A1", PostalCodeRules.format("t2x1a1"))
        assertEquals("T2X", PostalCodeRules.format("t2x"))
        assertEquals("T2X 1", PostalCodeRules.format("T2X1"))
    }

    @Test fun rejectsInvalidLetters() {
        assertEquals(ValidationError.REQUIRED, PostalCodeRules.validate(""))
        assertEquals(ValidationError.INVALID, PostalCodeRules.validate("W1A 1A1")) // W can't be first
        assertEquals(ValidationError.INVALID, PostalCodeRules.validate("Z1A 1A1")) // Z can't be first
        assertEquals(ValidationError.INVALID, PostalCodeRules.validate("T2D 1A1")) // no D
        assertEquals(ValidationError.INVALID, PostalCodeRules.validate("T2X 1O1")) // no O
        assertEquals(ValidationError.INVALID, PostalCodeRules.validate("T2X 1A"))
    }

    @Test fun provinceMatchIsASoftCheck() {
        assertTrue(PostalCodeRules.matchesProvince("T2X1A1", Province.AB))
        assertFalse(PostalCodeRules.matchesProvince("V5K0A1", Province.AB))
        assertTrue(PostalCodeRules.matchesProvince("M5V3L9", Province.ON))
        assertTrue(PostalCodeRules.matchesProvince("", Province.AB))
    }
}

class IntakeValidatorTest {
    private val valid = IntakeInput(
        fullName = "Jane Smith",
        phone = "4035550123",
        street = "123 Example St NW",
        city = "Calgary",
        province = Province.AB,
        postalCode = "T2X1A1",
        consentGiven = true,
    )

    @Test fun validInputHasNoErrors() {
        assertEquals(emptyMap<Field, ValidationError>(), IntakeValidator.validate(valid, IntakeConfig()))
    }

    @Test fun emptyFormFlagsEveryRequiredField() {
        val errors = IntakeValidator.validate(IntakeInput(), IntakeConfig())
        assertEquals(
            setOf(Field.FULL_NAME, Field.PHONE, Field.STREET, Field.CITY, Field.POSTAL_CODE, Field.CONSENT),
            errors.keys,
        )
    }

    @Test fun consentOnlyRequiredWhenEnabled() {
        val noConsent = valid.copy(consentGiven = false)
        assertEquals(ValidationError.REQUIRED, IntakeValidator.validate(noConsent, IntakeConfig())[Field.CONSENT])
        assertTrue(IntakeValidator.validate(noConsent, IntakeConfig(consentRequired = false)).isEmpty())
    }

    @Test fun unitIsOptionalButLimited() {
        assertTrue(IntakeValidator.validate(valid.copy(unit = ""), IntakeConfig()).isEmpty())
        assertEquals(
            ValidationError.TOO_LONG,
            IntakeValidator.validate(valid.copy(unit = "x".repeat(21)), IntakeConfig())[Field.UNIT],
        )
    }
}
