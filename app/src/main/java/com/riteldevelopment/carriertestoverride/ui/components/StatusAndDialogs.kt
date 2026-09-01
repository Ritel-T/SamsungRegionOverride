package com.riteldevelopment.carriertestoverride.ui.components

import android.icu.text.ListFormatter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.riteldevelopment.carriertestoverride.R
import com.riteldevelopment.carriertestoverride.data.SimInfo
import com.riteldevelopment.carriertestoverride.data.TargetApp
import com.riteldevelopment.carriertestoverride.ui.DialogRequest
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

/**
 * Every confirmation the screen can raise, in one place.
 *
 * Callers own dismissal: the view model's `confirm*` entry points already clear the dialog state, so the
 * confirm buttons deliberately do *not* also call [onDismiss] — doing both would clear it twice and, more
 * importantly, would let the dialog vanish even if a caller decided to keep it up.
 *
 * None of these repeat a warning already shown in the screen state. A dialog that re-states a warning the
 * user has already read trains them to dismiss dialogs without reading. Only the line specific to the
 * pending action is shown.
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
