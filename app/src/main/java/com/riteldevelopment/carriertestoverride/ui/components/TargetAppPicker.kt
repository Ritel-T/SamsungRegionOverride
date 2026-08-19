package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
 */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val icon = rememberAppIcon(packageName)
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .size(AppIconSize)
            .clip(shape)
            .background(
                if (icon == null) MaterialTheme.colorScheme.surfaceContainerHighest
                else Color.Transparent
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                // The row's label names the app; announcing the icon too would only repeat it.
                contentDescription = null,
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
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    text = "Target apps",
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
                    label = { Text("App name or package") },
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
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Text(
                            text = "Reading installed apps…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (results.isEmpty()) {
                    Text(
                        text = "No app matches that.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(results, key = { it.packageName }) { app ->
                        AppPickerRow(
                            app = app,
                            checked = app.packageName in request.selected,
                            onToggle = { onToggle(app.packageName) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                        TextButton(onClick = onReset) { Text("Reset") }
                        Spacer(Modifier.weight(1f))
                    }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        onClick = { onConfirm(request) },
                        enabled = !request.loading,
                    ) {
                        Text("Save")
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
private fun selectionSummary(request: DialogRequest.ChooseTargetApps): String = when {
    request.loading -> "Apply and restore stop these apps so they re-read the region."
    request.selected.isEmpty() -> "Nothing selected — apply and restore will not stop any app."
    request.selected.size == 1 -> "1 app is stopped on apply and restore."
    else -> "${request.selected.size} apps are stopped on apply and restore."
}

@Composable
private fun AppPickerRow(
    app: TargetApp,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (app.installed) scheme.onSurface else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (app.installed) app.packageName else "${app.packageName} · not installed",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}
