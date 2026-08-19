package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riteldevelopment.carriertestoverride.ui.QuickPick
import com.riteldevelopment.carriertestoverride.ui.RegionPreset

/**
 * One-tap regions, so the common case never opens the search dialog.
 *
 * Split into two labelled groups rather than one undifferentiated row. "Somewhere you have been before"
 * and "somewhere people usually go" are different claims, and a user scanning for the region they set
 * last week should not have to work out which chips are theirs. Recents come first because a region
 * already applied on this phone is a better guess than one the catalog merely thinks is popular.
 *
 * Chips carry the carrier and the country code, not the country name: the row has to survive
 * "United Arab Emirates" without wrapping into a wall, and the code is what the user is really setting.
 * Selecting a chip loads the same three fields the picker does, so the fields stay editable afterwards
 * and typing over any of them drops back to Custom exactly as before.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickPickRow(
    quickPicks: List<QuickPick>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (RegionPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (quickPicks.isEmpty()) return
    val recent = quickPicks.filter { it.recent }.map { it.preset }
    val common = quickPicks.filterNot { it.recent }.map { it.preset }

    Column(modifier = modifier.fillMaxWidth()) {
        if (recent.isNotEmpty()) {
            MicroLabel("RECENT")
            Spacer(Modifier.height(6.dp))
            ChipFlow(recent, selectedId, enabled, onSelect)
        }
        if (common.isNotEmpty()) {
            if (recent.isNotEmpty()) Spacer(Modifier.height(10.dp))
            MicroLabel("COMMON")
            Spacer(Modifier.height(6.dp))
            ChipFlow(common, selectedId, enabled, onSelect)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(
    presets: List<RegionPreset>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (RegionPreset) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        presets.forEach { preset ->
            FilterChip(
                selected = preset.id == selectedId,
                onClick = { onSelect(preset) },
                enabled = enabled,
                label = {
                    Text(
                        text = "${preset.carrier} · ${preset.countryIso.uppercase()}",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                shape = FilterChipDefaults.shape,
            )
        }
    }
}
