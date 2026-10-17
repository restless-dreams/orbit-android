package com.hyuchia.orbit.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography

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
            imageTransformer = Coil3ImageTransformerImpl,
            modifier = modifier.fillMaxWidth(),
        )
    }
}
