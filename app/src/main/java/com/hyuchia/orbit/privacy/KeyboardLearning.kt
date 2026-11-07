package com.hyuchia.orbit.privacy

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession

/**
 * Asks keyboards not to learn from what's typed into `content`'s fields
 * while `enabled`, as for a private map: words typed there would otherwise
 * come back as suggestions in other apps. The map's page asks the same of
 * its own fields (`OrbitPage.incognito`). When `enabled` changes, as when
 * quick capture's ideas go to another map, the fields stay as they are,
 * text and focus with them, and the keyboard starts again with the new ask.
 */
@Composable
fun KeyboardsDontLearn(enabled: Boolean, content: @Composable () -> Unit) {
    InterceptPlatformTextInput(if (enabled) NoPersonalizedLearning else AsItIs, content)
}

private object NoPersonalizedLearning : PlatformTextInputInterceptor {
    override suspend fun interceptStartInputMethod(request: PlatformTextInputMethodRequest, nextHandler: PlatformTextInputSession): Nothing {
        nextHandler.startInputMethod(
            object : PlatformTextInputMethodRequest {
                override fun createInputConnection(outAttributes: EditorInfo): InputConnection =
                    request.createInputConnection(outAttributes).also { outAttributes.imeOptions = outAttributes.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING }
            },
        )
    }
}

/** What a field asks of the keyboard, as it asks it: while keyboards may learn. */
private object AsItIs : PlatformTextInputInterceptor {
    override suspend fun interceptStartInputMethod(request: PlatformTextInputMethodRequest, nextHandler: PlatformTextInputSession): Nothing {
        nextHandler.startInputMethod(request)
    }
}
