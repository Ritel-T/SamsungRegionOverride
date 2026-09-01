package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riteldevelopment.carriertestoverride.R
import com.riteldevelopment.carriertestoverride.ui.theme.LocalOverrideColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Consistent gap between the screen's top-level blocks. */
val BlockGap: Dp = 14.dp

@Composable
internal fun rememberCardInteractionSource(): MutableInteractionSource = remember {
    MutableInteractionSource()
}

/**
 * Draws a press state over the whole card while keeping the touch point in card coordinates.
 *
 * A stock indication attached to the card cannot safely consume a press emitted by a smaller header:
 * its hotspot is measured in the header's coordinates, then the card is remeasured as the body expands.
 * This small renderer stores the original point, recomputes the farthest corner on every draw, and
 * therefore stays under the finger while the card changes height.
 */
@Composable
internal fun Modifier.cardRipple(
    source: MutableInteractionSource,
    pressOffset: DpOffset = DpOffset.Zero,
): Modifier {
    val density = LocalDensity.current
    val offset = with(density) { Offset(pressOffset.x.toPx(), pressOffset.y.toPx()) }
    val tint = MaterialTheme.colorScheme.onSurface
    val expansion = remember { Animatable(0f) }
    val opacity = remember { Animatable(0f) }
    var activePress by remember { mutableStateOf<PressInteraction.Press?>(null) }

    LaunchedEffect(source, offset) {
        var animation: Job? = null
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    animation?.cancel()
                    activePress = interaction
                    expansion.snapTo(0f)
                    opacity.snapTo(1f)
                    animation = launch {
                        expansion.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(CardRippleExpandMillis),
                        )
                    }
                }

                is PressInteraction.Release -> {
                    if (interaction.press !== activePress) return@collect
                    animation?.cancel()
                    animation = launch {
                        val remaining = ((1f - expansion.value) * CardRippleExpandMillis)
                            .roundToInt()
                            .coerceAtLeast(1)
                        expansion.animateTo(1f, tween(remaining))
                        opacity.animateTo(0f, tween(CardRippleFadeMillis))
                        activePress = null
                        expansion.snapTo(0f)
                    }
                }

                is PressInteraction.Cancel -> {
                    if (interaction.press !== activePress) return@collect
                    animation?.cancel()
                    animation = launch {
                        opacity.animateTo(0f, tween(CardRippleFadeMillis))
                        activePress = null
                        expansion.snapTo(0f)
                    }
                }

                else -> Unit
            }
        }
    }

    return drawWithContent {
        drawContent()
        val press = activePress ?: return@drawWithContent
        val rawCenter = press.pressPosition + offset
        val center = if (!rawCenter.x.isNaN() && !rawCenter.y.isNaN()) rawCenter
        else Offset(size.width / 2f, size.height / 2f)
        val horizontal = max(center.x, size.width - center.x)
        val vertical = max(center.y, size.height - center.y)
        val radius = hypot(horizontal, vertical) + with(density) { 8.dp.toPx() }
        drawCircle(
            color = tint,
            alpha = CardRippleAlpha * opacity.value,
            radius = radius * expansion.value,
            center = center,
        )
    }
}

private const val CardRippleExpandMillis = 280
private const val CardRippleFadeMillis = 160
private const val CardRippleAlpha = 0.12f

/** The header emits into the surface's source, so the visual ripple spans the full card only once. */
internal fun Modifier.cardHeaderClick(
    source: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = clickable(
    interactionSource = source,
    indication = null,
    role = Role.Button,
    onClick = onClick,
)

/** Keeps switch semantics while sending the press to the surface-level ripple. */
internal fun Modifier.cardHeaderToggleable(
    source: MutableInteractionSource,
    value: Boolean,
    enabled: Boolean,
    onValueChange: (Boolean) -> Unit,
): Modifier = toggleable(
    value = value,
    enabled = enabled,
    role = Role.Switch,
    interactionSource = source,
    indication = null,
    onValueChange = onValueChange,
)

/** Sized to sit on the subtitle's line without outweighing the layer title above it. */
private val LayerReaderIconSize: Dp = 16.dp

/** Shared with SIM-card headers so the DATA badge cannot make one card's content start lower. */
private val StateBadgeVerticalPadding: Dp = 2.dp

/** The badge's measured text line plus its real vertical padding, including the current font scale. */
@Composable
internal fun stateBadgeMinimumHeight(): Dp = with(LocalDensity.current) {
    MaterialTheme.typography.labelSmallEmphasized.lineHeight.toDp()
} + StateBadgeVerticalPadding * 2

/** Compact emphasized label used for field names and section headers. */
@Composable
fun MicroLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmallEmphasized.copy(letterSpacing = 0.sp),
        color = color,
    )
}

/**
 * A state marker whose *shape* carries meaning as well as its colour: filled when the layer is actually
 * applied to this SIM, outline-only when it is not. Colour alone would leave the two states
 * indistinguishable to a colour-blind reader and in a grayscale screenshot.
 *
 * Both states are drawn by the same code — a fill and a border, one of which is transparent — rather
 * than by two different modifier chains, so the flip is three colour animations instead of a swap. This
 * badge is how a layer reports that it landed, which is the payoff moment of the whole screen; cutting
 * straight to the filled state there reads as the screen redrawing rather than as the SIM changing.
 * Neither `background` nor `border` affects measurement, so the badge holds its size throughout.
 */
@Composable
fun StateBadge(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = LocalOverrideColors.current.success,
    onActiveColor: Color = LocalOverrideColors.current.onSuccess,
) {
    val shape = MaterialTheme.shapes.small
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        targetValue = if (active) activeColor else Color.Transparent,
        animationSpec = motion.defaultEffectsSpec(),
        label = "badgeContainer",
    )
    val edge by animateColorAsState(
        targetValue = if (active) Color.Transparent else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "badgeEdge",
    )
    val ink by animateColorAsState(
        targetValue = if (active) onActiveColor else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "badgeInk",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(container)
            .border(1.dp, edge, shape)
            .padding(horizontal = 6.dp, vertical = StateBadgeVerticalPadding)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmallEmphasized,
            color = ink,
        )
    }
}

/**
 * A badge that is not always there — LIVE, DATA — entering and leaving under its own animation.
 *
 * Separate from [StateBadge] because appearing and changing state are different events and only one of
 * them is about this badge's own colours. Row arrangements space measured children, and a settled
 * `AnimatedVisibility` emits no node at all, so a hidden badge leaves no gap behind it.
 */
@Composable
fun AppearingStateBadge(
    text: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = LocalOverrideColors.current.success,
    onActiveColor: Color = LocalOverrideColors.current.onSuccess,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        // Scaled as well as expanded, and clip = false so the overshoot on the spring is allowed to
        // show rather than being cut off at the badge's own bounds.
        enter = fadeIn(animationSpec = motion.fastEffectsSpec()) +
            scaleIn(animationSpec = motion.defaultSpatialSpec(), initialScale = 0.7f) +
            expandHorizontally(animationSpec = motion.defaultSpatialSpec(), clip = false),
        exit = fadeOut(animationSpec = motion.fastEffectsSpec()) +
            scaleOut(animationSpec = motion.fastSpatialSpec(), targetScale = 0.7f) +
            shrinkHorizontally(animationSpec = motion.fastSpatialSpec(), clip = false),
    ) {
        StateBadge(
            text = text,
            active = true,
            activeColor = activeColor,
            onActiveColor = onActiveColor,
        )
    }
}

/**
 * The disclosure chevron on an expandable block.
 *
 * Rotated rather than swapped between the up and down glyphs. Those two icons differ by exactly that
 * rotation, so turning one is the same picture doing what the tap just did, where swapping them is two
 * pictures with nothing in between. Three separate blocks on this screen open this way — the custom
 * target fields, the target-apps panel, the report and its probe — and all three open directly under the
 * finger that pressed them, which is the case where the missing half of the motion is most obvious.
 *
 * The rotation is the whole of the *visible* signal, and for a while it was the whole signal full stop:
 * a plain `onClick` row says "double tap to activate" and nothing about which way this block is about to
 * go. Passing [onToggle] puts the platform's own expand/collapse action on the row instead. That action
 * is labelled by the framework in the user's locale, so a screen reader can name the direction without
 * this project shipping the words for it in fifteen languages, and offering only one of the two is
 * itself the statement of which state the block is in.
 *
 * It is declared here, on the arrow, rather than on the row: these rows are `ListItem`s that merge their
 * descendants, so an action declared inside the merge rises into the node the reader actually lands on,
 * while one declared on an ancestor of it would sit on a node the reader passes straight through.
 */
@Composable
fun DisclosureChevron(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    onToggle: (() -> Unit)? = null,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "chevronRotation",
    )
    Icon(
        imageVector = Icons.Filled.KeyboardArrowDown,
        // No description of its own: the arrow says the same thing the expand/collapse action below
        // says, and a reader that announced both would say it twice.
        contentDescription = null,
        tint = tint,
        modifier = modifier
            .rotate(rotation)
            .then(
                if (onToggle == null) Modifier else Modifier.semantics {
                    if (expanded) collapse { onToggle(); true } else expand { onToggle(); true }
                }
            ),
    )
}

/**
 * A concise warning note for state that needs attention.
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
            .clip(MaterialTheme.shapes.medium)
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
    val motion = MaterialTheme.motionScheme
    val railColor by animateColorAsState(
        targetValue = when {
            applied -> success
            enabled -> accent
            else -> idleColor
        },
        animationSpec = motion.fastEffectsSpec(),
        label = "layerRailColor",
    )
    val layoutDirection = LocalLayoutDirection.current
    val interactionSource = rememberCardInteractionSource()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .cardRipple(
                source = interactionSource,
                pressOffset = DpOffset(
                    if (layoutDirection == LayoutDirection.Ltr) 17.dp else 14.dp,
                    14.dp,
                ),
            )
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .cardHeaderToggleable(
                    source = interactionSource,
                    value = enabled,
                    enabled = controlsEnabled,
                    onValueChange = onEnabledChange,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    AppearingStateBadge(
                        text = stringResource(R.string.badge_live),
                        visible = applied,
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
                onCheckedChange = null,
                enabled = controlsEnabled,
            )
        }

        // Animated for the same reason the body below is: turning the switch off while the layer is
        // still live collapses the body and raises this warning in the same frame, and an un-animated
        // line appearing inside a card that is simultaneously shrinking reads as a glitch.
        AnimatedVisibility(
            visible = applied && !enabled && liveButDisarmedText != null,
            enter = fadeIn(animationSpec = motion.fastEffectsSpec()) +
                expandVertically(animationSpec = motion.defaultSpatialSpec()),
            exit = fadeOut(animationSpec = motion.fastEffectsSpec()) +
                shrinkVertically(animationSpec = motion.defaultSpatialSpec()),
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                Text(
                    // Held through the exit: the text is null exactly when the caller has nothing to
                    // say, and reading it live would blank the line halfway out of view.
                    text = liveButDisarmedText.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        AnimatedVisibility(
            visible = enabled,
            enter = fadeIn(animationSpec = motion.fastEffectsSpec()) +
                expandVertically(animationSpec = motion.defaultSpatialSpec()),
            exit = fadeOut(animationSpec = motion.fastEffectsSpec()) +
                shrinkVertically(animationSpec = motion.defaultSpatialSpec()),
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
}
