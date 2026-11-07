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
import java.time.ZoneId
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Validate, build the PDF, write it atomically, then index it in Room (plan 3.4).
 * If the database insert fails, the PDF is removed again so the two never disagree.
 */
class SubmitIntakeUseCase(
    private val config: () -> IntakeConfig,
    private val pdfWriter: IntakePdfWriter,
    private val fileStore: IntakeFileStore,
    private val dao: SubmissionDao,
    private val clock: Clock,
    private val zone: ZoneId,
    private val random: Random,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : IntakeSubmitter {

    override suspend fun submit(input: IntakeInput): SubmitResult = withContext(ioDispatcher) {
        val cfg = config()
        val errors = IntakeValidator.validate(input, cfg)
        if (errors.isNotEmpty()) return@withContext SubmitResult.Invalid(errors)
        try {
            SubmitResult.Saved(save(input, cfg))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never log the input here: it is personal information.
            SubmitResult.Failed(e)
        }
    }

    private suspend fun save(input: IntakeInput, cfg: IntakeConfig): String {
        val createdAt = clock.instant()
        val local = createdAt.atZone(zone)
        repeat(MAX_NAME_ATTEMPTS) {
            val suffix = FileNamer.randomSuffix(random)
            val record = RecordFactory.create(
                input = input,
                config = cfg,
                id = UUID.randomUUID().toString(),
                recordCode = FileNamer.recordCode(local, suffix),
                createdAt = createdAt,
            )
            val nameForFile = record.fullName.takeIf { cfg.includeNameInFileName }
            val relativePath = FileNamer.relativePath(local, nameForFile, suffix)

            val written = try {
                fileStore.writeAtomically(relativePath) { out -> pdfWriter.write(record, out) }
            } catch (e: FileAlreadyExistsException) {
                return@repeat // Same name and suffix in the same minute: try another suffix.
            }

            try {
                dao.insert(record.toEntity(relativePath, written.sha256))
            } catch (e: Throwable) {
                fileStore.delete(relativePath)
                throw e
            }
            return record.recordCode
        }
        throw IOException("Could not find a free file name")
    }

    private companion object {
        const val MAX_NAME_ATTEMPTS = 5
    }
}
