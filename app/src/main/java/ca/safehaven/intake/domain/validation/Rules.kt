package ca.safehaven.intake.domain.validation

import ca.safehaven.intake.domain.model.Signature
import ca.safehaven.intake.domain.model.WaiverClause
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Field {
    FULL_NAME, STREET, CITY, ZIP, EMAIL, PHONE, REASON, HEARD_ABOUT, DATE_OF_BIRTH, GUARDIAN_NAME,
    INITIALS_NO_MEDICATION, INITIALS_GOOD_HEALTH, INITIALS_NO_COMMUNICABLE_DISEASE,
    INITIALS_INDEMNIFY, INITIALS_NO_NURSE, INITIALS_FACTS_TRUE,
    SIGNATURE,
}

/** The form field holding the initials for this clause. */
val WaiverClause.initialsField: Field
    get() = when (this) {
        WaiverClause.NO_MEDICATION -> Field.INITIALS_NO_MEDICATION
        WaiverClause.GOOD_HEALTH -> Field.INITIALS_GOOD_HEALTH
        WaiverClause.NO_COMMUNICABLE_DISEASE -> Field.INITIALS_NO_COMMUNICABLE_DISEASE
        WaiverClause.INDEMNIFY -> Field.INITIALS_INDEMNIFY
        WaiverClause.NO_NURSE -> Field.INITIALS_NO_NURSE
        WaiverClause.FACTS_TRUE -> Field.INITIALS_FACTS_TRUE
    }

enum class ValidationError { REQUIRED, TOO_SHORT, TOO_LONG, INVALID }

internal val WHITESPACE = Regex("\\s+")

object NameRules {
    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 100

    // Unicode letters (é, ñ, ...) plus spaces, straight/curly apostrophes, dots and hyphens.
    private val PATTERN = Regex("^[\\p{L}][\\p{L}\\p{M}'’ .-]*$")

    fun normalize(raw: String): String = raw.trim().replace(WHITESPACE, " ")

    fun validate(raw: String): ValidationError? {
        val name = normalize(raw)
        return when {
            name.isEmpty() -> ValidationError.REQUIRED
            name.length < MIN_LENGTH -> ValidationError.TOO_SHORT
            name.length > MAX_LENGTH -> ValidationError.TOO_LONG
            !PATTERN.matches(name) -> ValidationError.INVALID
            else -> null
        }
    }

    fun firstName(raw: String): String = normalize(raw).substringBefore(' ')
}

object PhoneRules {
    // NANP: optional leading 1, area code and exchange can't start with 0 or 1.
    private val NANP = Regex("^1?([2-9]\\d{2})([2-9]\\d{2})(\\d{4})$")

    fun digits(raw: String): String = raw.filter { it in '0'..'9' }

    /** Keeps what a visitor types to digits, at most 10 (or 11 with a leading 1). */
    fun sanitizeInput(raw: String): String {
        val d = digits(raw)
        return d.take(if (d.startsWith("1")) 11 else 10)
    }

    fun validate(raw: String): ValidationError? {
        val d = digits(raw)
        return when {
            d.isEmpty() -> ValidationError.REQUIRED
            !NANP.matches(d) -> ValidationError.INVALID
            else -> null
        }
    }

    /** `+16025550123`, or null if invalid. */
    fun toE164(raw: String): String? =
        NANP.matchEntire(digits(raw))?.groupValues?.let { "+1${it[1]}${it[2]}${it[3]}" }

    /** `(602) 555-0123`, or null if invalid. */
    fun formatDisplay(raw: String): String? =
        NANP.matchEntire(digits(raw))?.groupValues?.let { "(${it[1]}) ${it[2]}-${it[3]}" }

    /**
     * Formats a partly typed number while the visitor types. Only inserts separators between
     * digits (never after the last one), so the cursor mapping in the UI stays simple.
     */
    fun formatPartial(digits: String): String {
        val sb = StringBuilder()
        var national = digits
        if (digits.startsWith("1")) {
            sb.append('1')
            national = digits.drop(1)
            if (national.isNotEmpty()) sb.append(' ')
        }
        when {
            national.isEmpty() -> Unit
            national.length <= 3 -> sb.append('(').append(national)
            national.length <= 6 -> sb.append('(').append(national, 0, 3).append(") ").append(national, 3, national.length)
            else -> sb.append('(').append(national, 0, 3).append(") ")
                .append(national, 3, 6).append('-').append(national, 6, national.length)
        }
        return sb.toString()
    }
}

/** US ZIP or ZIP+4. The UI keeps digits only; the dash is display formatting. */
object ZipRules {
    fun sanitizeInput(raw: String): String = raw.filter { it in '0'..'9' }.take(9)

    fun validate(raw: String): ValidationError? {
        val d = sanitizeInput(raw)
        return when {
            d.isEmpty() -> ValidationError.REQUIRED
            d.length != 5 && d.length != 9 -> ValidationError.INVALID
            else -> null
        }
    }

    /** `12345` -> `12345`, `123456789` -> `12345-6789`. Works on partial input too. */
    fun format(raw: String): String {
        val d = sanitizeInput(raw)
        return if (d.length > 5) d.substring(0, 5) + "-" + d.substring(5) else d
    }
}

/** Email is optional (not every visitor has one), but must look like an address if given. */
object EmailRules {
    const val MAX_LENGTH = 254
    private val PATTERN = Regex("^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)*\\.[^\\s@.]{2,}$")

    /** Spaces are never valid in an address, so they are dropped while typing. */
    fun sanitizeInput(raw: String): String = raw.filterNot { it.isWhitespace() }

    fun normalize(raw: String): String = sanitizeInput(raw)

    fun validate(raw: String): ValidationError? {
        val email = normalize(raw)
        return when {
            email.isEmpty() -> null
            email.length > MAX_LENGTH -> ValidationError.TOO_LONG
            !PATTERN.matches(email) -> ValidationError.INVALID
            else -> null
        }
    }
}

/** Date of birth, typed as 8 digits `MMDDYYYY` and shown as `MM/DD/YYYY`. */
object DateOfBirthRules {
    const val MAX_AGE_YEARS = 120
    private val DISPLAY = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)

    fun sanitizeInput(raw: String): String = raw.filter { it in '0'..'9' }.take(8)

    /** The date, or null when [raw] isn't 8 digits forming a real calendar date. */
    fun parse(raw: String): LocalDate? {
        val d = sanitizeInput(raw)
        if (d.length != 8) return null
        return try {
            LocalDate.of(d.substring(4, 8).toInt(), d.substring(0, 2).toInt(), d.substring(2, 4).toInt())
        } catch (e: DateTimeException) {
            null
        }
    }

    fun validate(raw: String, today: LocalDate): ValidationError? {
        if (sanitizeInput(raw).isEmpty()) return ValidationError.REQUIRED
        val dob = parse(raw) ?: return ValidationError.INVALID
        return when {
            dob.isAfter(today) -> ValidationError.INVALID
            dob.isBefore(today.minusYears(MAX_AGE_YEARS.toLong())) -> ValidationError.INVALID
            else -> null
        }
    }

    fun ageOn(dob: LocalDate, today: LocalDate): Int = Period.between(dob, today).years

    /** False while the date is incomplete or invalid, so the guardian section only appears once it's real. */
    fun isMinor(raw: String, today: LocalDate, adultAge: Int): Boolean {
        if (validate(raw, today) != null) return false
        return ageOn(checkNotNull(parse(raw)), today) < adultAge
    }

    /** Inserts slashes while typing: `0315` -> `03/15`, `03152010` -> `03/15/2010`. */
    fun formatPartial(digits: String): String = buildString {
        digits.forEachIndexed { i, c ->
            if (i == 2 || i == 4) append('/')
            append(c)
        }
    }

    fun formatDisplay(date: LocalDate): String = DISPLAY.format(date)
}

/** Typed initials: letters only, upper case, 1 to 4 of them. */
object InitialsRules {
    const val MAX_LENGTH = 4

    fun sanitizeInput(raw: String): String =
        raw.filter { it.isLetter() }.uppercase(Locale.ROOT).take(MAX_LENGTH)

    fun validate(raw: String?): ValidationError? =
        if (sanitizeInput(raw.orEmpty()).isEmpty()) ValidationError.REQUIRED else null
}

object SignatureRules {
    /** Fewer points than this is a tap or a dot, not a signature. */
    const val MIN_POINTS = 10

    /** The ink must span at least this share of the pad's width, or [MIN_HEIGHT_FRACTION] of its height. */
    const val MIN_WIDTH_FRACTION = 0.08f
    const val MIN_HEIGHT_FRACTION = 0.3f

    fun validate(signature: Signature): ValidationError? = when {
        signature.pointCount == 0 -> ValidationError.REQUIRED
        signature.pointCount < MIN_POINTS -> ValidationError.TOO_SHORT
        isTiny(signature) -> ValidationError.TOO_SHORT
        else -> null
    }

    /** A small scribble can have many points and still print as a dot on the PDF. */
    private fun isTiny(signature: Signature): Boolean {
        if (signature.width <= 0f || signature.height <= 0f) return false
        val points = signature.strokes.flatten()
        val spanX = points.maxOf { it.x } - points.minOf { it.x }
        val spanY = points.maxOf { it.y } - points.minOf { it.y }
        return spanX < signature.width * MIN_WIDTH_FRACTION && spanY < signature.height * MIN_HEIGHT_FRACTION
    }
}

object TextRules {
    private val LINE_BREAKS = Regex("\\r\\n|[\\r\\n]")

    fun normalize(raw: String): String = raw.trim().replace(WHITESPACE, " ")

    /**
     * Single-line fields don't strip line breaks from pasted text, so anything after the first
     * line would be invisible yet still validated and saved. Turn each break into a space.
     */
    fun singleLine(raw: String): String = raw.replace(LINE_BREAKS, " ")

    fun validate(raw: String, required: Boolean, minLength: Int, maxLength: Int): ValidationError? {
        val text = normalize(raw)
        return when {
            text.isEmpty() -> if (required) ValidationError.REQUIRED else null
            text.length < minLength -> ValidationError.TOO_SHORT
            text.length > maxLength -> ValidationError.TOO_LONG
            else -> null
        }
    }
}
