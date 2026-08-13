package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riteldevelopment.carriertestoverride.data.SimInfo
import com.riteldevelopment.carriertestoverride.ui.theme.LocalOverrideColors
import com.riteldevelopment.carriertestoverride.ui.theme.TabularFigures

/**
 * The lane the arrow sits in. The label row and the detail row reserve exactly the same gap, so the
 * three rows stay in the same two columns and the numerals of the two sides line up digit for digit.
 */
private val ArrowLane: Dp = 40.dp

private const val NoSimLabel = "No SIM"
private const val UnknownLabel = "Unknown"

/**
 * Reported identity versus intended identity — the screen's answer to "did the override land?".
 *
 * The block is laid out as three full-width rows (labels / numerics / details) rather than as two
 * self-contained side columns. A side-column layout would let a wrapped 2-line carrier name on one
 * side push that side's numeral off the other's line, which destroys the only thing this component
 * exists to support: comparing 46000 against 23430 position by position.
 *
 * Only the four text values animate. The frame, labels and arrow are static, so the caller's
 * post-operation polling (it re-reads the SIMs several times over a few seconds) reads as the numbers
 * flipping, not as the whole block redrawing.
 */
@Composable
fun IdentityDiff(
    current: SimInfo?,
    targetMccMnc: String,
    targetCountryIso: String,
    targetCarrierName: String,
    modifier: Modifier = Modifier,
) {
    val currentNumeric = when {
        current == null -> NoSimLabel
        current.operatorNumeric.isBlank() -> UnknownLabel
        else -> current.operatorNumeric
    }
    // With no SIM the value line already says so; repeating it here would be noise. The empty string
    // still occupies a line, which keeps the block from changing height when a SIM appears.
    val currentDetail = if (current == null) "" else describe(current.countryIso, current.operatorName)
    val targetNumeric = targetMccMnc.ifBlank { UnknownLabel }
    val targetDetail = describe(targetCountryIso, targetCarrierName)

    // Equal numerics usually mean the override is already in place, so the target stops reading as
    // intent and starts reading as fact.
    val matched = current != null &&
        current.operatorNumeric.isNotBlank() &&
        current.operatorNumeric == targetMccMnc

    val successColor = LocalOverrideColors.current.success
    val accentColor = MaterialTheme.colorScheme.primary
    val quietColor = MaterialTheme.colorScheme.onSurfaceVariant
    val disabledColor = MaterialTheme.colorScheme.outlineVariant

    val targetColor by animateColorAsState(
        targetValue = if (matched) successColor else accentColor,
        label = "identityDiffTargetColor",
    )
    val arrowColor by animateColorAsState(
        targetValue = if (current == null) disabledColor else quietColor,
        label = "identityDiffArrowColor",
    )

    val numericStyle = MaterialTheme.typography.headlineMedium.merge(TabularFigures)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroLabel(text = "NOW", modifier = Modifier.weight(1f))
            Spacer(Modifier.width(ArrowLane))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MicroLabel(text = "TARGET")
                if (matched) StateBadge(text = "MATCH", active = true)
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Crossfade(
                targetState = currentNumeric,
                modifier = Modifier.weight(1f),
                label = "identityDiffCurrentValue",
            ) { value ->
                Text(
                    text = value,
                    // The no-SIM placeholder is prose, not a figure: at headline scale it would either
                    // dominate the block or get clipped, and it has no digits to align anyway.
                    style = if (value == NoSimLabel) MaterialTheme.typography.titleMedium else numericStyle,
                    color = quietColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier.width(ArrowLane),
                contentAlignment = Alignment.Center,
            ) {
                RightArrow(color = arrowColor)
            }
            Crossfade(
                targetState = targetNumeric,
                modifier = Modifier.weight(1f),
                label = "identityDiffTargetValue",
            ) { value ->
                Text(
                    text = value,
                    style = numericStyle,
                    color = targetColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        Row {
            Crossfade(
                targetState = currentDetail,
                modifier = Modifier.weight(1f),
                label = "identityDiffCurrentDetail",
            ) { detail ->
                DetailLine(text = detail, color = quietColor)
            }
            Spacer(Modifier.width(ArrowLane))
            Crossfade(
                targetState = targetDetail,
                modifier = Modifier.weight(1f),
                label = "identityDiffTargetDetail",
            ) { detail ->
                DetailLine(text = detail, color = targetColor)
            }
        }
    }
}

/** Country plus operator name, dropping whichever half the platform did not report. */
private fun describe(countryIso: String, operatorName: String): String =
    listOf(countryIso, operatorName)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
        .ifEmpty { UnknownLabel }

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
 * Drawn rather than imported: only the core Material icon set is on the classpath and it has no plain
 * arrow, and a text glyph would be laid out by whichever fallback font the locale supplies.
 */
@Composable
private fun RightArrow(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(width = 22.dp, height = 12.dp)) {
        val stroke = 1.5.dp.toPx()
        val head = 4.5.dp.toPx()
        val midY = size.height / 2f
        val tip = Offset(size.width, midY)
        drawLine(
            color = color,
            start = Offset(0f, midY),
            end = tip,
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width - head, midY - head),
            end = tip,
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width - head, midY + head),
            end = tip,
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
