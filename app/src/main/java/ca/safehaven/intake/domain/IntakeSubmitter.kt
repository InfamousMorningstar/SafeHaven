package ca.safehaven.intake.domain

import ca.safehaven.intake.domain.model.IntakeInput
import ca.safehaven.intake.domain.model.IntakeRecord
import ca.safehaven.intake.domain.validation.Field
import ca.safehaven.intake.domain.validation.ValidationError
import java.io.OutputStream

interface IntakeSubmitter {
    suspend fun submit(input: IntakeInput): SubmitResult
}

sealed interface SubmitResult {
    data class Saved(val recordCode: String) : SubmitResult
    data class Invalid(val errors: Map<Field, ValidationError>) : SubmitResult
    data class Failed(val cause: Throwable) : SubmitResult
}

/** Renders one record as a PDF. Implemented with android.graphics.pdf.PdfDocument. */
fun interface IntakePdfWriter {
    fun write(record: IntakeRecord, out: OutputStream)
}
