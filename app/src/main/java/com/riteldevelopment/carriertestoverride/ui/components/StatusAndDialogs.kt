package com.riteldevelopment.carriertestoverride.ui.components

import android.icu.text.ListFormatter
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.riteldevelopment.carriertestoverride.R
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
 *
 * Connected-but-not-granted gets the same treatment for the same reason, one step milder. Every
 * privileged call fails without the grant, so green would be claiming a working link that is not
 * working; but unlike a wrong uid this is a thing the user fixes in Shizuku in one tap, so it takes the
 * `tertiary` attention colour that [ShizukuStatus.NotRunning] already uses rather than `error`. The
 * padlock says the same thing in silhouette, which is what carries it in a grayscale screenshot.
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
            value = AnnotatedString(stringResource(R.string.shizuku_not_running)),
        )

        is ShizukuStatus.Connected -> ShizukuVisual(
            icon = if (status.granted) Icons.Filled.CheckCircle else Icons.Filled.Lock,
            tint = when {
                !status.privileged -> MaterialTheme.colorScheme.error
                !status.granted -> MaterialTheme.colorScheme.tertiary
                else -> LocalOverrideColors.current.success
            },
            value = AnnotatedString(
                stringResource(
                    R.string.shizuku_connected,
                    status.uid,
                    stringResource(
                        if (status.granted) R.string.shizuku_granted
                        else R.string.shizuku_not_granted
                    ),
                )
            ),
        )

        is ShizukuStatus.Unavailable -> ShizukuVisual(
            icon = Icons.Filled.Warning,
            tint = MaterialTheme.colorScheme.error,
            value = AnnotatedString(stringResource(R.string.shizuku_unavailable, status.reason)),
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

    // Shizuku being started, granted or revoked happens in another app, so this row's state changes on
    // the way back into this one. Animating the tint means the change is still arriving as the user
    // looks at it, rather than having already happened before the window was drawn.
    val tint by animateColorAsState(
        targetValue = visual.tint,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "shizukuTint",
    )

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
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            // The label stays neutral: it names the field, and only the value carries state colour.
            MicroLabel(text = stringResource(R.string.label_shizuku))
        }
        Text(
            text = visual.value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = tint,
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
    targetAppsAreDefault: Boolean,
    onDismiss: () -> Unit,
    onConfirmApply: (DialogRequest.ConfirmApply) -> Unit,
    onConfirmRestore: (SimInfo) -> Unit,
    onConfirmClearAll: (SimInfo) -> Unit,
    onConfirmWipeData: (List<TargetApp>) -> Unit,
    onToggleTargetApp: (String) -> Unit,
    onConfirmTargetApps: (DialogRequest.ChooseTargetApps) -> Unit,
    onResetTargetApps: () -> Unit,
) {
    when (dialog) {
        null -> Unit

        is DialogRequest.ChooseTargetApps -> TargetAppPickerDialog(
            request = dialog,
            // Offered only once there is a custom list to revert, so the control is never a no-op.
            showReset = !targetAppsAreDefault,
            onToggle = onToggleTargetApp,
            onConfirm = onConfirmTargetApps,
            onReset = onResetTargetApps,
            onDismiss = onDismiss,
        )

        is DialogRequest.ConfirmApply -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.dialog_apply_title)) },
            text = { Text(applyDialogBody(dialog)) },
            confirmButton = {
                TextButton(onClick = { onConfirmApply(dialog) }) {
                    Text(stringResource(R.string.action_start_disguise))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )

        is DialogRequest.RestoreWithoutMarkers -> {
            val sim = dialog.sim
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.dialog_restore_without_markers_title)) },
                text = {
                    Text(stringResource(R.string.dialog_restore_without_markers_body))
                },
                confirmButton = {
                    TextButton(onClick = { onConfirmRestore(sim) }) {
                        Text(stringResource(R.string.dialog_restore_anyway))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        is DialogRequest.ConfirmClearAll -> {
            val sim = dialog.sim
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.dialog_clear_all_title)) },
                text = { Text(clearAllDialogBody(sim)) },
                confirmButton = {
                    TextButton(onClick = { onConfirmClearAll(sim) }) {
                        // This wipes overrides this tool never wrote and cannot rebuild, so the confirm
                        // label is tinted error. The button stays a TextButton: a filled destructive
                        // button would out-weight "Cancel" and make the safe choice the harder one to hit.
                        Text(
                            text = stringResource(R.string.action_clear),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        is DialogRequest.ConfirmWipeData -> {
            val apps = dialog.apps
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.dialog_erase_data_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.dialog_erase_data_body,
                            ListFormatter.getInstance(LocalConfiguration.current.locales[0])
                                .format(apps.map { it.label }),
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onConfirmWipeData(apps) }) {
                        Text(
                            text = stringResource(R.string.action_erase),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun applyDialogBody(request: DialogRequest.ConfirmApply): AnnotatedString {
    val sim = request.sim
    val target = request.target
    val layers = request.layers
    val simLine = stringResource(
        R.string.dialog_apply_sim,
        stringResource(R.string.sim_number, sim.slotIndex + 1),
        sim.subId,
    )
    val countryLine = stringResource(R.string.dialog_apply_country, target.countryIso)
    val networkPrefix = stringResource(R.string.dialog_apply_network, "")
    val nameLine = stringResource(R.string.dialog_apply_name, target.carrierName)
    val framework = stringResource(R.string.dialog_apply_framework)
    val risk = when {
        (layers.simIdentity || sim.simLayerLive) && (layers.appCountry || sim.countryLayerLive) ->
            stringResource(R.string.dialog_apply_risk_both)
        layers.simIdentity || sim.simLayerLive -> stringResource(R.string.dialog_apply_risk_network)
        else -> stringResource(R.string.dialog_apply_risk_country)
    }

    return buildAnnotatedString {
        append(simLine)

        append("\n")
        var wrote = false
        if (layers.appCountry) {
            append(countryLine)
            wrote = true
        }
        if (layers.simIdentity) {
            if (wrote) append("  +  ")
            append(networkPrefix)
            appendFigures(target.mccMnc)
        }

        append("\n")
        append(nameLine)
        append("\n\n")
        append(framework)
        append("\n\n")
        append(risk)
    }
}

@Composable
private fun clearAllDialogBody(sim: SimInfo): AnnotatedString =
    AnnotatedString(stringResource(R.string.dialog_clear_all_body, sim.subId))
