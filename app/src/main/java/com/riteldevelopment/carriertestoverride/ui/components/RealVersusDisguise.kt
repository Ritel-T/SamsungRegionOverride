package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riteldevelopment.carriertestoverride.data.SimInfo
import com.riteldevelopment.carriertestoverride.data.describeRegion
import com.riteldevelopment.carriertestoverride.ui.theme.LocalOverrideColors
import com.riteldevelopment.carriertestoverride.ui.theme.TabularFigures

/**
 * The lane the arrow sits in. The label, numeric and detail rows all reserve exactly the same gap, so
 * the three rows stay in the same two columns and the numerals of the two sides line up digit for digit.
 */
private val ArrowLane: Dp = 34.dp

/** Card padding, needed as a number because the sliding highlight is painted outside the padded area. */
private val CardPadding: Dp = 14.dp

/** How far the highlight extends past the text it sits behind, so the values do not touch its edge. */
private val HighlightBleed: Dp = 7.dp

private const val NoSimLabel = "No SIM"
private const val UnknownLabel = "Unknown"

/**
 * Who this SIM really is, against who it is pretending to be.
 *
 * This replaced a "now versus target" block, which named the two columns after the *operation* rather
 * than after the two identities. That framing broke down the moment an override was live: the "now"
 * column then showed the fake identity, so the screen displayed the disguise twice and the real SIM
 * nowhere. Here the left column is always the truth and the right column is always the mask, whichever
 * of them the modem happens to be reporting at the time:
 *
 *  * **Nothing applied** — left is what the SIM reports, right is the target as a preview.
 *  * **A layer live** — left is the snapshot taken before the first override, right is what the SIM
 *    reports, which is what apps are actually being told.
 *
 * Which of the two is in force is the single most important fact on the screen, so it is stated four
 * ways over: a highlight that slides between the columns, a LIVE badge that travels with it, the
 * inactive side fading back, and the arrow taking the live colour and leaning with it. Position and
 * opacity survive a grayscale screenshot and a colour-blind reader, which colour alone would not.
 *
 * Only the values animate independently. The frame, labels and lane are static, so the caller's
 * post-operation polling — it re-reads the SIMs several times over a few seconds — reads as the numbers
 * flipping and the highlight travelling, not as the whole block redrawing.
 */
@Composable
fun RealVersusDisguise(
    sim: SimInfo?,
    targetMccMnc: String,
    targetCountryIso: String,
    targetCarrierName: String,
    modifier: Modifier = Modifier,
) {
    val live = sim?.disguised == true

    val realNumeric = when {
        sim == null -> NoSimLabel
        sim.realOperatorNumeric.isBlank() -> UnknownLabel
        else -> sim.realOperatorNumeric
    }
    // With no SIM the value line already says so; repeating it here would be noise. The empty string
    // still occupies a line, which keeps the block from changing height when a SIM appears.
    val realDetail = if (sim == null) "" else describe(sim.realCountryIso, sim.realOperatorName)

    // Live: what the modem reports, because that is what apps are being told right now — and if only one
    // layer landed, showing it is how the user finds out. Not live: the target, as a preview of Apply.
    val disguiseNumeric = when {
        live -> sim.operatorNumeric.ifBlank { UnknownLabel }
        else -> targetMccMnc.ifBlank { UnknownLabel }
    }
    val disguiseDetail = when {
        live -> describe(sim.countryIso, sim.operatorName)
        else -> describe(targetCountryIso, targetCarrierName)
    }

    val successColor = LocalOverrideColors.current.success
    val accentColor = MaterialTheme.colorScheme.primary
    val inkColor = MaterialTheme.colorScheme.onSurface
    val quietColor = MaterialTheme.colorScheme.onSurfaceVariant

    // One driver for every part of the effect, so the highlight, the fades and the arrow can never
    // disagree about which side is in force.
    val slide by animateFloatAsState(
        targetValue = if (live) 1f else 0f,
        // Just under critical damping: the highlight settles with a single small overshoot, which reads
        // as something moving into place. A bouncier spring would throw the capsule clear of the column
        // it is meant to be marking, and the whole point of the travel is that it lands on one side.
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "disguiseSlide",
    )

    val disguiseColor by animateColorAsState(
        targetValue = if (live) successColor else accentColor,
        label = "disguiseColor",
    )
    val highlightColor by animateColorAsState(
        // Grey rather than nothing while the SIM is itself: an empty left half would read as "this side
        // is switched off", when what it means is "this is the identity in force".
        targetValue = if (live) successColor.copy(alpha = 0.14f) else inkColor.copy(alpha = 0.05f),
        label = "disguiseHighlight",
    )

    val numericStyle = MaterialTheme.typography.headlineMedium.merge(TabularFigures)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            // Painted before the padding is applied, so the highlight can bleed out past the columns it
            // sits behind while staying inside the card's own clip.
            .drawBehind { drawSlidingHighlight(slide, highlightColor) }
            .padding(CardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SideLabel(text = "REAL", live = !live, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(ArrowLane))
            SideLabel(text = "DISGUISE", live = live, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Crossfade(
                targetState = realNumeric,
                modifier = Modifier.weight(1f),
                label = "realNumeric",
            ) { value ->
                Text(
                    text = value,
                    // The no-SIM placeholder is prose, not a figure: at headline scale it would either
                    // dominate the block or get clipped, and it has no digits to align anyway.
                    style = if (value == NoSimLabel) MaterialTheme.typography.titleMedium else numericStyle,
                    color = inkColor.fade(1f - slide),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier.width(ArrowLane),
                contentAlignment = Alignment.Center,
            ) {
                TransformArrow(color = if (sim == null) quietColor else disguiseColor, slide = slide)
            }
            Crossfade(
                targetState = disguiseNumeric,
                modifier = Modifier.weight(1f),
                label = "disguiseNumeric",
            ) { value ->
                Text(
                    text = value,
                    style = numericStyle,
                    color = disguiseColor.fade(slide),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        Row {
            Crossfade(
                targetState = realDetail,
                modifier = Modifier.weight(1f),
                label = "realDetail",
            ) { detail ->
                DetailLine(text = detail, color = quietColor.fade(1f - slide))
            }
            Spacer(Modifier.width(ArrowLane))
            Crossfade(
                targetState = disguiseDetail,
                modifier = Modifier.weight(1f),
                label = "disguiseDetail",
            ) { detail ->
                DetailLine(text = detail, color = disguiseColor.fade(slide))
            }
        }
    }
}

/**
 * Dims a colour toward the inactive end of the slide.
 *
 * Never all the way out: the side that is not in force is still information — it is the identity you
 * came from, or the one you are about to take — so it recedes rather than disappearing.
 *
 * Clamped because the driving spring overshoots by design, and an alpha outside 0..1 is a crash rather
 * than a slightly wrong colour.
 */
private fun Color.fade(active: Float): Color =
    copy(alpha = (alpha * (0.48f + 0.52f * active)).coerceIn(0f, 1f))

/**
 * The travelling highlight: a capsule the width of one column that slides between the two.
 *
 * Drawn rather than laid out. A real composable would have to be measured against the same weights as
 * the rows above it and then offset, and its position would settle a frame behind them; taking the
 * geometry straight from the parent's own size keeps it locked to the columns at every frame.
 */
private fun DrawScope.drawSlidingHighlight(slide: Float, color: Color) {
    val pad = CardPadding.toPx()
    val bleed = HighlightBleed.toPx()
    val lane = ArrowLane.toPx()
    val column = (size.width - 2 * pad - lane) / 2f
    if (column <= 0f) return
    val left = pad - bleed
    val right = pad + column + lane - bleed
    drawRoundRect(
        color = color,
        topLeft = Offset(left + (right - left) * slide, bleed),
        size = Size(column + bleed * 2, size.height - bleed * 2),
        cornerRadius = CornerRadius(10.dp.toPx()),
    )
}

/** A column heading, carrying the LIVE badge when that column is the identity currently in force. */
@Composable
private fun SideLabel(text: String, live: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MicroLabel(text = text)
        if (live) StateBadge(text = "LIVE", active = true)
    }
}

private fun describe(countryIso: String, operatorName: String): String =
    describeRegion(countryIso, operatorName).ifEmpty { UnknownLabel }

@Composable
private fun DetailLine(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        // An 80-character carrier name is allowed input; it wraps once and then gets cut, rather than
        // being permitted to grow the block without bound.
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The mark in the lane, drawn rather than imported: only the core Material icon set is on the classpath
 * and it has no plain arrow, and a text glyph would be laid out by whichever fallback font the locale
 * supplies.
 *
 * It leans with the highlight — back over the real side while the SIM is itself, forward once the
 * disguise is in force. A few dp of travel is enough to make the block read as one moving thing rather
 * than a static frame with a highlight sliding underneath it.
 */
@Composable
private fun TransformArrow(
    color: Color,
    slide: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(width = 22.dp, height = 12.dp)) {
        val stroke = 1.5.dp.toPx()
        val head = 4.5.dp.toPx()
        val lean = 3.dp.toPx() * (slide * 2f - 1f)
        val midY = size.height / 2f
        val tail = Offset(lean, midY)
        val tip = Offset(size.width + lean, midY)
        drawLine(color, tail, tip, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(
            color,
            Offset(tip.x - head, midY - head),
            tip,
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color,
            Offset(tip.x - head, midY + head),
            tip,
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
