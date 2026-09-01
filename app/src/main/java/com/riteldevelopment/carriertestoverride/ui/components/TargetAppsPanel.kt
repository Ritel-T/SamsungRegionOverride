package com.riteldevelopment.carriertestoverride.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riteldevelopment.carriertestoverride.R
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val motion = MaterialTheme.motionScheme
    var expanded by rememberSaveable { mutableStateOf(false) }
    val containerColor by animateColorAsState(
        targetValue = if (expanded) scheme.surfaceContainerLow else scheme.surfaceContainer,
        animationSpec = motion.fastEffectsSpec(),
        label = "targetAppsContainer",
    )
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
    ) {
        Column {
            ListItem(
                onClick = { expanded = !expanded },
                leadingContent = { TargetAppIconStack(apps) },
                trailingContent = {
                    DisclosureChevron(expanded = expanded, onToggle = { expanded = !expanded })
                },
                supportingContent = {
                    Text(
                        if (apps.isEmpty()) {
                            stringResource(R.string.target_apps_empty_summary)
                        } else {
                            pluralStringResource(
                                R.plurals.target_apps_selected_summary,
                                apps.size,
                                apps.size,
                            )
                        }
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            ) {
                Text(
                    text = stringResource(R.string.target_apps_heading),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = motion.fastEffectsSpec()) +
                    expandVertically(animationSpec = motion.defaultSpatialSpec()),
                exit = fadeOut(animationSpec = motion.fastEffectsSpec()) +
                    shrinkVertically(animationSpec = motion.defaultSpatialSpec()),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        FilledTonalButton(
                            onClick = onChoose,
                            shapes = ButtonDefaults.shapes(),
                            enabled = enabled,
                        ) {
                            Text(stringResource(R.string.action_choose))
                        }
                    }

                    if (apps.isEmpty()) {
                        Text(
                            text = stringResource(R.string.target_apps_none),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }

                    apps.forEachIndexed { index, app ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 18.dp),
                                color = scheme.outlineVariant,
                            )
                        }
                        ListItem(
                            supportingContent = if (app.installed) null else {
                                { Text(stringResource(R.string.badge_absent)) }
                            },
                            leadingContent = { AppIcon(app.packageName) },
                            trailingContent = {
                                FilledTonalButton(
                                    onClick = { onRun(app) },
                                    shapes = ButtonDefaults.shapes(),
                                    enabled = enabled && app.installed,
                                ) {
                                    Text(
                                        stringResource(
                                            if (relaunch) R.string.target_app_stop_open
                                            else R.string.target_app_force_stop
                                        )
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        ) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyLargeEmphasized,
                                color = if (app.installed) scheme.onSurface else scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    val labels = WipeMode.entries.map { stringResource(it.labelRes) }
                    // Every item is weighted, so the three states always split the row evenly. That is
                    // what makes the empty overflow indicator safe: these are three mutually exclusive
                    // choices, one of them hidden behind a menu would be worse than three cramped ones,
                    // and a long translation ellipsizes inside its third instead of moving out of sight.
                    ButtonGroup(
                        overflowIndicator = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                    ) {
                        WipeMode.entries.forEachIndexed { index, mode ->
                            toggleableItem(
                                checked = wipeMode == mode,
                                label = labels[index],
                                onCheckedChange = { checked -> if (checked) onWipeModeChange(mode) },
                                weight = 1f,
                                enabled = enabled,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = wipeExplanation(wipeMode),
                        modifier = Modifier.padding(horizontal = 18.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (wipeMode.destructive) scheme.error else scheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = relaunch,
                                enabled = enabled,
                                role = Role.Switch,
                                onValueChange = onRelaunchChange,
                            )
                            .padding(start = 18.dp, top = 8.dp, end = 14.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.target_app_open_after),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                        )
                        Switch(
                            checked = relaunch,
                            onCheckedChange = null,
                            enabled = enabled,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetAppIconStack(apps: List<TargetApp>) {
    val visible = apps.take(3)
    if (visible.isEmpty()) {
        Box(
            modifier = Modifier
                .size(AppIconSize)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text("0", style = MaterialTheme.typography.labelMediumEmphasized)
        }
        return
    }

    Box(modifier = Modifier.width(AppIconSize + 12.dp * (visible.size - 1))) {
        visible.forEachIndexed { index, app ->
            AppIcon(
                packageName = app.packageName,
                modifier = Modifier.offset(x = 12.dp * index),
            )
        }
    }
}

@Composable
private fun wipeExplanation(mode: WipeMode): String = stringResource(
    when (mode) {
        WipeMode.NONE -> R.string.wipe_keep_summary
        WipeMode.CACHE -> R.string.wipe_cache_summary
        WipeMode.DATA -> R.string.wipe_data_summary
    }
)
