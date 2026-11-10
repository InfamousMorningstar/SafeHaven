package ca.safehaven.intake.domain.validation

import ca.safehaven.intake.TestFixtures
import ca.safehaven.intake.TestFixtures.TODAY
import ca.safehaven.intake.domain.model.IntakeConfig
import ca.safehaven.intake.domain.model.IntakeInput
import ca.safehaven.intake.domain.model.Signature
import ca.safehaven.intake.domain.model.SignaturePoint
import ca.safehaven.intake.domain.model.WaiverClause
import java.time.LocalDate
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
        listOf("602-555-0123", "+1 (602) 555 0123", "6025550123", "16025550123").forEach {
            assertNull(it, PhoneRules.validate(it))
            assertEquals("+16025550123", PhoneRules.toE164(it))
            assertEquals("(602) 555-0123", PhoneRules.formatDisplay(it))
        }
    }

    @Test fun rejectsBadAreaCodesExchangesAndLengths() {
        assertEquals(ValidationError.REQUIRED, PhoneRules.validate(""))
        assertEquals(ValidationError.INVALID, PhoneRules.validate("0025550123")) // area code starts with 0
        assertEquals(ValidationError.INVALID, PhoneRules.validate("6021550123")) // exchange starts with 1
        assertEquals(ValidationError.INVALID, PhoneRules.validate("602555012"))
        assertEquals(ValidationError.INVALID, PhoneRules.validate("26025550123")) // 11 digits not starting with 1
        assertNull(PhoneRules.toE164("602555012"))
    }

    @Test fun sanitizeInputLimitsLength() {
        assertEquals("6025550123", PhoneRules.sanitizeInput("(602) 555-01234"))
        assertEquals("16025550123", PhoneRules.sanitizeInput("+1 602 555 01234"))
    }

    @Test fun formatPartialOnlyInsertsBetweenDigits() {
        assertEquals("", PhoneRules.formatPartial(""))
        assertEquals("(60", PhoneRules.formatPartial("60"))
        assertEquals("(602", PhoneRules.formatPartial("602"))
        assertEquals("(602) 5", PhoneRules.formatPartial("6025"))
        assertEquals("(602) 555-0", PhoneRules.formatPartial("6025550"))
        assertEquals("(602) 555-0123", PhoneRules.formatPartial("6025550123"))
        assertEquals("1", PhoneRules.formatPartial("1"))
        assertEquals("1 (602) 555-0123", PhoneRules.formatPartial("16025550123"))
    }
}

class ZipRulesTest {
    @Test fun acceptsFiveAndNineDigits() {
        assertNull(ZipRules.validate("85001"))
        assertNull(ZipRules.validate("850011234"))
        assertNull(ZipRules.validate("85001-1234"))
    }

    @Test fun rejectsOtherLengths() {
        assertEquals(ValidationError.REQUIRED, ZipRules.validate(""))
        assertEquals(ValidationError.INVALID, ZipRules.validate("8500"))
        assertEquals(ValidationError.INVALID, ZipRules.validate("850011"))
    }

    @Test fun sanitizesAndFormats() {
        assertEquals("850011234", ZipRules.sanitizeInput("85001-12345"))
        assertEquals("85001", ZipRules.format("85001"))
        assertEquals("85001-1", ZipRules.format("850011"))
        assertEquals("85001-1234", ZipRules.format("850011234"))
    }
}

class EmailRulesTest {
    @Test fun optionalButMustLookLikeAnAddress() {
        assertNull(EmailRules.validate(""))
        assertNull(EmailRules.validate("jane@example.com"))
        assertNull(EmailRules.validate("jane.smith+shfa@mail.example.org"))
        assertEquals(ValidationError.INVALID, EmailRules.validate("jane"))
        assertEquals(ValidationError.INVALID, EmailRules.validate("jane@example"))
        assertEquals(ValidationError.INVALID, EmailRules.validate("jane@@example.com"))
        assertEquals(ValidationError.INVALID, EmailRules.validate("jane@example.c"))
        assertEquals(ValidationError.INVALID, EmailRules.validate("jane@.com"))
    }

    @Test fun dropsSpaces() {
        assertEquals("jane@example.com", EmailRules.sanitizeInput(" jane @example.com "))
    }
}

class DateOfBirthRulesTest {
    @Test fun parsesRealDatesOnly() {
        assertEquals(LocalDate.of(1990, 3, 15), DateOfBirthRules.parse("03151990"))
        assertNull(DateOfBirthRules.parse("02302000")) // Feb 30
        assertNull(DateOfBirthRules.parse("13012000")) // month 13
        assertNull(DateOfBirthRules.parse("0315199"))
        assertEquals(LocalDate.of(2024, 2, 29), DateOfBirthRules.parse("02292024")) // leap day
    }

    @Test fun rejectsFutureAndImplausibleDates() {
        assertEquals(ValidationError.REQUIRED, DateOfBirthRules.validate("", TODAY))
        assertEquals(ValidationError.INVALID, DateOfBirthRules.validate("0315", TODAY))
        assertEquals(ValidationError.INVALID, DateOfBirthRules.validate("11102026", TODAY)) // tomorrow
        assertEquals(ValidationError.INVALID, DateOfBirthRules.validate("01011900", TODAY)) // over 120
        assertNull(DateOfBirthRules.validate("11092026", TODAY)) // born today
    }

    @Test fun under18SwitchesOnTheBirthday() {
        // Turns 18 on 2026-11-09 (TODAY): an adult.
        assertFalse(DateOfBirthRules.isMinor("11092008", TODAY, 18))
        // Turns 18 tomorrow: still a minor.
        assertTrue(DateOfBirthRules.isMinor("11102008", TODAY, 18))
        // Incomplete or invalid dates never count as a minor.
        assertFalse(DateOfBirthRules.isMinor("1110", TODAY, 18))
        assertFalse(DateOfBirthRules.isMinor("02302012", TODAY, 18))
    }

    @Test fun formats() {
        assertEquals("", DateOfBirthRules.formatPartial(""))
        assertEquals("03", DateOfBirthRules.formatPartial("03"))
        assertEquals("03/1", DateOfBirthRules.formatPartial("031"))
        assertEquals("03/15/1990", DateOfBirthRules.formatPartial("03151990"))
        assertEquals("03/15/1990", DateOfBirthRules.formatDisplay(LocalDate.of(1990, 3, 15)))
    }
}

class InitialsRulesTest {
    @Test fun lettersOnlyUpperCaseAtMostFour() {
        assertEquals("JS", InitialsRules.sanitizeInput("j.s"))
        assertEquals("JRRT", InitialsRules.sanitizeInput("jrrtx"))
        assertEquals("ZÖ", InitialsRules.sanitizeInput("zö"))
        assertEquals(ValidationError.REQUIRED, InitialsRules.validate(null))
        assertEquals(ValidationError.REQUIRED, InitialsRules.validate(" 1 "))
        assertNull(InitialsRules.validate("JS"))
    }
}

class SignatureRulesTest {
    @Test fun emptyOrTinySignaturesAreRejected() {
        assertEquals(ValidationError.REQUIRED, SignatureRules.validate(Signature.EMPTY))
        val dot = Signature(600f, 200f, listOf(listOf(SignaturePoint(1f, 1f), SignaturePoint(2f, 2f))))
        assertEquals(ValidationError.TOO_SHORT, SignatureRules.validate(dot))
        assertNull(SignatureRules.validate(TestFixtures.SIGNATURE))
    }
}

class IntakeValidatorTest {
    private val config = IntakeConfig()

    private fun validate(input: IntakeInput) = IntakeValidator.validate(input, config, TODAY)

    @Test fun validAdultAndMinorHaveNoErrors() {
        assertEquals(emptyMap<Field, ValidationError>(), validate(TestFixtures.VALID_ADULT))
        assertEquals(emptyMap<Field, ValidationError>(), validate(TestFixtures.VALID_MINOR))
    }

    @Test fun emptyFormFlagsEveryRequiredField() {
        val expected = setOf(
            Field.FULL_NAME, Field.STREET, Field.CITY, Field.ZIP, Field.PHONE, Field.REASON,
            Field.DATE_OF_BIRTH, Field.SIGNATURE,
        ) + WaiverClause.entries.map { it.initialsField }
        assertEquals(expected, validate(IntakeInput()).keys)
    }

    @Test fun guardianNameOnlyRequiredForMinors() {
        assertEquals(
            ValidationError.REQUIRED,
            validate(TestFixtures.VALID_MINOR.copy(guardianName = ""))[Field.GUARDIAN_NAME],
        )
        // An adult with a leftover guardian name is fine; it's ignored.
        assertTrue(validate(TestFixtures.VALID_ADULT.copy(guardianName = "")).isEmpty())
    }

    @Test fun eachMissingInitialIsItsOwnError() {
        val missing = TestFixtures.VALID_ADULT.copy(
            initials = TestFixtures.ALL_INITIALS - WaiverClause.NO_NURSE,
        )
        assertEquals(mapOf(Field.INITIALS_NO_NURSE to ValidationError.REQUIRED), validate(missing))
    }

    @Test fun everyClauseHasADistinctField() {
        assertEquals(WaiverClause.entries.size, WaiverClause.entries.map { it.initialsField }.toSet().size)
    }
}
