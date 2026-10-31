package com.hyuchia.orbit.settings

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.OrbitSnackbarHost
import com.hyuchia.orbit.ui.isShort

/** How wide Settings' screens let their words run on a tablet: longer lines are hard to read. */
internal val ReadingWidth = 720.dp

/** Settings' own bar is large; the screens under it have a medium one. */
internal enum class BarSize { Large, Medium }

/**
 * A screen of Settings, with its bar: large for Settings and medium for the
 * screens under it, collapsing as the screen scrolls, or slim from the start
 * on a short screen. On a tablet, the bar's back arrow and title line up
 * with the column the screen keeps to, while its color still fills the width.
 */
@Composable
internal fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    size: BarSize = BarSize.Medium,
    snackbars: SnackbarHostState? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A phone on its side: the bar is slim from the start, as Home's is.
        val short = isShort(maxHeight)
        val side = ((maxWidth - ReadingWidth) / 2).coerceAtLeast(0.dp)
        val insets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = side, right = side))
        val scrollBehavior = if (short) TopAppBarDefaults.pinnedScrollBehavior() else TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

        val back: @Composable () -> Unit = {
            val name = stringResource(R.string.common_back)

            Hint(name) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = name) }
            }
        }

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                when {
                    short -> TopAppBar(title = { Text(title) }, navigationIcon = back, actions = actions, windowInsets = insets, scrollBehavior = scrollBehavior)
                    size == BarSize.Large -> LargeFlexibleTopAppBar(title = { Text(title) }, navigationIcon = back, actions = actions, windowInsets = insets, scrollBehavior = scrollBehavior)
                    else -> MediumFlexibleTopAppBar(title = { Text(title) }, navigationIcon = back, actions = actions, windowInsets = insets, scrollBehavior = scrollBehavior)
                }
            },
            snackbarHost = { snackbars?.let { OrbitSnackbarHost(it) } },
            content = content,
        )
    }
}

/**
 * A setting that is on or off, as a row of Settings like the others: the
 * whole row switches it, and its switch shows which. TalkBack hears a switch.
 */
@Composable
internal fun SettingToggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        onClick = { onChange(!checked) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        // The switch stays level with the words, however many lines they take, as in Android's own settings.
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(horizontal = 8.dp)
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            },
    ) {
        Text(title)
    }
}
