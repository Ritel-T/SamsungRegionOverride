package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.riteldevelopment.carriertestoverride.ui.RegionPreset
import com.riteldevelopment.carriertestoverride.ui.RegionPresets
import com.riteldevelopment.carriertestoverride.ui.theme.TabularFigures

/**
 * The catalog entry currently loaded into the three target fields.
 *
 * A row that opens a searchable list, not a set of segmented buttons: the catalog runs to a couple of
 * hundred carriers, and any control that lays them all out is either a wall or a scroll. It shows what
 * is loaded rather than what is "selected", because the fields stay editable afterwards — typing over
 * any of them drops the preset and the row says so.
 */
@Composable
fun PresetField(
    preset: RegionPreset?,
    enabled: Boolean,
    onSelect: (RegionPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { picking = true }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset?.let { "${it.flag} ${it.label}" } ?: "Custom",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preset?.let { "${it.mccMnc} · ${it.countryIso.uppercase()}" }
                    ?: "Edited by hand",
                style = MaterialTheme.typography.bodySmall.merge(TabularFigures),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = "Choose a region",
            tint = scheme.onSurfaceVariant,
        )
    }

    if (picking) {
        PresetPickerDialog(
            selectedId = preset?.id,
            onDismiss = { picking = false },
            onPick = {
                picking = false
                onSelect(it)
            },
        )
    }
}

/**
 * Search over the catalog.
 *
 * A plain [Dialog] rather than an AlertDialog: the list needs a tall scrolling body, and AlertDialog's
 * text slot fights that. The query matches country, carrier, ISO and MCC/MNC at once, so "gb", "23430"
 * and "united kingdom" all find the same row and the user never has to know which field they searched.
 *
 * Sizing is deliberately absolute rather than fractional. The dialog window wraps its content, so a
 * `fillMaxWidth(fraction)` here resolves against an unbounded constraint and silently does nothing —
 * which leaves the [Surface] with no bounds to paint, and the picker renders as floating text over the
 * screen behind it. Filling the (bounded) max width and capping the list in dp is what AlertDialog does,
 * and it is the reason AlertDialog renders correctly.
 */
@Composable
private fun PresetPickerDialog(
    selectedId: String?,
    onDismiss: () -> Unit,
    onPick: (RegionPreset) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { RegionPresets.search(query) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    singleLine = true,
                    label = { Text("Country, carrier or code") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
                Spacer(Modifier.height(8.dp))

                if (results.isEmpty()) {
                    Text(
                        text = "No match. Close this and type the values in directly.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Capped rather than weighted: the Column wraps its content inside a wrap-content dialog
                // window, so there is no remaining space for a weight to claim.
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(results, key = { it.id }) { preset ->
                        PresetRow(
                            preset = preset,
                            selected = preset.id == selectedId,
                            onClick = { onPick(preset) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun PresetRow(
    preset: RegionPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Its own column so the names beside it start on a common left edge; flags differ in width
        // enough that inlining them would leave the carrier names visibly ragged.
        Text(
            text = preset.flag,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.carrier,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = preset.country,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        // Right-aligned and tabular so the codes form a column the eye can scan down.
        Box(contentAlignment = Alignment.CenterEnd) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = preset.mccMnc,
                    style = MaterialTheme.typography.bodyMedium.merge(TabularFigures),
                    color = if (selected) scheme.onSecondaryContainer else scheme.onSurface,
                )
                Text(
                    text = preset.countryIso.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                )
            }
        }
    }
}
