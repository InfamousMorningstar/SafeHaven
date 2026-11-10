package ca.safehaven.intake.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import ca.safehaven.intake.domain.model.IntakeRecord
import java.time.Instant
import java.time.LocalDate

/**
 * Version 2: the Arizona waiver (new submission columns plus the sequence counter).
 * Version 1 was the Canadian sign-in scaffold, which only ever existed on a developer emulator,
 * so AppContainer drops it instead of migrating. Every change from version 2 on needs a real
 * Migration, because by then there will be visitors' waivers on the tablet.
 */
@Database(
    entities = [SubmissionEntity::class, SequenceCounterEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun submissionDao(): SubmissionDao
    abstract fun sequenceCounterDao(): SequenceCounterDao
}

class Converters {
    @TypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    /** ISO-8601 (`2026-11-09`): readable in a DB inspector and sorts correctly as text. */
    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }
}

fun IntakeRecord.toEntity(pdfPath: String, pdfSha256: String) = SubmissionEntity(
    id = id,
    sequenceNumber = sequenceNumber,
    recordCode = recordCode,
    createdAt = createdAt,
    signedOn = signedOn,
    fullName = fullName,
    phoneE164 = phoneE164,
    email = email,
    city = city,
    state = state.name,
    zip = zip,
    reason = reason.name,
    dateOfBirth = dateOfBirth,
    isMinor = isMinor,
    guardianName = guardianName,
    waiverVersion = waiverVersion,
    pdfPath = pdfPath,
    pdfSha256 = pdfSha256,
)
