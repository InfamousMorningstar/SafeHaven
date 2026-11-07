package ca.safehaven.intake.ui

import android.os.Build
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * Stops the on-screen keyboard from learning what visitors type and suggesting it to the next
 * visitor (plan 3.2, "Keyboard privacy").
 *
 * - All Android versions: text fields get TYPE_TEXT_FLAG_NO_SUGGESTIONS.
 * - Android 8+: IME_FLAG_NO_PERSONALIZED_LEARNING as well.
 *
 * On the LG G Pad X (Android 6/7) the second flag doesn't exist, so also turn off prediction
 * and learning in the keyboard's own settings during setup, and test it on the real tablet.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PrivateTextInput(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, nextHandler ->
            val privateRequest = PlatformTextInputMethodRequest { outAttributes ->
                request.createInputConnection(outAttributes).also {
                    val isTextClass = (outAttributes.inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT
                    if (isTextClass) {
                        outAttributes.inputType = outAttributes.inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    }
                }
            }
            nextHandler.startInputMethod(privateRequest)
        },
        content = content,
    )
}
