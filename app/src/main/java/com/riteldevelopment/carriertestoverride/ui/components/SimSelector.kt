package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riteldevelopment.carriertestoverride.data.SimInfo
import com.riteldevelopment.carriertestoverride.ui.theme.TabularFigures

/**
 * Picking the subscription every later operation writes to.
 *
 * One card per *hardware slot*, side by side at fixed positions. No phone ships more than two, so the
 * whole set always fits and there is nothing to scroll: a rail that slides would hide half the choice
 * behind a gesture, and it would also let the cards change position as SIMs come and go. A slot with no
 * subscription keeps its place as a hatched placeholder, so the layout is identical whether the second
 * SIM is present or not.
 *
 * The cards carry facts a collapsed control could not: current MCC/MNC, lock state, and — the reason
 * restore is predictable — whether *this tool* has already written a layer onto that subscription.
 *
 * [enabled] goes false while a privileged operation is in flight. It gates the click only; nothing is
 * dimmed, because the state on these cards is what the user is waiting to see change.
 */
@Composable
fun SimSelector(
    sims: List<SimInfo>,
    slotCount: Int,
    selectedSubId: Int,
    scanError: String?,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (scanError != null) {
            ScanErrorBlock(message = scanError)
        }

        // The scan failed and produced nothing: "we could not read" is the whole story. Drawing empty
        // slots underneath would assert a slot count the failed scan never established.
        if (sims.isEmpty() && scanError != null) return@Column

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Bounds the row to the tallest card so the shorter one can fill it. Both cards then end
                // at the same line whichever slot happens to carry more text.
                .height(IntrinsicSize.Min)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (slot in 0 until slotCount.coerceAtLeast(1)) {
                val sim = sims.firstOrNull { it.slotIndex == slot }
                val slotModifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                if (sim == null) {
                    EmptySlotCard(slotIndex = slot, modifier = slotModifier)
                } else {
                    SimCard(
                        sim = sim,
                        selected = sim.subId == selectedSubId,
                        enabled = enabled,
                        onSelect = { onSelect(sim.subId) },
                        modifier = slotModifier,
                    )
                }
            }
        }

        if (sims.isEmpty() && scanError == null) {
            Text(
                text = "No subscription found. Insert a SIM or enable an eSIM, then rescan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val CardShape = RoundedCornerShape(12.dp)
private val CardPadding: Dp = 12.dp

/**
 * A card is selectable even when the SIM is not READY.
 *
 * Locking it would be the wrong lesson: only the SIM identity layer needs a loaded ICC, while the
 * app-country layer works on any subscription. The state is stated on the card and the choice is left
 * with the user; the layer below explains what READY is required for.
 */
@Composable
private fun SimCard(
    sim: SimInfo,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val borderColor by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.outlineVariant,
        label = "simCardBorderColor",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        label = "simCardBorderWidth",
    )
    val cardColor by animateColorAsState(
        targetValue = if (selected) scheme.surfaceContainerHigh else scheme.surfaceContainerLow,
        label = "simCardBackground",
    )

    Column(
        modifier = modifier
            // clip first so the selection ripple stays inside the rounded rect.
            .clip(CardShape)
            .background(cardColor)
            .border(borderWidth, borderColor, CardShape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(CardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroLabel(text = sim.displayName)
            Spacer(Modifier.weight(1f))
            SelectionMark(selected = selected)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = sim.operatorNumeric.ifBlank { Absent },
            style = MaterialTheme.typography.titleMedium.merge(TabularFigures),
            color = scheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = sim.countryIso.uppercase().ifBlank { Absent } + " · " +
                sim.operatorName.ifBlank { Absent },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "sub ${sim.subId}",
                style = MaterialTheme.typography.labelSmall.merge(TabularFigures),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = sim.stateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (sim.isReady) scheme.onSurfaceVariant else scheme.error,
                maxLines = 1,
            )
        }

        // Both badges are always drawn, never only the active ones: the empty pair is the statement
        // "this tool has written nothing to this SIM", which is exactly what decides whether restore has
        // anything to do.
        //
        // The weight pins them to the bottom edge, so when the sibling card is taller the two badge rows
        // still line up with each other instead of floating at different heights.
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            MicroLabel(text = "SET")
            StateBadge(text = "ID", active = sim.flags.simIdentity)
            StateBadge(text = "CTY", active = sim.flags.appCountry)
        }
    }
}

/**
 * A slot the hardware has but nothing occupies.
 *
 * Hatched outline rather than a faint filled card: it must read as "nothing here" at a glance and stay
 * distinguishable from an unselected SIM card in a grayscale screenshot, so the difference is texture
 * rather than tone. It is not selectable and carries no state.
 */
@Composable
private fun EmptySlotCard(
    slotIndex: Int,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = modifier
            .drawBehind {
                val stroke = 1.dp.toPx()
                val radius = CornerRadius(12.dp.toPx())
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = radius,
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(4.dp.toPx(), 4.dp.toPx())
                        ),
                    ),
                )
            }
            .padding(CardPadding),
        verticalArrangement = Arrangement.Center,
    ) {
        MicroLabel(text = "SIM ${slotIndex + 1}")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Empty slot",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Selection has to survive a grayscale screenshot and a colour-blind reader, so the border/fill change is
 * backed by a mark that differs in *shape*: a filled disc with a tick when chosen, an empty ring when not.
 * The tick is drawn rather than imported — material-icons-extended is not a dependency, and the ring/disc
 * pair keeps the corner the same size in both states so nothing shifts on selection.
 */
@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val idle = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier.size(18.dp)) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        if (selected) {
            drawCircle(color = primary, radius = radius, center = center)
            val tick = Path().apply {
                moveTo(center.x - radius * 0.44f, center.y + radius * 0.02f)
                lineTo(center.x - radius * 0.12f, center.y + radius * 0.34f)
                lineTo(center.x + radius * 0.46f, center.y - radius * 0.36f)
            }
            drawPath(
                path = tick,
                color = onPrimary,
                style = Stroke(
                    width = radius * 0.26f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        } else {
            val ring = radius * 0.16f
            drawCircle(
                color = idle,
                radius = radius - ring / 2f,
                center = center,
                style = Stroke(width = ring),
            )
        }
    }
}

/**
 * "We could not read the SIMs" is a different fact from "there are no SIMs", and the two lead to
 * different actions, so this never shares a surface with the empty state.
 */
@Composable
private fun ScanErrorBlock(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/** Shown when the platform reports an empty string, so a blank field never reads as a real value. */
private const val Absent = "—"
