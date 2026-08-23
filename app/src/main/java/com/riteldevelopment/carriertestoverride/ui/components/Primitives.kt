package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riteldevelopment.carriertestoverride.R
import com.riteldevelopment.carriertestoverride.ui.theme.LocalOverrideColors

/** Consistent gap between the screen's top-level blocks. */
val BlockGap: Dp = 14.dp

/** Sized to sit on the subtitle's line without outweighing the layer title above it. */
private val LayerReaderIconSize: Dp = 16.dp

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
 * chrome. Each is a flat block with a left rail whose colour encodes state: quiet grey when off,
 * accented when armed, success-coloured once the layer is actually applied.
 *
 * The rail used to encode the same state a second time in its *length*, running only 22dp down the edge
 * while the block was collapsed. On an expanded block that read as intended; on a collapsed one the
 * block is barely taller than the stub itself, so the rail arrived a quarter drawn and looked like a
 * clipping bug rather than a signal. Length is now constant and colour carries the state alone, which
 * costs nothing legible: armed is already stated by the switch's own position and applied by the LIVE
 * badge's fill, both of which survive a grayscale screenshot.
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
    /**
     * Packages that read this layer, shown as their own icons beside the subtitle.
     *
     * The question a user actually arrives with is "which switch makes *my* app believe me", and a
     * sentence naming apps answers it slower than the apps' own icons do. Uninstalled packages simply
     * do not render, which is honest: an icon row that showed a placeholder would be claiming this
     * phone has an app it does not.
     */
    readerPackages: List<String> = emptyList(),
    liveButDisarmedText: String? = null,
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
    val layoutDirection = LocalLayoutDirection.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .drawBehind {
                val width = 3.dp.toPx()
                drawRect(
                    color = railColor,
                    topLeft = Offset(
                        if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - width,
                        0f,
                    ),
                    size = Size(width, size.height),
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
                    if (applied) StateBadge(
                        text = stringResource(R.string.badge_live),
                        active = true,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    readerPackages.forEach { packageName ->
                        rememberAppIcon(packageName)?.let { bitmap ->
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                modifier = Modifier.size(LayerReaderIconSize),
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                enabled = controlsEnabled,
            )
        }

        if (applied && !enabled && liveButDisarmedText != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = liveButDisarmedText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
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
