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
 * its own fields (`OrbitPage.incognito`).
 */
@Composable
fun KeyboardsDontLearn(enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) {
        InterceptPlatformTextInput(NoPersonalizedLearning, content)
    } else {
        content()
    }
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
