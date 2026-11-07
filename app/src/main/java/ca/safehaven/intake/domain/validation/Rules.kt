package ca.safehaven.intake.domain.validation

import ca.safehaven.intake.domain.model.Province
import java.util.Locale

enum class Field { FULL_NAME, PHONE, STREET, UNIT, CITY, POSTAL_CODE, CONSENT }

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

    /** `+14035550123`, or null if invalid. */
    fun toE164(raw: String): String? =
        NANP.matchEntire(digits(raw))?.groupValues?.let { "+1${it[1]}${it[2]}${it[3]}" }

    /** `(403) 555-0123`, or null if invalid. */
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

object PostalCodeRules {
    // No D, F, I, O, Q, U anywhere; no W or Z as the first letter.
    private val PATTERN = Regex("^[ABCEGHJ-NPRSTVXY]\\d[ABCEGHJ-NPRSTV-Z]\\d[ABCEGHJ-NPRSTV-Z]\\d$")

    fun normalize(raw: String): String =
        raw.uppercase(Locale.ROOT).filter { it in 'A'..'Z' || it in '0'..'9' }

    fun sanitizeInput(raw: String): String = normalize(raw).take(6)

    fun validate(raw: String): ValidationError? {
        val code = normalize(raw)
        return when {
            code.isEmpty() -> ValidationError.REQUIRED
            !PATTERN.matches(code) -> ValidationError.INVALID
            else -> null
        }
    }

    /** `T2X1A1` -> `T2X 1A1`. Works on partial input too. */
    fun format(raw: String): String {
        val code = normalize(raw)
        return if (code.length > 3) code.substring(0, 3) + " " + code.substring(3) else code
    }

    /** Soft check only: true when we can't tell or the first letter fits the province. */
    fun matchesProvince(raw: String, province: Province): Boolean {
        val first = normalize(raw).firstOrNull() ?: return true
        return first in province.postalPrefixes
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
