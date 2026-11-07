package ca.safehaven.intake.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import ca.safehaven.intake.domain.validation.PhoneRules
import ca.safehaven.intake.domain.validation.PostalCodeRules

/**
 * Displays text through a formatter that only *inserts* characters (spaces, brackets, dashes),
 * keeping the cursor where the visitor expects it. The stored value stays unformatted.
 */
class InsertingVisualTransformation(private val formatter: (String) -> String) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val formatted = formatter(original)

        val originalToTransformed = IntArray(original.length + 1)
        val transformedToOriginal = IntArray(formatted.length + 1)
        var o = 0
        for (t in formatted.indices) {
            transformedToOriginal[t] = o
            if (o < original.length && formatted[t] == original[o]) {
                originalToTransformed[o] = t
                o++
            }
        }
        // The formatter must keep every original character, in order; otherwise show as typed.
        if (o != original.length) return TransformedText(text, OffsetMapping.Identity)
        originalToTransformed[original.length] = formatted.length
        transformedToOriginal[formatted.length] = original.length

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int) =
                originalToTransformed[offset.coerceIn(0, original.length)]

            override fun transformedToOriginal(offset: Int) =
                transformedToOriginal[offset.coerceIn(0, formatted.length)]
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }

    override fun equals(other: Any?) = other is InsertingVisualTransformation && other.formatter == formatter
    override fun hashCode() = formatter.hashCode()

    companion object {
        val Phone = InsertingVisualTransformation(PhoneRules::formatPartial)
        val PostalCode = InsertingVisualTransformation(PostalCodeRules::format)
    }
}
