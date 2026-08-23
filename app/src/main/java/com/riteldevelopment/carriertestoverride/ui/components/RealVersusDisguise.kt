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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.riteldevelopment.carriertestoverride.R
import com.riteldevelopment.carriertestoverride.data.SimInfo
import com.riteldevelopment.carriertestoverride.data.countryIsoForMccMnc
import com.riteldevelopment.carriertestoverride.data.describeRegion
import com.riteldevelopment.carriertestoverride.ui.theme.LocalOverrideColors
import com.riteldevelopment.carriertestoverride.ui.theme.TabularFigures

/**
 * The gutter between the two columns. The label, numeric and detail rows all reserve exactly the same
 * gap, so the three rows stay in the same two columns and the numerals of the two sides line up digit
 * for digit.
 */
private val Gutter: Dp = 26.dp

/** Card padding, needed as a number because the sliding highlight is painted outside the padded area. */
private val CardPadding: Dp = 14.dp

/** How far the highlight extends past the text it sits behind, so the values do not touch its edge. */
private val HighlightBleed: Dp = 7.dp

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
 * Which of the two is in force is the single most important fact on the screen, so it is stated three
 * ways over: a highlight that slides between the columns, a LIVE badge that travels with it, and the
 * inactive side fading back. Position and opacity survive a grayscale screenshot and a colour-blind
 * reader, which colour alone would not.
 *
 * Only the values animate independently. The frame, labels and gutter are static, so the caller's
 * post-operation polling — it re-reads the SIMs several times over a few seconds — reads as the numbers
 * flipping and the highlight travelling, not as the whole block redrawing.
 */
@Composable
fun RealVersusDisguise(
    sim: SimInfo?,
    targetMccMnc: String,
    targetCountryIso: String,
    targetCarrierName: String,
    countryLayerArmed: Boolean,
    networkLayerArmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val live = sim?.disguised == true
    val noSimLabel = stringResource(R.string.no_sim)
    val unknownLabel = stringResource(R.string.unknown)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val realNumeric = when {
        sim == null -> noSimLabel
        sim.realOperatorNumeric.isBlank() -> unknownLabel
        else -> sim.realOperatorNumeric
    }
    // With no SIM the value line already says so; repeating it here would be noise. The empty string
    // still occupies a line, which keeps the block from changing height when a SIM appears.
    val realDetail = if (sim == null) "" else {
        describe(sim.realCountryIso, sim.realOperatorName, unknownLabel)
    }

    // What Apply would leave in force, switch by switch, rather than the whole target regardless. A
    // disarmed layer writes nothing: with the network layer off the MCC stays as it is, and with the
    // country layer off no ISO code is written at all, so the region is whatever the MCC then says.
    // Previewing the full target either way would promise changes that are not going to happen.
    val previewNumeric = if (networkLayerArmed) targetMccMnc else sim?.realOperatorNumeric.orEmpty()
    val previewCountryIso = if (countryLayerArmed) {
        targetCountryIso.ifEmpty { countryIsoForMccMnc(previewNumeric) }
    } else {
        countryIsoForMccMnc(previewNumeric).ifEmpty { sim?.realCountryIso.orEmpty() }
    }

    // Live: what the modem reports, because that is what apps are being told right now — and if only one
    // layer landed, showing it is how the user finds out. Not live: the preview of Apply.
    val disguiseNumeric = when {
        live -> sim.operatorNumeric.ifBlank { unknownLabel }
        else -> previewNumeric.ifBlank { unknownLabel }
    }
    // The country comes from [SimInfo.disguiseCountryIso], and from the same derivation on the preview
    // side, rather than from `getSimCountryIso()` directly. A network-only override moves the MCC and
    // nothing else, so reading the platform's country back would pair a British operator numeric with
    // the real country beside it — a region nobody is pretending to be, on the one line whose whole job
    // is to name the region being pretended.
    val disguiseDetail = when {
        live -> describe(sim.disguiseCountryIso, sim.operatorName, unknownLabel)
        else -> describe(previewCountryIso, targetCarrierName, unknownLabel)
    }

    val successColor = LocalOverrideColors.current.success
    val accentColor = MaterialTheme.colorScheme.primary
    val inkColor = MaterialTheme.colorScheme.onSurface
    val quietColor = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    // One driver for every part of the effect, so the highlight, the fades and the values can never
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
            .drawBehind { drawSlidingHighlight(slide, highlightColor, rtl) }
            .padding(CardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SideLabel(
                text = stringResource(R.string.label_real),
                live = !live,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Gutter))
            SideLabel(
                text = stringResource(R.string.label_disguise),
                live = live,
                modifier = Modifier.weight(1f),
            )
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
                    style = if (value == noSimLabel) MaterialTheme.typography.titleMedium else numericStyle,
                    color = inkColor.fade(1f - slide),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier.width(Gutter),
                contentAlignment = Alignment.Center,
            ) {
                DirectionMark(color = outlineColor, rtl = rtl)
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
            Spacer(Modifier.width(Gutter))
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
private fun DrawScope.drawSlidingHighlight(slide: Float, color: Color, rtl: Boolean) {
    val pad = CardPadding.toPx()
    val bleed = HighlightBleed.toPx()
    val gutter = Gutter.toPx()
    val column = (size.width - 2 * pad - gutter) / 2f
    if (column <= 0f) return
    val left = pad - bleed
    val right = pad + column + gutter - bleed
    val start = if (rtl) right else left
    val end = if (rtl) left else right
    drawRoundRect(
        color = color,
        topLeft = Offset(start + (end - start) * slide, bleed),
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
        if (live) StateBadge(text = stringResource(R.string.badge_live), active = true)
    }
}

private fun describe(countryIso: String, operatorName: String, unknown: String): String =
    describeRegion(countryIso, operatorName).ifEmpty { unknown }

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
 * The mark in the gutter: which way to read the two columns, and nothing else.
 *
 * This was a full arrow that took the live colour and leaned with the highlight. It was the one thing
 * on the block competing with the block — a shaft and a head wide enough to fill the gutter, drawn in
 * an accent, shifting about between two columns of quiet figures. Four cues for one fact was one too
 * many, and the one to drop is the one carrying least: direction never changes, so animating it bought
 * nothing that position and opacity were not already saying.
 *
 * So, a bare chevron at outline weight, static. It reads as punctuation between the two columns rather
 * than as a graphic sitting between them. Drawn rather than imported because only the core Material
 * icon set is on the classpath, and a text glyph would be laid out by whichever fallback font the
 * locale happened to supply.
 */
@Composable
private fun DirectionMark(
    color: Color,
    rtl: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(width = 5.dp, height = 10.dp)) {
        val stroke = 1.5.dp.toPx()
        // Half a stroke in on every side, or the round caps would be clipped by the canvas bounds.
        val inset = stroke / 2f
        val tipX = if (rtl) inset else size.width - inset
        val baseX = if (rtl) size.width - inset else inset
        val tip = Offset(tipX, size.height / 2f)
        drawLine(color, Offset(baseX, inset), tip, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(
            color,
            Offset(baseX, size.height - inset),
            tip,
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
