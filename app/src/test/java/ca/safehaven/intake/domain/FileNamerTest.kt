package ca.safehaven.intake.domain

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamerTest {
    private val at = ZonedDateTime.of(2026, 11, 4, 14, 32, 10, 0, ZoneId.of("America/Edmonton"))

    @Test fun matchesPlanExample() {
        assertEquals("2026-11-04_1432_Smith-Jane_K7QF.pdf", FileNamer.fileName(at, "Jane Smith", "K7QF"))
        assertEquals("2026/11/2026-11-04_1432_Smith-Jane_K7QF.pdf", FileNamer.relativePath(at, "Jane Smith", "K7QF"))
        assertEquals("K7QF-20261104-1432", FileNamer.recordCode(at, "K7QF"))
    }

    @Test fun privacyModeLeavesNameOut() {
        assertEquals("2026-11-04_1432_K7QF.pdf", FileNamer.fileName(at, null, "K7QF"))
    }

    @Test fun slugTransliteratesAndStripsUnsafeCharacters() {
        assertEquals("OBrien-Smith-Zoe", FileNamer.nameSlug("Zoë O'Brien-Smith"))
        assertEquals("Nunez-Jose", FileNamer.nameSlug("José Núñez"))
        assertEquals("Smith-Jane", FileNamer.nameSlug("Jane 😀 Smith"))
        assertEquals("c-d-a-b", FileNamer.nameSlug("a/b\\ c:d*?\"<>|"))
        assertEquals("Strasse", FileNamer.nameSlug("Straße"))
    }

    @Test fun nonLatinNamesFallBackToNoName() {
        assertEquals("", FileNamer.nameSlug("王小明"))
        assertEquals("2026-11-04_1432_K7QF.pdf", FileNamer.fileName(at, "王小明", "K7QF"))
    }

    @Test fun slugIsCappedWithoutTrailingDash() {
        val slug = FileNamer.nameSlug("Bartholomew-Alexander Christopherson Montgomery-Wellington")
        assertTrue(slug.length <= FileNamer.MAX_SLUG_LENGTH)
        assertTrue(!slug.endsWith("-"))
    }

    @Test fun suffixUsesCrockfordAlphabet() {
        val random = Random(42)
        val suffixes = List(200) { FileNamer.randomSuffix(random) }
        suffixes.forEach { assertTrue(it, Regex("^[0-9A-HJKMNP-TV-Z]{4}$").matches(it)) }
        assertNotEquals(1, suffixes.toSet().size)
    }

    @Test fun usesLocalTimeAcrossDstChange() {
        // 2026-11-01 is the MDT -> MST change in Alberta; 08:30 UTC is 01:30 MST after the switch.
        val utc = ZonedDateTime.of(2026, 11, 1, 8, 30, 0, 0, ZoneId.of("UTC"))
        val local = utc.withZoneSameInstant(ZoneId.of("America/Edmonton"))
        assertEquals("2026-11-01_0130_K7QF.pdf", FileNamer.fileName(local, null, "K7QF"))
    }
}
