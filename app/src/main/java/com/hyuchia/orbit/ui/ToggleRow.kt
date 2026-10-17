package com.hyuchia.orbit.ui

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/** A setting that is on or off: the whole row toggles it, and its switch shows which. */
@Composable
fun ToggleRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    ListRow(
        headline = { Text(title) },
        supporting = { Text(description) },
        trailing = { Switch(checked = checked, onCheckedChange = null) },
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}
