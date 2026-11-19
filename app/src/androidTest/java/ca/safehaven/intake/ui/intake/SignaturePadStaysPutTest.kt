package ca.safehaven.intake.ui.intake

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ca.safehaven.intake.R
import ca.safehaven.intake.domain.model.IntakeConfig
import ca.safehaven.intake.domain.model.IntakeInput
import ca.safehaven.intake.domain.validation.Field
import ca.safehaven.intake.domain.validation.ValidationError
import ca.safehaven.intake.ui.theme.SafeHavenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test (2026-11-18): the signature is usually the last thing a visitor fixes after
 * tapping Submit too early. Fixing it used to remove the "Please check the highlighted fields"
 * banner below the pad; with the form scrolled to the bottom, the whole page then slid down
 * under the visitor's finger, every stroke after the first landed above the pad and scrolled the
 * page instead, and the PDF got only part of the signature. The pad must not move.
 */
@RunWith(AndroidJUnit4::class)
class SignaturePadStaysPutTest {

    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun fixingTheLastErrorDoesNotMoveThePad() {
        val withError = IntakeFormState(
            input = IntakeInput(),
            errors = mapOf(Field.SIGNATURE to ValidationError.REQUIRED),
            submitAttempted = true,
        )
        var form by mutableStateOf(withError)
        compose.setContent {
            SafeHavenTheme {
                IntakeFormScreen(
                    form = form,
                    config = IntakeConfig(),
                    shelterName = "Safe Haven for Animals",
                    onInputChange = {},
                    onFieldBlurred = {},
                    onSubmit = {},
                    onCancel = {},
                )
            }
        }

        // Scrolled to the bottom, as after Submit, so content below the pad changing height
        // would move everything above it.
        // The page itself; the reason chips are a second, horizontal scroller.
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText(context.getString(R.string.form_submit)))
        compose.onNodeWithText(context.getString(R.string.form_fix_errors)).assertExists()
        val pad = context.getString(R.string.signature_pad_description)
        val before = compose.onNodeWithContentDescription(pad).getBoundsInRoot()

        // The first stroke makes the signature valid: no errors left.
        form = withError.copy(errors = emptyMap())
        compose.waitForIdle()

        val after = compose.onNodeWithContentDescription(pad).getBoundsInRoot()
        assertEquals("The signature pad moved when the last error was fixed", before.top, after.top)
        // The banner is hidden (invisible to TalkBack too), only its space is kept.
        compose.onNodeWithText(context.getString(R.string.form_fix_errors)).assertDoesNotExist()
    }
}
