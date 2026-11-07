package ca.safehaven.intake.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import ca.safehaven.intake.domain.model.IntakeRecord
import java.time.Instant

@Database(entities = [SubmissionEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun submissionDao(): SubmissionDao
}

class Converters {
    @TypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}

fun IntakeRecord.toEntity(pdfPath: String, pdfSha256: String) = SubmissionEntity(
    id = id,
    recordCode = recordCode,
    createdAt = createdAt,
    fullName = fullName,
    phoneE164 = phoneE164,
    street = street,
    unit = unit,
    city = city,
    province = province.name,
    postalCode = postalCode,
    reason = reason?.name,
    consentGiven = consentGiven,
    consentTextVersion = consentTextVersion,
    hasSignature = false,
    pdfPath = pdfPath,
    pdfSha256 = pdfSha256,
)
