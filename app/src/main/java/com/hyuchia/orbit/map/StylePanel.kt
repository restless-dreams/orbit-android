package com.hyuchia.orbit.map

import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.page.PictureRequest
import com.hyuchia.orbit.theme.MapPalette
import com.hyuchia.orbit.theme.MapPalettes
import com.hyuchia.orbit.theme.MapStyle
import com.hyuchia.orbit.theme.MapStyles
import com.hyuchia.orbit.theme.hex
import com.hyuchia.orbit.ui.ToggleRow
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * How the map looks and opens: Orbit's styles, each previewed with this very
 * map; color sets; and the layout options its frontmatter keeps. Changes show
 * on the map at once, and are saved with it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StylePanel(model: MapViewModel, ui: MapUi, sheet: Boolean) {
    val options = ui.map?.options
    fun set(key: String, value: JsonElement?) = model.setOptions(mapOf(key to value))

    PanelFrame(title = stringResource(R.string.common_style_and_layout), sheet = sheet, onDismiss = { model.show(null) }, partial = true, scrim = false) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            val isSelected = { style: MapStyle -> style.name == ui.map?.style || (style.name == null && ui.map?.style == null) }

            if (sheet) {
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(MapStyles, key = { it.name ?: "app" }) { style ->
                        StyleTile(model, ui, style, selected = isSelected(style)) { set("theme", style.name?.let(::JsonPrimitive)) }
                    }
                }
            } else {
                // Beside the map there's height to spare: every style at once, two to a row.
                FlowRow(
                    maxItemsInEachRow = 2,
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) {
                    MapStyles.forEach { style ->
                        StyleTile(model, ui, style, selected = isSelected(style)) { set("theme", style.name?.let(::JsonPrimitive)) }
                    }
                }
            }

            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.map_colors), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

                MapPalettes.forEach { palette ->
                    val hexes = palette.colors?.map { it.hex() }
                    val selected = options?.palette?.map { it.uppercase() } == hexes

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(16.dp))
                            .selectable(selected = selected, role = Role.RadioButton) {
                                set("palette", hexes?.let { JsonArray(it.map(::JsonPrimitive)) })
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(stringResource(palette.nameResource), style = MaterialTheme.typography.bodyLarge, modifier = if (palette.colors == null) Modifier.weight(1f) else Modifier.width(120.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (palette.colors ?: emptyList()).forEach { color ->
                                Box(Modifier.size(22.dp).background(color, CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
                            }
                        }
                    }
                }
            }

            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.map_layout), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.map_opens_in), style = MaterialTheme.typography.bodyMedium)
                val opensInOverview = options?.viewMode == "all"

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !opensInOverview,
                        onClick = { set("viewMode", null) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text(stringResource(R.string.map_focus)) }
                    SegmentedButton(
                        selected = opensInOverview,
                        onClick = { set("viewMode", JsonPrimitive("all")) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text(stringResource(R.string.map_overview)) }
                }

                Setting(stringResource(R.string.map_orbit_rings), stringResource(R.string.map_orbit_rings_description), options?.showRings != false) { on ->
                    set("showRings", if (on) null else JsonPrimitive(false))
                }

                Setting(stringResource(R.string.map_progress_rings), stringResource(R.string.map_progress_rings_description), options?.progressRings != false) { on ->
                    // Saved only while off: on is how every map starts.
                    set("progressRings", if (on) null else JsonPrimitive(false))
                }

                val drifting = options?.orbitAnimation == true

                Setting(stringResource(R.string.map_drift), stringResource(R.string.map_drift_description), drifting) { on ->
                    set("orbitAnimation", if (on) JsonPrimitive(true) else null)
                }

                if (drifting) {
                    val speed = remember(options.orbitSpeed) { SliderState(value = (options.orbitSpeed ?: 90.0).toFloat(), trackRange = 20f..180f) }
                    val seconds = speed.value.roundToInt()
                    Text(pluralStringResource(R.plurals.map_one_turn_every, seconds, seconds), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        state = speed,
                        onValueChange = { speed.value = it },
                        onValueChangeFinished = { set("orbitSpeed", JsonPrimitive(speed.value.roundToInt())) },
                    )
                }

                Text(stringResource(R.string.map_levels_to_show), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = options?.maxDepth == null, onClick = { set("maxDepth", null) }, label = { Text(stringResource(R.string.map_all_levels)) })

                    (1..4).forEach { depth ->
                        FilterChip(selected = options?.maxDepth == depth, onClick = { set("maxDepth", JsonPrimitive(depth)) }, label = { Text("$depth") })
                    }
                }
            }
        }
    }
}

@Composable
private fun Setting(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ToggleRow(title, description, checked, onChange)
}

/** This map drawn in `style`, by Orbit, to pick from. */
@Composable
private fun StyleTile(model: MapViewModel, ui: MapUi, style: MapStyle, selected: Boolean, onPick: () -> Unit) {
    val preview by produceState<ImageBitmap?>(null, ui.revision, style.name, MaterialTheme.colorScheme) {
        val markdown = model.currentMarkdown()
        val bytes = model.picture(
            PictureRequest(markdown = markdown, format = "webp", width = 420, height = 280, scale = 1.0, style = style.name ?: ""),
        ) ?: return@produceState
        value = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }

    val background = if (style.name == null) MaterialTheme.colorScheme.surfaceContainerHighest else style.background

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .width(132.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onPick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 2f)
                .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                .padding(if (selected) 4.dp else 1.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(background),
        ) {
            preview?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(3f / 2f)) }
        }

        Text(stringResource(style.labelResource), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
