package com.hyuchia.orbit.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.ui.isShort

/** Why the map's page can't draw the map until a new page loads it. */
enum class PageProblem {
    /** Android stopped the page's process, as it does to free memory. */
    Stopped,

    /** The page itself didn't load. */
    DidNotLoad,
}

/**
 * In the map's place while its page can't draw it ([PageProblem]): what
 * happened, that the map itself is safe, and the way to load it again, as
 * Home says it has no maps yet, under a shape of Material 3 Expressive's. A
 * page that didn't load shows in the error colors. On a short screen, a
 * phone on its side, the shape stands beside the words, which keep the
 * height. Screen readers hear its title as it comes, and find it among the
 * headings.
 */
@Composable
internal fun PageProblemNotice(problem: PageProblem, onLoadAgain: () -> Unit, modifier: Modifier = Modifier) {
    val didNotLoad = problem == PageProblem.DidNotLoad

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        if (isShort(maxHeight)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 16.dp),
            ) {
                Badge(didNotLoad)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f, fill = false)) {
                    Words(didNotLoad, TextAlign.Start)
                    Spacer(Modifier.height(4.dp))
                    LoadAgain(onLoadAgain)
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 24.dp),
            ) {
                Badge(didNotLoad)
                Spacer(Modifier.height(4.dp))
                Words(didNotLoad, TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                LoadAgain(onLoadAgain)
            }
        }
    }
}

/** The problem's icon on a Cookie9Sided shape: the error's colors for a page that didn't load. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Badge(didNotLoad: Boolean) {
    val colors = MaterialTheme.colorScheme

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(88.dp)
            .background(if (didNotLoad) colors.errorContainer else colors.secondaryContainer, MaterialShapes.Cookie9Sided.toShape()),
    ) {
        Icon(
            if (didNotLoad) Icons.Outlined.ErrorOutline else Icons.Outlined.Memory,
            contentDescription = null,
            tint = if (didNotLoad) colors.onErrorContainer else colors.onSecondaryContainer,
            modifier = Modifier.size(40.dp),
        )
    }
}

/** What happened, as a heading screen readers hear as it comes, and that the map is safe. */
@Composable
private fun Words(didNotLoad: Boolean, align: TextAlign) {
    Text(
        stringResource(if (didNotLoad) R.string.map_did_not_load_title else R.string.map_stopped_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = align,
        modifier = Modifier.semantics {
            heading()
            liveRegion = LiveRegionMode.Polite
        },
    )
    Text(
        stringResource(if (didNotLoad) R.string.map_did_not_load else R.string.map_stopped),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = align,
    )
}

@Composable
private fun LoadAgain(onLoadAgain: () -> Unit) {
    Button(onClick = onLoadAgain, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(stringResource(R.string.map_load_it_again))
    }
}
