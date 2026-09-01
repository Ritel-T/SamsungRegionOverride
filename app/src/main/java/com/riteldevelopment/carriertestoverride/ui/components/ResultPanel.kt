package com.riteldevelopment.carriertestoverride.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.riteldevelopment.carriertestoverride.R
import com.riteldevelopment.carriertestoverride.data.OverrideRepository
import com.riteldevelopment.carriertestoverride.ui.BusyState
import com.riteldevelopment.carriertestoverride.ui.ResultState
import com.riteldevelopment.carriertestoverride.ui.ResultTone
import com.riteldevelopment.carriertestoverride.ui.theme.LocalOverrideColors
import kotlinx.coroutines.delay

/** How much of the report is shown before the user asks for the rest. Six lines is roughly one screen. */
private const val CollapsedDetailLines = 6

/** How long the copy button admits it did something, on the versions of Android that say nothing. */
private const val CopiedFeedbackMillis = 1600L

/** A discriminator rather than a payload, so stage updates do not restart the shell transition. */
private enum class ResultShellMode { BARE, BUSY, RESULT }

/**
 * The report the privileged operation produced.
 *
 * This is not a status footnote — it is the artefact users paste into bug threads, so it is sized and
 * spaced to be read and selected rather than glanced at. Two text bodies live here with opposite needs:
 * the narrative report, which must wrap and use the system face, and the runtime probe, which must *not*
 * wrap and must stay monospaced so reflected signatures line up column-wise.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ResultPanel(
    result: ResultState,
    modifier: Modifier = Modifier,
    busy: BusyState? = null,
) {
    val detail = result.detail
    val probe = result.probe
    val headline = result.headline.ifBlank { stringResource(R.string.result_nothing_run) }
    val motion = MaterialTheme.motionScheme

    // Nothing has run yet. A tinted box with an icon here would claim an outcome that does not exist, so
    // the initial state gets no container at all.
    //
    // Keyed on tone, not on emptiness. Every validation refusal — "No usable SIM is selected", "Shizuku
    // is not installed" — is an ERROR with a headline and nothing else, and testing emptiness alone
    // rendered those as quiet grey prose indistinguishable from the idle state.
    val bare = detail == null && probe == null && result.tone == ResultTone.IDLE
    var lastBusy by remember { mutableStateOf<BusyState?>(null) }
    if (busy != null) lastBusy = busy
    val shellMode = when {
        busy != null -> ResultShellMode.BUSY
        bare -> ResultShellMode.BARE
        else -> ResultShellMode.RESULT
    }

    // Crossfaded rather than swapped. This is the one place on the screen an operation reports back, and
    // the change it reports is exactly the bare line becoming a coloured panel — the first run of the
    // app goes straight from "Nothing has run yet" to a full green result. Cutting between those two
    // reads as the list reflowing rather than as an answer arriving.
    //
    // The height animator sits out here too, outside the crossfade rather than inside the panel.
    // Everything that resizes this block — the idle line becoming a panel, MORE/LESS on the report, the
    // probe opening — is a change to the same outer box, so owning all three in one place is what stops
    // them compounding. It is also why the parts inside only fade: an expandVertically nested under this
    // would have the outer animation chasing the inner one instead of following the content.
    Crossfade(
        targetState = shellMode,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = motion.defaultSpatialSpec()),
        animationSpec = motion.defaultEffectsSpec(),
        label = "resultShell",
    ) { mode ->
        when (mode) {
            ResultShellMode.BARE -> Text(
                text = headline,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ResultShellMode.BUSY -> lastBusy?.let { ResultProgressBody(it) }

            ResultShellMode.RESULT -> ResultBody(
                result = result,
                headline = headline,
                detail = detail,
                probe = probe,
            )
        }
    }
}

/**
 * Replaces the previous report while an operation is running.
 *
 * The bottom toolbar remains the primary narrator and owns Cancel. This quieter copy uses the same
 * already-localised stage text so the report slot never implies that an earlier result is still current,
 * without adding fifteen translations for a second progress vocabulary.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ResultProgressBody(busy: BusyState) {
    val stages = OverrideRepository.Stage.entries
    val step = stages.indexOf(busy.stage) + 1
    val content = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LoadingIndicator(
                modifier = Modifier
                    .size(28.dp)
                    .clearAndSetSemantics { },
                color = content,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(
                    R.string.busy_step,
                    step,
                    stages.size,
                    stringResource(busy.labelRes),
                ),
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = content,
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearWavyProgressIndicator(
            progress = { step.toFloat() / stages.size },
            // The bottom toolbar owns the screen's progress semantics. These visuals keep the report
            // slot honest without making TalkBack announce the same progress a second and third time.
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { },
            color = content,
            trackColor = content.copy(alpha = 0.18f),
        )
    }
}

/**
 * The panel proper: everything that only exists once something has actually run.
 *
 * Split out so the shell above can crossfade between this and the bare idle line. The remembered
 * expansion and copy-feedback state lives here, keyed on the report it belongs to, so a new result
 * arrives collapsed rather than inheriting the last one's disclosure.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ResultBody(
    result: ResultState,
    headline: String,
    detail: String?,
    probe: String?,
) {
    val palette = panelColors(result.tone)
    val motion = MaterialTheme.motionScheme
    // The panel changes tone in place when a second operation lands on top of a first — a restore
    // answering an apply, a retry answering an error. A hard cut there reads as the whole block being
    // replaced rather than as the same block reporting a new answer.
    val container by animateColorAsState(
        targetValue = palette.container,
        animationSpec = motion.fastEffectsSpec(),
        label = "resultContainer",
    )
    val content by animateColorAsState(
        targetValue = palette.content,
        animationSpec = motion.fastEffectsSpec(),
        label = "resultContent",
    )

    val context = LocalContext.current
    val clipText = remember(result, headline) {
        listOfNotNull(headline, detail, probe).joinToString("\n\n")
    }

    var detailExpanded by remember(detail) { mutableStateOf(false) }
    // Latched, never cleared: once expanded the text no longer overflows, and recomputing from the layout
    // would make the "LESS" button delete itself. Measurement is the only honest source here — counting
    // characters cannot know the panel width or where the text will break.
    var detailOverflows by remember(detail) { mutableStateOf(false) }
    var probeExpanded by remember(probe) { mutableStateOf(false) }
    var copied by remember(result) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(CopiedFeedbackMillis)
            copied = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(container)
            .padding(start = 14.dp, top = 10.dp, end = 10.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToneMark(tone = result.tone, tint = content)
            Text(
                text = headline,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = content,
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("carrier-override", clipText))
                    copied = true
                },
                colors = ButtonDefaults.textButtonColors(contentColor = content),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(
                        if (copied) R.string.action_copied else R.string.action_copy
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        // One selection spanning report + probe, so a single long-press drag grabs everything worth
        // pasting. The toggles inside are excluded — their labels are chrome, not report content.
        SelectionContainer {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (detail != null) {
                    Spacer(Modifier.height(4.dp))
                    // No animateContentSize of its own: the panel's outer Column now animates the whole
                    // block's height, and nesting a second one inside it makes the outer animation chase
                    // the inner one rather than follow the content.
                    Text(
                        text = detail,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = content,
                        maxLines = if (detailExpanded) Int.MAX_VALUE else CollapsedDetailLines,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { layout ->
                            if (!detailExpanded && layout.hasVisualOverflow) detailOverflows = true
                        },
                    )
                    if (detailOverflows) {
                        DisableSelection {
                            InlineToggle(
                                text = stringResource(
                                    if (detailExpanded) R.string.action_less else R.string.action_more
                                ),
                                expanded = detailExpanded,
                                tint = content,
                                onClick = { detailExpanded = !detailExpanded },
                            )
                        }
                    }
                }

                if (probe != null) {
                    DisableSelection {
                        InlineToggle(
                            text = stringResource(R.string.action_runtime_probe),
                            expanded = probeExpanded,
                            tint = content,
                            onClick = { probeExpanded = !probeExpanded },
                        )
                    }
                    // Fade only. The height is the outer animator's job, and adding expandVertically
                    // here would animate the same change twice at two different rates.
                    AnimatedVisibility(
                        visible = probeExpanded,
                        enter = fadeIn(animationSpec = motion.fastEffectsSpec()),
                        exit = fadeOut(animationSpec = motion.fastEffectsSpec()),
                    ) {
                        ProbeBlock(probe = probe, tint = content)
                    }
                }
            }
        }
    }
}

/**
 * The ASCII capability dump.
 *
 * Monospace stops at this block deliberately: it buys column alignment for reflected signatures and costs
 * readability everywhere else. Long signatures scroll sideways instead of wrapping — horizontal is a
 * different axis from the screen's vertical LazyColumn, so it composes cleanly where a nested vertical
 * scroll would not.
 *
 * The backdrop is derived from the tone's own content colour rather than a fixed surface role, because
 * this block sits inside four different containers and a fixed neutral would clash with the error tint.
 */
@Composable
private fun ProbeBlock(
    probe: String,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(tint.copy(alpha = 0.06f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = probe,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = tint,
            softWrap = false,
        )
    }
}

/** Disclosure control pulled back into the text column so its label lines up with the body above it. */
@Composable
private fun InlineToggle(
    text: String,
    expanded: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.offset(x = (-8).dp),
        colors = ButtonDefaults.textButtonColors(contentColor = tint),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
    ) {
        DisclosureChevron(
            expanded = expanded,
            modifier = Modifier.size(16.dp),
            tint = tint,
            onToggle = onClick,
        )
        Spacer(Modifier.width(4.dp))
        MicroLabel(text = text, color = tint)
    }
}

/**
 * The tone's leading mark.
 *
 * Colour alone cannot carry this: PARTIAL means one layer landed and the other did not, and in a
 * grayscale screenshot — which is what ends up in a bug thread — an amber panel and a green one are the
 * same panel. The triangle, the disc and the cross differ in silhouette, so the outcome survives.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToneMark(
    tone: ResultTone,
    tint: Color,
) {
    when (tone) {
        ResultTone.SUCCESS -> OutcomeMark(
            mark = Icons.Filled.CheckCircle,
            tint = tint,
            polygon = MaterialShapes.Cookie6Sided,
        )
        ResultTone.PARTIAL -> OutcomeMark(
            mark = Icons.Filled.Warning,
            tint = tint,
            polygon = MaterialShapes.SoftBurst,
        )
        ResultTone.ERROR -> OutcomeMark(
            mark = Icons.Filled.Close,
            tint = tint,
            polygon = MaterialShapes.Boom,
        )
        ResultTone.IDLE -> Unit
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OutcomeMark(
    mark: ImageVector,
    tint: Color,
    polygon: RoundedPolygon,
) {
    var entered by remember(polygon) { mutableStateOf(false) }
    LaunchedEffect(polygon) { entered = true }
    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "outcomeShape",
    )
    val polygons = remember(polygon) { listOf(MaterialShapes.Circle, polygon) }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = tint.copy(alpha = 0.16f),
            polygons = polygons,
        )
        Icon(
            imageVector = mark,
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    alpha = progress
                    scaleX = 0.76f + 0.24f * progress
                    scaleY = scaleX
                    rotationZ = -12f * (1f - progress)
                },
            tint = tint,
        )
    }
    Spacer(Modifier.width(10.dp))
}

@Immutable
private data class PanelColors(val container: Color, val content: Color)

/** Tertiary is the theme's reserved PARTIAL role; success has no Material slot and comes from the theme's own. */
@Composable
private fun panelColors(tone: ResultTone): PanelColors {
    val scheme = MaterialTheme.colorScheme
    val override = LocalOverrideColors.current
    return when (tone) {
        ResultTone.ERROR -> PanelColors(scheme.errorContainer, scheme.onErrorContainer)
        ResultTone.PARTIAL -> PanelColors(scheme.tertiaryContainer, scheme.onTertiaryContainer)
        ResultTone.SUCCESS -> PanelColors(override.successContainer, override.onSuccessContainer)
        ResultTone.IDLE -> PanelColors(scheme.surfaceContainer, scheme.onSurface)
    }
}
