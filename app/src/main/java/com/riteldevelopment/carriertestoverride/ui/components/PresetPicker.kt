package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.riteldevelopment.carriertestoverride.R
import com.riteldevelopment.carriertestoverride.ui.RegionPreset
import com.riteldevelopment.carriertestoverride.ui.RegionPresets
import com.riteldevelopment.carriertestoverride.ui.theme.TabularFigures
import java.util.Locale

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
    val locale = LocalConfiguration.current.locales[0]

    Surface(
        onClick = { picking = true },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset?.let { "${it.flag} ${it.displayCountry(locale)} · ${it.carrier}" }
                        ?: stringResource(R.string.preset_custom),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = preset?.let {
                        "${it.mccMnc} · ${it.countryIso.uppercase(Locale.ROOT)}"
                    } ?: stringResource(R.string.preset_edited_by_hand),
                    style = MaterialTheme.typography.bodySmall.merge(TabularFigures),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.preset_choose_region),
                tint = scheme.onSurfaceVariant,
            )
        }
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
    val locale = LocalConfiguration.current.locales[0]
    val results = remember(query, locale) { RegionPresets.search(query, locale) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
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
                    label = { Text(stringResource(R.string.preset_search_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
                Spacer(Modifier.height(8.dp))

                if (results.isEmpty()) {
                    Text(
                        text = stringResource(R.string.preset_no_match),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Capped rather than weighted: the Column wraps its content inside a wrap-content dialog
                // window, so there is no remaining space for a weight to claim.
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(results, key = { it.id }) { preset ->
                        PresetRow(
                            preset = preset,
                            selected = preset.id == selectedId,
                            onClick = { onPick(preset) },
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
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
    ListItem(
        selected = selected,
        onClick = onClick,
        leadingContent = {
            Text(
                text = preset.flag,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        supportingContent = {
            Text(
                text = preset.displayCountry(LocalConfiguration.current.locales[0]),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = preset.mccMnc,
                    style = MaterialTheme.typography.bodyMedium.merge(TabularFigures),
                )
                Text(
                    text = preset.countryIso.uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
    ) {
        Text(
            text = preset.carrier,
            style = MaterialTheme.typography.bodyLargeEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
