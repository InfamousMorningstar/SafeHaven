package ca.safehaven.intake.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

/**
 * The single-row counter behind the sequential record numbers (1, 2, 3, ...) in file names.
 *
 * It only ever goes up. `MAX(sequenceNumber) + 1` would hand out a deleted record's number
 * again once staff can delete records, and the client wants numbers never to repeat.
 */
@Entity(tableName = "sequence_counter")
data class SequenceCounterEntity(
    @PrimaryKey val id: Int,
    /** The last number handed out; 0 before the first submission. */
    val lastValue: Long,
)

@Dao
abstract class SequenceCounterDao {
    @Query("INSERT OR IGNORE INTO sequence_counter (id, lastValue) VALUES (1, 0)")
    abstract suspend fun ensureRow()

    /**
     * Also never goes below an existing row's number, in case the counter row was ever lost
     * while submissions survived (e.g. a partial restore).
     */
    @Query(
        "UPDATE sequence_counter SET lastValue = MAX(lastValue, " +
            "(SELECT IFNULL(MAX(sequenceNumber), 0) FROM submission)) + 1 WHERE id = 1",
    )
    abstract suspend fun increment()

    @Query("SELECT lastValue FROM sequence_counter WHERE id = 1")
    abstract suspend fun current(): Long

    /**
     * Reserves and returns the next number. Call it before writing the PDF; if the save then
     * fails, that number is simply skipped (a gap is fine, a repeat is not).
     */
    @Transaction
    open suspend fun next(): Long {
        ensureRow()
        increment()
        return current()
    }
}
