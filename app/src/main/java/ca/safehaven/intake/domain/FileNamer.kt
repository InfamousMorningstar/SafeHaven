package ca.safehaven.intake.domain

import java.text.Normalizer
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

/**
 * File naming from plan 3.6: `YYYY-MM-DD_HHmm_Lastname-Firstname_XXXX.pdf`, stored under
 * `YYYY/MM/`. XXXX is a random Crockford Base32 suffix so two visitors with the same name in
 * the same minute don't collide.
 */
object FileNamer {
    private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val SUFFIX_LENGTH = 4
    const val MAX_SLUG_LENGTH = 40

    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    private val TIME = DateTimeFormatter.ofPattern("HHmm", Locale.ROOT)
    private val COMPACT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
    private val MONTH_DIR = DateTimeFormatter.ofPattern("yyyy/MM", Locale.ROOT)

    private val WHITESPACE = Regex("\\s+")
    private val MARKS = Regex("\\p{M}+")
    private val DROPPED = Regex("['’.]")
    private val NON_ALNUM = Regex("[^A-Za-z0-9]")
    private val MULTI_DASH = Regex("-{2,}")

    fun randomSuffix(random: Random): String = buildString {
        repeat(SUFFIX_LENGTH) { append(CROCKFORD[random.nextInt(CROCKFORD.length)]) }
    }

    /** `K7QF-20261104-1432` */
    fun recordCode(at: ZonedDateTime, suffix: String): String =
        "$suffix-${COMPACT_DATE.format(at)}-${TIME.format(at)}"

    /** `2026/11` */
    fun monthDirectory(at: ZonedDateTime): String = MONTH_DIR.format(at)

    /** Pass `fullName = null` to leave the visitor's name out of the file name. */
    fun fileName(at: ZonedDateTime, fullName: String?, suffix: String): String {
        val slug = fullName?.let(::nameSlug).orEmpty()
        return listOfNotNull(DATE.format(at), TIME.format(at), slug.ifEmpty { null }, suffix)
            .joinToString("_") + ".pdf"
    }

    fun relativePath(at: ZonedDateTime, fullName: String?, suffix: String): String =
        monthDirectory(at) + "/" + fileName(at, fullName, suffix)

    /** `Jane Smith` -> `Smith-Jane`. Empty when nothing file-safe is left (e.g. non-Latin script). */
    fun nameSlug(fullName: String): String {
        val tokens = fullName.trim().split(WHITESPACE).map(::sanitizeToken).filter { it.isNotEmpty() }
        val ordered = if (tokens.size <= 1) tokens else listOf(tokens.last()) + tokens.dropLast(1)
        return ordered.joinToString("-").take(MAX_SLUG_LENGTH).trimEnd('-')
    }

    internal fun sanitizeToken(token: String): String {
        val ascii = Normalizer.normalize(token, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .replace("ß", "ss").replace("æ", "ae").replace("Æ", "AE")
            .replace("ø", "o").replace("Ø", "O").replace("ł", "l").replace("Ł", "L")
        return ascii
            .replace(DROPPED, "")
            .replace(NON_ALNUM, "-")
            .replace(MULTI_DASH, "-")
            .trim('-')
    }
}
