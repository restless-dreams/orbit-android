package com.hyuchia.orbit.media

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build.VERSION.SDK_INT
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil3.BitmapImage
import coil3.Extras
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.BitmapFactoryDecoder
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.getExtra
import coil3.gif.isAnimatedHeif
import coil3.request.ImageRequest
import coil3.request.Options
import com.hyuchia.orbit.data.animationExtension

/** Whether a request plays an animated picture even while Android's animations are off. */
private val PlayAnimation = Extras.Key(default = false)

/**
 * Plays an animated picture, a GIF or WebP that moves, even while Android's
 * animations are off: as the viewer's play button asks to. It's in the
 * request's key too, which is how Coil and Telephoto tell a request that
 * plays from one that doesn't, and load it again.
 */
fun ImageRequest.Builder.playAnimation(play: Boolean = true) = apply {
    extras[PlayAnimation] = play
    memoryCacheKeyExtra("animation", if (play) "play" else null)
}

/**
 * Animated pictures, GIFs and WebPs that move (and HEIFs, from Android 11),
 * shown still while Android's animations are off, as "Remove animations" in
 * its accessibility settings or battery saver turn them off: as their first
 * frame, a [FirstFrame], unless the request asks to play them
 * ([playAnimation]). Otherwise coil-gif's `AnimatedImageDecoder`, which
 * comes after it, plays them with Android's own decoder, and every other
 * picture goes on to Coil's.
 */
class StillAnimations(private val animationsOn: () -> Boolean = ValueAnimator::areAnimatorsEnabled) : Decoder.Factory {
    override fun create(result: SourceFetchResult, options: Options, imageLoader: ImageLoader): Decoder? {
        if (animationsOn() || options.getExtra(PlayAnimation)) {
            return null
        }

        val source = result.source.source()
        val moves = animationExtension(source.peek().inputStream()) != null || (SDK_INT >= 30 && DecodeUtils.isAnimatedHeif(source))

        if (!moves) {
            return null
        }

        return Decoder {
            val first = BitmapFactoryDecoder(result.source, options).decode()
            val bitmap = (first.image as BitmapImage).bitmap
            // Not kept in memory to share: once animations are back on, the picture plays again.
            DecodeResult(FirstFrame(bitmap).asImage(shareable = false), first.isSampled)
        }
    }
}

/**
 * An animated picture's first frame, shown still while Android's animations
 * are off ([StillAnimations]): drawn to fill its bounds, as Coil draws
 * pictures. Telling it apart lets the viewer offer to play it.
 */
class FirstFrame(val bitmap: Bitmap) : Drawable() {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    override fun draw(canvas: Canvas) {
        canvas.drawBitmap(bitmap, null, bounds, paint)
    }

    override fun getIntrinsicWidth(): Int = bitmap.width

    override fun getIntrinsicHeight(): Int = bitmap.height

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Whether Android's animations are on, as it changes: "Remove animations",
 * in its accessibility settings, turns them off, and so does battery saver
 * on some phones.
 */
@Composable
fun animationsOn(): Boolean {
    val context = LocalContext.current
    var on by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }

    DisposableEffect(context) {
        if (SDK_INT >= 33) {
            val listener = ValueAnimator.DurationScaleChangeListener { scale -> on = scale != 0f }
            ValueAnimator.registerDurationScaleChangeListener(listener)
            onDispose { ValueAnimator.unregisterDurationScaleChangeListener(listener) }
        } else {
            // Before Android 13, the setting itself: Android may change the animations' own scale after this hears it changed.
            val resolver = context.contentResolver

            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    on = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
                }
            }

            resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
            onDispose { resolver.unregisterContentObserver(observer) }
        }
    }

    return on
}
