package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riteldevelopment.carriertestoverride.R
import com.riteldevelopment.carriertestoverride.data.SimInfo
import com.riteldevelopment.carriertestoverride.data.flagEmoji
import com.riteldevelopment.carriertestoverride.ui.theme.TabularFigures
import java.util.Locale

/**
 * Picking the subscription every later operation writes to.
 *
 * One card per *hardware slot*, side by side at fixed positions. No phone ships more than two, so the
 * whole set always fits and there is nothing to scroll: a rail that slides would hide half the choice
 * behind a gesture, and it would also let the cards change position as SIMs come and go. A slot with no
 * subscription keeps its place as a hatched placeholder, so the layout is identical whether the second
 * SIM is present or not.
 *
 * The cards carry facts a collapsed control could not: current MCC/MNC, lock state, whether *this tool*
 * has already written a layer onto that subscription — the reason restore is predictable — and which
 * slot carries mobile data, which decides whether a disguise is visible to anything at all.
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
    val motion = MaterialTheme.motionScheme
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Held through the exit for the same reason the data-SIM notice below is: a rescan that succeeds
        // clears the message in the same frame the block starts collapsing, and reading it live would
        // blank the box on its way out.
        var lastScanError by remember { mutableStateOf<String?>(null) }
        if (scanError != null) lastScanError = scanError
        AnimatedVisibility(
            visible = scanError != null,
            enter = fadeIn(animationSpec = motion.fastEffectsSpec()) +
                expandVertically(animationSpec = motion.defaultSpatialSpec()),
            exit = fadeOut(animationSpec = motion.fastEffectsSpec()) +
                shrinkVertically(animationSpec = motion.defaultSpatialSpec()),
        ) {
            Column {
                ScanErrorBlock(message = lastScanError.orEmpty())
                Spacer(Modifier.height(8.dp))
            }
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
            Column {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.no_subscription),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Only worth saying when there is a data SIM to switch to. On a single-SIM phone whose data is
        // off there is nothing to choose, so the same sentence would name a problem with no fix.
        val selected = sims.firstOrNull { it.subId == selectedSubId }
        val dataSim = sims.firstOrNull { it.isDefaultData }
        val mismatched = selected != null && dataSim != null && selected.subId != dataSim.subId

        // The wording is held rather than read live, because by the time the exit animation runs the
        // mismatch that produced it is already gone — recomputing would have the line name the same SIM
        // twice on its way out. Held from the last frame it was true, it collapses saying what it said.
        var wording by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        if (mismatched) wording = dataSim.slotIndex to selected.slotIndex

        AnimatedVisibility(
            visible = mismatched,
            enter = fadeIn(animationSpec = motion.fastEffectsSpec()) +
                expandVertically(animationSpec = motion.defaultSpatialSpec()),
            exit = fadeOut(animationSpec = motion.fastEffectsSpec()) +
                shrinkVertically(animationSpec = motion.defaultSpatialSpec()),
        ) {
            wording?.let { (dataSlot, selectedSlot) ->
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = stringResource(
                        R.string.not_data_sim,
                        // Via the resource, not SimInfo.displayName, so these can never name the slots
                        // differently from the cards directly above them.
                        stringResource(R.string.sim_number, dataSlot + 1),
                        stringResource(R.string.sim_number, selectedSlot + 1),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Resting and selected corner radii. Shared so the empty-slot placeholder matches an unselected card. */
private val CardCornerResting: Dp = 12.dp
private val CardCornerSelected: Dp = 26.dp
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
    val motion = MaterialTheme.motionScheme
    val borderColor by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.outlineVariant,
        animationSpec = motion.fastEffectsSpec(),
        label = "simCardBorderColor",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "simCardBorderWidth",
    )
    val cardColor by animateColorAsState(
        targetValue = if (selected) scheme.surfaceContainerHigh else scheme.surfaceContainerLow,
        animationSpec = motion.fastEffectsSpec(),
        label = "simCardBackground",
    )
    // Selection changes the card's *shape*, not just its colour and border. That is the Expressive
    // move — form carries state — and it is the same argument SelectionMark already makes below: a
    // difference in shape survives a grayscale screenshot and a colour-blind reader, which a difference
    // in tone does not. The spring comes from the theme's expressive motion scheme, so it overshoots
    // slightly rather than easing flatly into place.
    val cornerRadius by animateDpAsState(
        targetValue = if (selected) CardCornerSelected else CardCornerResting,
        animationSpec = motion.slowSpatialSpec(),
        label = "simCardCorner",
    )
    val cardShape = RoundedCornerShape(cornerRadius)

    Column(
        modifier = modifier
            // clip first so the selection ripple stays inside the rounded rect.
            .clip(cardShape)
            .background(cardColor)
            .border(borderWidth, borderColor, cardShape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(CardPadding),
    ) {
        Row(
            modifier = Modifier.heightIn(min = stateBadgeMinimumHeight()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MicroLabel(text = stringResource(R.string.sim_number, sim.slotIndex + 1))
            // Toned rather than success-coloured: this is a fact about the phone, not a layer this tool
            // has applied, and the green below already means the latter.
            //
            // It animates in because it moves: switching which SIM carries data is done in Android
            // Settings, so the user comes back to this screen to see the badge land on the other card.
            AppearingStateBadge(
                text = stringResource(R.string.badge_data),
                visible = sim.isDefaultData,
                activeColor = scheme.secondaryContainer,
                onActiveColor = scheme.onSecondaryContainer,
            )
            Spacer(Modifier.weight(1f))
            SelectionMark(selected = selected)
        }

        Spacer(Modifier.height(6.dp))
        val iso = sim.countryIso.uppercase(Locale.ROOT).ifBlank { Absent }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            flagEmoji(sim.countryIso).takeIf { it.isNotEmpty() }?.let { flag ->
                Text(
                    text = flag,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                )
            }
            Text(
                text = iso,
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = scheme.onSurface,
                maxLines = 1,
            )
        }
        Text(
            text = listOf(
                sim.operatorNumeric.ifBlank { Absent },
                sim.operatorName.ifBlank { Absent },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall.merge(TabularFigures),
            color = scheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.sub_id, sim.subId),
                style = MaterialTheme.typography.labelSmall.merge(TabularFigures),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = simStateLabel(sim.simState),
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
            MicroLabel(text = stringResource(R.string.badge_set))
            StateBadge(text = stringResource(R.string.badge_identity), active = sim.flags.simIdentity)
            StateBadge(text = stringResource(R.string.badge_country), active = sim.flags.appCountry)
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
                val radius = CornerRadius(CardCornerResting.toPx())
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
        MicroLabel(text = stringResource(R.string.sim_number, slotIndex + 1))
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.empty_slot),
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
 *
 * The two states are ends of one animation rather than two drawings. This is the control the user's thumb
 * actually lands on, so it is the last place on the screen that should answer with a hard cut: the disc
 * grows out of the ring's centre while the ring fades, and the tick scales up behind it. One spring drives
 * all three, so they can never disagree about how far through the change they are.
 *
 * The spring is allowed to overshoot but the drawing is not: a radius past the canvas would be cut square
 * by its own bounds, and a negative one — the undershoot on the way back — would mirror the tick. So the
 * progress is clamped for drawing while the spring keeps its timing, which is where the snap comes from.
 */
@Composable
private fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val idle = MaterialTheme.colorScheme.outlineVariant
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "selectionMark",
    )
    Canvas(modifier = modifier.size(18.dp)) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val settled = progress.coerceIn(0f, 1f)

        if (settled < 1f) {
            val ring = radius * 0.16f
            drawCircle(
                color = idle.copy(alpha = idle.alpha * (1f - settled)),
                radius = radius - ring / 2f,
                center = center,
                style = Stroke(width = ring),
            )
        }
        if (settled > 0f) {
            drawCircle(color = primary, radius = radius * settled, center = center)
            // The tick's own vertices are already written relative to the centre, so scaling it is a
            // matter of scaling the radius they are measured against — no clip, no transform, and one
            // Path per frame either way.
            val arm = radius * settled
            val tick = Path().apply {
                moveTo(center.x - arm * 0.44f, center.y + arm * 0.02f)
                lineTo(center.x - arm * 0.12f, center.y + arm * 0.34f)
                lineTo(center.x + arm * 0.46f, center.y - arm * 0.36f)
            }
            drawPath(
                path = tick,
                color = onPrimary.copy(alpha = onPrimary.alpha * settled),
                style = Stroke(
                    width = radius * 0.26f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
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
            .clip(MaterialTheme.shapes.medium)
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

@Composable
private fun simStateLabel(state: Int): String = stringResource(
    SimInfo.simStateNameRes(state)
)
