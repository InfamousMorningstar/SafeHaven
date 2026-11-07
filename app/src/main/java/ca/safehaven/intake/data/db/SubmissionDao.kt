package ca.safehaven.intake.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubmissionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(submission: SubmissionEntity)

    @Query("SELECT * FROM submission ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SubmissionEntity>>

    @Query("SELECT * FROM submission WHERE id = :id")
    suspend fun getById(id: String): SubmissionEntity?
}
