package ca.safehaven.intake.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import ca.safehaven.intake.data.db.SubmissionDao
import ca.safehaven.intake.data.files.IntakeFileStore
import ca.safehaven.intake.staff.BackupResult
import ca.safehaven.intake.staff.BackupService
import ca.safehaven.intake.staff.BackupStatus
import java.io.IOException
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Backs the waiver PDFs up to a folder the staff picked once with the system folder picker
 * (storage access framework). The folder can be on an SD card, a USB stick, internal storage, or
 * Google Drive if the installed Drive app offers folders to the picker (see KIOSK_SETUP.md).
 *
 * With Drive, "copied" means handed to the Drive app, which uploads when it's online.
 */
class SafBackupService(
    context: Context,
    private val dao: SubmissionDao,
    fileStore: IntakeFileStore,
    clock: Clock,
    /**
     * Backups run here, not in the screen's scope: the staff area locks itself after two idle
     * minutes (or when the screen turns off), and that must not stop a long backup half-way.
     */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : BackupService {

    private data class StoredFolder(val uri: String, val name: String)

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val runner = BackupRunner(dao, fileStore, clock)

    private val folder = MutableStateFlow(loadFolder())
    private val running = MutableStateFlow(false)

    /** One run at a time; a second "Back up now" waits, then finds little or nothing pending. */
    private val runMutex = Mutex()

    override fun observeStatus(): Flow<BackupStatus> = combine(
        folder,
        dao.observePendingBackupCount(),
        dao.observeLastBackupAt(),
        running,
    ) { stored, pending, lastAt, isRunning ->
        BackupStatus(
            folderName = stored?.name,
            pendingCount = pending,
            lastBackupAt = lastAt,
            isRunning = isRunning,
        )
    }

    override fun folderPickerIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            )
            // Unofficial, but makes older pickers list internal storage without a menu tap.
            putExtra(EXTRA_SHOW_ADVANCED, true)
        }

    /**
     * @throws SecurityException if the picker didn't grant a persistable permission (the folder
     *   is then not saved).
     */
    override suspend fun setFolder(treeUri: Uri) {
        withContext(Dispatchers.IO) {
            val resolver = appContext.contentResolver
            resolver.takePersistableUriPermission(treeUri, READ_WRITE)

            val chosen = StoredFolder(treeUri.toString(), displayName(treeUri))
            val ok = prefs.edit()
                .putString(KEY_URI, chosen.uri)
                .putString(KEY_NAME, chosen.name)
                .commit()
            if (!ok) throw IOException("Could not save the backup folder")

            val previous = folder.value
            folder.value = chosen
            if (previous != null && previous.uri != chosen.uri) {
                // A different folder starts empty: every waiver goes to it, not only new ones.
                // (Files already there with the same name and size aren't copied twice.)
                dao.markAllPendingBackup()
                try {
                    resolver.releasePersistableUriPermission(Uri.parse(previous.uri), READ_WRITE)
                } catch (e: SecurityException) {
                    // Already gone.
                }
            }
        }
    }

    override suspend fun backUpNow(): BackupResult = scope.async {
        runMutex.withLock {
            running.value = true
            try {
                runOnce()
            } finally {
                running.value = false
            }
        }
    }.await() // If the caller goes away, only this wait is cancelled; the run carries on.

    private suspend fun runOnce(): BackupResult {
        val stored = folder.value ?: return runner.run(null).result
        val treeUri = Uri.parse(stored.uri)
        if (!hasPermission(treeUri)) return BackupResult.FolderUnavailable
        return runner.run(SafBackupFolder(appContext.contentResolver, treeUri)).result
    }

    private fun hasPermission(treeUri: Uri): Boolean =
        appContext.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }

    private fun displayName(treeUri: Uri): String {
        val fallback = treeUri.lastPathSegment ?: treeUri.toString()
        return try {
            val docUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            appContext.contentResolver
                .query(docUri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null }
                ?: fallback
        } catch (e: Exception) {
            // Any provider error: the name is cosmetic, the URI is what matters.
            fallback
        }
    }

    private fun loadFolder(): StoredFolder? {
        val uri = prefs.getString(KEY_URI, null) ?: return null
        return StoredFolder(uri, prefs.getString(KEY_NAME, null) ?: uri)
    }

    private companion object {
        const val PREFS_NAME = "backup"
        const val KEY_URI = "folder_uri"
        const val KEY_NAME = "folder_name"
        const val EXTRA_SHOW_ADVANCED = "android.content.extra.SHOW_ADVANCED"
        const val READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
