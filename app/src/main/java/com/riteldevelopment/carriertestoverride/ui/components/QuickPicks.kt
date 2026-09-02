package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.OutlinedToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickPickRow(
    quickPicks: List<QuickPick>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (RegionPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (quickPicks.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        // Extra-small toggle buttons reserve a little vertical breathing room around their shape.
        // Pull wrapped lines together slightly so the rows read as one compact choice group.
        verticalArrangement = Arrangement.spacedBy((-6).dp),
    ) {
        quickPicks.chunked(QuickPicksPerRow).forEach { row ->
            ButtonGroup(
                overflowIndicator = {},
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { pick ->
                    quickPickChip(
                        preset = pick.preset,
                        selected = pick.preset.id == selectedId,
                        enabled = enabled,
                        onSelect = onSelect,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ButtonGroupScope.quickPickChip(
    preset: RegionPreset,
    selected: Boolean,
    enabled: Boolean,
    onSelect: (RegionPreset) -> Unit,
) {
    // An OutlinedToggleButton rather than a FilterChip: it carries selection in its *shape*, rounding hard when
    // unpicked and squaring off when picked, and it squashes under the finger on the way there. A chip
    // could only change colour, and this row is a dozen near-identical items where a second, structural
    // difference is what makes the picked one findable at a glance.
    customItem(
        buttonGroupContent = {
            val scheme = MaterialTheme.colorScheme
            val interactionSource = remember { MutableInteractionSource() }
            OutlinedToggleButton(
                checked = selected,
                onCheckedChange = { onSelect(preset) },
                buttonSize = ToggleButtonSize.ExtraSmall,
                enabled = enabled,
                interactionSource = interactionSource,
                shapes = ToggleButtonShapes(
                    shape = RoundedCornerShape(28.dp),
                    pressedShape = RoundedCornerShape(16.dp),
                    checkedShape = RoundedCornerShape(10.dp),
                ),
                colors = ToggleButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = scheme.onSurfaceVariant,
                    checkedContainerColor = lerp(
                        scheme.surfaceContainerHighest,
                        scheme.primary,
                        0.14f,
                    ),
                    checkedContentColor = scheme.onSurface,
                ),
                border = BorderStroke(
                    1.dp,
                    if (selected) lerp(scheme.outline, scheme.primary, 0.45f)
                    else scheme.outlineVariant,
                ),
                // Re-roled from the toggle button's own checkbox semantics. Only one preset can be
                // loaded at a time, and "checked" invites a screen-reader user to tick several.
                modifier = Modifier
                    .weight(1f)
                    .animateWidth(interactionSource)
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
        },
        menuContent = {
            Text(
                text = "${preset.flag} ${preset.carrier}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

private const val QuickPicksPerRow = 4
