package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickPickChip(
    preset: RegionPreset,
    selected: Boolean,
    enabled: Boolean,
    onSelect: (RegionPreset) -> Unit,
) {
    // An OutlinedToggleButton rather than a FilterChip: it carries selection in its *shape*, rounding hard when
    // unpicked and squaring off when picked, and it squashes under the finger on the way there. A chip
    // could only change colour, and this row is a dozen near-identical items where a second, structural
    // difference is what makes the picked one findable at a glance.
    OutlinedToggleButton(
        checked = selected,
        onCheckedChange = { onSelect(preset) },
        buttonSize = ToggleButtonSize.ExtraSmall,
        enabled = enabled,
        // Re-roled from the toggle button's own checkbox semantics. Only one preset can be loaded at a
        // time, and "checked" invites a screen-reader user to tick several; the row is announced as the
        // single-choice group it is, matching the `selectableGroup` on the parent.
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { role = Role.RadioButton },
        icon = {
            Text(
                text = preset.flag,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
    ) {
        Text(
            text = preset.carrier,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
