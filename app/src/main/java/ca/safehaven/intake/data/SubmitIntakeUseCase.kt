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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Validate, reserve the next record number, build the PDF, then save it with its Room row
 * (plan 3.4). The order is: PDF written and on disk in `.tmp/`, row inserted, PDF moved into
 * place. The row is what makes the save count. If the insert fails, the PDF is removed; if the
 * app dies before the move, `IntakeFileStore.recoverTemp` finishes it on the next start. So a
 * PDF never exists without its row.
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
            // Not cancellable: once a number is reserved, the PDF and its row are both written or
            // both absent. A cancel landing between the two (the screen going away mid-save)
            // could otherwise delete the PDF of a row that Room had already committed.
            SubmitResult.Saved(withContext(NonCancellable) { save(input, cfg, createdAt, local) })
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

            val prepared = try {
                fileStore.prepare(relativePath) { out -> pdfWriter.write(record, out) }
            } catch (e: FileAlreadyExistsException) {
                // Can't happen with a counter that only goes up, unless the database was reset
                // or restored while the PDFs stayed. Never overwrite a signed waiver: take the
                // next number instead.
                return@repeat
            }

            try {
                dao.insert(record.toEntity(relativePath, prepared.sha256))
            } catch (e: Throwable) {
                prepared.discard()
                throw e
            }
            try {
                prepared.commit()
            } catch (e: Exception) {
                // The PDF couldn't be moved into place: take the row back so the two agree.
                try {
                    dao.deleteById(record.id)
                } catch (ignored: Exception) {
                    // The row stays; the next start moves the PDF into place for it.
                    throw e
                }
                prepared.discard()
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
