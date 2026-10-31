package com.hyuchia.orbit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * A small line that says what goes with a map, in the primary color when
 * something does (`goes`), and quieter for what doesn't or how it goes,
 * with an icon that sits on its first line however many the words take:
 * the same look wherever a map is shared, sent or shown.
 */
@Composable
fun WhatGoes(text: String, icon: ImageVector, goes: Boolean, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.labelLarge) {
    val color = if (goes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top, modifier = modifier) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(top = 2.dp).size(16.dp))
        Text(text, style = style, color = color)
    }
}
