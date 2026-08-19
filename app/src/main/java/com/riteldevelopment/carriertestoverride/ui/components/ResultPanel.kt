package com.riteldevelopment.carriertestoverride.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riteldevelopment.carriertestoverride.ui.ResultState
import com.riteldevelopment.carriertestoverride.ui.ResultTone
import com.riteldevelopment.carriertestoverride.ui.theme.LocalOverrideColors
import kotlinx.coroutines.delay

/** How much of the report is shown before the user asks for the rest. Six lines is roughly one screen. */
private const val CollapsedDetailLines = 6

/** How long the copy button admits it did something, on the versions of Android that say nothing. */
private const val CopiedFeedbackMillis = 1600L

/**
 * The report the privileged operation produced.
 *
 * This is not a status footnote — it is the artefact users paste into bug threads, so it is sized and
 * spaced to be read and selected rather than glanced at. Two text bodies live here with opposite needs:
 * the narrative report, which must wrap and use the system face, and the runtime probe, which must *not*
 * wrap and must stay monospaced so reflected signatures line up column-wise.
 */
@Composable
fun ResultPanel(
    result: ResultState,
    modifier: Modifier = Modifier,
) {
    val detail = result.detail
    val probe = result.probe

    // Nothing has run yet. A tinted box with an icon here would claim an outcome that does not exist, so
    // the initial state gets no container at all.
    //
    // Keyed on tone, not on emptiness. Every validation refusal — "No usable SIM is selected", "Shizuku
    // is not installed" — is an ERROR with a headline and nothing else, and testing emptiness alone
    // rendered those as quiet grey prose indistinguishable from the idle state.
    if (detail == null && probe == null && result.tone == ResultTone.IDLE) {
        Text(
            text = result.headline,
            modifier = modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val palette = panelColors(result.tone)
    // The panel changes tone in place as an operation moves PROGRESS -> SUCCESS/PARTIAL/ERROR; a hard cut
    // reads as the whole block being replaced.
    val container by animateColorAsState(palette.container, label = "resultContainer")
    val content by animateColorAsState(palette.content, label = "resultContent")

    val context = LocalContext.current
    val clipText = remember(result) {
        listOfNotNull(result.headline, detail, probe).joinToString("\n\n")
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
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .padding(start = 14.dp, top = 10.dp, end = 10.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToneMark(tone = result.tone, tint = content)
            Text(
                text = result.headline,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
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
                    text = if (copied) "Copied" else "Copy",
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
                    Text(
                        text = detail,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(),
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
                                text = if (detailExpanded) "LESS" else "MORE",
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
                            text = "RUNTIME PROBE",
                            expanded = probeExpanded,
                            tint = content,
                            onClick = { probeExpanded = !probeExpanded },
                        )
                    }
                    AnimatedVisibility(
                        visible = probeExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
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
            .clip(RoundedCornerShape(8.dp))
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
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
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
@Composable
private fun ToneMark(
    tone: ResultTone,
    tint: Color,
) {
    when (tone) {
        ResultTone.PROGRESS -> {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = tint,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
        }
        ResultTone.SUCCESS -> MarkIcon(mark = Icons.Filled.CheckCircle, tint = tint)
        ResultTone.PARTIAL -> MarkIcon(mark = Icons.Filled.Warning, tint = tint)
        ResultTone.ERROR -> MarkIcon(mark = Icons.Filled.Close, tint = tint)
        ResultTone.IDLE -> Unit
    }
}

@Composable
private fun MarkIcon(
    mark: ImageVector,
    tint: Color,
) {
    Icon(
        imageVector = mark,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = tint,
    )
    Spacer(Modifier.width(8.dp))
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
        ResultTone.PROGRESS, ResultTone.IDLE -> PanelColors(scheme.surfaceContainer, scheme.onSurface)
    }
}
