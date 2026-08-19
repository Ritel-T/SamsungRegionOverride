package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riteldevelopment.carriertestoverride.data.TargetApp
import com.riteldevelopment.carriertestoverride.data.WipeMode

/**
 * Making the apps re-read the region that was just written.
 *
 * Changing telephony is only half the job — Galaxy Store and the rest latch their region at startup, so
 * an override that has landed is invisible to them until their process is gone. Apply and restore already
 * force-stop these apps; this panel is the same thing on demand, plus the two options that only make
 * sense when a human is asking for them: throwing storage away, and opening the app afterwards.
 *
 * One button per app rather than one button for all of them. Relaunching brings an app to the foreground,
 * so a bulk run would throw three apps up in sequence and leave the user wherever the last one landed —
 * fine as an automatic step after apply, wrong as something you press deliberately. Per-app also means
 * the report describes exactly one app, and it is the shape a user-chosen app list drops into unchanged.
 *
 * The wipe level stays shared: it is a statement about how hard to reset, not about which app, and the
 * middle level does not work everywhere. Hiding that behind the buttons would make the tool claim a wipe
 * it cannot perform.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetAppsPanel(
    apps: List<TargetApp>,
    wipeMode: WipeMode,
    relaunch: Boolean,
    enabled: Boolean,
    onWipeModeChange: (WipeMode) -> Unit,
    onRelaunchChange: (Boolean) -> Unit,
    onRun: (TargetApp) -> Unit,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerLow)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MicroLabel(text = "TARGET APPS", modifier = Modifier.weight(1f))
            TextButton(onClick = onChoose, enabled = enabled) { Text("Choose") }
        }
        Spacer(Modifier.height(4.dp))

        // An empty list is a real choice, not a failure to load, and it changes what Apply does — so it
        // says what will happen rather than leaving a blank space where the rows were.
        if (apps.isEmpty()) {
            Text(
                text = "No apps selected. The region still changes; nothing gets stopped or wiped.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }

        apps.forEach { app ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app.packageName)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = app.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (app.installed) scheme.onSurface else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Only absence is badged. An installed app needs no marker — its live button says so —
                // whereas a missing one has to explain why its button is dead.
                if (!app.installed) {
                    StateBadge(text = "ABSENT", active = false)
                    Spacer(Modifier.width(8.dp))
                }
                FilledTonalButton(
                    onClick = { onRun(app) },
                    enabled = enabled && app.installed,
                ) {
                    Text(if (relaunch) "Stop & open" else "Force stop")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            WipeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = wipeMode == mode,
                    onClick = { onWipeModeChange(mode) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = WipeMode.entries.size,
                    ),
                ) {
                    Text(mode.label)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = wipeExplanation(wipeMode),
            style = MaterialTheme.typography.bodySmall,
            color = if (wipeMode.destructive) scheme.error else scheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(
                checked = relaunch,
                onCheckedChange = onRelaunchChange,
                enabled = enabled,
            )
            Text(
                text = "Open it afterwards",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
            )
        }
    }
}

private fun wipeExplanation(mode: WipeMode): String = when (mode) {
    WipeMode.NONE -> "Stop only, so the app re-reads the region on its next cold start."
    WipeMode.CACHE -> "Cache clear returns nothing on some One UI builds; the report says which."
    WipeMode.DATA -> "Erases app data. You will be signed out of that app."
}
