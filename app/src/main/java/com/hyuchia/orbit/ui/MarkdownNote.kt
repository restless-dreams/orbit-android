package com.hyuchia.orbit.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.Density
import coil3.compose.AsyncImagePainter
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.ImageWidth
import com.mikepenz.markdown.model.PlaceholderConfig

/**
 * A node's note as people read it: markdown rendered natively, or plain text.
 * Links to other ideas (`#id`) call [onNodeLink]; web links open in a tab.
 */
@Composable
fun MarkdownNote(note: String, plain: Boolean, onNodeLink: (String) -> Unit, modifier: Modifier = Modifier) {
    if (plain) {
        Text(note, style = MaterialTheme.typography.bodyLarge, modifier = modifier)
        return
    }

    val context = LocalContext.current

    val links = remember(onNodeLink) {
        object : UriHandler {
            override fun openUri(uri: String) {
                if (uri.startsWith("#")) {
                    onNodeLink(uri.removePrefix("#"))
                } else {
                    openLink(context, uri)
                }
            }
        }
    }

    val type = MaterialTheme.typography

    CompositionLocalProvider(LocalUriHandler provides links) {
        Markdown(
            content = note,
            typography = markdownTypography(
                h1 = type.headlineSmall,
                h2 = type.titleLarge,
                h3 = type.titleMedium,
                h4 = type.titleSmall,
                h5 = type.titleSmall,
                h6 = type.titleSmall,
                text = type.bodyLarge,
                paragraph = type.bodyLarge,
                bullet = type.bodyLarge,
                list = type.bodyLarge,
                ordered = type.bodyLarge,
            ),
            imageTransformer = remember { NotePictures() },
            modifier = modifier.fillMaxWidth(),
        )
    }
}

/**
 * A note's pictures load from the web or the note itself, as on the map's
 * page: an address of any other kind shows nothing. One that shows nothing,
 * or can't load (a dead address), takes no room: the renderer would keep a
 * blank 200 dp tall above the text for it.
 */
private class NotePictures : ImageTransformer {
    /** The pictures that couldn't load, by their address: the renderer reads it as it lays the text out again. */
    private val failed = mutableStateSetOf<String>()

    @Composable
    override fun transform(link: String): ImageData? {
        if (!isWebPicture(link)) {
            return null
        }

        val data = Coil3ImageTransformerImpl.transform(pictureAddress(link))
        val painter = data.painter as? AsyncImagePainter ?: return data
        val state by painter.state.collectAsState()
        val broken = state is AsyncImagePainter.State.Error

        LaunchedEffect(link, broken) {
            if (broken) {
                failed.add(link)
            } else {
                failed.remove(link)
            }
        }

        return if (broken) null else data
    }

    @Composable
    override fun intrinsicSize(painter: Painter): Size = Coil3ImageTransformerImpl.intrinsicSize(painter)

    override fun placeholderConfig(
        link: String,
        density: Density,
        containerSize: Size,
        imageWidth: ImageWidth,
        imageSize: Size,
        imageSizeChanged: ((String, Size) -> Unit)?,
    ): PlaceholderConfig = if (link in failed || !isWebPicture(link)) {
        PlaceholderConfig(Size.Zero)
    } else {
        Coil3ImageTransformerImpl.placeholderConfig(link, density, containerSize, imageWidth, imageSize, imageSizeChanged)
    }
}
