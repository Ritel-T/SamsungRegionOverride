package com.riteldevelopment.carriertestoverride.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.riteldevelopment.carriertestoverride.R
import com.riteldevelopment.carriertestoverride.data.OverrideRepository
import com.riteldevelopment.carriertestoverride.ui.BusyState
import com.riteldevelopment.carriertestoverride.ui.ResultState
import com.riteldevelopment.carriertestoverride.ui.ResultTone
import com.riteldevelopment.carriertestoverride.ui.theme.LocalOverrideColors
import kotlinx.coroutines.delay

/** How long the copy button admits it did something, on the versions of Android that say nothing. */
private const val CopiedFeedbackMillis = 1600L
private const val MaxLocalDetailChars = 12_000
private const val ResultResizeMillis = 520
private const val ResultFadeMillis = 300

/** A discriminator rather than a payload, so stage updates do not restart the shell transition. */
private enum class ResultShellMode { BUSY, RESULT }

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
    onReportIssue: () -> Unit = {},
) {
    val detail = result.detail
    val probe = result.probe
    val headline = result.headline.ifBlank { stringResource(R.string.result_nothing_run) }
    val motion = MaterialTheme.motionScheme

    var lastBusy by remember { mutableStateOf<BusyState?>(null) }
    if (busy != null) lastBusy = busy
    val shellMode = when {
        busy != null -> ResultShellMode.BUSY
        else -> ResultShellMode.RESULT
    }

    // Crossfaded rather than swapped. This is the one place on the screen an operation reports back, and
    // the same card changes tone when the first result arrives. Cutting between those states reads as the
    // list reflowing rather than as an answer arriving.
    // Detail disclosure owns its fade and height in one transition below, so closing it cannot leave a
    // second empty-height animation behind after the text has disappeared.
    Crossfade(
        targetState = shellMode,
        modifier = modifier.fillMaxWidth(),
        animationSpec = motion.defaultEffectsSpec(),
        label = "resultShell",
    ) { mode ->
        when (mode) {
            ResultShellMode.BUSY -> lastBusy?.let { ResultProgressBody(it) }

            ResultShellMode.RESULT -> ResultBody(
                result = result,
                headline = headline,
                detail = detail,
                probe = probe,
                onReportIssue = onReportIssue,
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
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
 * Split out so the shell above can keep the idle and completed states on the same card. The remembered
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
    onReportIssue: () -> Unit,
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
    val diagnostic = result.diagnostic
    val diagnosticText = remember(diagnostic, result.tone) {
        diagnostic?.toSafeText()
            ?: "SRO-DIAGNOSTIC/1\nresult=${result.tone.name}; failure=UNKNOWN"
    }
    val hasDetails = !detail.isNullOrBlank() || !probe.isNullOrBlank() || diagnostic != null
    val interactionSource = rememberCardInteractionSource()
    var detailsExpanded by remember(result) { mutableStateOf(false) }
    val actionColors = ButtonDefaults.textButtonColors(
        containerColor = content.copy(alpha = 0.12f),
        contentColor = content,
    )
    val actionPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
    val headerClick = if (hasDetails) {
        Modifier.cardHeaderClick(interactionSource) { detailsExpanded = !detailsExpanded }
    } else {
        Modifier
    }
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
            .cardRipple(interactionSource),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(headerClick)
                .padding(start = 14.dp, top = 10.dp, end = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToneMark(tone = result.tone, tint = content)
            Text(
                text = headline,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = content,
            )
            if (hasDetails) {
                DisclosureChevron(
                    expanded = detailsExpanded,
                    modifier = Modifier.size(24.dp),
                    tint = content,
                    onToggle = { detailsExpanded = !detailsExpanded },
                )
            }
        }

        AnimatedVisibility(
            visible = detailsExpanded,
            enter = fadeIn(animationSpec = tween(ResultFadeMillis, easing = LinearOutSlowInEasing)) +
                expandVertically(
                    animationSpec = tween(ResultResizeMillis, easing = LinearOutSlowInEasing),
                ),
            exit = fadeOut(animationSpec = tween(ResultFadeMillis, easing = LinearOutSlowInEasing)) +
                shrinkVertically(
                    animationSpec = tween(ResultResizeMillis, easing = LinearOutSlowInEasing),
                ),
        ) {
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // These local details are intentionally shown only after an explicit tap. They are useful
                // on the device, but the share/copy action below uses the allow-listed diagnostic instead.
                if (detail != null) {
                    SelectionContainer {
                        Text(
                            text = detail.take(MaxLocalDetailChars),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall,
                            color = content,
                        )
                    }
                }
                if (probe != null) {
                    ProbeBlock(probe = probe.take(MaxLocalDetailChars), tint = content)
                }
                if (diagnostic != null) {
                    Text(
                        text = stringResource(R.string.diagnostic_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = content,
                    )
                    ProbeBlock(probe = diagnosticText, tint = content)
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText("SRO diagnostic", diagnosticText)
                            )
                            copied = true
                        },
                        colors = actionColors,
                        contentPadding = actionPadding,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_content_copy),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(if (copied) R.string.action_copied else R.string.action_copy),
                            maxLines = 1,
                        )
                    }
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND)
                                            .setType("text/plain")
                                            .putExtra(Intent.EXTRA_TEXT, diagnosticText),
                                        null,
                                    )
                                )
                            }
                        },
                        colors = actionColors,
                        contentPadding = actionPadding,
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_share), maxLines = 1)
                    }
                    TextButton(
                        onClick = onReportIssue,
                        colors = actionColors,
                        contentPadding = actionPadding,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_report_issue), maxLines = 1)
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
        ResultTone.IDLE -> OutcomeMark(
            mark = Icons.Filled.Info,
            tint = tint,
            polygon = MaterialShapes.Circle,
        )
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
        ResultTone.IDLE -> PanelColors(scheme.surfaceContainerLow, scheme.onSurfaceVariant)
    }
}
