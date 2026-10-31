package com.hyuchia.orbit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.data.Uncleaned
import com.hyuchia.orbit.rememberWords

/**
 * Whether a way out shares as they are the map's files that can't go clean
 * ([Uncleaned]). It asks at the phone, as the way out starts, the first time
 * it would carry any, and the answer holds while the way out lasts. Until
 * the person says otherwise, those files stay on this device, and a browser
 * is never asked.
 */
@Stable
class AsTheyAre internal constructor() {
    /** The files asked about so far. */
    private var asked by mutableStateOf(emptySet<String>())

    /** The files the person chose to share as they are. */
    var chosen by mutableStateOf(emptySet<String>())
        private set

    /** The question waiting for an answer, and what starts once it has one. */
    internal var question by mutableStateOf<Question?>(null)
        private set

    /**
     * Starts a way out that would carry `files`, with those of them that go
     * as they are: asks first about any not asked about yet. Dismissing the
     * question starts nothing.
     */
    fun then(files: Uncleaned, start: (asTheyAre: Set<String>) -> Unit) {
        val new = files.only(files.names - asked)

        if (new.isEmpty) {
            start(chosen intersect files.names)
        } else {
            question = Question(new) { start(chosen intersect files.names) }
        }
    }

    /** Drops a question no one answered, as the way out it was for went away. */
    fun forget() {
        question = null
    }

    internal fun answer(question: Question, share: Boolean) {
        this.question = null
        asked = asked + question.files.names

        if (share) {
            chosen = chosen + question.files.names
        }

        question.start()
    }

    internal class Question(val files: Uncleaned, val start: () -> Unit)
}

/** An [AsTheyAre] for a way out, whose question shows here. */
@Composable
fun rememberAsTheyAre(): AsTheyAre {
    val choice = remember { AsTheyAre() }

    choice.question?.let { question ->
        AsTheyAreDialog(question.files, onChoice = { share -> choice.answer(question, share) }, onDismiss = choice::forget)
    }

    return choice
}

/**
 * Asks whether files Orbit couldn't clean go as they are, with what they'd
 * say: where and when they were taken or recorded, and on what. It names
 * them, by kind and by file, in the viewer's words. Leaving them out is
 * the choice it starts on.
 */
@Composable
fun AsTheyAreDialog(files: Uncleaned, onChoice: (asTheyAre: Boolean) -> Unit, onDismiss: () -> Unit) {
    val words = rememberWords()
    val (list, count) = MediaWords.uncleanedList(files, words) ?: return

    val text = when (MediaWords.how(files)) {
        MediaWords.Made.Taken -> R.plurals.sharing_as_they_are_taken
        MediaWords.Made.Recorded -> R.plurals.sharing_as_they_are_recorded
        MediaWords.Made.TakenOrRecorded -> R.plurals.sharing_as_they_are_taken_or_recorded
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            EscapeCloses(onDismiss)
            Text(words.plural(R.plurals.sharing_as_they_are_title, count, list))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(words.plural(text, count))
                Text(files.names.sorted().joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        // Enter leaves them out: what they say goes only when someone chooses it.
        confirmButton = { TextButton(onClick = { onChoice(false) }, modifier = Modifier.focusRequester(rememberDefaultButton())) { Text(words.plural(R.plurals.sharing_leave_them_out, count)) } },
        dismissButton = { TextButton(onClick = { onChoice(true) }) { Text(words.plural(R.plurals.sharing_share_them_as_they_are, count)) } },
    )
}

/**
 * The lines under a way out for the files of `files` that can't go clean:
 * those of `chosen` going as they are, with what they say, and the rest
 * staying on this device, each with its icon (`device`, this device's).
 */
fun uncleanedLines(files: Uncleaned, chosen: Set<String>, words: Words, device: ImageVector): List<UncleanedLine> = listOfNotNull(
    MediaWords.uncleaned(files.only(chosen), words, asTheyAre = true)?.let { UncleanedLine(it, goes = true, Icons.Outlined.LocationOn) },
    MediaWords.uncleaned(files.only(files.names - chosen), words, asTheyAre = false)?.let { UncleanedLine(it, goes = false, device) },
)

/** A line that names files that can't go clean: what it says, whether they go, and its icon. */
class UncleanedLine(val text: String, val goes: Boolean, val icon: ImageVector)
