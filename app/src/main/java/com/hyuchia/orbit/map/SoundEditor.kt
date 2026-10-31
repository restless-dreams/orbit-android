package com.hyuchia.orbit.map

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.data.isVoiceMemo
import com.hyuchia.orbit.media.SoundCard
import com.hyuchia.orbit.media.soundOf
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.page.Media
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.text.NumberFormat

/**
 * The editor's part for an idea's sound: a voice memo to record, a sound
 * file from the phone or a web address, and once it has one, the sound to
 * play, its caption, and ways to record again, replace or remove it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SoundSection(model: MapViewModel, ui: MapUi, node: MapNode, opened: Int, set: (String, JsonElement?) -> Unit) {
    val context = LocalContext.current
    val sound by model.sound.collectAsState()
    val untitled = stringResource(R.string.common_untitled)
    val item = soundOf(node, untitled)
    val audio = node.audio
    val fromPhone = audio != null && model.mediaModel(audio.src) is File
    val copying = ui.soundCopy?.takeIf { it.nodeId == node.id }

    val pickSound = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            model.addSound(node.id, uri, context.contentResolver)
        }
    }

    val chooseSound = { pickSound.launch(arrayOf("audio/*")) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (copying != null) {
            SoundCopying(copying, onCancel = model::cancelSound)
        } else if (item != null) {
            val length by produceState<Long?>(null, item.media.src) { value = model.soundLength(item.media.src) }
            SoundCard(item, sound, model.sounds, model::mediaModel, length)
        }

        if (audio == null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { model.recordMemo(node.id) }) {
                    Icon(Icons.Rounded.Mic, contentDescription = null, Modifier.size(18.dp))
                    Text("  ${stringResource(R.string.audio_record_a_voice_memo)}")
                }

                OutlinedButton(onClick = chooseSound) {
                    Icon(Icons.Outlined.AudioFile, contentDescription = null, Modifier.size(18.dp))
                    Text("  ${stringResource(R.string.audio_choose_a_sound)}")
                }
            }

            SoundProblemText(ui, node)
        }

        // The same field before and after an address is first set, so typing one goes on uninterrupted.
        key("address") {
            if (!fromPhone) {
                EditorField(
                    key = "$opened",
                    value = audio?.src.orEmpty(),
                    label = stringResource(if (audio == null) R.string.audio_or_a_sound_address else R.string.audio_sound_address),
                    placeholder = "https://…/sound.mp3",
                    singleLine = true,
                    keyboardType = KeyboardType.Uri,
                    onCommit = { url -> set("audio", url.trim().ifEmpty { null }?.let { sound(Media(it, caption = audio?.caption)) }) },
                )
            }
        }

        if (audio != null) {
            EditorField(
                key = "$opened ${audio.src}",
                value = audio.caption.orEmpty(),
                label = stringResource(R.string.map_caption),
                onCommit = { caption -> set("audio", mediaChange("caption", caption.ifBlank { null })) },
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                // A memo is recorded again; another sound gives way to one.
                FilledTonalButton(onClick = { model.recordMemo(node.id) }) {
                    Icon(Icons.Rounded.Mic, contentDescription = null, Modifier.size(18.dp))
                    Text("  ${stringResource(if (isVoiceMemo(audio.src)) R.string.audio_record_again else R.string.audio_record)}")
                }

                OutlinedButton(onClick = chooseSound) { Text(stringResource(R.string.map_replace)) }

                TextButton(onClick = {
                    if (sound?.item?.nodeId == node.id) {
                        model.stopSound()
                    }

                    set("audio", null)
                }) { Text(stringResource(R.string.common_remove)) }
            }

            SoundProblemText(ui, node)
        }
    }
}

/** A sound as the page takes it: its address and caption. */
private fun sound(value: Media): JsonElement = buildJsonObject {
    put("src", value.src)
    value.caption?.let { put("caption", it) }
}

/** How far along a sound from the phone is as it's copied in, with a way to stop it. */
@Composable
private fun SoundCopying(copying: SoundCopy, onCancel: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val locale = LocalConfiguration.current.locales[0]
                val copied = copying.progress

                Text(
                    if (copied != null) {
                        stringResource(R.string.audio_copying_the_sound_percent, NumberFormat.getPercentInstance(locale).format((copied * 100).toInt() / 100.0))
                    } else {
                        stringResource(R.string.audio_copying_the_sound)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
            }

            val progress = copying.progress

            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(end = 12.dp))
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(end = 12.dp))
            }
        }
    }
}

/** Why the last sound chosen for this idea didn't join it. */
@Composable
private fun SoundProblemText(ui: MapUi, node: MapNode) {
    val problem = ui.mediaProblem?.takeIf { it.nodeId == node.id && it.sound } ?: return

    Text(
        problem.message.text(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
