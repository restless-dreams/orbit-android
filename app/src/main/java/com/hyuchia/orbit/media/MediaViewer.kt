package com.hyuchia.orbit.media

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.mediaType
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.linkScheme
import com.hyuchia.orbit.ui.shareFile
import com.hyuchia.orbit.ui.shareLink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.flick.FlickToDismiss
import me.saket.telephoto.flick.FlickToDismissState
import me.saket.telephoto.flick.rememberFlickToDismissState
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.HardwareShortcutDetector
import me.saket.telephoto.zoomable.HardwareShortcutDetector.ShortcutEvent
import me.saket.telephoto.zoomable.HardwareShortcutsSpec
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import java.io.File
import kotlin.math.abs

/** A double tap zooms a fitted picture this many times, around the tap. */
private const val DOUBLE_TAP_ZOOM = 2.5f

/** Each press of + or - zooms by this much. */
private const val KEY_ZOOM = 1.5f

/** The darkness behind pictures, and of the bars over them. */
private val Dark = Color(0xFF0B0C0F)

/**
 * The map's pictures and videos, full screen, from `start`: swipe or use
 * the arrow keys between them, in the order the outline reads them. A
 * picture fits the screen and zooms with a pinch, a double tap where it was
 * tapped, the wheel, or + and - (0 fits it again), and stays sharp at any
 * zoom, as Telephoto reads big pictures a part at a time. A video's page
 * shows its poster, and plays with `onPlay`. A tap shows or hides the
 * controls: closing and sharing above, the caption and description below.
 * Back, Escape, and a flick up or down while the picture isn't zoomed close
 * it. `source` is what a picture's address loads from: a file in the map's
 * folder, or a web address. `onKey` takes keys first, as a presentation does.
 */
@OptIn(ExperimentalTelephotoApi::class)
@Composable
fun MediaViewer(
    items: List<GalleryItem>,
    start: Int,
    source: (String) -> Any?,
    onPlay: (GalleryItem) -> Unit,
    onClosed: () -> Unit,
    onKey: (android.view.KeyEvent) -> Boolean = { false },
) {
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onClosed() }
        return
    }

    var controls by rememberSaveable { mutableStateOf(true) }

    FullScreenWindow(onClosed = onClosed, immersive = !controls, onKey = onKey) { window ->
        val pager = rememberPagerState(initialPage = start.coerceIn(items.indices)) { items.size }
        val zooms = remember { mutableStateMapOf<Int, ZoomableState>() }
        // How far the page on screen has been flicked away: what's behind shows through as it goes.
        var flicked by remember { mutableFloatStateOf(0f) }
        val scope = rememberCoroutineScope()
        val focus = remember { FocusRequester() }
        val rightToLeft = LocalLayoutDirection.current == LayoutDirection.Rtl
        val view = LocalView.current

        // After a touch, Android spends the first arrow key on finding a view to focus: the viewer has it already.
        DisposableEffect(view) {
            view.isFocusableInTouchMode = true
            view.requestFocus()
            onDispose {}
        }

        LaunchedEffect(Unit) { focus.requestFocus() }

        fun step(by: Int) {
            val target = (pager.currentPage + by).coerceIn(items.indices)

            if (target != pager.currentPage) {
                scope.launch { pager.animateScrollToPage(target) }
            }
        }

        fun zoom(change: suspend (ZoomableState) -> Unit) {
            zooms[pager.currentPage]?.let { state -> scope.launch { change(state) } }
        }

        fun keys(event: KeyEvent): Boolean {
            if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
                return false
            }

            val typed = event.utf16CodePoint.toChar()

            when {
                event.key == Key.DirectionRight || event.key == Key.PageDown -> step(if (rightToLeft && event.key == Key.DirectionRight) -1 else 1)
                event.key == Key.DirectionLeft || event.key == Key.PageUp -> step(if (rightToLeft && event.key == Key.DirectionLeft) 1 else -1)
                typed == '+' || typed == '=' || event.key == Key.NumPadAdd || event.key == Key.ZoomIn -> zoom { it.zoomBy(KEY_ZOOM) }
                typed == '-' || event.key == Key.NumPadSubtract || event.key == Key.ZoomOut -> zoom { it.zoomBy(1f / KEY_ZOOM) }
                typed == '0' || event.key == Key.NumPad0 -> zoom { it.resetZoom() }
                event.key == Key.Spacebar || event.key == Key.Enter || event.key == Key.NumPadEnter -> {
                    val item = items[pager.currentPage]

                    if (item.kind != MediaKind.Video) {
                        return false
                    }

                    onPlay(item)
                }
                else -> return false
            }

            return true
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Dark.copy(alpha = 1f - flicked))
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent(::keys),
        ) {
            HorizontalPager(
                state = pager,
                key = { items[it].key },
                beyondViewportPageCount = 1,
                pageSpacing = 16.dp,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val item = items[page]
                val flick = rememberFlickToDismissState(rotateOnDrag = false)
                Flicked(flick, current = page == pager.currentPage, onFlicked = { flicked = it }, onDismissed = window::closeNow)

                FlickToDismiss(state = flick, modifier = Modifier.fillMaxSize()) {
                    when (item.kind) {
                        MediaKind.Picture -> PicturePage(item, source(item.media.src), onZoomable = { state -> if (state == null) zooms.remove(page) else zooms[page] = state }) {
                            controls = !controls
                        }
                        MediaKind.Video -> VideoPage(item, posterOf(item.media)?.let(source), onPlay = { onPlay(item) }) {
                            controls = !controls
                        }
                    }
                }
            }

            val shown = items[pager.currentPage]
            val fade = Modifier.graphicsLayer { alpha = (1f - flicked * 4f).coerceAtLeast(0f) }

            AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter).then(fade)) {
                TopControls(shown, pager, items.size, source(shown.media.src), onClose = window::close)
            }

            AnimatedVisibility(controls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter).then(fade)) {
                Caption(shown)
            }
        }
    }
}

/** Hears how far a page is flicked while it's the one on screen, and when a flick sends it away. */
@OptIn(ExperimentalTelephotoApi::class)
@Composable
private fun Flicked(flick: FlickToDismissState, current: Boolean, onFlicked: (Float) -> Unit, onDismissed: () -> Unit) {
    if (current) {
        LaunchedEffect(flick) { snapshotFlow { flick.offsetFraction }.collect(onFlicked) }
    }

    val gesture = flick.gestureState

    if (gesture is FlickToDismissState.GestureState.Dismissing) {
        LaunchedEffect(gesture) {
            delay(gesture.animationDuration / 2)
            onDismissed()
        }
    }
}

/** A picture, fitted to the screen and zoomable, with a spinner while it loads and a note if it can't. */
@OptIn(ExperimentalTelephotoApi::class)
@Composable
private fun PicturePage(item: GalleryItem, model: Any?, onZoomable: (ZoomableState?) -> Unit, onTap: () -> Unit) {
    val context = LocalContext.current
    var failed by remember(model) { mutableStateOf(model == null) }
    // A picture of the map's that isn't in its folder: left out of a backup that was full, say.
    var missing by remember(model) { mutableStateOf(false) }
    val zoomable = rememberZoomableState(hardwareShortcutsSpec = HardwareShortcutsSpec(shortcutDetector = WheelZoom))
    val state = rememberZoomableImageState(zoomable)

    DisposableEffect(zoomable) {
        onZoomable(zoomable)
        onDispose { onZoomable(null) }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (model != null) {
            val request = remember(model) {
                ImageRequest.Builder(context).data(model).listener(
                    onError = { _, _ ->
                        missing = model is File && !model.isFile
                        failed = true
                    },
                ).build()
            }

            ZoomableAsyncImage(
                model = request,
                contentDescription = item.description(),
                state = state,
                onClick = { onTap() },
                onDoubleClick = ZoomInOnDoubleTap,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (failed) {
            val problem = if (missing) {
                R.string.media_picture_missing
            } else {
                R.string.media_picture_unreadable
            }

            Problem(stringResource(problem), onTap)
        } else if (!state.isImageDisplayed) {
            LoadingIndicator(color = Color.White)
        }
    }
}

/** A video's page: its poster, fitted, with a big play button. */
@Composable
private fun VideoPage(item: GalleryItem, poster: Any?, onPlay: () -> Unit, onTap: () -> Unit) {
    val caption = item.media.caption?.takeIf { it.isNotBlank() }

    val play = if (caption != null) {
        stringResource(R.string.media_play_captioned, caption)
    } else {
        stringResource(R.string.media_play_video_of, item.label)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
    ) {
        if (poster != null) {
            AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }

        FilledIconButton(
            onClick = onPlay,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.92f), contentColor = Dark),
            modifier = Modifier
                .size(88.dp)
                .semantics { contentDescription = play },
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(52.dp))
        }
    }
}

@Composable
private fun Problem(message: String, onTap: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
            .padding(32.dp),
    ) {
        Box(Modifier.weight(1f))
        Icon(Icons.Outlined.BrokenImage, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(48.dp))
        Text(message, color = Color.White, style = MaterialTheme.typography.bodyLarge)
        Box(Modifier.weight(1f))
    }
}

/** Closing, which picture this is of how many, and sharing it, over a shade so they read on any picture. */
@Composable
private fun TopControls(item: GalleryItem, pager: PagerState, count: Int, source: Any?, onClose: () -> Unit) {
    val context = LocalContext.current

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Dark.copy(alpha = 0.72f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 24.dp),
        ) {
            val close = stringResource(R.string.common_close)

            Hint(close) {
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = close) }
            }

            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(item.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)

                if (count > 1) {
                    Text(
                        stringResource(R.string.media_position, pager.currentPage + 1, count),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }
            }

            if (shareable(source)) {
                val shareLabel = if (item.kind == MediaKind.Picture) {
                    stringResource(R.string.media_share_picture)
                } else {
                    stringResource(R.string.media_share_video)
                }

                Hint(shareLabel) {
                    IconButton(onClick = { share(context, item, source) }) {
                        Icon(Icons.Outlined.Share, contentDescription = shareLabel)
                    }
                }
            }
        }
    }
}

/** The caption, and a picture's description under it, over a shade at the bottom; long ones scroll. */
@Composable
private fun Caption(item: GalleryItem) {
    val caption = item.media.caption?.takeIf { it.isNotBlank() }
    val description = item.media.alt?.takeIf { it.isNotBlank() && it != caption && item.kind == MediaKind.Picture }

    if (caption == null && description == null) {
        return
    }

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Dark.copy(alpha = 0.8f))))
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).union(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)))
            .padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 20.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            caption?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Color.White) }
            description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.78f)) }
        }
    }
}

/** Whether a picture or video can be shared: a file in the map's folder that's on this phone, or a web address. */
private fun shareable(source: Any?): Boolean = (source is File && source.isFile) || (source is String && linkScheme(source).let { it == "http" || it == "https" })

/** Shares the file itself for one in the map's folder, and the address for one on the web. */
private fun share(context: Context, item: GalleryItem, source: Any?) {
    when (source) {
        is File -> shareFile(context, FileProvider.getUriForFile(context, "${context.packageName}.files", source), mediaType(source.name), item.label)
        is String -> shareLink(context, source, item.label)
    }
}

/** A double tap zooms into a fitted picture around the tap, and another fits it again. */
private object ZoomInOnDoubleTap : DoubleClickToZoomListener {
    override suspend fun onDoubleClick(state: ZoomableState, centroid: Offset) {
        val transformation = state.contentTransformation.takeIf { it.isSpecified } ?: return

        if ((state.zoomFraction ?: 0f) > 0.01f) {
            state.resetZoom()
        } else {
            state.zoomTo(transformation.scaleMetadata.initialScale.scaleX * DOUBLE_TAP_ZOOM, centroid)
        }
    }
}

/** The mouse wheel zooms around the pointer, as it does on the map; keys are the viewer's own. */
private object WheelZoom : HardwareShortcutDetector {
    override fun detectKey(event: KeyEvent): ShortcutEvent? = null

    override fun detectScroll(event: PointerEvent): ShortcutEvent? {
        val scroll = event.changes.fold(0f) { total, change -> total + change.scrollDelta.y }

        if (scroll == 0f) {
            return null
        }

        return ShortcutEvent.Zoom(
            direction = if (scroll < 0f) ShortcutEvent.ZoomDirection.In else ShortcutEvent.ZoomDirection.Out,
            zoomFactor = (ShortcutEvent.DefaultZoomFactor * abs(scroll)).coerceAtMost(0.5f),
            centroid = event.changes.first().position,
        )
    }
}
