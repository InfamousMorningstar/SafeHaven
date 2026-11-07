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
 */
class IntakeFileStore(private val root: File) {

    data class WrittenFile(val file: File, val sha256: String)

    private val tempDir: File get() = File(root, TEMP_DIR_NAME)

    /**
     * Writes a new file at [relativePath] using [writer].
     * @throws FileAlreadyExistsException if that file already exists (the caller picks a new name).
     */
    fun writeAtomically(relativePath: String, writer: (OutputStream) -> Unit): WrittenFile {
        val dest = resolve(relativePath)
        if (dest.exists()) throw FileAlreadyExistsException(dest)
        ensureDirectory(tempDir)

        val temp = File.createTempFile("intake-", ".part", tempDir)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileOutputStream(temp).use { fileOut ->
                val out = DigestOutputStream(BufferedOutputStream(fileOut), digest)
                writer(out)
                out.flush()
                fileOut.fd.sync()
            }
            ensureDirectory(checkNotNull(dest.parentFile))
            if (dest.exists()) throw FileAlreadyExistsException(dest)
            if (!temp.renameTo(dest)) throw IOException("Could not move PDF into place: $relativePath")
            return WrittenFile(dest, digest.digest().toHex())
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    /** Resolves a path under the root, refusing anything that would escape it. */
    fun resolve(relativePath: String): File {
        val rootPath = root.canonicalFile
        val file = File(rootPath, relativePath).canonicalFile
        require(file.path.startsWith(rootPath.path + File.separator)) { "Path escapes storage root" }
        return file
    }

    fun delete(relativePath: String): Boolean = resolve(relativePath).delete()

    /** Removes leftovers from writes that were interrupted (e.g. by a power cut). */
    fun clearTemp() {
        tempDir.listFiles()?.forEach { it.delete() }
    }

    private fun ensureDirectory(dir: File) {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Could not create ${dir.name}")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        const val TEMP_DIR_NAME = ".tmp"
    }
}
