package com.riteldevelopment.carriertestoverride.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.riteldevelopment.carriertestoverride.data.OverrideRepository
import com.riteldevelopment.carriertestoverride.data.SimInfo
import com.riteldevelopment.carriertestoverride.data.TargetApp
import com.riteldevelopment.carriertestoverride.data.WipeMode
import com.riteldevelopment.carriertestoverride.ui.components.BlockGap
import com.riteldevelopment.carriertestoverride.ui.components.HazardNote
import com.riteldevelopment.carriertestoverride.ui.components.LayerSection
import com.riteldevelopment.carriertestoverride.ui.components.MicroLabel
import com.riteldevelopment.carriertestoverride.ui.components.OverrideDialogs
import com.riteldevelopment.carriertestoverride.ui.components.PresetField
import com.riteldevelopment.carriertestoverride.ui.components.QuickPickRow
import com.riteldevelopment.carriertestoverride.ui.components.RealVersusDisguise
import com.riteldevelopment.carriertestoverride.ui.components.ResultPanel
import com.riteldevelopment.carriertestoverride.ui.components.ShizukuStatusRow
import com.riteldevelopment.carriertestoverride.ui.components.SimSelector
import com.riteldevelopment.carriertestoverride.ui.components.TargetAppsPanel
import com.riteldevelopment.carriertestoverride.ui.components.rememberAppIcon

/** Everything the screen can do, hoisted so the screen itself stays previewable and stateless. */
data class OverrideActions(
    val onSelectSim: (Int) -> Unit,
    val onSelectPreset: (RegionPreset) -> Unit,
    val onMccMncChange: (String) -> Unit,
    val onCountryIsoChange: (String) -> Unit,
    val onCarrierNameChange: (String) -> Unit,
    val onSimIdentityLayerChange: (Boolean) -> Unit,
    val onAppCountryLayerChange: (Boolean) -> Unit,
    val onCarrierNameOverrideChange: (Boolean) -> Unit,
    val onWipeModeChange: (WipeMode) -> Unit,
    val onRelaunchChange: (Boolean) -> Unit,
    val onApply: () -> Unit,
    val onRestore: () -> Unit,
    val onClearAll: () -> Unit,
    val onRefreshApps: (TargetApp) -> Unit,
    val onRescan: () -> Unit,
    val onCancel: () -> Unit,
    val onOpenShizuku: () -> Unit,
    val onChooseTargetApps: () -> Unit,
    val onToggleTargetApp: (String) -> Unit,
    val onConfirmTargetApps: (DialogRequest.ChooseTargetApps) -> Unit,
    val onResetTargetApps: () -> Unit,
    val onDismissDialog: () -> Unit,
    val onConfirmApply: (DialogRequest.ConfirmApply) -> Unit,
    val onConfirmRestoreWithoutMarkers: (SimInfo) -> Unit,
    val onConfirmClearAll: (SimInfo) -> Unit,
    val onConfirmWipeData: (List<TargetApp>) -> Unit,
)

/**
 * The standing note.
 *
 * Names Country rather than Network because that is what the measurements point at: with only Network
 * applied IMS stayed registered across every run, while Country live is what the deregistration tracks.
 * It says "can" rather than "will" for the same reason — the same build, run twice, went both ways, and
 * a warning that overstates its certainty is one the user catches out and then stops believing.
 */
private const val RISK_TEXT =
    "Country can stop calls and SMS on this SIM until you Restore; Network on its own has not. " +
        "Cycling the SIM does not bring them back while the override is live. Restore also clears " +
        "every transient CarrierConfig value on this SIM, including ones other tools wrote, and a " +
        "reboot undoes everything."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverrideScreen(
    state: OverrideUiState,
    actions: OverrideActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Region Override") },
                actions = {
                    ShizukuButton(onClick = actions.onOpenShizuku)
                    OverflowMenu(state = state, actions = actions)
                },
            )
        },
        bottomBar = { ActionBar(state = state, actions = actions) },
    ) { padding ->
        // Horizontal insets are real in landscape on a cutout device; dropping them would let the
        // notch sit on top of the identity digits this screen exists to let the user compare.
        val direction = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp + padding.calculateStartPadding(direction),
                end = 16.dp + padding.calculateEndPadding(direction),
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(BlockGap),
        ) {
            item("shizuku") {
                ShizukuStatusRow(status = state.shizuku)
            }

            item("sims") {
                SimSelector(
                    sims = state.sims,
                    slotCount = state.slotCount,
                    selectedSubId = state.selectedSubId,
                    scanError = state.simScanError,
                    enabled = !state.isBusy,
                    onSelect = actions.onSelectSim,
                )
            }

            item("diff") {
                RealVersusDisguise(
                    sim = state.selectedSim,
                    targetMccMnc = state.mccMnc,
                    targetCountryIso = state.countryIso,
                    targetCarrierName = state.carrierName,
                    countryLayerArmed = state.layers.appCountry,
                    networkLayerArmed = state.layers.simIdentity,
                )
            }

            item("target") {
                TargetBlock(state = state, actions = actions)
            }

            // Country before Network, matching both the order the service writes them in and the order
            // a user needs them: the country signal is what most apps read, so it is the one most
            // people are here for. The network signal is the specialist case.
            item("layer-country") {
                CountryLayer(state = state, actions = actions)
            }

            item("layer-sim") {
                NetworkLayer(state = state, actions = actions)
            }

            item("apps") {
                TargetAppsPanel(
                    apps = state.targetApps,
                    wipeMode = state.wipeMode,
                    relaunch = state.relaunchApps,
                    enabled = !state.isBusy,
                    onWipeModeChange = actions.onWipeModeChange,
                    onRelaunchChange = actions.onRelaunchChange,
                    onRun = actions.onRefreshApps,
                    onChoose = actions.onChooseTargetApps,
                )
            }

            item("result") {
                ResultPanel(result = state.result)
            }

            item("risk") {
                HazardNote(text = RISK_TEXT)
            }

            item("device") {
                DeviceLine(state.device)
            }
        }
    }

    OverrideDialogs(
        dialog = state.dialog,
        targetAppsAreDefault = state.targetAppsAreDefault,
        onDismiss = actions.onDismissDialog,
        onConfirmApply = actions.onConfirmApply,
        onConfirmRestore = actions.onConfirmRestoreWithoutMarkers,
        onConfirmClearAll = actions.onConfirmClearAll,
        onConfirmWipeData = actions.onConfirmWipeData,
        onToggleTargetApp = actions.onToggleTargetApp,
        onConfirmTargetApps = actions.onConfirmTargetApps,
        onResetTargetApps = actions.onResetTargetApps,
    )
}

/**
 * The one place this screen sends people when it cannot proceed.
 *
 * Shizuku's own launcher icon and nothing else. It sat next to a leave-the-app glyph for one revision,
 * on the reasoning that an app icon states an identity but not a verb; in the bar it just read as two
 * marks for one control. An app icon in an app bar is already a recognised idiom for "go to that app",
 * and the status line directly below says whether Shizuku is running, so the verb was never carrying
 * the weight the extra glyph cost.
 *
 * The glyph survives as the fallback for a phone with no Shizuku installed, where there is no icon to
 * draw. The action still leads somewhere useful there — the view model answers a missing package with
 * an install hint rather than silence — so the control must not vanish, or that hint is unreachable.
 */
@Composable
private fun ShizukuButton(onClick: () -> Unit) {
    val icon = rememberAppIcon(KnownPackages.SHIZUKU)
    IconButton(onClick = onClick) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = "Open Shizuku",
                modifier = Modifier.size(24.dp),
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = "Open Shizuku",
            )
        }
    }
}

/**
 * The destructive action lives here rather than as a third button: it is rare, and it wipes persistent
 * CarrierConfig overrides written by *other* tools, so it should not sit a thumb-width from Apply.
 */
@Composable
private fun OverflowMenu(state: OverrideUiState, actions: OverrideActions) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "More")
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Rescan SIMs") },
            enabled = !state.isBusy,
            onClick = {
                expanded = false
                actions.onRescan()
            },
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = "Clear all CarrierConfig overrides…",
                    color = MaterialTheme.colorScheme.error,
                )
            },
            enabled = state.canClearAll,
            onClick = {
                expanded = false
                actions.onClearAll()
            },
        )
    }
}

/**
 * The preset picker and the carrier name.
 *
 * The name lives here rather than inside a layer because *both* layers consume it — the network layer
 * writes it as PNN/SPN, and the country layer can write it as the subscription's display name.
 * Everything that belongs to exactly one layer lives inside that layer instead.
 */
@Composable
private fun TargetBlock(state: OverrideUiState, actions: OverrideActions) {
    Column {
        MicroLabel("PRETEND TO BE")
        Spacer(Modifier.height(8.dp))
        PresetField(
            preset = state.preset,
            enabled = !state.isBusy,
            onSelect = actions.onSelectPreset,
        )
        // Under the field they fill, so tapping one shows its effect in the line directly above.
        Spacer(Modifier.height(8.dp))
        QuickPickRow(
            quickPicks = state.quickPicks,
            selectedId = state.presetId,
            enabled = !state.isBusy,
            onSelect = actions.onSelectPreset,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = state.carrierName,
            onValueChange = actions.onCarrierNameChange,
            enabled = !state.isBusy,
            singleLine = true,
            label = { Text("Carrier name") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The everyday switch: what country the phone reports to apps.
 *
 * Named for the signal rather than the mechanism. "App country" described where the value is written;
 * "Country" describes what the user is setting, and the icons beside it say who will believe it.
 */
@Composable
private fun CountryLayer(state: OverrideUiState, actions: OverrideActions) {
    val sim = state.selectedSim
    LayerSection(
        title = "Country",
        subtitle = "TikTok and most apps read this",
        enabled = state.layers.appCountry,
        applied = sim?.countryLayerLive == true,
        accent = MaterialTheme.colorScheme.secondary,
        controlsEnabled = !state.isBusy,
        onEnabledChange = actions.onAppCountryLayerChange,
        readerPackages = KnownPackages.COUNTRY_READERS,
    ) {
        OutlinedTextField(
            value = state.countryIso,
            onValueChange = actions.onCountryIsoChange,
            enabled = !state.isBusy,
            singleLine = true,
            label = { Text("Country ISO") },
            supportingText = { Text("Two letters, e.g. gb") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = state.layers.carrierNameOverride,
                onCheckedChange = actions.onCarrierNameOverrideChange,
                enabled = !state.isBusy,
            )
            Column {
                Text(
                    text = "Rename the SIM too",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // The old label said "Also override the display name", which named a field rather than
                // an effect and left people unable to guess what ticking it would do.
                Text(
                    text = "Shows the carrier name above in the status bar and Settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The one fact that should reach someone who reads nothing else on this screen, kept to a
        // single line and placed against the switch that causes it rather than in the standing note at
        // the bottom, which is where a reader who has already decided will not look.
        Spacer(Modifier.height(6.dp))
        Text(
            text = "May stop calls and SMS until you Restore.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** The specialist switch: the network code the SIM claims to be on. */
@Composable
private fun NetworkLayer(state: OverrideUiState, actions: OverrideActions) {
    val sim = state.selectedSim
    LayerSection(
        title = "Network",
        subtitle = "Galaxy Store and Samsung apps read this",
        enabled = state.layers.simIdentity,
        applied = sim?.simLayerLive == true,
        accent = MaterialTheme.colorScheme.primary,
        controlsEnabled = !state.isBusy,
        onEnabledChange = actions.onSimIdentityLayerChange,
        readerPackages = KnownPackages.NETWORK_READERS,
    ) {
        OutlinedTextField(
            value = state.mccMnc,
            onValueChange = actions.onMccMncChange,
            enabled = !state.isBusy,
            singleLine = true,
            label = { Text("MCC/MNC") },
            supportingText = { Text("5 or 6 digits") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        if (sim != null && !sim.isReady) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "This layer needs a READY SIM; ${sim.displayName} is ${sim.stateLabel}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Pinned so the primary action never drifts below the fold, and so the busy narration replaces the
 * controls in place rather than appearing somewhere else on screen.
 */
@Composable
private fun ActionBar(state: OverrideUiState, actions: OverrideActions) {
    Surface(tonalElevation = 3.dp) {
        Crossfade(targetState = state.busy, label = "actionBar") { busy ->
            if (busy == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = actions.onApply,
                        enabled = state.canApply,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Apply")
                    }
                    OutlinedButton(
                        onClick = actions.onRestore,
                        enabled = state.canRestore,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Restore")
                    }
                }
            } else {
                BusyBar(busy = busy, onCancel = actions.onCancel)
            }
        }
    }
}

/**
 * Named stages rather than a nameless spinner: waiting on Shizuku and waiting on the user's permission
 * grant are indistinguishable from a hang otherwise, and both can last indefinitely.
 */
@Composable
private fun BusyBar(busy: BusyState, onCancel: () -> Unit) {
    val stages = OverrideRepository.Stage.entries
    val step = stages.indexOf(busy.stage) + 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$step/${stages.size} · ${busy.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (busy.cancellable) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DeviceLine(device: DeviceInfo) {
    Text(
        text = "${device.manufacturer} ${device.model} · API ${device.apiLevel} · v${device.appVersion}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
