package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riteldevelopment.carriertestoverride.ui.theme.LocalOverrideColors

/** Consistent gap between the screen's top-level blocks. */
val BlockGap: Dp = 14.dp

/** Small tracked-out label used for field names and section headers. */
@Composable
fun MicroLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        color = color,
    )
}

/**
 * A state marker whose *shape* carries meaning as well as its colour: filled when the layer is actually
 * applied to this SIM, outline-only when it is not. Colour alone would leave the two states
 * indistinguishable to a colour-blind reader and in a grayscale screenshot.
 */
@Composable
fun StateBadge(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = LocalOverrideColors.current.success,
    onActiveColor: Color = LocalOverrideColors.current.onSuccess,
) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (active) Modifier.background(activeColor)
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = if (active) onActiveColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The standing risk notice.
 *
 * Deliberately not a warm-tinted box: those read as decoration and get tuned out. Attention comes from a
 * hatched edge — a texture borrowed from hazard marking — while the surface itself stays quiet, so the
 * block can sit permanently on screen without competing with live state.
 */
@Composable
fun HazardNote(text: String, modifier: Modifier = Modifier) {
    val stripeColor = LocalOverrideColors.current.hazardStripe
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .drawWithCache {
                val band = 7.dp.toPx()
                val step = 7.dp.toPx()
                val stroke = 2.dp.toPx()
                onDrawBehind {
                    clipRect(right = band) {
                        var x = -size.height
                        while (x < band + size.height) {
                            drawLine(
                                color = stripeColor,
                                start = Offset(x, size.height),
                                end = Offset(x + size.height, 0f),
                                strokeWidth = stroke,
                            )
                            x += step
                        }
                    }
                }
            }
            .padding(start = 17.dp, top = 12.dp, end = 14.dp, bottom = 12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One override layer.
 *
 * The two layers are intentionally *not* two identical cards — a reader stops distinguishing repeated
 * chrome. Each is a flat block with a left rail whose colour and length encode state: a short grey stub
 * when off, a full accented rail when armed, success-coloured once the layer is actually applied.
 *
 * The rail is painted rather than laid out, because a `fillMaxHeight` child inside a Row that lives in a
 * scrollable parent has an unbounded height constraint.
 */
@Composable
fun LayerSection(
    title: String,
    subtitle: String,
    enabled: Boolean,
    applied: Boolean,
    accent: Color,
    controlsEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val success = LocalOverrideColors.current.success
    val idleColor = MaterialTheme.colorScheme.outlineVariant
    val railColor by animateColorAsState(
        targetValue = when {
            applied -> success
            enabled -> accent
            else -> idleColor
        },
        label = "layerRailColor",
    )
    // 1f = the rail runs the full height of the block; the stub is a fixed 22dp.
    val railFraction by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        label = "layerRailExtent",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .drawBehind {
                val width = 3.dp.toPx()
                val stub = 22.dp.toPx()
                val height = stub + (size.height - stub) * railFraction
                drawRect(
                    color = railColor,
                    topLeft = Offset.Zero,
                    size = Size(width, height.coerceAtMost(size.height)),
                )
            }
            .padding(start = 17.dp, top = 14.dp, end = 14.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (applied) StateBadge(text = "LIVE", active = true)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                enabled = controlsEnabled,
            )
        }

        AnimatedVisibility(
            visible = enabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
}
