package ca.safehaven.intake.data.files

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

/**
 * App-private PDF storage (plan 3.7 / D3-B), rooted at `filesDir/intake/`.
 *
 * Writes are atomic: the PDF goes to `.tmp/`, is fsync'd, then renamed into place, so a power
 * cut never leaves a half-written PDF where staff would see it.
 *
 * A save is two steps, [prepare] then [PreparedFile.commit], so the caller can write the
 * database row in between. The row is then the record that the save happened: if the app dies
 * before the rename, [recoverTemp] finishes the rename on the next start.
 */
class IntakeFileStore(private val root: File) {

    data class WrittenFile(val file: File, val sha256: String)

    private val tempDir: File get() = File(root, TEMP_DIR_NAME)

    /**
     * Writes a new file at [relativePath] using [writer].
     * @throws FileAlreadyExistsException if that file already exists (the caller picks a new name).
     */
    fun writeAtomically(relativePath: String, writer: (OutputStream) -> Unit): WrittenFile {
        val prepared = prepare(relativePath, writer)
        try {
            return WrittenFile(prepared.commit(), prepared.sha256)
        } finally {
            prepared.discard()
        }
    }

    /** A complete, fsync'd file waiting in `.tmp/` to be moved to its final place. */
    inner class PreparedFile internal constructor(
        private val temp: File,
        private val dest: File,
        val sha256: String,
    ) {
        /**
         * Moves the file into place.
         * @throws FileAlreadyExistsException if a file took that place in the meantime.
         */
        fun commit(): File {
            ensureDirectory(checkNotNull(dest.parentFile))
            if (dest.exists()) throw FileAlreadyExistsException(dest)
            if (!temp.renameTo(dest)) throw IOException("Could not move PDF into place")
            return dest
        }

        /** Removes the waiting file. Harmless after [commit]. */
        fun discard() {
            if (temp.exists()) temp.delete()
        }
    }

    /**
     * Writes a new file with [writer] and leaves it waiting in `.tmp/`, complete and on disk.
     * @throws FileAlreadyExistsException if [relativePath] already exists (the caller picks a new name).
     */
    fun prepare(relativePath: String, writer: (OutputStream) -> Unit): PreparedFile {
        val dest = resolve(relativePath)
        if (dest.exists()) throw FileAlreadyExistsException(dest)
        ensureDirectory(tempDir)

        val temp = File(tempDir, tempName(relativePath))
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileOutputStream(temp).use { fileOut ->
                val out = DigestOutputStream(BufferedOutputStream(fileOut), digest)
                writer(out)
                out.flush()
                fileOut.fd.sync()
            }
            return PreparedFile(temp, dest, digest.digest().toHex())
        } catch (e: Throwable) {
            temp.delete()
            throw e
        }
    }

    /** The files waiting in `.tmp/` right now. Take this before any new save can start. */
    fun pendingTemp(): List<File> = tempDir.listFiles()?.toList().orEmpty()

    /**
     * Deals with [leftovers] from saves that were cut off (power loss, crash). A leftover whose
     * database row exists ([isRecorded] gets its relative path) was a finished save that only
     * missed its rename: it is moved into place. Anything else is a save that never completed
     * and is removed. Returns how many files were moved into place.
     */
    fun recoverTemp(leftovers: List<File>, isRecorded: (relativePath: String) -> Boolean): Int {
        var recovered = 0
        for (temp in leftovers) {
            val relativePath = relativePathOf(temp)
            val dest = relativePath?.let {
                try {
                    resolve(it)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
            if (relativePath != null && dest != null && !dest.exists() && isRecorded(relativePath)) {
                val parent = checkNotNull(dest.parentFile)
                if ((parent.isDirectory || parent.mkdirs()) && temp.renameTo(dest)) {
                    recovered++
                    continue
                }
                continue // Couldn't move it now: keep it for the next start.
            }
            temp.delete()
        }
        return recovered
    }

    /** Resolves a path under the root, refusing anything that would escape it. */
    fun resolve(relativePath: String): File {
        val rootPath = root.canonicalFile
        val file = File(rootPath, relativePath).canonicalFile
        require(file.path.startsWith(rootPath.path + File.separator)) { "Path escapes storage root" }
        return file
    }

    fun delete(relativePath: String): Boolean = resolve(relativePath).delete()

    /** `2026/11/x.pdf` -> `2026~11~x.pdf.part`: the temp name carries the final place. */
    private fun tempName(relativePath: String): String =
        relativePath.replace('/', TEMP_PATH_SEPARATOR) + TEMP_SUFFIX

    private fun relativePathOf(temp: File): String? =
        temp.name.takeIf { it.endsWith(TEMP_SUFFIX) }
            ?.removeSuffix(TEMP_SUFFIX)
            ?.replace(TEMP_PATH_SEPARATOR, '/')

    private fun ensureDirectory(dir: File) {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Could not create ${dir.name}")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        const val TEMP_DIR_NAME = ".tmp"
        private const val TEMP_SUFFIX = ".part"
        private const val TEMP_PATH_SEPARATOR = '~'
    }
}
