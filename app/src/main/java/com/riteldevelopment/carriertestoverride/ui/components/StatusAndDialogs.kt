package com.riteldevelopment.carriertestoverride.ui.components

import android.icu.text.ListFormatter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import com.riteldevelopment.carriertestoverride.R
import com.riteldevelopment.carriertestoverride.data.SimInfo
import com.riteldevelopment.carriertestoverride.data.TargetApp
import com.riteldevelopment.carriertestoverride.ui.DialogRequest

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

        is DialogRequest.RestoreWithoutMarkers -> {
            val sim = dialog.sim
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.dialog_restore_without_markers_title)) },
                text = {
                    Text(stringResource(R.string.dialog_restore_without_markers_body))
                },
                confirmButton = {
                    ElasticTextButton(onClick = { onConfirmRestore(sim) }) {
                        Text(stringResource(R.string.dialog_restore_anyway))
                    }
                },
                dismissButton = {
                    ElasticTextButton(onClick = onDismiss) {
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
                    ElasticTextButton(onClick = { onConfirmClearAll(sim) }) {
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
                    ElasticTextButton(onClick = onDismiss) {
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
                    ElasticTextButton(onClick = { onConfirmWipeData(apps) }) {
                        Text(
                            text = stringResource(R.string.action_erase),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    ElasticTextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun clearAllDialogBody(sim: SimInfo): AnnotatedString =
    AnnotatedString(stringResource(R.string.dialog_clear_all_body, sim.subId))
