package com.hyuchia.orbit.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.BuildConfig
import com.hyuchia.orbit.R
import com.hyuchia.orbit.container
import com.hyuchia.orbit.data.MapText
import com.hyuchia.orbit.data.openAsset
import com.hyuchia.orbit.page.OrbitBuild
import com.hyuchia.orbit.ui.openLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the app's source is published, as the GPL asks. */
private const val SOURCE_CODE = "https://github.com/restless-dreams/orbit-android"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onOpenMap: (String) -> Unit, onPrivacy: () -> Unit, onLicenses: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val orbit by produceState<OrbitBuild?>(null) {
        value = withContext(Dispatchers.IO) { OrbitBuild.bundled(context) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back)) }
                },
            )
        },
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
                // On a tablet, lines as long as the screen is wide are hard to read: About keeps to a column.
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp),
        ) {
            AppIcon(Modifier.padding(top = 16.dp).size(112.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )

            orbit?.let { build ->
                // A build of Orbit not released yet has no version of its own, only its commit.
                val version = listOfNotNull(build.version.takeIf { it != "0.0.0" }, build.commit.takeIf { it.isNotEmpty() }).joinToString(" ")
                Text(
                    if (version.isEmpty()) {
                        stringResource(R.string.settings_maps_drawn_by_library)
                    } else {
                        stringResource(R.string.settings_maps_drawn_by_library_version, version)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                // Only in the copies no store updates: see UPDATES_URL in app/build.gradle.kts.
                if (context.container.updates.available) {
                    UpdatesItem()
                }

                ListItem(
                    onClick = { openLink(context, BuildConfig.WEBSITE_URL) },
                    supportingContent = { Text(BuildConfig.WEBSITE_URL.removePrefix("https://")) },
                    leadingContent = { Icon(Icons.Outlined.Language, contentDescription = null) },
                ) {
                    Text(stringResource(R.string.settings_orbit_on_the_web))
                }

                // Only in builds for stores that allow donation links: see SUPPORT_URL in app/build.gradle.kts.
                if (BuildConfig.SUPPORT_URL.isNotEmpty()) {
                    ListItem(
                        onClick = { openLink(context, BuildConfig.SUPPORT_URL) },
                        supportingContent = { Text(stringResource(R.string.settings_support_orbit_description)) },
                        leadingContent = { Icon(Icons.Outlined.VolunteerActivism, contentDescription = null) },
                    ) {
                        Text(stringResource(R.string.settings_support_orbit))
                    }
                }

                ListItem(
                    onClick = { openLink(context, "https://orbit.hyuchia.com/docs/format") },
                    supportingContent = { Text(stringResource(R.string.settings_how_maps_are_written_description)) },
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) },
                ) {
                    Text(stringResource(R.string.settings_how_maps_are_written))
                }

                ListItem(
                    onClick = onPrivacy,
                    supportingContent = { Text(stringResource(R.string.settings_privacy_description)) },
                    leadingContent = { Icon(Icons.Outlined.PrivacyTip, contentDescription = null) },
                ) {
                    Text(stringResource(R.string.settings_privacy))
                }

                ListItem(
                    onClick = { openLink(context, SOURCE_CODE) },
                    supportingContent = { Text(stringResource(R.string.settings_source_code_description)) },
                    leadingContent = { Icon(Icons.Outlined.Code, contentDescription = null) },
                ) {
                    Text(stringResource(R.string.settings_source_code))
                }

                ListItem(
                    onClick = onLicenses,
                    supportingContent = { Text(stringResource(R.string.settings_open_source_licenses_description)) },
                    leadingContent = { Icon(Icons.Outlined.Gavel, contentDescription = null) },
                ) {
                    Text(stringResource(R.string.settings_open_source_licenses))
                }

                ListItem(
                    onClick = {
                        scope.launch {
                            val container = context.container
                            // The welcome map in the app's language, found by its title, so it opens again rather than being added twice.
                            val markdown = withContext(Dispatchers.IO) { context.openAsset("welcome.md").bufferedReader().use { it.readText() } }
                            val title = MapText.outline(markdown, container.words).title
                            val existing = container.maps.maps.value?.firstOrNull { it.title == title }
                            onOpenMap(existing?.id ?: container.maps.add(markdown, pinned = true))
                        }
                    },
                    supportingContent = { Text(stringResource(R.string.settings_welcome_map_description)) },
                    leadingContent = { Icon(Icons.Outlined.WavingHand, contentDescription = null) },
                ) {
                    Text(stringResource(R.string.settings_welcome_map))
                }
            }
        }
    }
}

/** The app's icon as a launcher draws it: its two layers, cropped to the middle 72 of their 108. */
@Composable
private fun AppIcon(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(percent = 22)), contentAlignment = Alignment.Center) {
        val layer = Modifier.requiredSize(maxWidth * 1.5f)
        Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = layer)
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = layer)
    }
}
