package ca.safehaven.intake.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream

/**
 * A folder picked with `ACTION_OPEN_DOCUMENT_TREE` (API 21), seen through [BackupFolder].
 *
 * Files go straight into the picked folder, with no year/month sub-folders. The names already
 * sort by date (`2026-11-09_0001.pdf`), and a flat folder needs one listing and one create per
 * file. Sub-folders would mean finding or creating them on every run, and providers differ in
 * how (and how quickly, for Drive) a new folder becomes visible, which risks duplicate folders.
 */
internal class SafBackupFolder(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) : BackupFolder {

    override fun listFiles(): List<RemoteFile> = unavailableOn {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootDocumentId())
        val projection = arrayOf(
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_SIZE,
            Document.COLUMN_MIME_TYPE,
        )
        val cursor = resolver.query(childrenUri, projection, null, null, null)
            ?: throw FileNotFoundException("Backup folder can't be listed")
        cursor.use { c ->
            val files = ArrayList<RemoteFile>(c.count.coerceAtLeast(0))
            while (c.moveToNext()) {
                if (c.getString(2) == Document.MIME_TYPE_DIR) continue
                val name = c.getString(0) ?: continue
                val size = if (c.isNull(1)) null else c.getLong(1)
                files += RemoteFile(name, size)
            }
            files
        }
    }

    override fun createFile(name: String, mimeType: String): BackupTarget {
        val uri: Uri? = unavailableOn {
            val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId())
            // API 23 returns null on failure; newer versions throw.
            DocumentsContract.createDocument(resolver, parent, mimeType, name)
        }
        return SafTarget(uri ?: throw IOException("The backup folder refused a new file"))
    }

    private fun rootDocumentId(): String = DocumentsContract.getTreeDocumentId(treeUri)

    private inner class SafTarget(private val uri: Uri) : BackupTarget {
        override fun openOutputStream(): OutputStream =
            resolver.openOutputStream(uri, "w") ?: throw IOException("Could not write to the backup file")

        override fun delete() {
            try {
                DocumentsContract.deleteDocument(resolver, uri)
            } catch (e: Exception) {
                // Best effort: the copy is incomplete either way and stays pending.
            }
        }
    }

    private inline fun <T> unavailableOn(block: () -> T): T =
        try {
            block()
        } catch (e: SecurityException) {
            throw FolderUnavailableException(e)
        } catch (e: FileNotFoundException) {
            throw FolderUnavailableException(e)
        } catch (e: IllegalArgumentException) {
            throw FolderUnavailableException(e)
        }
}
