package com.hyuchia.orbit.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.AppSettings
import com.hyuchia.orbit.data.MapSummary
import com.hyuchia.orbit.home.MapThumbnail
import com.hyuchia.orbit.home.describe
import com.hyuchia.orbit.theme.LocalAppSettings
import com.hyuchia.orbit.theme.OrbitTheme
import com.hyuchia.orbit.theme.appIsDark
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which map a widget shows: asked as it's placed on the home screen, and again from the widget's settings. */
class MapWidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // Leaving without a choice keeps the widget off the home screen, or as it was.
        setResult(Activity.RESULT_CANCELED, result())

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
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

            CompositionLocalProvider(LocalAppSettings provides (current ?: AppSettings())) {
                OrbitTheme(dark = appIsDark()) {
                    ChooseMap(maps.orEmpty(), shown, onChoose = ::choose, onClose = ::finish)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooseMap(maps: List<MapSummary>, shown: String?, onChoose: (MapSummary) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val ordered = maps.sortedWith(compareByDescending<MapSummary> { it.metadata.pinned }.thenByDescending { it.metadata.opened })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_choose_map)) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.common_close)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp)) {
            items(ordered, key = { it.id }) { map ->
                ListItem(
                    headlineContent = { Text(map.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
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
