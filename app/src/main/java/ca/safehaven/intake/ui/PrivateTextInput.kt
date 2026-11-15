package ca.safehaven.intake.ui

import android.os.Build
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

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
                    // No microphone key: dictation would send what a visitor says to the
                    // keyboard's speech service. Honoured by the AOSP and Google keyboards;
                    // other keyboards ignore it (KIOSK_SETUP.md says to turn voice typing off).
                    outAttributes.privateImeOptions = NO_MICROPHONE_KEY
                }
            }
            nextHandler.startInputMethod(privateRequest)
        },
    ) {
        // No Cut/Copy/Paste menu on the visitor's fields: whatever one visitor copied would be
        // offered to the next one as "Paste". The menu also lists other apps' text actions
        // (Translate, Web search), which have no place on a kiosk.
        CompositionLocalProvider(LocalTextToolbar provides NoTextToolbar, content = content)
    }
}

private const val NO_MICROPHONE_KEY = "nm,com.google.android.inputmethod.latin.noMicrophoneKey"

/** A text toolbar that never shows. */
private object NoTextToolbar : TextToolbar {
    override val status: TextToolbarStatus = TextToolbarStatus.Hidden

    override fun hide() = Unit

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = Unit
}
