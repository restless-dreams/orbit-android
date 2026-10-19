package com.hyuchia.orbit.map

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hyuchia.orbit.R
import com.hyuchia.orbit.Words
import com.hyuchia.orbit.page.MapNode
import com.hyuchia.orbit.rememberWords
import com.hyuchia.orbit.ui.EscapeCloses
import com.hyuchia.orbit.ui.WavyProgress
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.util.Currency
import java.util.Date
import java.util.Locale

/** The name of a way of counting's first number: what the goal is after. */
@get:StringRes
private val GoalWay.firstLabel: Int
    get() = when (this) {
        GoalWay.SavingUp -> R.string.goal_target
        GoalWay.PayingOff -> R.string.goal_owed_at_the_start
        GoalWay.Counting -> R.string.goal_how_many
    }

/** The name of a way of counting's second number: how far it's got. */
@get:StringRes
private val GoalWay.secondLabel: Int
    get() = when (this) {
        GoalWay.SavingUp -> R.string.goal_saved_so_far
        GoalWay.PayingOff -> R.string.goal_owed_now
        GoalWay.Counting -> R.string.goal_have_so_far
    }

private fun sameNumber(first: BigDecimal?, second: BigDecimal?): Boolean =
    if (first == null || second == null) first == second else first.compareTo(second) == 0

/** Why a number can't be a goal's, in words; null when it can. */
private fun goalNumberProblem(reading: NumberReading, words: Words): String? = when (reading) {
    is NumberReading.Problem -> reading.message(words)
    is NumberReading.Number -> if (reading.value.signum() < 0) words.string(R.string.goal_number_zero_or_more) else null
    NumberReading.Empty -> null
}

/**
 * A number typed in the phone's language, on the decimal keyboard. It saves
 * a moment after typing stops, when the keyboard's action is pressed, and
 * as it leaves the screen, once it reads as a number; emptied, it saves
 * null. What doesn't read as one is said under it, and isn't saved. A
 * number changed elsewhere, by a step or an undo, shows at once.
 */
@Composable
fun NumberField(
    key: String,
    value: BigDecimal?,
    label: String,
    onCommit: (BigDecimal?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    currency: Currency? = null,
    word: String? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    val words = rememberWords()
    var text by remember(key) { mutableStateOf(value?.let { GoalNumbers.field(it, locale) }.orEmpty()) }
    var committed by remember(key) { mutableStateOf(value) }
    val commit by rememberUpdatedState(onCommit)

    LaunchedEffect(key, value) {
        if (!sameNumber(value, committed)) {
            committed = value
            text = value?.let { GoalNumbers.field(it, locale) }.orEmpty()
        }
    }

    fun save() {
        val reading = GoalNumbers.read(text, locale)

        if (goalNumberProblem(reading, words) != null) {
            return
        }

        val number = (reading as? NumberReading.Number)?.value

        if (!sameNumber(number, committed)) {
            committed = number
            commit(number)
        }
    }

    LaunchedEffect(key, text) {
        delay(700)
        save()
    }

    DisposableEffect(key) {
        onDispose { save() }
    }

    val problem = goalNumberProblem(GoalNumbers.read(text, locale), words)
    val side = currency?.let { GoalNumbers.currencySide(it, locale) }
    val raise = LocalSheetRaise.current
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        prefix = side?.takeIf { it.second }?.let { (symbol) -> { Text(symbol) } },
        suffix = (side?.takeIf { !it.second }?.first ?: word)?.let { unit -> { Text(unit) } },
        supportingText = problem?.let { message -> { Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } },
        isError = problem != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { save() }),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focus ->
                if (focus.isFocused) {
                    raise()
                }
            },
    )
}

/**
 * A goal's numbers in the editor: how it counts (saving up, paying off or
 * counting), the two numbers that way asks for, and its currency or what
 * it counts. The way follows the numbers once there's a target; until
 * then, the one picked stays.
 */
@Composable
fun GoalSection(model: MapViewModel, node: MapNode, under: Rollup) {
    val goal = Goal.of(node) ?: return
    val locale = LocalConfiguration.current.locales[0]
    val words = rememberWords()
    var way by rememberSaveable(node.id) { mutableStateOf(goal.way) }

    LaunchedEffect(node.id, goal.way, goal.target != null) {
        if (goal.target != null) {
            way = goal.way
        }
    }

    // The word counted and the currency chosen are kept while the other way is tried.
    var word by rememberSaveable(node.id) { mutableStateOf(goal.word) }

    LaunchedEffect(goal.word) {
        goal.word?.let { word = it }
    }

    var currencyCode by rememberSaveable(node.id) { mutableStateOf((goal.currency ?: phoneCurrency(locale)).currencyCode) }

    LaunchedEffect(goal.currency) {
        goal.currency?.let { currencyCode = it.currencyCode }
    }

    val currency = Currency.getInstance(currencyCode)
    var choosingCurrency by remember { mutableStateOf(false) }
    val money = way != GoalWay.Counting

    fun set(changes: Map<String, JsonElement>) {
        // Money's numbers go with their currency.
        val unit = if (money && goal.currency == null) mapOf(GOAL_UNIT to JsonPrimitive(currency.currencyCode)) else emptyMap()
        model.setGoal(node.id, unit + changes)
    }

    val (first, second) = goal.fields(way, node.attrs)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            GoalWay.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = way == option,
                    onClick = {
                        if (way != option) {
                            way = option
                            model.setGoal(node.id, goal.switchTo(option, currency, word))
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, GoalWay.entries.size),
                    icon = {},
                ) {
                    Text(stringResource(option.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        NumberField(
            key = "${node.id} ${way.name} first",
            value = first,
            label = stringResource(way.firstLabel),
            currency = currency.takeIf { money },
            word = word.takeIf { !money },
            onCommit = { value -> set(goal.firstChanges(way, value)) },
        )
        NumberField(
            key = "${node.id} ${way.name} second",
            value = second,
            label = stringResource(way.secondLabel),
            placeholder = GoalNumbers.field(goal.start, locale),
            currency = currency.takeIf { money },
            word = word.takeIf { !money },
            onCommit = { value -> set(mapOf(GOAL_CURRENT to (value?.let(::number) ?: JsonNull))) },
        )

        if (money) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.goal_currency), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                val change = stringResource(R.string.goal_currency_change, currency.getDisplayName(locale))
                AssistChip(
                    onClick = { choosingCurrency = true },
                    label = { Text("${currency.currencyCode} · ${currency.getDisplayName(locale)}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                    modifier = Modifier.semantics { contentDescription = change },
                )
            }
        } else {
            val raise = LocalSheetRaise.current
            EditorField(
                key = node.id,
                value = goal.word.orEmpty(),
                label = stringResource(R.string.goal_whats_counted),
                placeholder = stringResource(R.string.goal_whats_counted_example),
                singleLine = true,
                capitalization = KeyboardCapitalization.None,
                modifier = Modifier.onFocusChanged { focus ->
                    if (focus.isFocused) {
                        raise()
                    }
                },
                onCommit = { text ->
                    word = text.trim().ifEmpty { null }
                    set(mapOf(GOAL_UNIT to (text.trim().ifEmpty { null }?.let(::JsonPrimitive) ?: JsonNull)))
                },
            )
        }

        Text(GoalNumbers.editorSummary(goal, node.childCount > 0, under, words, locale), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (choosingCurrency) {
        CurrencyPicker(
            current = currency,
            onPick = { picked ->
                choosingCurrency = false
                currencyCode = picked.currencyCode
                model.setGoal(node.id, mapOf(GOAL_UNIT to JsonPrimitive(picked.currencyCode)))
            },
            onDismiss = { choosingCurrency = false },
        )
    }
}

/** ISO 4217 codes for what isn't money people save or owe: funds, precious metals, units of account and tests. */
private val NOT_MONEY = setOf(
    "BOV", "CHE", "CHW", "CLF", "COU", "MXV", "USN", "USS", "UYI", "UYW",
    "XAG", "XAU", "XBA", "XBB", "XBC", "XBD", "XDR", "XPD", "XPT", "XSU", "XTS", "XUA", "XXX",
)

/** The currencies to choose from: the phone's first, then every one in use today, by name. */
private fun currencyChoices(locale: Locale): List<Currency> {
    val now = Date()

    val inUse = Currency.getAvailableCurrencies().filter { currency ->
        currency.currencyCode !in NOT_MONEY && currency.defaultFractionDigits >= 0 &&
            runCatching { android.icu.util.Currency.isAvailable(currency.currencyCode, now, null) }.getOrDefault(true)
    }

    val phone = phoneCurrency(locale)
    return listOf(phone) + inUse.filter { it != phone }.sortedBy { it.getDisplayName(locale).lowercase(locale) }
}

/** A list of currencies to choose one from, found by name or code. */
@Composable
private fun CurrencyPicker(current: Currency, onPick: (Currency) -> Unit, onDismiss: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val choices = remember(locale) { currencyChoices(locale) }
    var query by remember { mutableStateOf("") }
    val shown = choices.filter { query.isBlank() || it.currencyCode.contains(query.trim(), ignoreCase = true) || it.getDisplayName(locale).contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            EscapeCloses(onDismiss)
            Text(stringResource(R.string.goal_currency))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.goal_find_a_currency)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown, key = { it.currencyCode }) { currency ->
                        val symbol = currency.getSymbol(locale).takeIf { it != currency.currencyCode }

                        ListItem(
                            onClick = { onPick(currency) },
                            leadingContent = {
                                Text(currency.currencyCode, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(40.dp))
                            },
                            trailingContent = when {
                                currency == current -> ({ Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.goal_current_currency)) })
                                symbol != null -> ({ Text(symbol, style = MaterialTheme.typography.titleMedium) })
                                else -> null
                            },
                        ) {
                            Text(currency.getDisplayName(locale), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * An amount to add to a goal, take off or pay, with its currency or what it
 * counts beside it, and what the goal comes to once it's in.
 */
@Composable
fun GoalAmountDialog(goal: Goal, change: GoalChange, onDone: (BigDecimal) -> Unit, onDismiss: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val words = rememberWords()
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val reading = GoalNumbers.read(text, locale)
    val amount = (reading as? NumberReading.Number)?.value

    val problem = when {
        reading is NumberReading.Problem -> reading.message(words)
        amount != null -> goal.refuse(change, amount, words, locale)
        else -> null
    }

    val ready = amount != null && problem == null
    val after = amount?.takeIf { ready }?.let { goal.plus(goal.signed(change, it)) }

    fun done() {
        if (amount != null && ready) {
            onDone(amount)
        }
    }

    val side = goal.currency?.let { GoalNumbers.currencySide(it, locale) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            EscapeCloses(onDismiss)
            Text(stringResource(change.title))
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.goal_amount)) },
                prefix = side?.takeIf { it.second }?.let { (symbol) -> { Text(symbol) } },
                suffix = (side?.takeIf { !it.second }?.first ?: goal.word)?.let { unit -> { Text(unit) } },
                supportingText = {
                    val message = problem ?: after?.let { GoalNumbers.summary(it, words, locale, toGo = false) }

                    if (message != null) {
                        Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                },
                isError = problem != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { done() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = ::done, enabled = ready) { Text(stringResource(change.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * The quick ways to change a goal at the top of its long-press menu: −1 and
 * +1 for a count, with the menu staying open so steps follow each other;
 * amounts for money; and the goal's own numbers, in its editor. A goal
 * without a target that follows the goals and to-dos `under` it says how far
 * along they are.
 */
@Composable
fun GoalMenuItems(goal: Goal, under: Rollup, onStep: (Int) -> Unit, onChange: (GoalChange) -> Unit, onEdit: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val words = rememberWords()
    val following = goal.target == null && under.total > 0
    Text(
        if (following) GoalNumbers.progress(under, words, locale) else GoalNumbers.summary(goal, words, locale),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )

    if (goal.target == null) {
        MenuItem(stringResource(if (following) R.string.goal_menu_goal else R.string.goal_menu_set_the_target), Icons.Outlined.TrackChanges, onEdit)
        return
    }

    when (goal.way) {
        GoalWay.Counting -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            val takeOneOff = stringResource(R.string.goal_take_one_off)
            val addOne = stringResource(R.string.goal_add_one)
            FilledTonalButton(
                onClick = { onStep(-1) },
                enabled = goal.canStepDown,
                modifier = Modifier.semantics { contentDescription = takeOneOff },
            ) { Text("−1") }
            Text(
                GoalNumbers.amount(goal.current, null, locale),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(
                onClick = { onStep(1) },
                modifier = Modifier.semantics { contentDescription = addOne },
            ) { Text("+1") }
        }
        GoalWay.SavingUp -> {
            MenuItem(stringResource(R.string.goal_menu_add_an_amount), Icons.Outlined.Add) { onChange(GoalChange.Add) }
            MenuItem(stringResource(R.string.goal_menu_take_off_an_amount), Icons.Outlined.Remove) { onChange(GoalChange.TakeOff) }
        }
        GoalWay.PayingOff -> MenuItem(stringResource(R.string.goal_menu_paid_an_amount), Icons.Outlined.Payments) { onChange(GoalChange.Paid) }
    }

    MenuItem(stringResource(R.string.goal_menu_goal), Icons.Outlined.TrackChanges, onEdit)
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

/**
 * A goal in its idea's details: how far along it is, its numbers in words,
 * and a way to move it on: −1 and +1 for a count, an amount for money.
 */
@Composable
fun GoalDetails(model: MapViewModel, node: MapNode, goal: Goal) {
    val locale = LocalConfiguration.current.locales[0]
    val words = rememberWords()
    var changing by rememberSaveable(node.id) { mutableStateOf<GoalChange?>(null) }
    val share by animateFloatAsState(goal.share.toFloat(), label = "goal")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WavyProgress(progress = { share }, modifier = Modifier.fillMaxWidth())
        Text(GoalNumbers.summary(goal, words, locale), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })

        when {
            goal.target == null -> FilledTonalButton(onClick = { model.show(Panel.Editor(node.id)) }) { Text(stringResource(R.string.goal_set_a_target)) }
            goal.way == GoalWay.Counting -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalIconButton(onClick = { model.addToGoal(node.id, BigDecimal.ONE.negate()) }, enabled = goal.canStepDown) {
                    Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.goal_take_one_off))
                }

                FilledTonalIconButton(onClick = { model.addToGoal(node.id, BigDecimal.ONE) }) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.goal_add_one))
                }
            }
            goal.way == GoalWay.SavingUp -> FilledTonalButton(onClick = { changing = GoalChange.Add }) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("  " + stringResource(R.string.goal_details_add))
            }
            else -> FilledTonalButton(onClick = { changing = GoalChange.Paid }) {
                Icon(Icons.Outlined.Payments, contentDescription = null)
                Text("  " + stringResource(R.string.goal_details_add_a_payment))
            }
        }
    }

    changing?.let { change ->
        GoalAmountDialog(
            goal = goal,
            change = change,
            onDone = { amount ->
                changing = null
                model.addToGoal(node.id, goal.signed(change, amount))
            },
            onDismiss = { changing = null },
        )
    }
}

/**
 * How far along the goals and to-dos under an idea are, in its details: for
 * an idea whose ring follows them (a Progress idea, a goal without a target,
 * or an idea with a ring by itself).
 */
@Composable
fun ProgressDetails(under: Rollup) {
    val locale = LocalConfiguration.current.locales[0]
    val words = rememberWords()
    val share by animateFloatAsState(under.share.toFloat(), label = "progress")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (under.total > 0) {
            WavyProgress(progress = { share }, modifier = Modifier.fillMaxWidth())
            Text(GoalNumbers.progress(under, words, locale), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        } else {
            Text(stringResource(R.string.goal_no_to_dos_or_goals_under_it), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
