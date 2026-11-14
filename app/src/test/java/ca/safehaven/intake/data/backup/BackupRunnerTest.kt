package ca.safehaven.intake.data.backup

import ca.safehaven.intake.data.db.SubmissionDao
import ca.safehaven.intake.data.db.SubmissionEntity
import ca.safehaven.intake.data.files.IntakeFileStore
import ca.safehaven.intake.staff.BackupResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupRunnerTest {

    @get:Rule val tmp = TemporaryFolder()

    private val now = Instant.parse("2026-11-10T18:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var fileStore: IntakeFileStore
    private lateinit var root: File
    private val dao = FakeDao()

    @Before fun setUp() {
        root = tmp.newFolder("intake")
        fileStore = IntakeFileStore(root)
    }

    // --- Fakes ---

    private class FakeDao : SubmissionDao {
        val rows = LinkedHashMap<String, SubmissionEntity>()
        var markFailure: Exception? = null

        override suspend fun pendingBackup() =
            rows.values.filter { it.backedUpAt == null }.sortedBy { it.sequenceNumber }

        override suspend fun markBackedUp(id: String, at: Instant) {
            markFailure?.let { throw it }
            rows[id] = rows.getValue(id).copy(backedUpAt = at)
        }

        override suspend fun insert(submission: SubmissionEntity) {
            rows[submission.id] = submission
        }

        override fun observeAll(): Flow<List<SubmissionEntity>> = emptyFlow()
        override fun observeCount(): Flow<Int> = emptyFlow()
        override suspend fun countByPdfPath(pdfPath: String) = rows.values.count { it.pdfPath == pdfPath }
        override suspend fun markAllPendingBackup() = Unit
        override suspend fun getById(id: String) = rows[id]
        override fun search(query: String, digits: String): Flow<List<SubmissionEntity>> = emptyFlow()
        override suspend fun deleteById(id: String) = if (rows.remove(id) != null) 1 else 0
        override fun observePendingBackupCount(): Flow<Int> = emptyFlow()
        override fun observeLastBackupAt(): Flow<Instant?> = emptyFlow()
        override suspend fun markExported(id: String, at: Instant) = Unit
    }

    /** An in-memory folder. Files that are created but never closed count as half-written. */
    private class FakeFolder : BackupFolder {
        val files = LinkedHashMap<String, ByteArray>()
        val reportedSizes = HashMap<String, Long?>()
        var listFailure: Exception? = null
        /** Throws on the n-th createFile (1-based) while writing. */
        var failWriteOnCreate = 0
        var unavailableOnCreate = 0
        var creates = 0

        override fun listFiles(): List<RemoteFile> {
            listFailure?.let { throw it }
            return files.map { (name, bytes) ->
                RemoteFile(name, if (name in reportedSizes) reportedSizes[name] else bytes.size.toLong())
            }
        }

        override fun createFile(name: String, mimeType: String): BackupTarget {
            creates++
            if (creates == unavailableOnCreate) throw FolderUnavailableException(SecurityException())
            assertEquals("application/pdf", mimeType)
            var actual = name
            var n = 1
            while (actual in files) actual = name.removeSuffix(".pdf") + " (${n++}).pdf"
            files[actual] = ByteArray(0)
            val failThis = creates == failWriteOnCreate
            val key = actual
            return object : BackupTarget {
                override fun openOutputStream(): OutputStream = object : ByteArrayOutputStream() {
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        if (failThis) throw IOException("disk full")
                        super.write(b, off, len)
                    }
                    override fun close() {
                        files[key] = toByteArray()
                    }
                }
                override fun delete() {
                    files.remove(key)
                }
            }
        }
    }

    // --- Helpers ---

    private fun addRow(seq: Long, writePdf: Boolean = true): SubmissionEntity {
        val code = seq.toString().padStart(4, '0')
        val path = "2026/11/2026-11-09_$code.pdf"
        if (writePdf) fileStore.writeAtomically(path) { it.write(pdfBytes(seq)) }
        val row = SubmissionEntity(
            id = "id-$seq",
            sequenceNumber = seq,
            recordCode = code,
            createdAt = Instant.parse("2026-11-09T17:00:00Z").plusSeconds(seq),
            signedOn = LocalDate.of(2026, 11, 9),
            fullName = "Test Person $seq",
            phoneE164 = "+16025550100",
            email = null,
            city = "Phoenix",
            state = "AZ",
            zip = "85001",
            reason = "VISITING",
            dateOfBirth = LocalDate.of(1990, 1, 1),
            isMinor = false,
            guardianName = null,
            waiverVersion = "test",
            pdfPath = path,
            pdfSha256 = "0".repeat(64),
        )
        dao.rows[row.id] = row
        return row
    }

    private fun pdfBytes(seq: Long) = ByteArray(100_000 + seq.toInt()) { (it * 31 + seq).toByte() }

    private fun runner() = BackupRunner(dao, fileStore, clock)

    private fun backedUp(seq: Long) = dao.rows.getValue("id-$seq").backedUpAt

    // --- Tests ---

    @Test fun noFolder() = runTest {
        addRow(1)
        val outcome = runner().run(null)
        assertEquals(BackupResult.NoFolder, outcome.result)
        assertNull(backedUp(1))
    }

    @Test fun nothingPendingIsDoneZero() = runTest {
        assertEquals(BackupResult.Done(0), runner().run(FakeFolder()).result)
    }

    @Test fun copiesEachPendingPdfAndMarksIt() = runTest {
        addRow(1); addRow(2); addRow(3)
        val folder = FakeFolder()

        val outcome = runner().run(folder)

        assertEquals(BackupResult.Done(3), outcome.result)
        assertEquals(listOf("2026-11-09_0001.pdf", "2026-11-09_0002.pdf", "2026-11-09_0003.pdf"), folder.files.keys.toList())
        for (seq in 1L..3L) {
            assertArrayEquals(pdfBytes(seq), folder.files.getValue("2026-11-09_000$seq.pdf"))
            assertEquals(now, backedUp(seq))
        }
        // A second run has nothing left to do.
        assertEquals(BackupResult.Done(0), runner().run(folder).result)
        assertEquals(3, folder.creates)
    }

    @Test fun alreadyBackedUpRowsAreNotCopiedAgain() = runTest {
        addRow(1)
        dao.rows["id-1"] = dao.rows.getValue("id-1").copy(backedUpAt = Instant.EPOCH)
        addRow(2)
        val folder = FakeFolder()
        assertEquals(BackupResult.Done(1), runner().run(folder).result)
        assertEquals(listOf("2026-11-09_0002.pdf"), folder.files.keys.toList())
    }

    @Test fun oneFailedFileStaysPendingAndTheRestAreStillCopied() = runTest {
        addRow(1); addRow(2); addRow(3)
        val folder = FakeFolder().apply { failWriteOnCreate = 2 }

        val result = runner().run(folder).result

        assertTrue(result is BackupResult.Failed)
        result as BackupResult.Failed
        assertEquals(2, result.copied)
        assertTrue(result.cause is IOException)
        assertEquals(now, backedUp(1))
        assertNull(backedUp(2))
        assertEquals(now, backedUp(3))
        // The half-written copy was removed; the file after it was still copied.
        assertEquals(listOf("2026-11-09_0001.pdf", "2026-11-09_0003.pdf"), folder.files.keys.toList())

        // The next run copies only the one that failed, without duplicates.
        assertEquals(BackupResult.Done(1), runner().run(folder).result)
        assertEquals(3, folder.files.size)
    }

    @Test fun givesUpAfterThreeFailuresInARow() = runTest {
        repeat(6) { addRow(it + 1L) }
        dao.markFailure = IllegalStateException("db closed")
        val folder = FakeFolder()

        val result = runner().run(folder).result

        assertEquals(0, (result as BackupResult.Failed).copied)
        assertEquals(BackupRunner.MAX_FAILURES_IN_A_ROW, folder.creates)
    }

    @Test fun databaseFailureAfterCopyIsAFailure() = runTest {
        addRow(1)
        dao.markFailure = IllegalStateException("db closed")
        val result = runner().run(FakeFolder()).result
        assertTrue(result is BackupResult.Failed)
        assertEquals(0, (result as BackupResult.Failed).copied)
    }

    @Test fun sameNameAndSizeIsMarkedWithoutCopying() = runTest {
        addRow(1); addRow(2)
        val folder = FakeFolder()
        // Copied by an earlier run that died before marking it.
        folder.files["2026-11-09_0001.pdf"] = pdfBytes(1)

        assertEquals(BackupResult.Done(2), runner().run(folder).result)

        assertEquals(1, folder.creates) // Only 0002 was created.
        assertEquals(now, backedUp(1))
        assertEquals(2, folder.files.size)
    }

    @Test fun sameNameDifferentSizeIsCopiedAgainUnderAProviderName() = runTest {
        addRow(1)
        val folder = FakeFolder()
        folder.files["2026-11-09_0001.pdf"] = ByteArray(10)

        assertEquals(BackupResult.Done(1), runner().run(folder).result)

        assertArrayEquals(pdfBytes(1), folder.files.getValue("2026-11-09_0001 (1).pdf"))
        assertEquals(now, backedUp(1))
    }

    @Test fun unknownRemoteSizeIsCopiedAgain() = runTest {
        addRow(1)
        val folder = FakeFolder()
        folder.files["2026-11-09_0001.pdf"] = pdfBytes(1)
        folder.reportedSizes["2026-11-09_0001.pdf"] = null

        assertEquals(BackupResult.Done(1), runner().run(folder).result)
        assertEquals(1, folder.creates)
    }

    @Test fun missingLocalPdfIsSkippedAndStaysPending() = runTest {
        addRow(1); addRow(2, writePdf = false); addRow(3)
        val folder = FakeFolder()

        val outcome = runner().run(folder)

        assertEquals(BackupResult.Done(copied = 2, missing = 1), outcome.result)
        assertEquals(1, outcome.missingLocal)
        assertEquals(now, backedUp(1))
        assertNull(backedUp(2))
        assertEquals(now, backedUp(3))
    }

    @Test fun folderThatCannotBeListedIsUnavailable() = runTest {
        addRow(1)
        val folder = FakeFolder().apply { listFailure = FolderUnavailableException(SecurityException()) }
        assertEquals(BackupResult.FolderUnavailable, runner().run(folder).result)
        assertNull(backedUp(1))
    }

    @Test fun folderLostMidRunIsUnavailableAndKeepsWhatWasCopied() = runTest {
        addRow(1); addRow(2)
        val folder = FakeFolder().apply { unavailableOnCreate = 2 }
        assertEquals(BackupResult.FolderUnavailable, runner().run(folder).result)
        assertEquals(now, backedUp(1))
        assertNull(backedUp(2))
    }

    @Test fun fileNameIsTheLastPathSegment() {
        assertEquals("2026-11-09_0042.pdf", BackupRunner.fileName(addRow(42, writePdf = false)))
    }
}
