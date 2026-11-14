package ca.safehaven.intake.data.backup

import ca.safehaven.intake.data.db.SubmissionDao
import ca.safehaven.intake.data.db.SubmissionEntity
import ca.safehaven.intake.data.files.IntakeFileStore
import ca.safehaven.intake.staff.BackupResult
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/*
 * The backup loop, free of Android classes so it runs in JVM unit tests. SafBackupFolder adapts a
 * storage-access-framework folder to [BackupFolder].
 *
 * Nothing here logs. File names contain the record code, so not even those.
 */

/** A file already in the backup folder. [size] is null when the provider doesn't report one. */
internal data class RemoteFile(val name: String, val size: Long?)

/** The folder the staff picked. */
internal interface BackupFolder {
    /**
     * Lists the files directly in the folder (not sub-folders).
     * @throws FolderUnavailableException if the folder is gone or access was revoked.
     */
    fun listFiles(): List<RemoteFile>

    /**
     * Creates a new, empty file. The provider may change [name] if it's taken (e.g. `x (1).pdf`).
     * @throws FolderUnavailableException if the folder is gone or access was revoked.
     */
    fun createFile(name: String, mimeType: String): BackupTarget
}

internal interface BackupTarget {
    fun openOutputStream(): OutputStream

    /** Best effort; never throws. Removes a half-written copy after a failure. */
    fun delete()
}

/** The folder can't be reached: staff must pick it again. */
internal class FolderUnavailableException(cause: Throwable? = null) : Exception(cause)

internal data class BackupOutcome(
    val result: BackupResult,
    /** Rows skipped because their PDF is missing on the tablet. They stay pending. */
    val missingLocal: Int,
)

internal class BackupRunner(
    private val dao: SubmissionDao,
    private val fileStore: IntakeFileStore,
    private val clock: Clock,
) {
    /**
     * Copies every pending PDF, oldest first, into [folder] (null: none chosen). A file that
     * fails stays pending and the run moves on to the next one, so one bad file can't hold back
     * every newer waiver. The run gives up after [MAX_FAILURES_IN_A_ROW] failures in a row (the
     * folder is probably full or offline) or as soon as the folder itself is unavailable.
     */
    suspend fun run(folder: BackupFolder?): BackupOutcome {
        if (folder == null) return BackupOutcome(BackupResult.NoFolder, 0)

        val pending = dao.pendingBackup()
        if (pending.isEmpty()) return BackupOutcome(BackupResult.Done(0), 0)

        // Listed once per run: name -> sizes (a Drive folder can hold two files with one name).
        val existing: Map<String, List<Long?>> = try {
            folder.listFiles().groupBy({ it.name }, { it.size })
        } catch (e: FolderUnavailableException) {
            return BackupOutcome(BackupResult.FolderUnavailable, 0)
        }

        var copied = 0
        var missing = 0
        var firstFailure: Exception? = null
        var failuresInARow = 0
        for (row in pending) {
            currentCoroutineContext().ensureActive()

            val local = localPdf(row)
            if (local == null) {
                missing++
                continue
            }
            val length = local.length()
            val name = fileName(row)

            try {
                // Already there from an earlier run whose "mark" step didn't happen (e.g. the
                // app was killed): same name and same size means same file, so don't copy again.
                if (existing[name]?.contains(length) != true) {
                    copy(local, length, folder, name)
                }
                dao.markBackedUp(row.id, clock.instant())
                copied++
                failuresInARow = 0
            } catch (e: FolderUnavailableException) {
                return BackupOutcome(BackupResult.FolderUnavailable, missing)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (firstFailure == null) firstFailure = e
                if (++failuresInARow >= MAX_FAILURES_IN_A_ROW) break
            }
        }
        val failure = firstFailure
        val result = if (failure == null) {
            BackupResult.Done(copied, missing)
        } else {
            BackupResult.Failed(copied, failure, missing)
        }
        return BackupOutcome(result, missing)
    }

    private fun localPdf(row: SubmissionEntity): File? =
        try {
            fileStore.resolve(row.pdfPath).takeIf { it.isFile }
        } catch (e: IllegalArgumentException) {
            null // A path outside the storage root; treat as missing.
        } catch (e: IOException) {
            null
        }

    private fun copy(local: File, length: Long, folder: BackupFolder, name: String) {
        val target = folder.createFile(name, PDF_MIME_TYPE)
        try {
            val written = target.openOutputStream().use { out ->
                FileInputStream(local).use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        total += read
                    }
                    out.flush()
                    total
                }
            } // close() is where some providers commit the file, so its errors count too.
            // Generic message on purpose: no file name (it contains the record code).
            if (written != length) throw IOException("Backup copy is incomplete")
        } catch (e: Throwable) {
            // Don't leave a half-written copy behind; the next run would see a same-name file of
            // the wrong size and create "name (1).pdf" next to it.
            target.delete()
            throw e
        }
    }

    companion object {
        const val PDF_MIME_TYPE = "application/pdf"
        private const val BUFFER_SIZE = 64 * 1024
        const val MAX_FAILURES_IN_A_ROW = 3

        /** `2026/11/2026-11-09_0001.pdf` -> `2026-11-09_0001.pdf`. */
        fun fileName(row: SubmissionEntity): String = row.pdfPath.substringAfterLast('/')
    }
}
