package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
 * One flow of chips rather than two labelled groups. The headers cost four lines to state a
 * distinction the order already makes — history sits at the front, everything after it is a suggestion
 * — and on a screen whose main fault was density that trade is not worth taking. Recents still come
 * first, and still win the duplicates: a region already applied on this phone is a better guess than
 * one the catalog merely thinks is popular.
 *
 * The flag does the identifying and the carrier name disambiguates within a country, which is why the
 * chip drops the ISO letters it used to repeat: "GB" next to the flag of the United Kingdom is the same
 * fact twice, and the row has to survive wrapping on a narrow screen.
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
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        quickPicks.forEach { pick ->
            QuickPickChip(
                preset = pick.preset,
                selected = pick.preset.id == selectedId,
                enabled = enabled,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun QuickPickChip(
    preset: RegionPreset,
    selected: Boolean,
    enabled: Boolean,
    onSelect: (RegionPreset) -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = { onSelect(preset) },
        enabled = enabled,
        // Shorter and tighter than the default. A chip row is a scanning surface, not a set of
        // buttons to read, so it should cost as little vertical space as it can while staying tappable.
        modifier = Modifier.height(32.dp),
        label = {
            Text(
                text = "${preset.flag} ${preset.carrier}",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        shape = FilterChipDefaults.shape,
    )
}
