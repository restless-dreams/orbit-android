package com.hyuchia.orbit.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.hyuchia.orbit.MainActivity
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.home.MapThumbnail
import com.hyuchia.orbit.home.describe
import com.hyuchia.orbit.privacy.LockedOrbit
import com.hyuchia.orbit.privacy.PrivateOnScreen
import com.hyuchia.orbit.privacy.PrivateView
import com.hyuchia.orbit.privacy.ShowPrivateMapsButton
import com.hyuchia.orbit.privacy.conceals
import com.hyuchia.orbit.privacy.keepPrivateMapsOutOfRecents
import com.hyuchia.orbit.privacy.lockedLook
import com.hyuchia.orbit.privacy.rememberUnlocking
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.OrbitTheme
import com.hyuchia.orbit.theme.ThemedSystemBars
import com.hyuchia.orbit.theme.ThemedWindowBackground
import com.hyuchia.orbit.theme.appIsDark
import com.hyuchia.orbit.ui.Hint
import com.hyuchia.orbit.ui.OrbitGlyph
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which map a widget shows: asked as it's placed on the home screen, and again from the widget's settings. */
class MapWidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Its list names private maps while they're unlocked: Recents gets no picture of it then, as of Orbit itself.
        keepPrivateMapsOutOfRecents()
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Leaving without a choice keeps the widget off the home screen, or as it was.
        setResult(Activity.RESULT_CANCELED, result())

        // Another app can start this screen with any number: only Orbit's own widgets are set up here.
        val widget = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || widget?.provider?.packageName != packageName) {
            finish()
            return
        }

        val app = container
        val settings = app.settings.settings.stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        // The map the widget shows now, when it's changing maps.
        var shown by mutableStateOf<String?>(null)

        lifecycleScope.launch {
            if (app.maps.maps.value == null) {
                app.maps.load()
            }
        }

        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@MapWidgetConfigureActivity).getGlanceIdBy(appWidgetId)
            shown = MapWidget().getAppWidgetState<Preferences>(this@MapWidgetConfigureActivity, glanceId)[WidgetMapKey]
        }

        setContent {
            val current by settings.collectAsStateWithLifecycle()
            val maps by app.maps.maps.collectAsStateWithLifecycle()

            // Once the settings are read, so private maps that hide never show for a moment first.
            current?.let { chosen ->
                CompositionLocalProvider(LocalAppSettings provides chosen) {
                    val dark = appIsDark()
                    ThemedSystemBars(dark)

                    OrbitTheme(dark = dark) {
                        ThemedWindowBackground()
                        ChooseMap(maps, shown, onChoose = ::choose, onClose = ::finish)
                    }
                }
            }
        }
    }

    private fun result(): Intent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    private fun choose(map: MapSummary) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@MapWidgetConfigureActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@MapWidgetConfigureActivity, glanceId) { it[WidgetMapKey] = map.id }
            MapWidget().update(this@MapWidgetConfigureActivity, glanceId)
            setResult(Activity.RESULT_OK, result())
            finish()
        }
    }
}

/**
 * The maps a widget can show, as its list orders them: pinned first, then the most recently opened.
 * Private maps that are locked come last, without their names; while Settings hides them, they're
 * left out altogether.
 */
internal fun widgetChoices(maps: List<MapSummary>, privacy: PrivateView, hiding: Boolean): List<MapSummary> {
    val listed = if (privacy.conceals(hiding)) maps.filterNot(privacy::hides) else maps
    return listed.sortedWith(compareBy<MapSummary> { privacy.hides(it) }.thenByDescending { it.metadata.pinned }.thenByDescending { it.metadata.opened })
}

/** Whether the widget's list names private maps (`listed`): unlocked ones, and Recents gets no picture of it then. */
internal fun widgetNamesPrivateMaps(listed: List<MapSummary>, privacy: PrivateView): Boolean = listed.any { it.metadata.isPrivate && !privacy.hides(it) }

/**
 * The widget's list of maps to choose from, `maps` while they're read (null
 * until then, with Material's loading indicator), and a way into Orbit when
 * there are none yet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChooseMap(maps: List<MapSummary>?, shown: String?, onChoose: (MapSummary) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    // While private maps are locked, they're listed last, without their names, or left out while they hide (see widgetChoices).
    val privacy by context.container.privateMaps.view.collectAsStateWithLifecycle()
    val hiding = LocalAppSettings.current.hideLockedPrivateMaps
    val ordered = widgetChoices(maps.orEmpty(), privacy, hiding)
    PrivateOnScreen(widgetNamesPrivateMaps(ordered, privacy))
    val unlocking = rememberUnlocking()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_choose_map)) },
                navigationIcon = {
                    val close = stringResource(R.string.common_close)

                    Hint(close) {
                        IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = close) }
                    }
                },
                actions = {
                    // While private maps hide, the way back to them, there whether there are any or not.
                    if (privacy.conceals(hiding)) {
                        ShowPrivateMapsButton(onShow = { unlocking.ask() })
                    }
                },
            )
        },
    ) { padding ->
        val direction = LocalLayoutDirection.current

        if (maps == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator(Modifier.size(48.dp))
            }

            return@Scaffold
        }

        // No maps yet, or none that shows while private maps hide, as a new Home looks: the way to start one.
        if (ordered.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 32.dp),
            ) {
                OrbitGlyph(branches = 6, modifier = Modifier.size(120.dp))
                Text(
                    stringResource(R.string.widget_no_maps),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Button(onClick = {
                    context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    onClose()
                }) {
                    Text(stringResource(R.string.widget_open_orbit))
                }
            }

            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(direction),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            items(ordered, key = { it.id }) { map ->
                if (privacy.hides(map)) {
                    ListItem(
                        headlineContent = { Text(map.metadata.cover?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.private_map), maxLines = 1) },
                        supportingContent = { Text(stringResource(R.string.private_locked_card_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingContent = {
                            LockedOrbit(
                                emblemSize = 32.dp,
                                look = lockedLook(map.id),
                                icon = map.metadata.cover?.icon,
                                modifier = Modifier
                                    .size(width = 96.dp, height = 60.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = stringResource(R.string.private_unlock_private_maps)) { unlocking.ask() }
                            .padding(horizontal = 4.dp),
                    )

                    return@items
                }

                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (map.metadata.isPrivate) {
                                Icon(Icons.Outlined.Lock, contentDescription = stringResource(R.string.private_badge), modifier = Modifier.size(16.dp))
                            }

                            Text(map.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    supportingContent = { Text(describe(resources, map), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = if (map.id == shown) {
                        { Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.widget_shown_now), tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    leadingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MapThumbnail(
                                id = map.id,
                                contentKey = map.contentKey,
                                style = map.outline.style,
                                branches = map.outline.branches,
                                modifier = Modifier
                                    .size(width = 96.dp, height = 60.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            ) { context.container.maps.read(map.id) }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = stringResource(R.string.widget_show_map, map.title)) { onChoose(map) }
                        .padding(horizontal = 4.dp),
                )
            }
        }
    }
}
