package ca.safehaven.intake.data.files

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class IntakeFileStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store() = IntakeFileStore(File(tmp.root, "intake"))

    @Test fun writesFileWithChecksumAndNoTempLeftovers() {
        val store = store()
        val bytes = "%PDF-test".toByteArray()

        val written = store.writeAtomically("2026/11/a.pdf") { it.write(bytes) }

        assertTrue(written.file.isFile)
        assertEquals("%PDF-test", written.file.readText())
        val expected = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals(expected, written.sha256)
        assertTrue(File(tmp.root, "intake/.tmp").listFiles().orEmpty().isEmpty())
    }

    @Test fun failedWriteLeavesNothingBehind() {
        val store = store()
        try {
            store.writeAtomically("2026/11/b.pdf") {
                it.write(1)
                throw IOException("power cut")
            }
            fail("expected IOException")
        } catch (expected: IOException) {
        }
        assertFalse(File(tmp.root, "intake/2026/11/b.pdf").exists())
        assertTrue(File(tmp.root, "intake/.tmp").listFiles().orEmpty().isEmpty())
    }

    @Test(expected = FileAlreadyExistsException::class)
    fun refusesToOverwrite() {
        val store = store()
        store.writeAtomically("2026/11/c.pdf") { it.write(1) }
        store.writeAtomically("2026/11/c.pdf") { it.write(2) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesPathsOutsideRoot() {
        store().resolve("../escape.pdf")
    }

    @Test fun clearTempRemovesPartialFiles() {
        val store = store()
        val tempDir = File(tmp.root, "intake/.tmp").apply { mkdirs() }
        File(tempDir, "intake-123.part").writeText("half")
        store.clearTemp()
        assertTrue(tempDir.listFiles().orEmpty().isEmpty())
    }
}
