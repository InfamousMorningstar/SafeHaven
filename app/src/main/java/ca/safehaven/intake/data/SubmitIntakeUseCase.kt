package ca.safehaven.intake.data

import ca.safehaven.intake.data.db.SubmissionDao
import ca.safehaven.intake.data.db.toEntity
import ca.safehaven.intake.data.files.IntakeFileStore
import ca.safehaven.intake.domain.FileNamer
import ca.safehaven.intake.domain.IntakePdfWriter
import ca.safehaven.intake.domain.IntakeSubmitter
import ca.safehaven.intake.domain.RecordFactory
import ca.safehaven.intake.domain.SubmitResult
import ca.safehaven.intake.domain.model.IntakeConfig
import ca.safehaven.intake.domain.model.IntakeInput
import ca.safehaven.intake.domain.validation.IntakeValidator
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Validate, reserve the next record number, build the PDF, write it atomically, then index it
 * in Room (plan 3.4). If the database insert fails, the PDF is removed again so the two never
 * disagree.
 *
 * @param reserveSequenceNumber returns the next number from the counter that only goes up
 *   (`SequenceCounterDao.next`). A save that fails afterwards leaves a gap, never a repeat.
 */
class SubmitIntakeUseCase(
    private val config: () -> IntakeConfig,
    private val pdfWriter: IntakePdfWriter,
    private val fileStore: IntakeFileStore,
    private val dao: SubmissionDao,
    private val reserveSequenceNumber: suspend () -> Long,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : IntakeSubmitter {

    override suspend fun submit(input: IntakeInput): SubmitResult = withContext(ioDispatcher) {
        val cfg = config()
        val createdAt = clock.instant()
        // The shelter's local time: it decides the date on the waiver, the under-18 rule and the file name.
        val local = createdAt.atZone(ZoneId.of(cfg.timeZoneId))
        val errors = IntakeValidator.validate(input, cfg, local.toLocalDate())
        if (errors.isNotEmpty()) return@withContext SubmitResult.Invalid(errors)
        try {
            SubmitResult.Saved(save(input, cfg, createdAt, local))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never log the input here: it is personal information.
            SubmitResult.Failed(e)
        }
    }

    private suspend fun save(input: IntakeInput, cfg: IntakeConfig, createdAt: Instant, local: ZonedDateTime): String {
        repeat(MAX_NUMBER_ATTEMPTS) {
            val sequenceNumber = reserveSequenceNumber()
            val record = RecordFactory.create(
                input = input,
                config = cfg,
                id = UUID.randomUUID().toString(),
                sequenceNumber = sequenceNumber,
                createdAt = createdAt,
                signedOn = local.toLocalDate(),
            )
            val relativePath = FileNamer.relativePath(local, sequenceNumber)

            val written = try {
                fileStore.writeAtomically(relativePath) { out -> pdfWriter.write(record, out) }
            } catch (e: FileAlreadyExistsException) {
                // Can't happen with a counter that only goes up, unless the database was reset
                // or restored while the PDFs stayed. Never overwrite a signed waiver: take the
                // next number instead.
                return@repeat
            }

            try {
                dao.insert(record.toEntity(relativePath, written.sha256))
            } catch (e: Throwable) {
                fileStore.delete(relativePath)
                throw e
            }
            return record.recordCode
        }
        throw IOException("Could not find a free record number")
    }

    private companion object {
        const val MAX_NUMBER_ATTEMPTS = 5
    }
}
