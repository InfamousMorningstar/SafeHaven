package ca.safehaven.intake.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Escapes the `LIKE` wildcards in user-typed text for [SubmissionDao.search]. */
fun escapeLike(text: String): String =
    text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

@Dao
interface SubmissionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(submission: SubmissionEntity)

    @Query("SELECT * FROM submission ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SubmissionEntity>>

    @Query("SELECT COUNT(*) FROM submission")
    fun observeCount(): Flow<Int>

    /** 1 when a row points at this PDF, else 0. */
    @Query("SELECT COUNT(*) FROM submission WHERE pdfPath = :pdfPath")
    suspend fun countByPdfPath(pdfPath: String): Int

    @Query("SELECT * FROM submission WHERE id = :id")
    suspend fun getById(id: String): SubmissionEntity?

    /**
     * Staff search, newest first. [query] matches anywhere in the name, email or record code;
     * [digits] (the query with everything but digits removed) matches the phone number.
     * Pass empty strings to list everything. `%`, `_` and `\` in [query] must be escaped with
     * a backslash ([escapeLike]), so they match themselves.
     */
    @Query(
        "SELECT * FROM submission WHERE :query = '' " +
            "OR fullName LIKE '%' || :query || '%' ESCAPE '\\' " +
            "OR guardianName LIKE '%' || :query || '%' ESCAPE '\\' " +
            "OR email LIKE '%' || :query || '%' ESCAPE '\\' " +
            "OR recordCode LIKE '%' || :query || '%' ESCAPE '\\' " +
            "OR (:digits != '' AND phoneE164 LIKE '%' || :digits || '%') " +
            "ORDER BY sequenceNumber DESC",
    )
    fun search(query: String, digits: String): Flow<List<SubmissionEntity>>

    /** Returns the number of rows deleted (0 or 1). The sequence counter is not touched. */
    @Query("DELETE FROM submission WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("SELECT * FROM submission WHERE backedUpAt IS NULL ORDER BY sequenceNumber")
    suspend fun pendingBackup(): List<SubmissionEntity>

    @Query("SELECT COUNT(*) FROM submission WHERE backedUpAt IS NULL")
    fun observePendingBackupCount(): Flow<Int>

    @Query("SELECT MAX(backedUpAt) FROM submission")
    fun observeLastBackupAt(): Flow<Instant?>

    @Query("UPDATE submission SET backedUpAt = :at WHERE id = :id")
    suspend fun markBackedUp(id: String, at: Instant)

    /** Every waiver becomes pending again: used when staff choose a different backup folder. */
    @Query("UPDATE submission SET backedUpAt = NULL")
    suspend fun markAllPendingBackup()

    @Query("UPDATE submission SET exportedAt = :at WHERE id = :id")
    suspend fun markExported(id: String, at: Instant)
}
