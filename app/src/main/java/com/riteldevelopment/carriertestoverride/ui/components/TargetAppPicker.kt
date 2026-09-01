package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.riteldevelopment.carriertestoverride.R
import com.riteldevelopment.carriertestoverride.data.TargetApp
import com.riteldevelopment.carriertestoverride.ui.DialogRequest
import java.util.Locale

/** The icon column width, shared by the picker rows and the panel rows so the two lists line up. */
val AppIconSize = 28.dp

/**
 * An app's icon, or a neutral placeholder while it loads or if it has none.
 *
 * The placeholder is a filled square rather than nothing, so a list of rows does not jitter sideways as
 * icons arrive one by one.
 *
 * Icons are decoded off the main thread and land a frame or two after the row does, which on a full
 * picker list means a dozen of them appearing at slightly different moments. Fading them in — and fading
 * the placeholder out underneath — turns that from a scatter of pops into the list settling. The
 * placeholder's own alpha is driven by the same value, so the two never both show at full strength.
 */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val icon = rememberAppIcon(packageName)
    val shape = MaterialTheme.shapes.small
    val loaded by animateFloatAsState(
        targetValue = if (icon == null) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "appIconFade",
    )
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier = modifier
            .size(AppIconSize)
            .clip(shape)
            .background(placeholder.copy(alpha = placeholder.alpha * (1f - loaded).coerceIn(0f, 1f))),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                // The row's label names the app; announcing the icon too would only repeat it.
                contentDescription = null,
                alpha = loaded.coerceIn(0f, 1f),
                modifier = Modifier.size(AppIconSize),
            )
        }
    }
}

/**
 * Choosing which apps this tool stops, wipes and relaunches.
 *
 * A plain [Dialog] for the same reason the region picker is one: it needs a tall scrolling body, and
 * AlertDialog's text slot fights that. Sizing is absolute rather than fractional because the dialog
 * window wraps its content, so a fractional width resolves against an unbounded constraint and silently
 * does nothing.
 *
 * The list is every launchable app, which is long, so it is searchable. Selection is a working set held
 * in the dialog: nothing is written until Save, and dismissing changes nothing.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TargetAppPickerDialog(
    request: DialogRequest.ChooseTargetApps,
    showReset: Boolean,
    onToggle: (String) -> Unit,
    onConfirm: (DialogRequest.ChooseTargetApps) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, request.available) {
        if (query.isBlank()) {
            request.available
        } else {
            val needle = query.lowercase(Locale.ROOT)
            request.available.filter {
                it.label.lowercase(Locale.ROOT).contains(needle) ||
                    it.packageName.lowercase(Locale.ROOT).contains(needle)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = stringResource(R.string.target_app_picker_title),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = selectionSummary(request),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    singleLine = true,
                    enabled = !request.loading,
                    label = { Text(stringResource(R.string.target_app_search)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
                Spacer(Modifier.height(8.dp))

                if (request.loading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LoadingIndicator(modifier = Modifier.size(28.dp))
                        Text(
                            text = stringResource(R.string.target_app_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (results.isEmpty()) {
                    Text(
                        text = stringResource(R.string.target_app_no_match),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(results, key = { it.packageName }) { app ->
                        AppPickerRow(
                            app = app,
                            checked = app.packageName in request.selected,
                            onToggle = { onToggle(app.packageName) },
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (showReset) {
                        TextButton(onClick = onReset) { Text(stringResource(R.string.action_reset)) }
                        Spacer(Modifier.weight(1f))
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(
                        onClick = { onConfirm(request) },
                        enabled = !request.loading,
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}

/**
 * States the consequence, not just the count.
 *
 * Zero selected is a legitimate choice — it means "change the region but leave my apps alone" — and it
 * is worth spelling out, because a bare "0 selected" reads like a mistake the dialog is about to make
 * on the user's behalf.
 */
@Composable
private fun selectionSummary(request: DialogRequest.ChooseTargetApps): String = when {
    request.loading -> stringResource(R.string.target_apps_loading_summary)
    request.selected.isEmpty() -> stringResource(R.string.target_apps_empty_summary)
    else -> pluralStringResource(
        R.plurals.target_apps_selected_summary,
        request.selected.size,
        request.selected.size,
    )
}

@Composable
private fun AppPickerRow(
    app: TargetApp,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    ListItem(
        checked = checked,
        onCheckedChange = { onToggle() },
        leadingContent = { AppIcon(app.packageName) },
        supportingContent = {
            Text(
                text = if (app.installed) app.packageName else {
                    stringResource(R.string.target_app_not_installed, app.packageName)
                },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Checkbox(checked = checked, onCheckedChange = null)
        },
    ) {
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLargeEmphasized,
            color = if (app.installed) {
                Color.Unspecified
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
