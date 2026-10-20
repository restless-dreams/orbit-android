package com.hyuchia.orbit.present

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hyuchia.orbit.Logs
import com.hyuchia.orbit.R
import kotlinx.coroutines.CoroutineScope
import java.io.File

private const val TAG = "TvCasting"

/** A TV the phone is casting to. */
fun interface Tv {
    /** An audience on the TV for a presentation of the map in `mapFolder`: a new one each time a presentation starts on it. */
    fun audience(mapFolder: File, scope: CoroutineScope): Audience
}

/**
 * How this build reaches TVs, as `tvCasting` in its own source set. The
 * build for Google Play casts to them with Google Cast, given a receiver
 * (see CONTRIBUTING.md); the libre build has no Google code, and casts the
 * phone's screen with Android's own screen casting, as the build for
 * Google Play does on phones without Google Play services.
 */
interface TvCasting {
    /** The TV the phone is casting to, while it is. */
    @Composable
    fun rememberTv(): Tv?

    /** The way onto a TV, as [CastButton] describes it. */
    @Composable
    fun Button(dark: Boolean, modifier: Modifier, screenCast: Boolean)

    /** The TV the phone is casting to, while it is, as [CastingButton] describes it. */
    @Composable
    fun CastingButton(dark: Boolean, modifier: Modifier)
}

/**
 * The way onto a TV. Where the build and the phone cast with Google Cast,
 * Google's Cast button: it picks a TV, or stops casting to it, and shows
 * only when there's a TV around. Elsewhere, and where `screenCast` allows,
 * Android's own screen casting, which shows the phone's screen on the TV.
 * `dark` is the screen behind the button, for its icon's color.
 */
@Composable
fun CastButton(dark: Boolean, modifier: Modifier = Modifier, screenCast: Boolean = false) {
    tvCasting.Button(dark, modifier, screenCast)
}

/**
 * The TV the phone is casting to, while it is: Google's Cast button, to
 * stop or change it. Nothing otherwise, and it never starts Google Cast nor
 * looks for TVs: only presenting does, with [CastButton].
 */
@Composable
fun CastingButton(dark: Boolean, modifier: Modifier = Modifier) {
    tvCasting.CastingButton(dark, modifier)
}

/** Opens Android's screen casting, where the phone's screen goes onto a TV; nothing on phones without it. */
@Composable
internal fun ScreenCastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val intent = remember { Intent(Settings.ACTION_CAST_SETTINGS) }

    if (intent.resolveActivity(context.packageManager) == null) {
        return
    }

    IconButton(
        onClick = {
            try {
                context.startActivity(intent)
            } catch (missing: ActivityNotFoundException) {
                Logs.warn(TAG, "This phone has no screen casting", missing)
            }
        },
        modifier = modifier,
    ) {
        Icon(Icons.Outlined.Cast, contentDescription = stringResource(R.string.present_show_on_a_tv))
    }
}
