package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.riteldevelopment.carriertestoverride.data.ShizukuStatus
import com.riteldevelopment.carriertestoverride.data.SimInfo
import com.riteldevelopment.carriertestoverride.data.TargetApp
import com.riteldevelopment.carriertestoverride.ui.DialogRequest
import com.riteldevelopment.carriertestoverride.ui.theme.LocalOverrideColors
import com.riteldevelopment.carriertestoverride.ui.theme.TabularFigures

/**
 * Tabular figures are applied as a *span* over each numeric run rather than merged into the paragraph
 * style: `TabularFigures` also carries a weight and extra tracking, which is right for a column of codes
 * and wrong for a sentence. Applied per run, subId and MCC/MNC stay comparable while the prose around
 * them is set normally.
 */
private val FigureSpan: SpanStyle = TabularFigures.toSpanStyle()

private fun AnnotatedString.Builder.appendFigures(value: String) {
    withStyle(FigureSpan) { append(value) }
}

/** Everything the status row derives from [ShizukuStatus], resolved in one `when` instead of three. */
private class ShizukuVisual(
    val icon: ImageVector,
    val tint: Color,
    val value: AnnotatedString,
)

/**
 * Shizuku liveness, as a single row.
 *
 * Deliberately not a card: the screen already stacks several blocks, and connection state is context for
 * everything below it rather than a thing you act on. It reads as a caption, not a panel.
 *
 * A [ShizukuStatus.Connected] whose uid is neither shell nor root is coloured with `error`, not `success`
 * — Shizuku running under an unexpected uid means the privileged calls will fail in confusing ways later,
 * so it is flagged as a fault here rather than being reported as a healthy connection.
 */
@Composable
fun ShizukuStatusRow(
    status: ShizukuStatus,
    modifier: Modifier = Modifier,
) {
    val visual = when (status) {
        ShizukuStatus.NotRunning -> ShizukuVisual(
            icon = Icons.Filled.Warning,
            tint = MaterialTheme.colorScheme.tertiary,
            value = AnnotatedString("Not running — start Shizuku first"),
        )

        is ShizukuStatus.Connected -> ShizukuVisual(
            icon = if (status.granted) Icons.Filled.CheckCircle else Icons.Filled.Lock,
            tint = if (status.privileged) {
                LocalOverrideColors.current.success
            } else {
                MaterialTheme.colorScheme.error
            },
            value = buildAnnotatedString {
                append("Connected · uid ")
                appendFigures(status.uid.toString())
                append(" · ")
                append(if (status.granted) "granted" else "not granted")
            },
        )

        is ShizukuStatus.Unavailable -> ShizukuVisual(
            icon = Icons.Filled.Warning,
            tint = MaterialTheme.colorScheme.error,
            value = AnnotatedString("Unavailable: ${status.reason}"),
        )
    }

    // The reason string is an exception class name plus message and can be arbitrarily long. It must
    // wrap: an ellipsised exception name is worthless for diagnosis, which is the only reason it is
    // surfaced at all. So the value takes the remaining width and no maxLines is set.
    //
    // That means the row can grow past one line, so the lead cluster is top-aligned and given exactly one
    // body line of height — it then centres on the *first* line of the value instead of drifting toward
    // the middle of a wrapped paragraph. Resolved through Density so it tracks the user's font scale.
    val leadHeight = with(LocalDensity.current) {
        MaterialTheme.typography.bodySmall.lineHeight.toDp()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.height(leadHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = visual.icon,
                // The value text says the same thing; announcing the icon too would just repeat it.
                contentDescription = null,
                tint = visual.tint,
                modifier = Modifier.size(16.dp),
            )
            // The label stays neutral: it names the field, and only the value carries state colour.
            MicroLabel(text = "SHIZUKU")
        }
        Text(
            text = visual.value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = visual.tint,
        )
    }
}

/**
 * Every confirmation the screen can raise, in one place.
 *
 * Callers own dismissal: the view model's `confirm*` entry points already clear the dialog state, so the
 * confirm buttons deliberately do *not* also call [onDismiss] — doing both would clear it twice and, more
 * importantly, would let the dialog vanish even if a caller decided to keep it up.
 *
 * None of these repeat the standing [HazardNote] text. That block stays permanently visible on the
 * screen, and a dialog that re-states a warning the user has already read trains them to dismiss dialogs
 * without reading. Only the line specific to the pending action is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverrideDialogs(
    dialog: DialogRequest?,
    onDismiss: () -> Unit,
    onConfirmApply: (DialogRequest.ConfirmApply) -> Unit,
    onConfirmRestore: (SimInfo) -> Unit,
    onConfirmClearAll: (SimInfo) -> Unit,
    onConfirmWipeData: (List<TargetApp>) -> Unit,
) {
    when (dialog) {
        null -> Unit

        is DialogRequest.ConfirmApply -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Apply this region?") },
            text = { Text(applyDialogBody(dialog)) },
            confirmButton = {
                TextButton(onClick = { onConfirmApply(dialog) }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            },
        )

        is DialogRequest.RestoreWithoutMarkers -> {
            val sim = dialog.sim
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Nothing recorded for this SIM") },
                text = {
                    Text(
                        "Restoring anyway uses the layer switches as they stand. Clearing app country " +
                            "removes every transient CarrierConfig test value on this subId, and with no " +
                            "snapshot the SIM identity falls back to the real records — a reboot is surer."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onConfirmRestore(sim) }) {
                        Text("Restore anyway")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                },
            )
        }

        is DialogRequest.ConfirmClearAll -> {
            val sim = dialog.sim
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Clear every CarrierConfig test override?") },
                text = { Text(clearAllDialogBody(sim)) },
                confirmButton = {
                    TextButton(onClick = { onConfirmClearAll(sim) }) {
                        // This wipes overrides this tool never wrote and cannot rebuild, so the confirm
                        // label is tinted error. The button stays a TextButton: a filled destructive
                        // button would out-weight "Cancel" and make the safe choice the harder one to hit.
                        Text(
                            text = "Clear",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                },
            )
        }

        is DialogRequest.ConfirmWipeData -> {
            val apps = dialog.apps
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Erase app data?") },
                text = {
                    Text(
                        "This erases all data for ${apps.joinToString { it.label }}. " +
                            "You will be signed out of them and any downloads or local settings go with it."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onConfirmWipeData(apps) }) {
                        Text(
                            text = "Erase",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

private fun applyDialogBody(request: DialogRequest.ConfirmApply): AnnotatedString = buildAnnotatedString {
    val sim = request.sim
    val target = request.target
    val layers = request.layers

    append("${sim.displayName} · sub ")
    appendFigures(sim.subId.toString())

    append("\n")
    // `requestApply()` refuses an empty selection before this dialog can be raised, so at least one of
    // the two branches always runs and the line is never left dangling.
    var wrote = false
    if (layers.simIdentity) {
        append("SIM identity ")
        appendFigures(target.mccMnc)
        wrote = true
    }
    if (layers.appCountry) {
        if (wrote) append("  +  ")
        append("App country ${target.countryIso}")
    }

    append("\nName: ${target.carrierName}")
    append("\n\nThe radio keeps its real network; only what the framework reports changes.")
}

private fun clearAllDialogBody(sim: SimInfo): AnnotatedString = buildAnnotatedString {
    append("Removes the transient and persistent CarrierConfig test values on sub ")
    appendFigures(sim.subId.toString())
    append(
        ", including any written by other tools. It does not restore the SIM identity layer, and it " +
            "cannot rebuild what another tool had set."
    )
}
