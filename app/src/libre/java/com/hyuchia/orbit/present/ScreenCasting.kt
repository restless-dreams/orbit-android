package com.hyuchia.orbit.present

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The libre build has no Google code: a presentation reaches a TV as the phone's screen, with Android's own screen casting. */
val tvCasting: TvCasting = ScreenCasting

private object ScreenCasting : TvCasting {
    @Composable
    override fun rememberTv(): Tv? = null

    @Composable
    override fun Button(dark: Boolean, modifier: Modifier, screenCast: Boolean) {
        if (screenCast) {
            ScreenCastButton(modifier)
        }
    }
}
