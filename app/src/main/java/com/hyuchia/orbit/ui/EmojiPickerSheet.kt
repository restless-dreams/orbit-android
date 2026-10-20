package com.hyuchia.orbit.ui

import android.content.Context
import android.graphics.Paint
import android.view.ContextThemeWrapper
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.emoji2.emojipicker.RecentEmojiProvider
import com.hyuchia.orbit.R
import com.hyuchia.orbit.theme.isDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A phone on its side: with the keyboard up, the search keeps only what it needs. */
private val ShortScreen = 480.dp

/**
 * Android's emoji picker, with recent emoji and every category, for an idea's icon, under a
 * search that finds emoji by their names and keywords in the app's language and in English.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EmojiPickerSheet(title: String, hasIcon: Boolean, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val recent = remember { RecentEmoji(context) }
    val chosenTones = remember { chosenSkinTones(context) }
    val index by produceState<EmojiIndex?>(null) { value = EmojiNames.index(context) }
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()

    // Every emoji's words are searched again for each letter typed: off the main thread, and only the latest letter's matches arrive.
    val found by produceState<EmojiResults?>(null, index, query) {
        val names = index
        val typed = query
        value = if (names == null || typed.isBlank()) null else EmojiResults(typed, withContext(Dispatchers.Default) { names.search(typed) })
    }

    // The sheet's window has Android's dark dialog theme, whose text the picker would write its headings in on a light sheet.
    val pickerTheme = if (MaterialTheme.colorScheme.isDark) android.R.style.Theme_DeviceDefault_NoActionBar else android.R.style.Theme_DeviceDefault_Light_NoActionBar

    // Above a keyboard on a phone on its side there's room for the field and a row of emoji, without the title.
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    val typingOnShortScreen = windowHeight < ShortScreen && WindowInsets.isImeVisible

    // The picker keeps what's picked in it as recent emoji itself; the search's picks join them here.
    fun pickFound(emoji: String) {
        recent.recordSelection(emoji)
        onPick(emoji)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        dragHandle = if (typingOnShortScreen) null else ({ BottomSheetDefaults.DragHandle() }),
    ) {
        EscapeCloses(onDismiss)

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding(),
        ) {
            if (!typingOnShortScreen) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 12.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))

                    if (hasIcon) {
                        TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.ui_no_icon)) }
                    }
                }
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.ui_search_emoji)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.ui_clear_the_search)) }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { found?.matches?.firstOrNull()?.let { pickFound(shownEmoji(it, chosenTones)) } }),
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            // As tall as the picker has always been, and shorter when the keyboard or a phone on its side leaves less room.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 420.dp),
            ) {
                // The picker stays, hidden, while there's a search, so it's as it was when the search is cleared.
                AndroidView(
                    factory = { viewContext ->
                        EmojiPickerView(ContextThemeWrapper(viewContext, pickerTheme)).apply {
                            emojiGridColumns = 9
                            setRecentEmojiProvider(recent)
                            setOnEmojiPickedListener { item -> onPick(item.emoji) }
                        }
                    },
                    update = { picker -> picker.isVisible = !searching },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                )

                val results = found

                if (searching && results != null) {
                    EmojiMatches(results.matches, results.query.trim(), chosenTones, onPick = ::pickFound)
                }
            }
        }
    }
}

/** What a search found, and what was typed for it: the field can be a letter ahead while the next search runs. */
private class EmojiResults(val query: String, val matches: List<EmojiMatch>)

/** The search's matches in a grid, the first one marked, since Enter picks it; a long press offers an emoji's skin tones. */
@Composable
private fun EmojiMatches(matches: List<EmojiMatch>, query: String, chosenTones: Map<String, String>, onPick: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme

    if (matches.isEmpty()) {
        Text(
            stringResource(R.string.ui_no_emoji_found, query),
            color = colors.onSurfaceVariant,
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surfaceContainerLow)
                .padding(24.dp),
        )

        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(48.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceContainerLow),
    ) {
        itemsIndexed(matches, key = { _, match -> match.emoji }) { position, match ->
            FoundEmoji(match, shownEmoji(match, chosenTones), marked = position == 0, onPick = onPick)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun FoundEmoji(match: EmojiMatch, shown: String, marked: Boolean, onPick: (String) -> Unit) {
    var choosingTone by remember { mutableStateOf(false) }
    val chooseTone = stringResource(R.string.ui_choose_a_skin_tone)
    val colors = MaterialTheme.colorScheme

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (marked) colors.secondaryContainer else Color.Transparent)
            .combinedClickable(
                onLongClickLabel = if (match.variants.isEmpty()) null else chooseTone,
                onLongClick = if (match.variants.isEmpty()) null else ({ choosingTone = true }),
                onClick = { onPick(shown) },
            )
            .semantics { contentDescription = if (shown == match.emoji) match.name else match.nameOf(shown) },
    ) {
        EmojiText(shown)

        DropdownMenu(expanded = choosingTone, onDismissRequest = { choosingTone = false }) {
            FlowRow(maxItemsInEachRow = 6, modifier = Modifier.padding(horizontal = 8.dp)) {
                (listOf(match.emoji) + match.variants).forEach { variant ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (variant == shown) colors.secondaryContainer else Color.Transparent)
                            .combinedClickable(
                                onClick = {
                                    choosingTone = false
                                    onPick(variant)
                                },
                            )
                            .semantics { contentDescription = if (variant == match.emoji) match.name else match.nameOf(variant) },
                    ) {
                        EmojiText(variant)
                    }
                }
            }
        }
    }
}

/** An emoji the size the picker draws them, whatever the font size: it's a picture to tap, not text to read. */
@Composable
private fun EmojiText(emoji: String) {
    val size = with(LocalDensity.current) { 28.dp.toSp() }

    // Screen readers say the emoji's name, from the button around it, rather than the character's.
    Text(emoji, fontSize = size, lineHeight = size, modifier = Modifier.clearAndSetSemantics {})
}

/** The emoji to show for a match: the skin tone pasted, or the one last chosen in the picker, or the emoji itself. */
private fun shownEmoji(match: EmojiMatch, chosenTones: Map<String, String>): String =
    match.pasted ?: chosenTones[match.emoji]?.takeIf { it in match.variants } ?: match.emoji

/** Where Android's picker keeps its recent emoji and the skin tone last chosen for each emoji. */
private const val PICKER_PREFERENCES = "androidx.emoji2.emojipicker.preferences"
private const val RECENT_KEY = "pref_key_recent_emoji"
private const val CHOSEN_TONES_KEY = "pref_key_sticky_variant"

/**
 * The picker's recent emoji, kept where the picker keeps them ("😀,🚀", the latest first), so the
 * search's picks join the ones made in the picker.
 */
private class RecentEmoji(context: Context) : RecentEmojiProvider {
    private val preferences = context.getSharedPreferences(PICKER_PREFERENCES, Context.MODE_PRIVATE)
    private val emoji = preferences.getString(RECENT_KEY, null).orEmpty().split(',').filter { it.isNotEmpty() }.toMutableList()

    override suspend fun getRecentEmojiList(): List<String> = emoji.toList()

    override fun recordSelection(emoji: String) {
        this.emoji.remove(emoji)
        this.emoji.add(0, emoji)
        preferences.edit { putString(RECENT_KEY, this@RecentEmoji.emoji.joinToString(",")) }
    }
}

/** The skin tone last chosen in the picker for each emoji ("👍=👍🏽|…"), to show the search's matches the same way. */
private fun chosenSkinTones(context: Context): Map<String, String> = context.getSharedPreferences(PICKER_PREFERENCES, Context.MODE_PRIVATE)
    .getString(CHOSEN_TONES_KEY, null).orEmpty()
    .split('|')
    .mapNotNull { entry -> entry.split('=').takeIf { it.size == 2 }?.let { (emoji, tone) -> emoji to tone } }
    .toMap()

/** The search's emoji and words, read from the app's assets once for each language the app is in. */
private object EmojiNames {
    private val lock = Mutex()
    private val indexes = mutableMapOf<String, EmojiIndex>()

    suspend fun index(context: Context): EmojiIndex = withContext(Dispatchers.Default) {
        val assets = context.assets
        val languages = assets.list("emoji").orEmpty().map { it.substringBefore('.') }.filter { it != "emoji" }.toSet()
        val locales = context.resources.configuration.locales
        val language = (0 until locales.size()).map { locales[it].language }.firstOrNull { it in languages } ?: "en"

        lock.withLock {
            indexes.getOrPut(language) {
                fun read(name: String) = assets.open("emoji/$name.txt").bufferedReader().use { it.readText() }
                val paint = Paint()
                EmojiIndex.read(read("emoji"), read(language), if (language == "en") null else read("en"), drawable = paint::hasGlyph)
            }
        }
    }
}
